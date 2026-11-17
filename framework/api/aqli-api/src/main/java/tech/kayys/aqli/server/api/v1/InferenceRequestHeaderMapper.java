package tech.kayys.aqli.server.api.v1;

import tech.kayys.aqli.spi.auth.ApiKeyConstants;
import tech.kayys.aqli.spi.inference.InferenceRequest;

/**
 * Applies REST authentication headers to the internal inference request.
 *
 * <p>An explicit request API key takes precedence. The default community key
 * is treated as an unset value so a caller-supplied HTTP header is not
 * accidentally ignored.</p>
 */
public final class InferenceRequestHeaderMapper {

    private InferenceRequestHeaderMapper() {
    }

    public static InferenceRequest withApiKeyHeader(InferenceRequest request, String apiKeyHeader) {
        if (request == null) {
            throw new IllegalArgumentException("inference request is required");
        }
        if (apiKeyHeader == null || apiKeyHeader.isBlank()) {
            return request;
        }

        String requestApiKey = request.getApiKey();
        if (requestApiKey == null
                || requestApiKey.isBlank()
                || ApiKeyConstants.COMMUNITY_API_KEY.equals(requestApiKey)) {
            return request.toBuilder().apiKey(apiKeyHeader).build();
        }

        return request;
    }
}
