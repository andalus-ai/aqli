package tech.kayys.aqli.server.api.v1;

import tech.kayys.aljabr.spi.model.ModelInfo;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ModelCapabilityMapper {

    private ModelCapabilityMapper() {}

    public static Map<String, Object> toCapabilityMatrix(String modelId, Optional<ModelInfo> modelInfo, List<Object> providers, Optional<String> preferredProvider) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("model_id", modelId);
        boolean available = modelInfo != null && modelInfo.isPresent();
        map.put("known", available);
        map.put("available", available);
        map.put("api_contract", Map.of(
                "responses", true,
                "responses_streaming", true,
                "tools_request_schema", true
        ));
        map.put("openai_compatibility", Map.of(
                "chat_completions", true
        ));
        map.put("tooling", Map.of(
                "tool_execution", false
        ));
        map.put("rag", Map.of(
                "context_injection", true
        ));
        return map;
    }
}
