package tech.kayys.aqli.runtime;

import tech.kayys.aqli.runtime.control.ExecutionController;

public final class ExecutionSession {
    public final ExecutionController controller;

    public ExecutionSession(ExecutionController controller) {
        this.controller = controller;
    }
}