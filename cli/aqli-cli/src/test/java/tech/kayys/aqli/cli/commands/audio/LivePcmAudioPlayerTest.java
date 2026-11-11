package tech.kayys.aqli.cli.commands.audio;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.util.Map;
import org.junit.jupiter.api.Test;

class LivePcmAudioPlayerTest {
    @Test
    void disabledPlayerIgnoresPcmWithoutOpeningAudioDevice() {
        try (LivePcmAudioPlayer player = new LivePcmAudioPlayer(false)) {
            assertDoesNotThrow(() -> player.write(new byte[] { 1, 2, 3, 4 },
                    Map.of("audio_sample_rate", 16_000, "audio_channels", 1)));
        }
    }

    @Test
    void disabledPlayerAcceptsEmptyAndNullChunks() {
        try (LivePcmAudioPlayer player = new LivePcmAudioPlayer(false)) {
            assertDoesNotThrow(() -> player.write(null, Map.of()));
            assertDoesNotThrow(() -> player.write(new byte[0], Map.of()));
        }
    }
}
