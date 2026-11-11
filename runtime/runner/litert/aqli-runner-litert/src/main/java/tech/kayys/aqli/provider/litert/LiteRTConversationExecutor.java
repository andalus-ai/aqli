package tech.kayys.aqli.provider.litert;

import com.google.ai.edge.litertlm.Contents;
import com.google.ai.edge.litertlm.Conversation;
import com.google.ai.edge.litertlm.ConversationConfig;
import com.google.ai.edge.litertlm.Engine;
import com.google.ai.edge.litertlm.MessageCallback;
import com.google.ai.edge.litertlm.SamplerConfig;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Owns the lifecycle of one official LiteRT-LM conversation.
 *
 * <p>Callers supply prompt policy, sampling parameters, timeout, token limit,
 * and output handling. This class owns conversation creation, asynchronous
 * message completion, cancellation, error propagation, and cleanup.</p>
 */
public final class LiteRTConversationExecutor {
    private LiteRTConversationExecutor() {
    }

    public static Result execute(
            Engine engine,
            String prompt,
            int topK,
            double topP,
            double temperature,
            int maxTokens,
            Duration timeout,
            Consumer<String> output) throws Exception {
        if (engine == null) {
            throw new IllegalArgumentException("LiteRT engine is required");
        }
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("Conversation timeout must be positive");
        }

        SamplerConfig sampler = new SamplerConfig(
                Math.max(1, topK),
                Math.max(0.0d, Math.min(1.0d, topP)),
                Math.max(0.0d, temperature),
                0);
        ConversationConfig config = new ConversationConfig(
                null, List.of(), List.of(), sampler, false, null, Map.of());

        try (Conversation conversation = engine.createConversation(config)) {
            long conversationReadyNanos = System.nanoTime();
            CountDownLatch done = new CountDownLatch(1);
            AtomicBoolean cancelled = new AtomicBoolean();
            AtomicInteger streamUpdates = new AtomicInteger();
            AtomicLong firstChunkNanos = new AtomicLong();
            AtomicReference<Throwable> error = new AtomicReference<>();
            LiteRTTokenStreamLimiter limiter = new LiteRTTokenStreamLimiter(maxTokens);

            conversation.sendMessageAsync(Contents.Companion.of(prompt), new MessageCallback() {
                @Override
                public void onMessage(com.google.ai.edge.litertlm.Message message) {
                    if (cancelled.get()) {
                        return;
                    }
                    streamUpdates.incrementAndGet();
                    firstChunkNanos.compareAndSet(0L, System.nanoTime());
                    String delta = limiter.offer(message.toString());
                    if (!delta.isEmpty() && output != null) {
                        output.accept(delta);
                    }
                    if (limiter.atLimit() && cancelled.compareAndSet(false, true)) {
                        conversation.cancelProcess();
                        done.countDown();
                    }
                }

                @Override
                public void onDone() {
                    done.countDown();
                }

                @Override
                public void onError(Throwable throwable) {
                    if (!cancelled.get()) {
                        error.compareAndSet(null, throwable);
                    }
                    done.countDown();
                }
            }, Map.of());

            if (!done.await(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                conversation.cancelProcess();
                throw new IllegalStateException("LiteRT-LM conversation timed out");
            }
            if (error.get() != null && !cancelled.get()) {
                throw new IllegalStateException(error.get());
            }
            return new Result(streamUpdates.get(), limiter.emittedTokenCount(), conversationReadyNanos, firstChunkNanos.get());
        }
    }

    public record Result(int streamUpdates, int outputTokens, long conversationReadyNanos, long firstChunkNanos) {
    }
}
