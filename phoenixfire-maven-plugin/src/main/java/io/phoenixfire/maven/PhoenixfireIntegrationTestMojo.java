package io.phoenixfire.maven;

import io.phoenixfire.core.engine.ExecutionSummary;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Runs integration tests with Phoenixfire, bound to the {@code integration-test} phase as an opt-in
 * replacement for {@code maven-failsafe-plugin:integration-test}.
 *
 * <p>Following Failsafe semantics, failures here do not fail the build immediately; instead a
 * summary file is written and the {@code verify} goal asserts on it later, so that any post-IT
 * teardown still runs.
 */
@Mojo(name = "integration-test",
        defaultPhase = LifecyclePhase.INTEGRATION_TEST,
        requiresDependencyResolution = ResolutionScope.TEST,
        threadSafe = true)
public class PhoenixfireIntegrationTestMojo extends AbstractPhoenixfireMojo {

    @Parameter(defaultValue = "${project.build.directory}/phoenixfire-it-reports",
            property = "phoenixfire.it.reportsDirectory")
    private File reportsDir;

    @Parameter(property = "skipITs", defaultValue = "false")
    private boolean skipITs;

    @Override
    protected boolean isSkipped() {
        return skip || skipTests || skipITs;
    }

    @Override
    protected List<String> defaultIncludes() {
        return List.of("**/IT*.java", "**/*IT.java", "**/*ITCase.java");
    }

    @Override
    protected File reportsDirectory() {
        return reportsDir;
    }

    @Override
    protected void handleResult(ExecutionSummary summary) throws MojoFailureException {
        try {
            SummaryFile.write(reportsDir, summary);
        } catch (IOException e) {
            throw new MojoFailureException("Failed to write Phoenixfire IT summary: " + e.getMessage(), e);
        }
        // Do not fail here; verify will assert on the summary.
        getLog().info("Phoenixfire integration-test complete. " + summary.describe());
    }
}
