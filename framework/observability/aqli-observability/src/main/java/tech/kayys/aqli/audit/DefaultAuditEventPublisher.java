package tech.kayys.aqli.audit;

import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.aqli.spi.observability.AuditPayload;
import io.smallrye.mutiny.Uni;
import io.quarkus.arc.DefaultBean;

@ApplicationScoped
@DefaultBean
public class DefaultAuditEventPublisher implements AuditEventPublisher {

    @Override
    public Uni<Void> publish(AuditPayload payload) {
        // Default no-op implementation
        return Uni.createFrom().voidItem();
    }
}
