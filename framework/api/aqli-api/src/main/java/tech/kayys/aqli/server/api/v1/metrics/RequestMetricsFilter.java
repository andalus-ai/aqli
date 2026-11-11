package tech.kayys.aqli.server.metrics;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

@Provider
@Priority(Priorities.USER)
public class RequestMetricsFilter implements ContainerRequestFilter {

    @Inject
    MeterRegistry registry;

    private final Counter total;

    @Inject
    public RequestMetricsFilter(MeterRegistry registry) {
        this.registry = registry;
        this.total = registry.counter("aqli.requests.total");
    }

    @Override
    public void filter(ContainerRequestContext requestContext) {
        total.increment();
    }
}
