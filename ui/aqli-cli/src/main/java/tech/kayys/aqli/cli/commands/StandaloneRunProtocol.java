package tech.kayys.aqli.cli.commands;

/**
 * Shared process contract for format-specific standalone {@code aqli run}
 * execution paths.
 *
 * <p>A standalone runner returns {@link #FALLBACK_TO_FULL_CLI} only when it did not
 * handle the request and the full Picocli command should continue. Other
 * non-zero statuses represent an execution failure and must not silently
 * trigger a second inference attempt.</p>
 */
final class StandaloneRunProtocol {

    static final int FALLBACK_TO_FULL_CLI = 42;

    private StandaloneRunProtocol() {
    }

    static boolean isFallbackToFullCliStatus(int status) {
        return status == FALLBACK_TO_FULL_CLI;
    }
}
