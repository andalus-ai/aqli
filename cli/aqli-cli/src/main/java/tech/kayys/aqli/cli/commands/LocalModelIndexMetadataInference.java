package tech.kayys.aqli.cli.commands;

/**
 * Maps local model-index format metadata to the provider and canonical format
 * understood by the CLI's model-resolution flow.
 *
 * <p>This adapter does not resolve models or choose execution routes.</p>
 */
final class LocalModelIndexMetadataInference {

    private LocalModelIndexMetadataInference() {
    }

    static String providerFor(LocalModelIndex.Entry entry) {
        if (entry == null || entry.format == null) {
            return null;
        }
        return ModelFormatResolver.providerForFormat(entry.format);
    }

    static String formatFor(LocalModelIndex.Entry entry) {
        if (entry == null || entry.format == null || entry.format.isBlank()) {
            return null;
        }
        return ModelFormatResolver.canonicalFormat(entry.format);
    }
}
