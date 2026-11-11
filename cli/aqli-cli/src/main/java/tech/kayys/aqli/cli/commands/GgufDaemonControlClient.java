package tech.kayys.aqli.cli.commands;

import static tech.kayys.aqli.inference.gguf.GgufDaemonProtocol.writeString;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Socket transport for daemon control requests.
 *
 * <p>The runner owns daemon discovery and shutdown policy; this client owns
 * only the stop-request wire exchange.</p>
 */
final class GgufDaemonControlClient {

    int requestStop(int port, String stopMagic, int connectTimeoutMillis, int commandError) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), connectTimeoutMillis);
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream());
            writeString(out, stopMagic);
            out.flush();
            int channel = in.read();
            int status = in.readInt();
            return channel == 0 ? status : commandError;
        } catch (Exception ignored) {
            // Preserve the stop command's best-effort behavior when the daemon
            // has already exited or its control socket is unavailable.
            return 0;
        }
    }
}
