package tech.kayys.aqli.model.repo.hf;


import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.aljabr.spi.model.ModelRepository;
import tech.kayys.aljabr.spi.model.ModelRepositoryProvider;
import tech.kayys.aljabr.spi.model.RepositoryContext;

@ApplicationScoped
public final class HuggingFaceRepositoryProvider implements ModelRepositoryProvider {

    @Inject
    HuggingFaceClient client;

    @Inject
    HuggingFaceConfig config;

    @Inject
    tech.kayys.aqli.model.repo.local.ManifestStore manifestStore;

    @Override
    public String scheme() {
        return "hf";
    }

    public ModelRepository create(RepositoryContext context) {
        return new HuggingFaceRepository(context.cacheDir(), client, config, manifestStore);
    }
}