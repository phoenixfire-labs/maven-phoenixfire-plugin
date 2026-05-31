package io.phoenixfire.core.testsupport;

import io.phoenixfire.api.spi.ForkConfig;
import io.phoenixfire.core.config.PhoenixfireConfiguration;
import io.phoenixfire.core.supervisor.ForkLauncher;

import java.io.IOException;
import java.nio.file.Path;

/** Fork launcher that always fails for IO error coverage. */
public final class FailingForkLauncher extends ForkLauncher {

    public FailingForkLauncher(PhoenixfireConfiguration config, int controllerPort) {
        super(config, controllerPort);
    }

    @Override
    public Process launch(String forkId, String token, ForkConfig forkConfig, Path logFile) throws IOException {
        throw new IOException("simulated launch failure");
    }
}
