#include <ggml-backend.h>
#include <llama.h>

#include <algorithm>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <cctype>
#include <cstdlib>
#include <memory>
#include <mutex>
#include <string>
#include <thread>
#include <unordered_map>
#include <vector>

namespace {

struct cached_llama_model {
  llama_model *model = nullptr;
  const llama_vocab *vocab = nullptr;

  explicit cached_llama_model(llama_model *loaded_model)
      : model(loaded_model),
        vocab(loaded_model == nullptr ? nullptr : llama_model_get_vocab(loaded_model)) {
  }

  ~cached_llama_model() {
    if (model != nullptr) {
      llama_model_free(model);
    }
  }

  cached_llama_model(const cached_llama_model &) = delete;
  cached_llama_model &operator=(const cached_llama_model &) = delete;
};

struct aqli_gguf_ctx {
  std::shared_ptr<cached_llama_model> model_ref;
  llama_context *ctx = nullptr;
  const llama_vocab *vocab = nullptr;
  std::mutex mutex;
  int n_gpu_layers = 0;
  int n_threads = 0;
  std::string backend_name = "CPU";
  int64_t model_load_us = 0;
  int64_t context_init_us = 0;
  int64_t last_tokenize_us = 0;
  int64_t last_prefill_us = 0;
  int64_t last_decode_us = 0;
  int64_t last_prompt_cache_us = 0;
  int last_prompt_tokens = 0;
  int last_generated_tokens = 0;
  int last_decoded_tokens = 0;
  int last_prompt_cache_min_tokens = 0;
  int last_prompt_cache_repeat_min_tokens = 0;
  bool last_prompt_cache_eager_short = false;
  std::string prompt_cache_key;
  std::vector<uint8_t> prompt_cache_state;
  int prompt_cache_tokens = 0;
  llama_state_seq_flags prompt_cache_flags = LLAMA_STATE_SEQ_FLAGS_NONE;
  std::vector<llama_token> shared_prompt_cache_tokens;
  std::vector<uint8_t> shared_prompt_cache_state;
  std::vector<llama_token> token_buffer;
  std::string token_cache_prompt;
  std::vector<llama_token> token_cache_tokens;
  int token_cache_count = 0;
  std::vector<char> piece_buffer;
  size_t last_output_bytes = 0;
  llama_batch prompt_batch = {};
  int prompt_batch_capacity = 0;
  llama_sampler *sampler = nullptr;
  bool sampler_greedy = false;
  float sampler_temperature = 0.0f;
  int sampler_top_k = 0;
  float sampler_top_p = 1.0f;
  uint32_t sampler_seed = 0;
  int64_t last_sampler_us = 0;
  bool last_sampler_reused = false;
  std::string last_tokenize_cache_status = "disabled";
  bool last_repeated_prompt = false;
  int shared_prompt_cache_token_count = 0;
  llama_state_seq_flags shared_prompt_cache_flags = LLAMA_STATE_SEQ_FLAGS_NONE;
  std::string last_prompt_cache_status = "disabled";

