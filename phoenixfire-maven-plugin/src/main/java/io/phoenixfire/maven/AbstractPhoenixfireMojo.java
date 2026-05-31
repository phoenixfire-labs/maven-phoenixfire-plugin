package io.phoenixfire.maven;

import io.phoenixfire.api.model.IsolationLevel;
import io.phoenixfire.core.config.PhoenixfireConfiguration;
import io.phoenixfire.core.engine.ExecutionEngine;
import io.phoenixfire.core.engine.ExecutionSummary;
import io.phoenixfire.core.select.TestSelector;
import io.phoenixfire.core.util.ArgLine;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Common configuration and execution flow for all Phoenixfire goals. Subclasses provide the lifecycle
 * binding, the default include patterns, the reports directory, and how to react to the result.
 */
public abstract class AbstractPhoenixfireMojo extends AbstractMojo {

    /** Surefire-style late-expansion placeholder, e.g. {@code @{argLine}}. */
    private static final Pattern LATE_PROPERTY = Pattern.compile("@\\{(.+?)\\}");

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

    /**
     * A file listing additional include patterns, one per line ({@code #} starts a comment).
     * Surefire {@code includesFile} parity - lets large selections live in a file instead of the POM
     * or the command line.
     */
    @Parameter(property = "phoenixfire.includesFile")
    protected File includesFile;

    /** A file listing additional exclude patterns, one per line ({@code #} comments allowed). */
    @Parameter(property = "phoenixfire.excludesFile")
    protected File excludesFile;

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

    /**
     * 1-based shard index for this run (Jest {@code --shard} style). Only takes effect when
     * {@link #shardCount} &gt; 1. Sharding is by test class, balanced by class count, and deterministic
     * so every node computes the same partition without coordination.
     */
    @Parameter(property = "phoenixfire.shard.index", defaultValue = "0")
    protected int shardIndex;

    /** Total number of shards to split the suite across; {@code <= 1} disables sharding. */
    @Parameter(property = "phoenixfire.shard.count", defaultValue = "0")
    protected int shardCount;

    /** Maximum total attempts per test before it is forced to a terminal state. */
    @Parameter(property = "phoenixfire.maxAttempts", defaultValue = "3")
    protected int maxAttempts;

    /** Re-run deterministic assertion failures this many times at the same isolation level. */
    @Parameter(property = "phoenixfire.rerunFailingTestsCount", defaultValue = "0")
    protected int rerunFailingTestsCount;

    /**
     * Attempts allowed in the shared fork pool before an infrastructure failure (crash/hang/OOM)
     * escalates to an isolated fork. The default {@code 1} escalates on the first shared-pool crash;
     * a higher value retries the affected tests in a fresh shared-pool fork that many times first -
     * the "run in a shared JVM, restart where the dead fork left off, then isolate" workflow. Bounded
     * by {@code maxAttempts}.
     */
    @Parameter(property = "phoenixfire.sharedForkPoolMaxPasses", defaultValue = "1")
    protected int sharedForkPoolMaxPasses;

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

    /** Write an append-only NDJSON audit log of execution events ({@code journal.ndjson}). */
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

    // --- Run-envelope metadata (vendor-agnostic; supplied via -D properties and/or plugin <configuration>) ---

    /** Git commit SHA for the run envelope, e.g. {@code -Dphoenixfire.git.sha=$GITHUB_SHA}. */
    @Parameter(property = "phoenixfire.git.sha")
    protected String gitSha;

    /** Git branch for the run envelope, e.g. {@code -Dphoenixfire.git.branch=$GITHUB_REF_NAME}. */
    @Parameter(property = "phoenixfire.git.branch")
    protected String gitBranch;

    /** Whether the working tree was dirty ({@code true} / {@code false}); omitted when unset. */
    @Parameter(property = "phoenixfire.git.dirty")
    protected Boolean gitDirty;

    /** CI provider label for the run envelope (free-form), e.g. {@code github}, {@code gitlab}. */
    @Parameter(property = "phoenixfire.ci.provider")
    protected String ciProvider;

    /** CI build identifier for the run envelope, e.g. {@code -Dphoenixfire.ci.buildId=$GITHUB_RUN_ID}. */
    @Parameter(property = "phoenixfire.ci.buildId")
    protected String ciBuildId;

    /** CI build URL for the run envelope. */
    @Parameter(property = "phoenixfire.ci.buildUrl")
    protected String ciBuildUrl;

    /** Arbitrary user labels added to the run envelope (e.g. {@code service}, {@code team}). */
    @Parameter
    protected Map<String, String> runLabels = new LinkedHashMap<>();

    protected abstract boolean isSkipped();

    protected abstract List<String> defaultIncludes();

    /**
     * The goal-specific selection expression: {@code -Dtest} for unit tests, {@code -Dit.test} for
     * integration tests. Returns {@code null}/blank when not set.
     */
    protected abstract String testFilterExpression();

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

        List<String> mergedIncludes = new ArrayList<>(includes);
        mergedIncludes.addAll(readPatternFile(includesFile));

