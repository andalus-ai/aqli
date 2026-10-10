package tech.kayys.aqli.core.memory;


import tech.kayys.alkhawarizm.core.tensor.*;
import tech.kayys.aqli.ir.*;
import tech.kayys.aqli.ir.schema.*;
import tech.kayys.aqli.ir.validate.*;
import java.util.*;

import java.lang.foreign.MemorySegment;

public interface Buffer {
    MemorySegment segment();

    long sizeBytes();

    void retain();

    void release();

    default void copyFrom(MemorySegment src, long bytes) {
        segment().asSlice(0, bytes).copyFrom(src.asSlice(0, bytes));
    }
}