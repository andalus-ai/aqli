package tech.kayys.aqli.spi.inference;

import io.smallrye.mutiny.Multi;
import tech.kayys.aqli.spi.Message;
import tech.kayys.aqli.spi.auth.ApiKeyConstants;
import tech.kayys.aqli.spi.context.RequestContext;

public record StreamingSession(
        String sessionId,
        String modelId,
        RequestContext requestContext,
        Multi<Message> stream) {
    public String apiKey() {
        if (requestContext.apiKey() == null || requestContext.apiKey().isBlank()) {
            return ApiKeyConstants.COMMUNITY_API_KEY;
        }
        return requestContext.apiKey();
    }
}