        // -Dtest / -Dit.test: its class patterns override includes (Surefire parity) so that a named
        // class outside the default globs is still discovered; method-level precision is applied later.
        String testFilter = testFilterExpression();
        List<String> selectorGlobs = TestSelector.parse(testFilter).discoveryIncludeGlobs();
        List<String> effectiveIncludes;
        if (!selectorGlobs.isEmpty()) {
            effectiveIncludes = selectorGlobs;
        } else {
            effectiveIncludes = mergedIncludes.isEmpty() ? defaultIncludes() : mergedIncludes;
        }

        List<String> mergedExcludes = new ArrayList<>(excludes);
        mergedExcludes.addAll(readPatternFile(excludesFile));

        List<String> jvmArgs = ArgLine.tokenize(expandLateProperties(argLine));

        File reports = reportsDirectory();
        File journalFile = new File(reports, "journal.ndjson");

        return PhoenixfireConfiguration.builder()
                .classpath(classpath)
                .scanRoots(scanRoots)
                .includes(effectiveIncludes)
                .excludes(mergedExcludes)
                .baseJvmArgs(jvmArgs)
                .systemProperties(systemPropertyVariables == null ? Map.of() : systemPropertyVariables)
                .environment(environmentVariables == null ? Map.of() : environmentVariables)
                .forkCount(forkCount)
                .maxAttempts(maxAttempts)
                .rerunFailingTestsCount(rerunFailingTestsCount)
                .sharedForkPoolMaxPasses(sharedForkPoolMaxPasses)
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
                .testFilter(testFilter)
                .shardIndex(shardIndex)
                .shardCount(shardCount)
                .build();
    }

    private io.phoenixfire.api.run.RunMetadata buildRunMetadata() {
        RunMetadataFactory.Overrides overrides = new RunMetadataFactory.Overrides();
        overrides.gitSha = gitSha;
        overrides.gitBranch = gitBranch;
        overrides.gitDirty = gitDirty;
        overrides.ciProvider = ciProvider;
        overrides.ciBuildId = ciBuildId;
        overrides.ciBuildUrl = ciBuildUrl;
        overrides.groupId = project.getGroupId();
        overrides.artifactId = project.getArtifactId();
        overrides.version = project.getVersion();
        overrides.labels = runLabels == null ? Map.of() : runLabels;
        return RunMetadataFactory.build(overrides);
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
                if (isForkClasspathPluginArtifact(artifact)) {
                    String path = artifact.getFile().getAbsolutePath();
                    if (!classpath.contains(path)) {
                        classpath.add(path);
                    }
                }
            }
        }
        return classpath;
    }

    /** Phoenixfire modules and JUnit Platform launcher jars required in every test fork. */
    private static boolean isForkClasspathPluginArtifact(Artifact artifact) {
        if (artifact.getFile() == null) {
            return false;
        }
        String groupId = artifact.getGroupId();
        return "io.github.benmanifold".equals(groupId) || "org.junit.platform".equals(groupId);
    }

    /** Reads include/exclude patterns from a file: one per line, blank lines and {@code #} comments ignored. */
    private List<String> readPatternFile(File file) throws MojoExecutionException {
        if (file == null) {
            return List.of();
        }
        if (!file.isFile()) {
            getLog().warn("Phoenixfire pattern file not found, ignoring: " + file);
            return List.of();
        }
        List<String> patterns = new ArrayList<>();
        try {
            for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                    patterns.add(trimmed);
                }
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Could not read pattern file " + file + ": " + e.getMessage(), e);
        }
        return patterns;
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

    /**
     * Surefire {@code @{property}} late expansion: resolve {@code @{name}} against build-time
     * properties (project properties first - this is where {@code jacoco:prepare-agent} publishes its
     * {@code argLine} - then JVM system properties) just before the fork is launched. Unresolved
     * placeholders are left untouched so a typo is visible rather than silently dropped.
     */
    private String expandLateProperties(String value) {
        if (value == null || !value.contains("@{")) {
            return value;
        }
        Matcher m = LATE_PROPERTY.matcher(value);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String resolved = lookupProperty(m.group(1));
            m.appendReplacement(out, Matcher.quoteReplacement(resolved != null ? resolved : m.group(0)));
        }
        m.appendTail(out);
        return out.toString();
    }

    private String lookupProperty(String name) {
        String value = project.getProperties().getProperty(name);
        return value != null ? value : System.getProperty(name);
    }

    protected URLClassLoader createSpiClassLoader(List<String> classpath) throws MojoExecutionException {
        List<URL> urls = new ArrayList<>();
        for (String entry : classpath) {
            try {
                if (entry.contains("://")) {
                    urls.add(new java.net.URL(entry));
                } else {
                    urls.add(new File(entry).toURI().toURL());
                }
            } catch (MalformedURLException e) {
                throw new MojoExecutionException("Bad classpath entry: " + entry, e);
            }
        }
        return new URLClassLoader(urls.toArray(new URL[0]), getClass().getClassLoader());
    }
}
