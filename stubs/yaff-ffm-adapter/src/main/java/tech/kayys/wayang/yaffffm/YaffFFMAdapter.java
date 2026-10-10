package tech.kayys.wayang.yaffffm;

import java.nio.ByteBuffer;

/**
 * Prototype adapter that will use native YAFF via FFM eventually. For now it implements
 * a ByteBuffer-based marshaller to avoid preview APIs in compile-time.
 */
public final class YaffFFMAdapter implements YaffMarshaller {

    private final String nativeLibName;

    public YaffFFMAdapter() {
        this("yaff");
    }

    public YaffFFMAdapter(String nativeLibName) {
        this.nativeLibName = nativeLibName;
        // Try to load the native YAFF library (libyaff) — optional for prototype
        try {
            System.loadLibrary(nativeLibName);
        } catch (Throwable t) {
            System.err.println("Warning: failed to load native YAFF library: " + t.getMessage());
        }
    }

    @Override
    public ByteBuffer marshal(Object request) {
        // TODO: implement actual YAFF encoding. For prototype, return an empty ByteBuffer.
        return ByteBuffer.allocate(0);
    }

    @Override
    public Object unmarshal(ByteBuffer yaffBuffer) {
        // TODO: implement YAFF decoding. For prototype, return null.
        return null;
    }
}
