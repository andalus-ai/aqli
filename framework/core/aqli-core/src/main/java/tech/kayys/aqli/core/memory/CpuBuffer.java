package tech.kayys.aqli.core.memory;


import tech.kayys.aljabr.core.tensor.*;
import tech.kayys.aqli.ir.*;
import tech.kayys.aqli.ir.schema.*;
import tech.kayys.aqli.ir.validate.*;
import java.util.*;

import java.lang.foreign.*;

public final class CpuBuffer implements Buffer {
    private final Arena arena;
    private final MemorySegment segment;
    private final boolean owned;
    private int refCount = 1;

    public CpuBuffer(long sizeBytes) {
        this.arena = Arena.ofShared();
        this.segment = arena.allocate(sizeBytes);
        this.owned = true;
    }

    public CpuBuffer(MemorySegment segment, Arena arena) {
        this.segment = segment;
        this.arena = arena;
        this.owned = false;
    }

    @Override
    public MemorySegment segment() {
        return segment;
    }

    @Override
    public long sizeBytes() {
        return segment.byteSize();
    }

    @Override
    public synchronized void retain() {
        refCount++;
    }

    @Override
    public synchronized void release() {
        refCount--;
        if (refCount == 0 && owned) {
            arena.close();
        }
    }
}