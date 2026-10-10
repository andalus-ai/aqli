package tech.kayys.wayang.yaffffm;

import java.nio.ByteBuffer;

/**
 * Simple marshaller interface for YAFF using ByteBuffer to avoid preview APIs in prototype.
 * Implementations should produce YAFF-encoded ByteBuffers ready to be shared via shm.
 */
public interface YaffMarshaller {
    /**
     * Serialize the given request object into a YAFF ByteBuffer.
     * The caller owns or manages the buffer lifecycle depending on transport semantics.
     */
    ByteBuffer marshal(Object request);

    /**
     * Deserialize a YAFF ByteBuffer into a response object.
     */
    Object unmarshal(ByteBuffer yaffBuffer);
}
