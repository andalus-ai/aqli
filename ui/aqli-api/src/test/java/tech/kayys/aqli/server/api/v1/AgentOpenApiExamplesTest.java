package tech.kayys.aqli.server.api.v1;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentOpenApiExamplesTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void preflightExampleMatchesReadinessContractShape() throws Exception {
        JsonNode response = MAPPER.readTree(AgentOpenApiExamples.PREFLIGHT_RESPONSE);
        JsonNode schemas = MAPPER.valueToTree(AgentContractMapper.contract()).path("schemas");
        JsonNode schema = schemas.path("agent_preflight_response");

        assertEquals("aqli.agent_preflight", response.path("object").asText());
        assertEquals("v1", response.path("contract_version").asText());
        assertEquals("feature_flags", response.path("feature_negotiation").path("mode").asText());
        assertEquals("agent_serving",
                response.path("feature_negotiation").path("default_feature_profile").asText());
        assertTrue(response.path("feature_negotiation").path("required_features").isArray());
        assertEquals("chat_agent", response.path("checks").path("feature_profile").asText());
        assertEquals("chat_agent",
                response.path("check_results").path("feature_negotiation").path("details")
                        .path("feature_profile").asText());
        assertEquals("aqli.agent_readiness_report",
                response.path("readiness_report").path("object").asText());
        assertEquals("v1", response.path("readiness_report").path("contract_version").asText());
        assertEquals("feature_flags",
                response.path("readiness_report").path("feature_negotiation").path("mode").asText());
        assertRequiredFields(response, schema, "$");
        assertRequiredFields(response.path("readiness_report"),
                schema.path("properties").path("readiness_report"),
                "$.readiness_report");
        assertTrue(response.path("trace").has("request_id"));
        assertEquals(7, response.path("ready_check_count").asInt());
        assertEquals(0, response.path("blocked_check_count").asInt());
        assertEquals(0, response.path("skipped_check_count").asInt());
        assertTrue(response.path("blocking_codes").isArray());
        assertTrue(response.path("warning_codes").isArray());
        assertTrue(response.path("issue_hints").isArray());
        assertTrue(response.path("remediation_plan").isArray());
        assertTrue(response.path("blocking_remediation_plan").isArray());
        assertTrue(response.path("warning_remediation_plan").isArray());
        assertTrue(response.path("remediation_plan_by_area").isObject());
        assertTrue(response.path("remediation_plan_by_code").isObject());
        assertTrue(response.path("issue_codes_by_area").isObject());
        assertEquals(7, response.path("checked_areas").size());
        assertEquals("capabilities", response.path("checked_areas").get(0).asText());
        assertEquals("feature_negotiation", response.path("checked_areas").get(2).asText());
        assertEquals(7, response.path("readiness_report").path("checked_areas").size());
        assertEquals("capabilities", response.path("readiness_report").path("checked_areas").get(0).asText());
        assertEquals(response.path("checked_areas"),
                response.path("readiness_report").path("checked_areas"));

        JsonNode checkSchema = resolveRef(schemas,
                schema.path("properties").path("check_results").path("additionalProperties"));
        response.path("check_results").fields().forEachRemaining(entry ->
                assertRequiredFields(entry.getValue(), checkSchema, "$.check_results." + entry.getKey()));
        JsonNode readinessCheckSchema = resolveRef(schemas, schema.path("properties")
                .path("readiness_report")
                .path("properties")
                .path("checks")
                .path("additionalProperties"));
        response.path("readiness_report").path("checks").fields().forEachRemaining(entry ->
                assertRequiredFields(entry.getValue(), readinessCheckSchema,
                        "$.readiness_report.checks." + entry.getKey()));

        JsonNode check = response.path("check_results").path("mcp_discovery");
        assertEquals("ready", check.path("status").asText());
        assertTrue(check.path("ready").asBoolean());
        assertTrue(check.path("requested").asBoolean());
        assertTrue(check.path("blocking_messages").isArray());
        assertTrue(check.path("warning_messages").isArray());
        assertTrue(check.path("blocking_codes").isArray());
        assertTrue(check.path("warning_codes").isArray());
        assertTrue(check.path("issue_hints").isArray());
        assertTrue(check.path("remediation_plan").isArray());
        assertTrue(check.path("blocking_remediation_plan").isArray());
        assertTrue(check.path("warning_remediation_plan").isArray());
        assertTrue(check.path("remediation_plan_by_code").isObject());

        assertEquals(check, response.path("readiness_report")
                .path("checks")
                .path("mcp_discovery"));
    }

    @Test
    void contractExampleIncludesReadinessIssueCatalog() throws Exception {
        JsonNode response = MAPPER.readTree(AgentOpenApiExamples.AGENT_CONTRACT_RESPONSE);

        assertEquals("aqli.agent_contract", response.path("object").asText());
        assertEquals("v1", response.path("contract_version").asText());
        assertEquals("v1", response.path("supported_contract_versions").get(0).asText());
        assertEquals("feature_flags", response.path("feature_negotiation").path("mode").asText());
        assertTrue(response.path("compatibility").isArray());
        assertTrue(response.path("feature_negotiation").path("required_features").isArray());
        assertEquals("TOOL_DEFINITIONS_INVALID",
                response.path("readiness_issue_catalog").get(0).path("code").asText());
        JsonNode catalogByCode = response.path("readiness_issue_catalog_by_code");
        assertEquals("tool_validation",
                catalogByCode.path("TOOL_DEFINITIONS_INVALID").path("area").asText());
        assertEquals("blocking",
                catalogByCode.path("TOOL_DEFINITIONS_INVALID").path("default_severity").asText());
    }

    @Test
    void readinessIssueCatalogExampleMatchesCatalogShape() throws Exception {
        JsonNode response = MAPPER.readTree(AgentOpenApiExamples.READINESS_ISSUE_CATALOG_RESPONSE);
        JsonNode schemas = MAPPER.valueToTree(AgentContractMapper.contract()).path("schemas");
        JsonNode schema = schemas.path("agent_readiness_issue_catalog_response");

        assertEquals("aqli.agent_readiness_issue_catalog", response.path("object").asText());
        assertRequiredFields(response, schema, "$.readiness_issue_catalog");
        assertEquals("TOOL_DEFINITIONS_INVALID", response.path("items").get(0).path("code").asText());
        assertEquals("tool_validation",
                response.path("by_code").path("TOOL_DEFINITIONS_INVALID").path("area").asText());
        assertEquals("blocking",
                response.path("by_area").path("tool_validation").get(0).path("default_severity").asText());
        assertTrue(response.path("boundary").path("validation_only").asBoolean());
        assertTrue(!response.path("boundary").path("model_invoked").asBoolean());
    }

    private static void assertRequiredFields(JsonNode payload, JsonNode schema, String path) {
        assertTrue(payload.isObject(), path + " must be an object");
        assertTrue(schema.path("required").isArray(), path + " schema must declare required fields");
        schema.path("required").forEach(field ->
                assertTrue(payload.has(field.asText()), path + " missing required field " + field.asText()));
    }

    private static JsonNode resolveRef(JsonNode schemas, JsonNode schema) {
        String ref = schema.path("$ref").asText("");
        if (!ref.startsWith("#/schemas/")) {
            return schema;
        }
        return schemas.path(ref.substring("#/schemas/".length()));
    }
}
