package tech.kayys.aqli.runtime.pipeline;

import tech.kayys.aqli.runtime.distributed.Partition;

public final class PipelineStage {
    public final Partition partition;

    public PipelineStage(Partition partition) {
        this.partition = partition;
    }
}
