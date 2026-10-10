package tech.kayys.aqli.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.quarkus.jackson.ObjectMapperCustomizer;
import jakarta.inject.Singleton;

/**
 * Registers the Jackson JSR-310 module so that {@link java.time.Instant}
 * fields (e.g. {@code lastModified} in HuggingFaceModelInfo) can be
 * deserialized without requiring an explicit module activation.
 */
@Singleton
public class JacksonConfig implements ObjectMapperCustomizer {

    @Override
    public void customize(ObjectMapper mapper) {
        mapper.registerModule(new JavaTimeModule());
    }
}
