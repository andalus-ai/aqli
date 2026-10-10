package tech.kayys.wayang.yaffffm;

import java.io.File;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.io.File;
import java.io.IOException;
import java.nio.file.StandardOpenOption;

/**
 * Prototype shared-memory transport for YAFF payloads.
 *
 * Approach:
 * - Create an anonymous temp file under /dev/shm (or system temp) and memory-map it.
 * - Write YAFF-encoded bytes into the mapped buffer.
 * - Share the file path/offset/length via control-plane message (HTTP/Unix socket).
 *
 * Note: For true anonymous shared memory one might use shm_open on POSIX; this prototype
 * uses a temp file in a tmpfs-backed directory for portability.
 */
public class WayangYaffShmTransport {

    private final Path shmDir;

    public WayangYaffShmTransport(Path shmDir) {
        this.shmDir = shmDir;
    }

    public static Path defaultShmDir() {
        String tmp = System.getenv("WAYANG_SHM_DIR");
        if (tmp != null && !tmp.isEmpty()) return Path.of(tmp);
        // prefer /dev/shm when available
        File devShm = new File("/dev/shm");
        if (devShm.exists() && devShm.isDirectory()) return devShm.toPath();
        return Path.of(System.getProperty("java.io.tmpdir"));
    }

    /**
     * Allocate a shared memory region of 'size' bytes and return a MappedByteBuffer wrapper.
     * Caller is responsible for cleaning up the backing file when finished.
     */
    public MappedByteBuffer allocateShm(long size) throws IOException {
        Path tmpFile = java.nio.file.Files.createTempFile(shmDir, "yaff-shm-", ".dat");
        FileChannel fc = FileChannel.open(tmpFile, StandardOpenOption.READ, StandardOpenOption.WRITE);
        fc.truncate(size);
        MappedByteBuffer mb = fc.map(FileChannel.MapMode.READ_WRITE, 0, size);
        return mb;
    }

    /**
     * Produce control metadata to be sent over control plane to consumer.
     */
    public ShmControlMetadata controlMetadata(Path backingFile, long offset, long length) {
        return new ShmControlMetadata(backingFile.toString(), offset, length);
    }

    public static final class ShmControlMetadata {
        public final String path;
        public final long offset;
        public final long length;

        public ShmControlMetadata(String path, long offset, long length) {
            this.path = path;
            this.offset = offset;
            this.length = length;
        }
    }
}
