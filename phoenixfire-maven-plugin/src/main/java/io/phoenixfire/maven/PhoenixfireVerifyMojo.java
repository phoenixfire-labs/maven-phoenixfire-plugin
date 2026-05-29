package io.phoenixfire.maven;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.io.IOException;

/**
 * Asserts on the integration-test summary written by the {@code integration-test} goal and fails the
 * build if any integration test failed or crashed. Bound to the {@code verify} phase, mirroring
 * {@code maven-failsafe-plugin:verify}.
 */
@Mojo(name = "verify", defaultPhase = LifecyclePhase.VERIFY, threadSafe = true)
public class PhoenixfireVerifyMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project.build.directory}/phoenixfire-it-reports",
            property = "phoenixfire.it.reportsDirectory")
    private File reportsDir;

    @Parameter(property = "phoenixfire.testFailureIgnore", defaultValue = "false")
    private boolean testFailureIgnore;

    @Parameter(property = "phoenixfire.skip", defaultValue = "false")
    private boolean skip;

    @Parameter(property = "skipTests", defaultValue = "false")
    private boolean skipTests;

    @Parameter(property = "skipITs", defaultValue = "false")
    private boolean skipITs;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip || skipTests || skipITs) {
            getLog().info("Phoenixfire verify skipped.");
            return;
        }
        SummaryFile.Summary summary;
        try {
            summary = SummaryFile.read(reportsDir);
        } catch (IOException e) {
            throw new MojoExecutionException("Could not read Phoenixfire IT summary: " + e.getMessage(), e);
        }
        if (summary == null) {
            getLog().info("No Phoenixfire integration-test summary found; nothing to verify.");
            return;
        }
        getLog().info("Phoenixfire verify: total=" + summary.total
                + ", failed=" + summary.failed + ", crashed=" + summary.crashed);
        if (summary.hasFailures() && !testFailureIgnore) {
            throw new MojoFailureException("There are integration test failures.\n"
                    + "Failures: " + summary.failed + ", Crashed: " + summary.crashed
                    + "\nSee reports in " + reportsDir);
        }
    }
}
