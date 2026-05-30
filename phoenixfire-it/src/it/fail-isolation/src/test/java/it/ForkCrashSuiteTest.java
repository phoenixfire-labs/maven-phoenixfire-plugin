package it;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.fail;

class ForkCrashSuiteTest {

    @Test
    void aFailsFromPollution() throws IOException {
        if (!Files.exists(Paths.get("target", "phoenixfire-pollution-retry.marker"))) {
            fail("simulated pollution failure before the fork dies");
        }
    }

    @Test
    void zCrashesTheFork() throws IOException {
        Path crashMarker = Paths.get("target", "phoenixfire-fork-crash.marker");
        if (!Files.exists(crashMarker)) {
            Files.createDirectories(crashMarker.getParent());
            Files.write(Paths.get("target", "phoenixfire-pollution-retry.marker"), new byte[0]);
            Files.write(crashMarker, new byte[0]);
            Runtime.getRuntime().halt(137);
        }
    }
}
