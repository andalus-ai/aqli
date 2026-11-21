# AQLI API documentation

The AQLI API server is the HTTP serving boundary for inference, embeddings, model discovery, tool schemas and agent integration. Its Gradle project is `:api:aqli-api`, located at `framework/api/aqli-api`.

## API references

- [API quick reference](API_QUICK_REFERENCE.md) — endpoints, headers and short request examples.
- [Complete API examples](API_EXAMPLES.md) — chat, streaming, embeddings, models and agent integration.
- [API server module guide](../framework/api/aqli-api/README.md) — module responsibilities and development commands.
- [Observability guide](OBSERVABILITY.md) — metrics, tracing and operational signals.

## Start the API

When using an installed AQLI CLI:

```bash
aqli serve --model <model-id> --port 8080
```

For direct Gradle development:

```bash
./gradlew :api:aqli-api:quarkusDev
```

The health endpoint is `GET /health`. OpenAPI is available at `/q/openapi` when the development OpenAPI endpoint is enabled.

## Authentication

Protected endpoints accept either:

```http
X-API-Key: <key>
```

or:

```http
Authorization: Bearer <key>
```

The standalone development configuration may use the `community` key. Production deployments should configure their own authentication and authorization policy.

Do not rely on a test script or endpoint not present in the repository. Use the API examples and the running server's OpenAPI document to verify the current contract.