  ~aqli_gguf_ctx() {
    if (sampler != nullptr) {
      llama_sampler_free(sampler);
    }
    if (prompt_batch_capacity > 0) {
      llama_batch_free(prompt_batch);
    }
    if (ctx != nullptr) {
      llama_free(ctx);
    }
  }
};

static std::once_flag backend_once;
static std::mutex model_cache_mutex;
static std::unordered_map<std::string, std::shared_ptr<cached_llama_model>> model_cache;

static void set_error(char *error, size_t error_size, const std::string &message) {
  if (error == nullptr || error_size == 0) {
    return;
  }
  std::snprintf(error, error_size, "%s", message.c_str());
}

static int default_threads() {
  unsigned int detected = std::thread::hardware_concurrency();
  if (detected == 0) {
    return 4;
  }
  return std::max(1, std::min<int>(8, static_cast<int>(detected)));
}

static std::string model_cache_key(
    const char *model_path,
    int n_gpu_layers,
    int use_mmap,
    int use_mlock) {
  return std::string(model_path)
      + "|gpu=" + std::to_string(n_gpu_layers)
      + "|mmap=" + std::to_string(use_mmap != 0)
      + "|mlock=" + std::to_string(use_mlock != 0);
}

static std::shared_ptr<cached_llama_model> acquire_cached_model(
    const char *model_path,
    const llama_model_params &model_params,
    int n_gpu_layers,
    int use_mmap,
    int use_mlock,
    int64_t *model_load_us,
    char *error,
    size_t error_size) {
  const std::string key = model_cache_key(model_path, n_gpu_layers, use_mmap, use_mlock);
  const int64_t cache_start_us = llama_time_us();
  std::lock_guard<std::mutex> guard(model_cache_mutex);
  auto found = model_cache.find(key);
  if (found != model_cache.end()) {
    if (model_load_us != nullptr) {
      *model_load_us = llama_time_us() - cache_start_us;
    }
    return found->second;
  }

  llama_model *loaded_model = llama_model_load_from_file(model_path, model_params);
  if (model_load_us != nullptr) {
    *model_load_us = llama_time_us() - cache_start_us;
  }
  if (loaded_model == nullptr) {
    set_error(error, error_size, "llama_model_load_from_file failed");
    return nullptr;
  }

  auto cached = std::make_shared<cached_llama_model>(loaded_model);
  if (cached->vocab == nullptr) {
    set_error(error, error_size, "llama_model_get_vocab failed");
    return nullptr;
  }
  model_cache[key] = cached;
  return cached;
}

static bool env_flag_enabled(const char *name, bool default_value = false) {
  const char *value = std::getenv(name);
  if (value == nullptr) {
    return default_value;
  }
  std::string normalized = value;
  std::transform(normalized.begin(), normalized.end(), normalized.begin(),
                 [](unsigned char c) { return static_cast<char>(std::tolower(c)); });
  return normalized == "1" || normalized == "true" || normalized == "yes" || normalized == "on";
}

static int env_int(const char *name, int default_value) {
  const char *value = std::getenv(name);
  if (value == nullptr || value[0] == '\0') {
    return default_value;
  }
  char *end = nullptr;
  long parsed = std::strtol(value, &end, 10);
  if (end == value) {
    return default_value;
  }
  return static_cast<int>(std::max<long>(1, parsed));
}

static void aqli_llama_log_callback(ggml_log_level level, const char *text, void *) {
  if (level == GGML_LOG_LEVEL_ERROR && text != nullptr) {
    std::fputs(text, stderr);
  }
}

static bool contains_case_insensitive(const std::string &haystack, const char *needle) {
  if (needle == nullptr || needle[0] == '\0') {
    return true;
  }
  std::string lower_haystack = haystack;
  std::string lower_needle = needle;
  std::transform(lower_haystack.begin(), lower_haystack.end(), lower_haystack.begin(),
                 [](unsigned char c) { return static_cast<char>(std::tolower(c)); });
  std::transform(lower_needle.begin(), lower_needle.end(), lower_needle.begin(),
                 [](unsigned char c) { return static_cast<char>(std::tolower(c)); });
  return lower_haystack.find(lower_needle) != std::string::npos;
}

static std::string detect_accelerator_backend_name() {
  for (size_t i = 0; i < ggml_backend_dev_count(); ++i) {
    ggml_backend_dev_t dev = ggml_backend_dev_get(i);
    if (dev == nullptr) {
      continue;
    }
    auto type = ggml_backend_dev_type(dev);
    if (type != GGML_BACKEND_DEVICE_TYPE_GPU && type != GGML_BACKEND_DEVICE_TYPE_IGPU) {
      continue;
    }
    const char *name = ggml_backend_dev_name(dev);
    const char *description = ggml_backend_dev_description(dev);
    std::string label = name != nullptr ? name : "GPU";
    std::string details = description != nullptr ? description : "";
    std::string combined = label + " " + details;
    if (contains_case_insensitive(combined, "metal")) {
      return details.empty() ? label : "Metal (" + details + ")";
    }
    return details.empty() ? label : label + " (" + details + ")";
  }
  return "";
}

static llama_sampler *create_sampler(float temperature, int top_k, float top_p, uint32_t seed) {
  if (temperature <= 0.0f || top_k == 1) {
    return llama_sampler_init_greedy();
  }
  llama_sampler_chain_params params = llama_sampler_chain_default_params();
  params.no_perf = true;
  llama_sampler *chain = llama_sampler_chain_init(params);
  if (top_k > 0) {
    llama_sampler_chain_add(chain, llama_sampler_init_top_k(top_k));
  }
  if (top_p > 0.0f && top_p < 1.0f) {
    llama_sampler_chain_add(chain, llama_sampler_init_top_p(top_p, 1));
  }
  llama_sampler_chain_add(chain, llama_sampler_init_temp(std::max(0.0f, temperature)));
  llama_sampler_chain_add(chain, llama_sampler_init_dist(seed == 0 ? LLAMA_DEFAULT_SEED : seed));
  return chain;
}

static llama_sampler *acquire_sampler(
    aqli_gguf_ctx *handle,
    float temperature,
    int top_k,
    float top_p,
    uint32_t seed) {
  const bool greedy = temperature <= 0.0f || top_k == 1;
  const uint32_t normalized_seed = seed == 0 ? LLAMA_DEFAULT_SEED : seed;
  const int64_t sampler_start_us = llama_time_us();
  if (handle->sampler != nullptr
      && handle->sampler_greedy == greedy
      && (greedy || (handle->sampler_temperature == temperature
          && handle->sampler_top_k == top_k
          && handle->sampler_top_p == top_p
          && handle->sampler_seed == normalized_seed))) {
    llama_sampler_reset(handle->sampler);
    handle->last_sampler_us = llama_time_us() - sampler_start_us;
    handle->last_sampler_reused = true;
    return handle->sampler;
  }

  if (handle->sampler != nullptr) {
    llama_sampler_free(handle->sampler);
    handle->sampler = nullptr;
  }
  handle->sampler = create_sampler(temperature, top_k, top_p, seed);
  handle->sampler_greedy = greedy;
  handle->sampler_temperature = temperature;
  handle->sampler_top_k = top_k;
  handle->sampler_top_p = top_p;
  handle->sampler_seed = normalized_seed;
  handle->last_sampler_us = llama_time_us() - sampler_start_us;
  handle->last_sampler_reused = false;
  return handle->sampler;
}

static int append_output_bytes(
    const char *data,
    int byte_count,
    char *output,
    size_t output_size,
    size_t &output_used,
    char *error,
    size_t error_size) {
  if (byte_count < 0) {
    set_error(error, error_size, "llama_token_to_piece returned a negative byte count");
    return -7;
  }
  size_t bytes = static_cast<size_t>(byte_count);
  if (output_used + bytes + 1 > output_size) {
    set_error(error, error_size, "output buffer too small");
    return -9;
  }
  if (bytes > 0) {
    std::memcpy(output + output_used, data, bytes);
    output_used += bytes;
  }
  output[output_used] = '\0';
  return 0;
}

static int append_piece_to_output(
    const llama_vocab *vocab,
    llama_token token,
    std::vector<char> &piece_buffer,
    char *output,
    size_t output_size,
    size_t &output_used,
    char *error,
    size_t error_size) {
  char stack_buf[256];
  int written = llama_token_to_piece(vocab, token, stack_buf, sizeof(stack_buf), 0, false);
  if (written >= 0 && written <= static_cast<int>(sizeof(stack_buf))) {
    return append_output_bytes(stack_buf, written, output, output_size, output_used, error, error_size);
  }
  if (written < 0) {
    int needed = -written;
    if (needed <= 0) {
      set_error(error, error_size, "llama_token_to_piece failed");
      return -7;
    }
    if (piece_buffer.size() < static_cast<size_t>(needed) + 1) {
      piece_buffer.resize(static_cast<size_t>(needed) + 1);
    }
    written = llama_token_to_piece(
        vocab,
        token,
        piece_buffer.data(),
        static_cast<int>(piece_buffer.size()),
        0,
        false);
    if (written >= 0) {
      return append_output_bytes(piece_buffer.data(), written, output, output_size, output_used, error, error_size);
    }
  }
  set_error(error, error_size, "llama_token_to_piece output buffer was too small");
  return -7;
}

static bool ensure_prompt_batch(
    aqli_gguf_ctx *handle,
    int capacity,
    char *error,
    size_t error_size) {
  if (capacity <= 0 || handle->prompt_batch_capacity >= capacity) {
    return true;
  }
  if (handle->prompt_batch_capacity > 0) {
    llama_batch_free(handle->prompt_batch);
    handle->prompt_batch = {};
    handle->prompt_batch_capacity = 0;
  }
  llama_batch batch = llama_batch_init(capacity, 0, 1);
  bool ready = batch.token != nullptr
      && batch.pos != nullptr
      && batch.n_seq_id != nullptr
      && batch.seq_id != nullptr
      && batch.logits != nullptr;
  for (int i = 0; ready && i < capacity; ++i) {
    ready = batch.seq_id[i] != nullptr;
  }
  if (!ready) {
    llama_batch_free(batch);
    set_error(error, error_size, "llama_batch_init failed for prompt workspace");
    return false;
  }
  handle->prompt_batch = batch;
  handle->prompt_batch_capacity = capacity;
  return true;
}

static bool decode_prompt_chunks(
    aqli_gguf_ctx *handle,
    std::vector<llama_token> &tokens,
    int n_prompt,
    char *error,
    size_t error_size) {
  const int n_batch = std::max(1, static_cast<int>(llama_n_batch(handle->ctx)));
  int n_processed = 0;
  while (n_processed < n_prompt) {
    int n_tokens = std::min(n_prompt - n_processed, n_batch);
    llama_batch prompt_batch = llama_batch_get_one(tokens.data() + n_processed, n_tokens);
    int decode_status = llama_decode(handle->ctx, prompt_batch);
    if (decode_status != 0) {
      set_error(error,
                error_size,
                "llama_decode failed during prompt prefill at token " + std::to_string(n_processed)
                    + " with batch " + std::to_string(n_tokens));
      return false;
    }
    n_processed += n_tokens;
  }
  return true;
}

static int tokenize_prompt(
    aqli_gguf_ctx *handle,
    const char *prompt,
    int prompt_len) {
  const bool token_cache_allowed = env_flag_enabled("AQLI_GGUF_FAST_TOKEN_CACHE", true);
  std::vector<llama_token> &tokens = handle->token_buffer;
  const int64_t tokenize_start_us = llama_time_us();
  if (token_cache_allowed
      && handle->token_cache_count > 0
      && handle->token_cache_prompt == prompt
      && static_cast<int>(handle->token_cache_tokens.size()) == handle->token_cache_count) {
    if (tokens.size() < static_cast<size_t>(handle->token_cache_count)) {
      tokens.resize(static_cast<size_t>(handle->token_cache_count));
    }
    std::copy(
        handle->token_cache_tokens.begin(),
        handle->token_cache_tokens.end(),
        tokens.begin());
    handle->last_tokenize_us = llama_time_us() - tokenize_start_us;
    handle->last_tokenize_cache_status = "hit";
    return handle->token_cache_count;
  }

  int token_capacity = std::max(32, prompt_len + 32);
  if (tokens.size() < static_cast<size_t>(token_capacity)) {
    tokens.resize(static_cast<size_t>(token_capacity));
  }
  int n_prompt = llama_tokenize(handle->vocab, prompt, prompt_len, tokens.data(), token_capacity, true, true);
  if (n_prompt < 0) {
    token_capacity = -n_prompt;
    if (tokens.size() < static_cast<size_t>(token_capacity)) {
      tokens.resize(static_cast<size_t>(token_capacity));
    }
    n_prompt = llama_tokenize(handle->vocab, prompt, prompt_len, tokens.data(), token_capacity, true, true);
  }
  handle->last_tokenize_us = llama_time_us() - tokenize_start_us;
  handle->last_tokenize_cache_status = token_cache_allowed ? "miss" : "disabled-env";
  if (token_cache_allowed && n_prompt > 0) {
    handle->token_cache_prompt = prompt;
    handle->token_cache_tokens.assign(tokens.begin(), tokens.begin() + n_prompt);
    handle->token_cache_count = n_prompt;
  }
  return n_prompt;
}

static bool decode_token_range_explicit(
    aqli_gguf_ctx *handle,
    const std::vector<llama_token> &tokens,
    int start,
    int count,
    bool output_last,
    char *error,
    size_t error_size) {
  if (count <= 0) {
    return true;
  }
  const int n_batch = std::max(1, static_cast<int>(llama_n_batch(handle->ctx)));
  if (!ensure_prompt_batch(handle, n_batch, error, error_size)) {
    return false;
  }
  int n_processed = 0;
  while (n_processed < count) {
    int n_tokens = std::min(count - n_processed, n_batch);
    llama_batch &batch = handle->prompt_batch;
    batch.n_tokens = n_tokens;
    for (int i = 0; i < n_tokens; ++i) {
      int token_index = start + n_processed + i;
      batch.token[i] = tokens[static_cast<size_t>(token_index)];
      batch.pos[i] = token_index;
      batch.n_seq_id[i] = 1;
      batch.seq_id[i][0] = 0;
      batch.logits[i] = (output_last && token_index == start + count - 1) ? 1 : 0;
    }
    int decode_status = llama_decode(handle->ctx, batch);
    if (decode_status != 0) {
      set_error(error,
                error_size,
                "llama_decode failed during explicit prompt prefill at token " + std::to_string(start + n_processed)
                    + " with batch " + std::to_string(n_tokens));
      return false;
    }
    n_processed += n_tokens;
  }
  return true;
}

static bool decode_single_token_explicit(
    aqli_gguf_ctx *handle,
    llama_token token,
    llama_pos pos,
    char *error,
    size_t error_size) {
  int32_t n_seq_id = 1;
  llama_seq_id seq_id = 0;
  llama_seq_id *seq_id_ptr = &seq_id;
  int8_t logits = 1;
  llama_batch batch = {
      /*n_tokens =*/ 1,
      /*token    =*/ &token,
      /*embd     =*/ nullptr,
      /*pos      =*/ &pos,
      /*n_seq_id =*/ &n_seq_id,
      /*seq_id   =*/ &seq_id_ptr,
      /*logits   =*/ &logits,
  };
  int decode_status = llama_decode(handle->ctx, batch);
  if (decode_status != 0) {
    set_error(error, error_size, "llama_decode failed during explicit single-token decode");
    return false;
  }
  return true;
}

static bool save_prompt_prefix_cache(
    aqli_gguf_ctx *handle,
    const char *prompt,
    int n_prefix_tokens) {
  handle->prompt_cache_key.clear();
  handle->prompt_cache_state.clear();
  handle->prompt_cache_tokens = 0;
  handle->prompt_cache_flags = env_flag_enabled("AQLI_GGUF_FAST_PROMPT_CACHE_ON_DEVICE", true)
      ? LLAMA_STATE_SEQ_FLAGS_ON_DEVICE
      : LLAMA_STATE_SEQ_FLAGS_NONE;

  const size_t state_size = llama_state_seq_get_size_ext(handle->ctx, 0, handle->prompt_cache_flags);
  if (state_size == 0) {
    handle->last_prompt_cache_status = "store-empty";
    return false;
  }
  handle->prompt_cache_state.assign(state_size, 0);
  const int64_t cache_start_us = llama_time_us();
  const size_t written = llama_state_seq_get_data_ext(
      handle->ctx,
      handle->prompt_cache_state.data(),
      handle->prompt_cache_state.size(),
      0,
      handle->prompt_cache_flags);
  handle->last_prompt_cache_us += llama_time_us() - cache_start_us;
  if (written != handle->prompt_cache_state.size()) {
    handle->prompt_cache_state.clear();
    handle->last_prompt_cache_status = "store-failed";
    return false;
  }
  handle->prompt_cache_key = prompt;
  handle->prompt_cache_tokens = n_prefix_tokens;
  handle->last_prompt_cache_status = "stored";
  return true;
}

static bool save_shared_prompt_prefix_cache(
    aqli_gguf_ctx *handle,
    const std::vector<llama_token> &tokens,
    int n_prefix_tokens) {
  handle->shared_prompt_cache_tokens.clear();
  handle->shared_prompt_cache_state.clear();
  handle->shared_prompt_cache_token_count = 0;
  handle->shared_prompt_cache_flags = env_flag_enabled("AQLI_GGUF_FAST_SHARED_PROMPT_CACHE_ON_DEVICE", false)
      ? LLAMA_STATE_SEQ_FLAGS_ON_DEVICE
      : LLAMA_STATE_SEQ_FLAGS_NONE;

  if (n_prefix_tokens <= 0 || n_prefix_tokens > static_cast<int>(tokens.size())) {
    return false;
  }
  const size_t state_size = llama_state_seq_get_size_ext(handle->ctx, 0, handle->shared_prompt_cache_flags);
  if (state_size == 0) {
    return false;
  }
  handle->shared_prompt_cache_state.assign(state_size, 0);
  const int64_t cache_start_us = llama_time_us();
  const size_t written = llama_state_seq_get_data_ext(
      handle->ctx,
      handle->shared_prompt_cache_state.data(),
      handle->shared_prompt_cache_state.size(),
      0,
      handle->shared_prompt_cache_flags);
  handle->last_prompt_cache_us += llama_time_us() - cache_start_us;
  if (written != handle->shared_prompt_cache_state.size()) {
    handle->shared_prompt_cache_state.clear();
    return false;
  }
  handle->shared_prompt_cache_tokens.assign(tokens.begin(), tokens.begin() + n_prefix_tokens);
  handle->shared_prompt_cache_token_count = n_prefix_tokens;
  return true;
}

static bool restore_prompt_prefix_cache(
    aqli_gguf_ctx *handle,
    const char *prompt,
    const std::vector<llama_token> &tokens,
    int n_prompt,
    char *error,
    size_t error_size) {
  if (handle->prompt_cache_state.empty()
      || handle->prompt_cache_tokens != n_prompt - 1
      || handle->prompt_cache_key != prompt) {
    return false;
  }
  llama_memory_clear(llama_get_memory(handle->ctx), true);
  const int64_t cache_start_us = llama_time_us();
  const size_t restored = llama_state_seq_set_data_ext(
      handle->ctx,
      handle->prompt_cache_state.data(),
      handle->prompt_cache_state.size(),
      0,
      handle->prompt_cache_flags);
  handle->last_prompt_cache_us = llama_time_us() - cache_start_us;
  if (restored != handle->prompt_cache_state.size()) {
    handle->prompt_cache_state.clear();
    handle->prompt_cache_key.clear();
    handle->prompt_cache_tokens = 0;
    handle->last_prompt_cache_status = "restore-failed";
    return false;
  }
  if (!decode_single_token_explicit(
          handle,
          tokens[static_cast<size_t>(n_prompt - 1)],
          n_prompt - 1,
          error,
          error_size)) {
    handle->last_prompt_cache_status = "replay-failed";
    return false;
  }
  handle->last_prompt_cache_status = "hit";
  return true;
}

static int restore_shared_prompt_prefix_cache(
    aqli_gguf_ctx *handle,
    const std::vector<llama_token> &tokens,
    int n_prompt) {
  if (handle->shared_prompt_cache_state.empty()
      || handle->shared_prompt_cache_token_count <= 0
      || handle->shared_prompt_cache_token_count >= n_prompt) {
    return 0;
  }
  if (static_cast<int>(handle->shared_prompt_cache_tokens.size()) != handle->shared_prompt_cache_token_count) {
    return 0;
  }
  for (int i = 0; i < handle->shared_prompt_cache_token_count; ++i) {
    if (handle->shared_prompt_cache_tokens[static_cast<size_t>(i)] != tokens[static_cast<size_t>(i)]) {
      return 0;
    }
  }

  llama_memory_clear(llama_get_memory(handle->ctx), true);
  const int64_t cache_start_us = llama_time_us();
  const size_t restored = llama_state_seq_set_data_ext(
      handle->ctx,
      handle->shared_prompt_cache_state.data(),
      handle->shared_prompt_cache_state.size(),
      0,
      handle->shared_prompt_cache_flags);
  handle->last_prompt_cache_us = llama_time_us() - cache_start_us;
  if (restored != handle->shared_prompt_cache_state.size()) {
    handle->shared_prompt_cache_state.clear();
    handle->shared_prompt_cache_tokens.clear();
    handle->shared_prompt_cache_token_count = 0;
    handle->last_prompt_cache_status = "prefix-restore-failed";
    return 0;
  }
  handle->last_prompt_cache_status = "prefix-hit";
  return handle->shared_prompt_cache_token_count;
}

} // namespace

