package tech.kayys.aqli.model.repo.local;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.aljabr.spi.model.ModelRepository;
import tech.kayys.aljabr.spi.model.ModelRepositoryProvider;
import tech.kayys.aljabr.spi.model.RepositoryContext;

/** Repository provider for {@code local:} model references. */
@ApplicationScoped
public final class LocalModelRepositoryProvider implements ModelRepositoryProvider {

    private final LocalModelRepository repository;

    @Inject
    public LocalModelRepositoryProvider(LocalModelRepository repository) {
        this.repository = repository;
    }

    @Override
    public String scheme() {
        return "local";
    }

    @Override
    public ModelRepository create(RepositoryContext context) {
        return repository;
    }
}
