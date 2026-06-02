package io.phoenixfire.core.discovery;

import io.phoenixfire.api.junit.LauncherCompatibilityDiagnostics;
import io.phoenixfire.api.model.TestId;
import io.phoenixfire.core.supervisor.ForkDiscoveryResult;
import io.phoenixfire.core.supervisor.ForkSupervisor;
import io.phoenixfire.core.util.PhoenixfireLogger;

import java.util.List;

/**
 * Discovers the full expected set of tests before execution begins.
 *
 * <p>Discovery runs in a dedicated fork (via {@link ForkSupervisor}) using the JUnit Platform
 * Launcher, so that loading or scanning test classes cannot crash the controller. The flattened set
 * of leaf {@link TestId}s is the contract the rest of the engine accounts against.
 */
public final class DiscoveryService {

    private final ForkSupervisor supervisor;
    private final PhoenixfireLogger log;

    public DiscoveryService(ForkSupervisor supervisor, PhoenixfireLogger log) {
        this.supervisor = supervisor;
        this.log = log;
    }

    public List<TestId> discover() {
        ForkDiscoveryResult result = supervisor.runDiscovery();
        if (!result.clean()) {
            log.warn("Discovery fork did not exit cleanly (failureMode=" + result.failureMode()
                    + ", exit=" + result.exitCode() + "); discovered " + result.discovered().size()
                    + " tests before failure.");
            if (result.diagnostic() != null) {
                log.warn("Discovery diagnostic tail:\n" + result.diagnostic());
            }
            LauncherCompatibilityDiagnostics.suggestRemediation(result.diagnostic())
                    .ifPresent(hint -> {
                        log.error(hint);
                        if (result.discovered().isEmpty()) {
                            throw new DiscoveryFailedException(hint);
                        }
                    });
        } else {
            log.info("Discovered " + result.discovered().size() + " tests.");
        }
        return result.discovered();
    }
}
