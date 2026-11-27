package tech.kayys.aqli.runtime.tool;

import tech.kayys.aqli.spi.tool.Tool;

import java.util.Optional;

/**
 * Resolves a declared tool name to its application-provided implementation.
 *
 * <p>Implementations may consult an application registry or plugin scope.
 * Resolution is deliberately separate from authorization and execution.</p>
 */
@FunctionalInterface
public interface ToolCallResolver {

    /**
     * Resolve a tool by its declared name.
     *
     * @param name name from the validated model tool call
     * @return the matching tool, or empty when no implementation is available
     */
    Optional<Tool> resolve(String name);
}