extern "C" {

void *aqli_gguf_open(const char *model_path,
                       const char *backend_dir,
                       int n_ctx,
                       int n_batch,
                       int n_ubatch,
                       int n_threads,
                       int n_threads_batch,
                       int n_gpu_layers,
                       int use_mmap,
                       int use_mlock,
                       int use_swa_full,
                       char *error,
                       size_t error_size) {
  if (model_path == nullptr || model_path[0] == '\0') {
    set_error(error, error_size, "model_path is required");
    return nullptr;
  }

  std::call_once(backend_once, [backend_dir]() {
    if (!env_flag_enabled("AQLI_GGUF_FAST_RUN_DEBUG")) {
      llama_log_set(aqli_llama_log_callback, nullptr);
    }
    if (backend_dir != nullptr && backend_dir[0] != '\0') {
      ggml_backend_load_all_from_path(backend_dir);
    } else {
      ggml_backend_load_all();
    }
    llama_backend_init();
  });

  auto *handle = new aqli_gguf_ctx();
  handle->n_gpu_layers = n_gpu_layers;
  handle->n_threads = n_threads > 0 ? n_threads : default_threads();
  if (n_gpu_layers == 0) {
    handle->backend_name = "CPU";
  } else {
    handle->backend_name = detect_accelerator_backend_name();
    if (handle->backend_name.empty()) {
      set_error(error, error_size, "GPU offload was requested, but no GPU/Metal backend device was loaded");
      delete handle;
      return nullptr;
    }
  }

  llama_model_params model_params = llama_model_default_params();
  model_params.n_gpu_layers = n_gpu_layers;
  model_params.use_mmap = use_mmap != 0;
  model_params.use_mlock = use_mlock != 0;

  handle->model_ref = acquire_cached_model(
      model_path,
      model_params,
      n_gpu_layers,
      use_mmap,
      use_mlock,
      &handle->model_load_us,
      error,
      error_size);
  if (handle->model_ref == nullptr) {
    delete handle;
    return nullptr;
  }

  llama_context_params ctx_params = llama_context_default_params();
  ctx_params.n_ctx = n_ctx > 0 ? static_cast<uint32_t>(n_ctx) : 2048;
  uint32_t requested_batch = n_batch > 0 ? static_cast<uint32_t>(n_batch) : 1024;
  ctx_params.n_batch = std::max<uint32_t>(1, std::min<uint32_t>(ctx_params.n_ctx, requested_batch));
  uint32_t requested_ubatch = n_ubatch > 0 ? static_cast<uint32_t>(n_ubatch) : 512;
  ctx_params.n_ubatch = std::max<uint32_t>(1, std::min<uint32_t>(ctx_params.n_batch, requested_ubatch));
  ctx_params.n_threads = handle->n_threads;
  ctx_params.n_threads_batch = n_threads_batch > 0 ? n_threads_batch : handle->n_threads;
  ctx_params.no_perf = true;
  ctx_params.swa_full = use_swa_full != 0;

  // Flash Attention is DISABLED by default on this llama.cpp gemma4 build.
  //
  // Root cause: the Metal FA kernel sweeps the full ctx_size KV buffer (not just
  // filled slots) making prefill O(n_queries × ctx_allocated) instead of
  // O(n_queries × n_tokens_used). At ctx=4096 this causes a 7-second prefill for
  // a 23-token prompt — far worse than baseline.
  //
  // FA also provides no measurable decode benefit for ctx<=1024 with short sequences
  // on M4. Enable with AQLI_GGUF_FLASH_ATTN=1 once the Metal kernel is fixed upstream.
  if (n_gpu_layers != 0 && env_flag_enabled("AQLI_GGUF_FLASH_ATTN", false)) {
    ctx_params.flash_attn_type = LLAMA_FLASH_ATTN_TYPE_ENABLED;
    if (env_flag_enabled("AQLI_GGUF_KV_QUANT", false)) {
      ctx_params.type_k = GGML_TYPE_Q8_0;
      ctx_params.type_v = GGML_TYPE_Q8_0;
    }
  }

  int64_t context_init_start_us = llama_time_us();
  handle->ctx = llama_init_from_model(handle->model_ref->model, ctx_params);
  handle->context_init_us = llama_time_us() - context_init_start_us;
  if (handle->ctx == nullptr) {
    set_error(error, error_size, "llama_init_from_model failed");
    delete handle;
    return nullptr;
  }
  handle->vocab = handle->model_ref->vocab;
  if (handle->vocab == nullptr) {
    set_error(error, error_size, "llama_model_get_vocab failed");
    delete handle;
    return nullptr;
  }

  llama_set_n_threads(handle->ctx, handle->n_threads, ctx_params.n_threads_batch);
  return handle;
}

int aqli_gguf_generate(void *raw_handle,
                         const char *prompt,
                         int max_tokens,
                         float temperature,
                         int top_k,
                         float top_p,
                         uint32_t seed,
                         char *output,
                         size_t output_size,
                         char *error,
                         size_t error_size) {
  if (raw_handle == nullptr) {
    set_error(error, error_size, "GGUF handle is null");
    return -1;
  }
  if (prompt == nullptr || prompt[0] == '\0') {
    set_error(error, error_size, "prompt is required");
    return -2;
  }
  if (output == nullptr || output_size == 0) {
    set_error(error, error_size, "output buffer is required");
    return -3;
  }

  auto *handle = static_cast<aqli_gguf_ctx *>(raw_handle);
  std::lock_guard<std::mutex> guard(handle->mutex);
  output[0] = '\0';
  handle->last_tokenize_us = 0;
  handle->last_prefill_us = 0;
  handle->last_decode_us = 0;
  handle->last_prompt_cache_us = 0;
  handle->last_prompt_tokens = 0;
  handle->last_generated_tokens = 0;
  handle->last_decoded_tokens = 0;
  handle->last_prompt_cache_min_tokens = 0;
  handle->last_prompt_cache_repeat_min_tokens = 0;
  handle->last_prompt_cache_eager_short = false;
  handle->last_output_bytes = 0;
  handle->last_sampler_us = 0;
  handle->last_sampler_reused = false;
  handle->last_tokenize_cache_status = "disabled";
  handle->last_repeated_prompt = false;
  handle->last_prompt_cache_status = "disabled";

  const int prompt_len = static_cast<int>(std::strlen(prompt));
  std::vector<llama_token> &tokens = handle->token_buffer;
  int n_prompt = tokenize_prompt(handle, prompt, prompt_len);
  if (n_prompt <= 0) {
    set_error(error, error_size, "prompt tokenization failed");
    return -4;
  }
  handle->last_prompt_tokens = n_prompt;
  const int n_ctx = static_cast<int>(llama_n_ctx(handle->ctx));
  if (n_prompt >= n_ctx) {
    set_error(error,
              error_size,
              "prompt token count " + std::to_string(n_prompt)
                  + " leaves no decode room in context " + std::to_string(n_ctx));
    return -5;
  }

  int64_t prefill_start_us = llama_time_us();
  bool prompt_ready = false;
  // Sequence-state snapshots are not free on Metal; short prompts prefill faster than cache save/restore.
  const int prompt_cache_min_tokens = env_int("AQLI_GGUF_FAST_PROMPT_CACHE_MIN_TOKENS", 128);
  handle->last_prompt_cache_min_tokens = prompt_cache_min_tokens;
  const int shared_prompt_cache_min_tokens = env_int("AQLI_GGUF_FAST_PROMPT_CACHE_SHARED_MIN_TOKENS", 64);
  const bool prompt_cache_allowed = env_flag_enabled("AQLI_GGUF_FAST_PROMPT_CACHE", true);
  const int repeat_prompt_cache_min_tokens = env_int("AQLI_GGUF_FAST_PROMPT_CACHE_REPEAT_MIN_TOKENS", 8);
  handle->last_prompt_cache_repeat_min_tokens = repeat_prompt_cache_min_tokens;
  const bool repeated_prompt = handle->last_tokenize_cache_status == "hit";
  handle->last_repeated_prompt = repeated_prompt;
  const bool repeated_short_prompt_cache_enabled =
      repeated_prompt && n_prompt >= repeat_prompt_cache_min_tokens;
  const bool eager_short_prompt_cache_enabled =
      env_flag_enabled("AQLI_GGUF_FAST_PROMPT_CACHE_EAGER_SHORT", true)
      && n_prompt < prompt_cache_min_tokens
      && n_prompt >= repeat_prompt_cache_min_tokens;
  handle->last_prompt_cache_eager_short = eager_short_prompt_cache_enabled;
  const bool prompt_cache_enabled = prompt_cache_allowed
      && n_prompt > 1
      && (n_prompt >= prompt_cache_min_tokens
          || repeated_short_prompt_cache_enabled
          || eager_short_prompt_cache_enabled);
  if (prompt_cache_enabled) {
    prompt_ready = restore_prompt_prefix_cache(handle, prompt, tokens, n_prompt, error, error_size);
    if (!prompt_ready) {
      int restored_shared_tokens = restore_shared_prompt_prefix_cache(handle, tokens, n_prompt);
      if (restored_shared_tokens > 0) {
        const int n_tail_prefix_tokens = (n_prompt - 1) - restored_shared_tokens;
        if (!decode_token_range_explicit(
                handle,
                tokens,
                restored_shared_tokens,
                n_tail_prefix_tokens,
                false,
                error,
                error_size)) {
          return -5;
        }
        save_prompt_prefix_cache(handle, prompt, n_prompt - 1);
        if (!decode_single_token_explicit(
                handle,
                tokens[static_cast<size_t>(n_prompt - 1)],
                n_prompt - 1,
                error,
                error_size)) {
          return -5;
        }
        handle->last_prompt_cache_status = "prefix-hit";
        prompt_ready = true;
      }
    }
    if (!prompt_ready) {
      llama_memory_clear(llama_get_memory(handle->ctx), true);
      const int shared_prefix_tokens = std::min(
          n_prompt - 1,
          env_int("AQLI_GGUF_FAST_PROMPT_CACHE_SHARED_TOKENS", 64));
      const bool use_shared_prefix_cache = shared_prefix_tokens >= shared_prompt_cache_min_tokens;
      if (use_shared_prefix_cache) {
        if (!decode_token_range_explicit(handle, tokens, 0, shared_prefix_tokens, false, error, error_size)) {
          return -5;
        }
        save_shared_prompt_prefix_cache(handle, tokens, shared_prefix_tokens);
      }
      const int n_prefix_tokens = n_prompt - 1;
      if (!decode_token_range_explicit(
              handle,
              tokens,
              use_shared_prefix_cache ? shared_prefix_tokens : 0,
              use_shared_prefix_cache ? n_prefix_tokens - shared_prefix_tokens : n_prefix_tokens,
              false,
              error,
              error_size)) {
        return -5;
      }
      save_prompt_prefix_cache(handle, prompt, n_prefix_tokens);
      if (!decode_single_token_explicit(
              handle,
              tokens[static_cast<size_t>(n_prompt - 1)],
              n_prompt - 1,
              error,
              error_size)) {
        return -5;
      }
      prompt_ready = true;
    }
  }
  if (!prompt_ready) {
    if (!prompt_cache_enabled) {
      if (!prompt_cache_allowed) {
        handle->last_prompt_cache_status = "disabled-env";
      } else if (n_prompt <= 1) {
        handle->last_prompt_cache_status = "single-token";
      } else if (n_prompt < prompt_cache_min_tokens) {
        handle->last_prompt_cache_status = "below-threshold";
      } else {
        handle->last_prompt_cache_status = "skipped";
      }
    }
    llama_memory_clear(llama_get_memory(handle->ctx), true);
    if (!decode_prompt_chunks(handle, tokens, n_prompt, error, error_size)) {
      return -5;
    }
  }
  handle->last_prefill_us = llama_time_us() - prefill_start_us;

  llama_sampler *sampler = acquire_sampler(handle, temperature, top_k, top_p, seed);
  if (sampler == nullptr) {
    set_error(error, error_size, "failed to create sampler");
    return -6;
  }

  size_t output_used = 0;
  int generated_tokens = 0;
  int limit = std::min(max_tokens > 0 ? max_tokens : 256, n_ctx - n_prompt);
  int64_t decode_start_us = llama_time_us();
  while (generated_tokens < limit) {
    llama_token token = llama_sampler_sample(sampler, handle->ctx, -1);
    if (llama_vocab_is_eog(handle->vocab, token)) {
      break;
    }
    int append_status = append_piece_to_output(
        handle->vocab,
        token,
        handle->piece_buffer,
        output,
        output_size,
        output_used,
        error,
        error_size);
    if (append_status != 0) {
      return append_status;
    }
    generated_tokens++;
    // Sampler chains keep token history; accept emitted tokens even for greedy mode.
    llama_sampler_accept(sampler, token);
    if (generated_tokens >= limit) {
      break;
    }
    llama_pos token_pos = n_prompt + generated_tokens - 1;
    if (!decode_single_token_explicit(handle, token, token_pos, error, error_size)) {
      return -8;
    }
    handle->last_decoded_tokens++;
  }
  handle->last_decode_us = llama_time_us() - decode_start_us;
  handle->last_generated_tokens = generated_tokens;
  handle->last_output_bytes = output_used;

  return generated_tokens;
}

int aqli_gguf_last_metrics(void *raw_handle, char *output, size_t output_size) {
  if (raw_handle == nullptr || output == nullptr || output_size == 0) {
    return -1;
  }
  auto *handle = static_cast<aqli_gguf_ctx *>(raw_handle);
  int written = std::snprintf(
      output,
      output_size,
      "backend=%s, gpuLayers=%d, threads=%d, ctx=%d, batch=%d, "
      "modelLoad=%.3fms, contextInit=%.3fms, tokenize=%.3fms, prefill=%.3fms, decode=%.3fms, "
      "tokenizeCache=%s, tokenCacheTokens=%d, "
      "sampler=%s, samplerMs=%.3fms, "
      "promptCache=%s, promptCacheMs=%.3fms, promptStateBytes=%zu, sharedPromptStateBytes=%zu, "
      "promptTokens=%d, generatedTokens=%d, decodedTokens=%d, repeatedPrompt=%s, "
      "promptCacheEagerShort=%s, "
      "promptCacheMinTokens=%d, promptCacheRepeatMinTokens=%d, "
      "tokenBufferSize=%zu, tokenBufferCapacity=%zu, pieceBufferCapacity=%zu, "
      "outputBytes=%zu, promptBatchCapacity=%d",
      handle->backend_name.c_str(),
      handle->n_gpu_layers,
      handle->n_threads,
      static_cast<int>(llama_n_ctx(handle->ctx)),
      static_cast<int>(llama_n_batch(handle->ctx)),
      handle->model_load_us / 1000.0,
      handle->context_init_us / 1000.0,
      handle->last_tokenize_us / 1000.0,
      handle->last_prefill_us / 1000.0,
      handle->last_decode_us / 1000.0,
      handle->last_tokenize_cache_status.c_str(),
      handle->token_cache_count,
      handle->last_sampler_reused ? "reused" : "created",
      handle->last_sampler_us / 1000.0,
      handle->last_prompt_cache_status.c_str(),
      handle->last_prompt_cache_us / 1000.0,
      handle->prompt_cache_state.size(),
      handle->shared_prompt_cache_state.size(),
      handle->last_prompt_tokens,
      handle->last_generated_tokens,
      handle->last_decoded_tokens,
      handle->last_repeated_prompt ? "true" : "false",
      handle->last_prompt_cache_eager_short ? "true" : "false",
      handle->last_prompt_cache_min_tokens,
      handle->last_prompt_cache_repeat_min_tokens,
      handle->token_buffer.size(),
      handle->token_buffer.capacity(),
      handle->piece_buffer.capacity(),
      handle->last_output_bytes,
      handle->prompt_batch_capacity);
  if (written < 0 || static_cast<size_t>(written) >= output_size) {
    output[0] = '\0';
    return -2;
  }
  return written;
}

const char *aqli_gguf_backend_name(void *raw_handle) {
  if (raw_handle == nullptr) {
    return "unknown";
  }
  auto *handle = static_cast<aqli_gguf_ctx *>(raw_handle);
  return handle->backend_name.c_str();
}

void aqli_gguf_hard_exit(int status) {
  std::fflush(stdout);
  std::fflush(stderr);
  std::_Exit(status);
}

void aqli_gguf_close(void *raw_handle) {
  auto *handle = static_cast<aqli_gguf_ctx *>(raw_handle);
  delete handle;
}

} // extern "C"
