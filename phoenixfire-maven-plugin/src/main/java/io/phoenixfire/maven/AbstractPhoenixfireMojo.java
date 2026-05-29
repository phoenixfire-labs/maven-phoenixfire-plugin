package io.phoenixfire.maven;

import io.phoenixfire.api.model.IsolationLevel;
import io.phoenixfire.core.config.PhoenixfireConfiguration;
import io.phoenixfire.core.engine.ExecutionEngine;
import io.phoenixfire.core.engine.ExecutionSummary;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Common configuration and execution flow for all Phoenixfire goals. Subclasses provide the lifecycle
 * binding, the default include patterns, the reports directory, and how to react to the result.
 */
public abstract class AbstractPhoenixfireMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    protected MavenProject project;

    @Parameter(defaultValue = "${plugin.artifacts}", readonly = true, required = true)
    protected List<Artifact> pluginArtifacts;

    /** Include patterns (Surefire-style globs). Empty means use the goal's defaults. */
    @Parameter
    protected List<String> includes = new ArrayList<>();

    /** Exclude patterns (Surefire-style globs). */
    @Parameter
    protected List<String> excludes = new ArrayList<>();

    /** Extra JVM arguments for each fork (Surefire {@code argLine} parity). */
    @Parameter(property = "argLine")
    protected String argLine;

    /** System properties passed to every fork. */
    @Parameter
    protected Map<String, String> systemPropertyVariables = new LinkedHashMap<>();

    /** Environment variables passed to every fork. */
    @Parameter
    protected Map<String, String> environmentVariables = new LinkedHashMap<>();

    /** Number of concurrent forks for the shared pool. */
    @Parameter(property = "phoenixfire.forkCount", defaultValue = "1")
    protected int forkCount;

    /** Maximum total attempts per test before it is forced to a terminal state. */
    @Parameter(property = "phoenixfire.maxAttempts", defaultValue = "3")
    protected int maxAttempts;

    /** Re-run deterministic assertion failures this many times at the same isolation level. */
    @Parameter(property = "phoenixfire.rerunFailingTestsCount", defaultValue = "0")
    protected int rerunFailingTestsCount;

    /** Heartbeat emit interval inside forks, in milliseconds. */
    @Parameter(property = "phoenixfire.heartbeatInterval", defaultValue = "2000")
    protected long heartbeatInterval;

    /** Max idle time without a heartbeat before a fork is treated as hung, in milliseconds. */
    @Parameter(property = "phoenixfire.heartbeatTimeout", defaultValue = "30000")
    protected long heartbeatTimeout;

    /** Backoff applied before a retry, in milliseconds. */
    @Parameter(property = "phoenixfire.backoff", defaultValue = "0")
    protected long backoff;

    /** Isolation escalation ladder, as level names. */
    @Parameter
    protected List<String> escalationLadder = new ArrayList<>();

    /** Write the crash-safe NDJSON execution journal. */
    @Parameter(property = "phoenixfire.journalEnabled", defaultValue = "true")
    protected boolean journalEnabled;

    /** Do not fail the build on test failures/crashes. */
    @Parameter(property = "phoenixfire.testFailureIgnore", defaultValue = "false")
    protected boolean testFailureIgnore;

    /**
     * If {@code true}, a test that crashed/failed initially but recovered on a retry (flaky) also
     * fails the build. By default ({@code false}) only tests that never recover - i.e. crash or fail
     * on the initial attempt and on every enabled retry/escalation - fail the build.
     */
    @Parameter(property = "phoenixfire.failOnFlakyTests", defaultValue = "false")
    protected boolean failOnFlakyTests;

    /** Skip test execution entirely. */
    @Parameter(property = "skipTests", defaultValue = "false")
    protected boolean skipTests;

    /** Phoenixfire-specific skip flag. */
    @Parameter(property = "phoenixfire.skip", defaultValue = "false")
    protected boolean skip;

    /** Alias for {@code skipTests} (Surefire parity). */
    @Parameter(property = "maven.test.skip", defaultValue = "false")
    protected boolean mavenTestSkip;

    // --- Run-envelope metadata (vendor-agnostic; overrides win, optional local git fallback) ---

    /** Git commit SHA for the run envelope. Maps your CI's variable here, e.g. {@code -Dphoenixfire.git.sha=$GITHUB_SHA}. */
    @Parameter(property = "phoenixfire.git.sha")
    protected String gitSha;

    /** Git branch for the run envelope, e.g. {@code -Dphoenixfire.git.branch=$GITHUB_REF_NAME}. */
    @Parameter(property = "phoenixfire.git.branch")
    protected String gitBranch;

    /** CI provider label for the run envelope (free-form), e.g. {@code github}, {@code gitlab}. */
    @Parameter(property = "phoenixfire.ci.provider")
    protected String ciProvider;

    /** CI build identifier for the run envelope, e.g. {@code -Dphoenixfire.ci.buildId=$GITHUB_RUN_ID}. */
    @Parameter(property = "phoenixfire.ci.buildId")
    protected String ciBuildId;

    /** CI build URL for the run envelope. */
    @Parameter(property = "phoenixfire.ci.buildUrl")
    protected String ciBuildUrl;

    /** Attempt a local {@code git} fallback for commit/branch/dirty when not supplied via overrides. */
    @Parameter(property = "phoenixfire.collectGitMetadata", defaultValue = "true")
    protected boolean collectGitMetadata;

    /** Arbitrary user labels added to the run envelope (e.g. {@code service}, {@code team}). */
    @Parameter
    protected Map<String, String> runLabels = new LinkedHashMap<>();

    protected abstract boolean isSkipped();

    protected abstract List<String> defaultIncludes();

    protected abstract File reportsDirectory();

    protected abstract void handleResult(ExecutionSummary summary) throws MojoFailureException;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (isSkipped() || mavenTestSkip) {
            getLog().info("Phoenixfire execution skipped.");
            return;
        }

        PhoenixfireConfiguration config = buildConfiguration();
        URLClassLoader spiLoader = createSpiClassLoader(config.classpath());

        try (ExecutionEngine engine = new ExecutionEngine(config, new MavenLoggerAdapter(getLog()), spiLoader)) {
            ExecutionSummary summary = engine.run();
            handleResult(summary);
        } catch (MojoFailureException e) {
            throw e;
        } catch (Exception e) {
            throw new MojoExecutionException("Phoenixfire execution failed: " + e.getMessage(), e);
        } finally {
            try {
                spiLoader.close();
            } catch (Exception ignored) {
                // best effort
            }
        }
    }

    protected PhoenixfireConfiguration buildConfiguration() throws MojoExecutionException {
        List<String> classpath = buildForkClasspath();
        List<String> scanRoots = buildScanRoots();

        List<String> effectiveIncludes = includes.isEmpty() ? defaultIncludes() : includes;
        List<String> jvmArgs = splitArgLine(argLine);

        File reports = reportsDirectory();
        File journalFile = new File(reports, "journal.ndjson");

        return PhoenixfireConfiguration.builder()
                .classpath(classpath)
                .scanRoots(scanRoots)
                .includes(effectiveIncludes)
                .excludes(excludes)
                .baseJvmArgs(jvmArgs)
                .systemProperties(systemPropertyVariables == null ? Map.of() : systemPropertyVariables)
                .environment(environmentVariables == null ? Map.of() : environmentVariables)
                .forkCount(forkCount)
                .maxAttempts(maxAttempts)
                .rerunFailingTestsCount(rerunFailingTestsCount)
                .heartbeatIntervalMillis(heartbeatInterval)
                .heartbeatTimeoutMillis(heartbeatTimeout)
                .backoffMillis(backoff)
                .escalationLadder(parseLadder())
                .journalEnabled(journalEnabled)
                .reportsDirectory(reports)
                .journalFile(journalFile)
                .testFailureIgnore(testFailureIgnore)
                .workingDirectory(project.getBasedir() != null ? project.getBasedir().getAbsolutePath() : null)
                .runMetadata(buildRunMetadata())
                .build();
    }

    private io.phoenixfire.api.run.RunMetadata buildRunMetadata() {
        RunMetadataFactory.Overrides overrides = new RunMetadataFactory.Overrides();
        overrides.gitSha = gitSha;
        overrides.gitBranch = gitBranch;
        overrides.ciProvider = ciProvider;
        overrides.ciBuildId = ciBuildId;
        overrides.ciBuildUrl = ciBuildUrl;
        overrides.groupId = project.getGroupId();
        overrides.artifactId = project.getArtifactId();
        overrides.version = project.getVersion();
        overrides.labels = runLabels == null ? Map.of() : runLabels;
        return RunMetadataFactory.build(overrides, project.getBasedir(), collectGitMetadata);
    }

    private List<String> buildForkClasspath() throws MojoExecutionException {
        List<String> classpath = new ArrayList<>();
        try {
            classpath.addAll(project.getTestClasspathElements());
        } catch (DependencyResolutionRequiredException e) {
            throw new MojoExecutionException("Could not resolve test classpath: " + e.getMessage(), e);
        }
        // Ensure the fork runner, API and JUnit Platform launcher are present in the fork.
        if (pluginArtifacts != null) {
            for (Artifact artifact : pluginArtifacts) {
                String groupId = artifact.getGroupId();
                if (("io.phoenixfire".equals(groupId) || "org.junit.platform".equals(groupId))
                        && artifact.getFile() != null) {
                    String path = artifact.getFile().getAbsolutePath();
                    if (!classpath.contains(path)) {
                        classpath.add(path);
                    }
                }
            }
        }
        return classpath;
    }

    private List<String> buildScanRoots() {
        List<String> roots = new ArrayList<>();
        if (project.getBuild() != null) {
            if (project.getBuild().getTestOutputDirectory() != null) {
                roots.add(project.getBuild().getTestOutputDirectory());
            }
            if (project.getBuild().getOutputDirectory() != null) {
                roots.add(project.getBuild().getOutputDirectory());
            }
        }
        return roots;
    }

    private List<IsolationLevel> parseLadder() throws MojoExecutionException {
        if (escalationLadder == null || escalationLadder.isEmpty()) {
            return List.of();
        }
        List<IsolationLevel> levels = new ArrayList<>();
        for (String name : escalationLadder) {
            try {
                levels.add(IsolationLevel.valueOf(name.trim()));
            } catch (IllegalArgumentException e) {
                throw new MojoExecutionException("Unknown isolation level in escalationLadder: " + name);
            }
        }
        return levels;
    }

    private static List<String> splitArgLine(String argLine) {
        if (argLine == null || argLine.isBlank()) {
            return List.of();
        }
        return Arrays.asList(argLine.trim().split("\\s+"));
    }

    private URLClassLoader createSpiClassLoader(List<String> classpath) throws MojoExecutionException {
        List<URL> urls = new ArrayList<>();
        for (String entry : classpath) {
            try {
                urls.add(new File(entry).toURI().toURL());
            } catch (MalformedURLException e) {
                throw new MojoExecutionException("Bad classpath entry: " + entry, e);
            }
        }
        return new URLClassLoader(urls.toArray(new URL[0]), getClass().getClassLoader());
    }
}
