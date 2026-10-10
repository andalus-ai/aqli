package tech.kayys.wayang.yaffffm;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Basic SHM YAFF transport provider.
 * This implementation is a thin adapter that uses WayangYaffShmTransport for allocation and
 * the YaffFFMAdapter for (de)serialization. It is intentionally minimal to allow local
 * testing; production-quality code should add lifecycle, security, and cleanup handling.
 *
 * NOTE: This class intentionally does NOT implement the WayangYaffTransportProvider interface
 * to avoid a compile-time dependency on the Wayang SDK. The SDK will discover and use this
 * class via reflection (ReflectionWayangYaffTransportProvider) at runtime.
 */
public class ShmYaffTransportProvider {

    private final WayangYaffShmTransport shmTransport;
    private final YaffFFMAdapter adapter;

    public ShmYaffTransportProvider() {
        this(WayangYaffShmTransport.defaultShmDir());
    }

    public ShmYaffTransportProvider(Path shmDir) {
        Objects.requireNonNull(shmDir);
        this.shmTransport = new WayangYaffShmTransport(shmDir);
        this.adapter = new YaffFFMAdapter();
    }

    public int priority() {
        return 10; // high priority
    }

    public String id() {
        return "shm-yaff";
    }

    public ByteBuffer sendRequest(ByteBuffer request) throws Exception {
        // For prototype: echo back the request after a trivial unmarshal/marshal roundtrip
        Object reqObj = adapter.unmarshal(request);
        // Do inference handshake with local runtime via shared memory (omitted)
        // For now marshal the same object back
        ByteBuffer response = adapter.marshal(reqObj);
        return response;
    }
}
