package tech.kayys.aqli.server.api.v1;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.Map;

record AgentStreamOptions(
        boolean includeUsage,
        boolean includeTrace,
        boolean includeStreamMetadata) {
    static final AgentStreamOptions DEFAULT = new AgentStreamOptions(false, true, true);

    static AgentStreamOptions from(JsonNode payload) {
        JsonNode options = payload == null ? null : payload.path("stream_options");
        if (options == null || options.isMissingNode() || options.isNull() || !options.isObject()) {
            return DEFAULT;
        }
        return new AgentStreamOptions(
                options.path("include_usage").asBoolean(false),
                !options.has("include_trace") || options.path("include_trace").asBoolean(true),
                !options.has("include_stream_metadata")
                        || options.path("include_stream_metadata").asBoolean(true));
    }

    Map<String, Object> summary(String surface) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("surface", surface);
        summary.put("include_usage", includeUsage);
        summary.put("include_trace", includeTrace);
        summary.put("include_stream_metadata", includeStreamMetadata);
        summary.put("done_sentinel", "[DONE]");
        return summary;
    }
}
