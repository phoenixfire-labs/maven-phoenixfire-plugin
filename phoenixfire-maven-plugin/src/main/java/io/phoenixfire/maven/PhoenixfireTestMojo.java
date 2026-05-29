package io.phoenixfire.maven;

import io.phoenixfire.core.engine.ExecutionSummary;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

import java.io.File;
import java.util.List;

/**
 * Runs unit tests with fault-tolerant Phoenixfire orchestration, bound to the {@code test} phase as
 * an opt-in replacement for {@code maven-surefire-plugin:test}.
 */
@Mojo(name = "test",
        defaultPhase = LifecyclePhase.TEST,
        requiresDependencyResolution = ResolutionScope.TEST,
        threadSafe = true)
public class PhoenixfireTestMojo extends AbstractPhoenixfireMojo {

    @Parameter(defaultValue = "${project.build.directory}/phoenixfire-reports", property = "phoenixfire.reportsDirectory")
    private File reportsDir;

    @Override
    protected boolean isSkipped() {
        return skip || skipTests;
    }

    @Override
    protected List<String> defaultIncludes() {
        return List.of("**/Test*.java", "**/*Test.java", "**/*Tests.java", "**/*TestCase.java");
    }

    @Override
    protected File reportsDirectory() {
        return reportsDir;
    }

    @Override
    protected void handleResult(ExecutionSummary summary) throws MojoFailureException {
        if (summary.hasFailures() && !testFailureIgnore) {
            throw new MojoFailureException("There are test failures.\n\n" + summary.describe()
                    + "\nSee reports in " + reportsDir);
        }
    }
}
