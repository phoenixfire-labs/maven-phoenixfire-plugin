package io.phoenixfire.maven;

import io.phoenixfire.api.model.IsolationLevel;
import io.phoenixfire.core.config.PhoenixfireConfiguration;
import io.phoenixfire.core.engine.ExecutionSummary;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.model.Build;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbstractPhoenixfireMojoTest {

    private static final String SIMULATED_FORK = "io.phoenixfire.maven.testsupport.MavenSimulatedFork";
    private static final String PROP_SIM_MODE = "phoenixfire.sim.mode";
    private static final String MODE_DISCOVER = "discover";
    private static final String MODE_EXECUTE_PASS = "execute-pass";
    private static final String MODE_EXECUTE_FAIL = "execute-fail";

    @TempDir
    Path tempDir;

    private String prevForkMain;
    private String prevSimMode;

    @BeforeEach
    @SuppressWarnings("unused") // invoked by JUnit, not by direct calls
    void enableSimulatedFork() {
        prevForkMain = System.getProperty("phoenixfire.fork.main");
        prevSimMode = System.getProperty(PROP_SIM_MODE);
        System.setProperty("phoenixfire.fork.main", SIMULATED_FORK);
        System.setProperty(PROP_SIM_MODE, MODE_DISCOVER);
    }

    @AfterEach
    @SuppressWarnings("unused") // invoked by JUnit, not by direct calls
    void clearForkProps() {
        restoreProperty("phoenixfire.fork.main", prevForkMain);
        restoreProperty(PROP_SIM_MODE, prevSimMode);
    }

    @Test
    void executeSkipsOnMavenTestSkip() throws Exception {
        ExposingMojo mojo = newMojo();
        MojoTestReflection.setField(mojo, "mavenTestSkip", true);
        assertDoesNotThrow(mojo::execute);
    }

    @Test
    void executeSkipsOnSkipTests() throws Exception {
        ExposingMojo mojo = newMojo();
        MojoTestReflection.setField(mojo, "skipTests", true);
        assertDoesNotThrow(mojo::execute);
    }

    @Test
    void executeRunsWithSimulatedFork() throws Exception {
        ExposingMojo mojo = newMojo();
        System.setProperty(PROP_SIM_MODE, MODE_EXECUTE_PASS);
        assertDoesNotThrow(mojo::execute);
    }

    @Test
    void executeWrapsUnexpectedException() throws Exception {
        FaultInjectingMojo mojo = new FaultInjectingMojo();
        mojo.setLog(new SystemStreamLog());
        configureMojo(mojo);
        System.setProperty(PROP_SIM_MODE, MODE_EXECUTE_PASS);
        MojoExecutionException ex = assertThrows(MojoExecutionException.class, mojo::execute);
        assertTrue(ex.getMessage().contains("Phoenixfire execution failed"));
    }

    @Test
    void executeSpiLoaderCloseSwallowsErrors() throws Exception {
        CloseFailMojo mojo = new CloseFailMojo();
        mojo.setLog(new SystemStreamLog());
        configureMojo(mojo);
        System.setProperty(PROP_SIM_MODE, MODE_EXECUTE_PASS);
        assertDoesNotThrow(mojo::execute);
    }

    @Test
    void buildConfigurationHandlesNullBasedirRunLabelsAndPluginArtifacts() throws Exception {
        ExposingMojo mojo = new ExposingMojo();
        mojo.setLog(new SystemStreamLog());
        MavenProject project = new FixedClasspathProject(simulatedForkClasspath());
        project.setGroupId("g");
        project.setArtifactId("a");
        project.setVersion("1.0");
        project.setFile(null);
        MojoTestReflection.setField(mojo, "project", project);
        MojoTestReflection.setField(mojo, "reportsDir", tempDir.resolve("reports").toFile());
        MojoTestReflection.setField(mojo, "pluginArtifacts", null);
        MojoTestReflection.setField(mojo, "runLabels", null);
        MojoTestReflection.setField(mojo, "argLine", null);
        PhoenixfireConfiguration config = mojo.exposeBuildConfiguration();
        assertNull(config.workingDirectory());
    }

    @Test
    void buildConfigurationSkipsDuplicateClasspathEntries() throws Exception {
        File jar = jarPath(AbstractPhoenixfireMojo.class);
        List<String> classpath = new ArrayList<>(simulatedForkClasspath());
        classpath.add(jar.getAbsolutePath());
        ExposingMojo mojo = new ExposingMojo();
        mojo.setLog(new SystemStreamLog());
        MavenProject project = new FixedClasspathProject(classpath);
        project.setGroupId("g");
        project.setArtifactId("a");
        project.setVersion("1.0");
        project.setFile(tempDir.toFile());
        MojoTestReflection.setField(mojo, "project", project);
        MojoTestReflection.setField(mojo, "reportsDir", tempDir.resolve("reports").toFile());
        MojoTestReflection.setField(mojo, "pluginArtifacts",
                List.of(pluginArtifact("io.github.phoenixfire-labs", "phoenixfire-core", jar)));
        assertEquals(1, mojo.exposeBuildConfiguration().classpath().stream()
                .filter(p -> p.equals(jar.getAbsolutePath())).count());
    }

    @Test
    void buildConfigurationUsesDefaultIncludesWhenNoneConfigured() throws Exception {
        ExposingMojo mojo = newMojo();
        MojoTestReflection.setField(mojo, "includes", List.of());
        PhoenixfireConfiguration config = mojo.exposeBuildConfiguration();
        assertTrue(config.includes().stream().anyMatch(p -> p.contains("Test")));
    }

    @Test
    void buildConfigurationHandlesNullEscalationLadder() throws Exception {
        ExposingMojo mojo = newMojo();
        MojoTestReflection.setField(mojo, "escalationLadder", null);
        assertDoesNotThrow(mojo::exposeBuildConfiguration);
    }

    @Test
    void buildConfigurationReadsPatternFileCommentsAndBlanks() throws Exception {
        File includes = tempDir.resolve("patterns.txt").toFile();
        Files.writeString(includes.toPath(), "# comment\n\n  **/Ok.java  \n");
        ExposingMojo mojo = newMojo();
        MojoTestReflection.setField(mojo, "includes", List.of());
        MojoTestReflection.setField(mojo, "includesFile", includes);
        PhoenixfireConfiguration config = mojo.exposeBuildConfiguration();
        assertTrue(config.includes().contains("**/Ok.java"));
    }

    @Test
    void executeRethrowsMojoFailureException() throws Exception {
        ExposingMojo mojo = newMojo();
        MojoTestReflection.setField(mojo, "failOnFlakyTests", false);
        MojoTestReflection.setField(mojo, "testFailureIgnore", false);
        System.setProperty(PROP_SIM_MODE, MODE_EXECUTE_FAIL);
        assertNotNull(assertThrows(MojoFailureException.class, () -> mojo.execute()));
    }

    @Test
    void buildConfigurationMergesIncludesAndExcludesFiles() throws Exception {
        File includes = tempDir.resolve("includes.txt").toFile();
        Files.writeString(includes.toPath(), "# comment\n**/Extra*.java\n");
        File excludes = tempDir.resolve("excludes.txt").toFile();
        Files.writeString(excludes.toPath(), "**/Skip*.java\n");

        ExposingMojo mojo = newMojo();
        MojoTestReflection.setField(mojo, "includes", new ArrayList<>(List.of("**/Base*.java")));
        MojoTestReflection.setField(mojo, "excludes", new ArrayList<>());
        MojoTestReflection.setField(mojo, "includesFile", includes);
        MojoTestReflection.setField(mojo, "excludesFile", excludes);

        PhoenixfireConfiguration config = mojo.exposeBuildConfiguration();
        assertTrue(config.includes().contains("**/Extra*.java"));
        assertTrue(config.excludes().contains("**/Skip*.java"));
    }

    @Test
    void buildConfigurationWarnsOnMissingPatternFile() throws Exception {
        ExposingMojo mojo = newMojo();
        MojoTestReflection.setField(mojo, "includesFile", new File(tempDir.toFile(), "missing-includes.txt"));
        assertDoesNotThrow(mojo::exposeBuildConfiguration);
    }

    @Test
    void buildConfigurationExpandsLateArgLineFromProjectProperties() throws Exception {
        ExposingMojo mojo = newMojo();
        project(mojo).getProperties().setProperty("argLine", "-Xmx256m");
        MojoTestReflection.setField(mojo, "argLine", "@{argLine} -ea");

        PhoenixfireConfiguration config = mojo.exposeBuildConfiguration();
        assertTrue(config.baseJvmArgs().stream().anyMatch(a -> a.contains("-Xmx256m")));
    }

    @Test
    void buildConfigurationExpandsLateArgLineFromSystemProperty() throws Exception {
        ExposingMojo mojo = newMojo();
        String key = "phoenixfire.test.argLine." + System.nanoTime();
        System.setProperty(key, "-Dcustom");
        try {
            MojoTestReflection.setField(mojo, "argLine", "@{" + key + "}");
            PhoenixfireConfiguration config = mojo.exposeBuildConfiguration();
            assertTrue(config.baseJvmArgs().stream().anyMatch(a -> a.contains("-Dcustom")));
        } finally {
            System.clearProperty(key);
        }
    }

    @Test
    void buildConfigurationLeavesUnresolvedLatePlaceholder() throws Exception {
        ExposingMojo mojo = newMojo();
        MojoTestReflection.setField(mojo, "argLine", "@{no.such.property}");

        PhoenixfireConfiguration config = mojo.exposeBuildConfiguration();
        assertTrue(config.baseJvmArgs().stream().anyMatch(a -> a.contains("@{no.such.property}")));
    }

    @Test
    void buildConfigurationUsesCustomEscalationLadder() throws Exception {
        ExposingMojo mojo = newMojo();
        MojoTestReflection.setField(mojo, "escalationLadder", List.of("SHARED_FORK_POOL", "FRESH_FORK"));

        PhoenixfireConfiguration config = mojo.exposeBuildConfiguration();
        assertEquals(List.of(IsolationLevel.SHARED_FORK_POOL, IsolationLevel.FRESH_FORK),
                config.escalationLadder());
    }

    @Test
    void buildConfigurationRejectsInvalidEscalationLevel() throws Exception {
        ExposingMojo mojo = newMojo();
        MojoTestReflection.setField(mojo, "escalationLadder", List.of("NOT_A_LEVEL"));
        assertNotNull(assertThrows(MojoExecutionException.class, () -> mojo.exposeBuildConfiguration()));
    }

    @Test
    void buildConfigurationUsesTestFilterIncludes() throws Exception {
        ExposingMojo mojo = newMojo();
        MojoTestReflection.setField(mojo, "test", "com.example.FooTest");
        MojoTestReflection.setField(mojo, "includes", List.of("**/IgnoredWhenFilterSet.java"));

        PhoenixfireConfiguration config = mojo.exposeBuildConfiguration();
        assertTrue(config.includes().stream().anyMatch(g -> g.contains("FooTest")));
    }

    @Test
    void buildConfigurationAddsPluginArtifactsToClasspath() throws Exception {
        ExposingMojo mojo = newMojo();
        File pluginJar = jarPath(AbstractPhoenixfireMojo.class);
        File junitJar = jarPath(org.junit.platform.launcher.Launcher.class);
        DefaultArtifact skipped = pluginArtifact("other.group", "ignored", pluginJar);
        skipped.setFile(null);
        List<Artifact> artifacts = List.of(
                pluginArtifact("io.github.phoenixfire-labs", "phoenixfire-core", pluginJar),
                pluginArtifact("org.junit.platform", "junit-platform-launcher", junitJar),
                skipped);
        MojoTestReflection.setField(mojo, "pluginArtifacts", artifacts);

        PhoenixfireConfiguration config = mojo.exposeBuildConfiguration();
        assertTrue(config.classpath().contains(pluginJar.getAbsolutePath()));
        assertTrue(config.classpath().contains(junitJar.getAbsolutePath()));
    }

    @Test
    void buildConfigurationIncludesScanRootsAndRunMetadata() throws Exception {
        ExposingMojo mojo = newMojo();
        MojoTestReflection.setField(mojo, "gitSha", "deadbeef");
        MojoTestReflection.setField(mojo, "ciProvider", "github");
        MojoTestReflection.setField(mojo, "runLabels", Map.of("team", "core"));

        PhoenixfireConfiguration config = mojo.exposeBuildConfiguration();
        assertNotNull(config.scanRoots());
        assertTrue(config.scanRoots().stream().anyMatch(p -> p.contains("test-classes")));
        assertEquals("deadbeef", config.runMetadata().gitSha());
        assertEquals("github", config.runMetadata().ciProvider());
    }

    @Test
    void buildConfigurationHandlesNullPropertyMaps() throws Exception {
        ExposingMojo mojo = newMojo();
        MojoTestReflection.setField(mojo, "systemPropertyVariables", null);
        MojoTestReflection.setField(mojo, "environmentVariables", null);
        assertDoesNotThrow(mojo::exposeBuildConfiguration);
    }

    @Test
    void buildConfigurationFailsWhenClasspathUnresolved() throws Exception {
        ExposingMojo mojo = newMojo();
        MojoTestReflection.setField(mojo, "project", new UnresolvableClasspathProject());
        assertNotNull(assertThrows(MojoExecutionException.class, () -> mojo.exposeBuildConfiguration()));
    }

    @Test
    void createSpiClassLoaderFailsOnMalformedClasspathEntry() throws Exception {
        ExposingMojo mojo = newMojo();
        Method m = AbstractPhoenixfireMojo.class.getDeclaredMethod("createSpiClassLoader", List.class);
        m.setAccessible(true);
        var ex = assertThrows(java.lang.reflect.InvocationTargetException.class,
                () -> m.invoke(mojo, List.of("http://[::1]]")));
        assertTrue(ex.getCause() instanceof MojoExecutionException);
    }

    @Test
    void buildConfigurationFailsWhenPatternFileCannotBeRead() throws Exception {
        File patternFile = tempDir.resolve("locked.txt").toFile();
        Files.writeString(patternFile.toPath(), "**/X.java");
        try (FileChannel channel = FileChannel.open(patternFile.toPath(), StandardOpenOption.WRITE)) {
            channel.lock();
            ExposingMojo mojo = newMojo();
            MojoTestReflection.setField(mojo, "includesFile", patternFile);
            assertNotNull(assertThrows(MojoExecutionException.class, () -> mojo.exposeBuildConfiguration()));
        }
    }

    @Test
    void createSpiClassLoaderAcceptsClasspathEntries() throws Exception {
        ExposingMojo mojo = newMojo();
        Method m = AbstractPhoenixfireMojo.class.getDeclaredMethod("createSpiClassLoader", List.class);
        m.setAccessible(true);
        Object loader = m.invoke(mojo, simulatedForkClasspath());
        assertNotNull(loader);
        ((AutoCloseable) loader).close();
    }

    private ExposingMojo newMojo() throws Exception {
        ExposingMojo mojo = new ExposingMojo();
        mojo.setLog(new SystemStreamLog());
        configureMojo(mojo);
        return mojo;
    }

    private void configureMojo(PhoenixfireTestMojo mojo) throws Exception {
        MavenProject project = new FixedClasspathProject(simulatedForkClasspath());
        project.setGroupId("g");
        project.setArtifactId("a");
        project.setVersion("1.0");
        project.setFile(tempDir.toFile());
        Build build = new Build();
        build.setDirectory(tempDir.resolve("target").toString());
        build.setOutputDirectory(tempDir.resolve("target/classes").toString());
        build.setTestOutputDirectory(tempDir.resolve("target/test-classes").toString());
        project.setBuild(build);
        project.getProperties().setProperty("argLine", "");

        File reports = tempDir.resolve("phoenixfire-reports").toFile();
        Files.createDirectories(reports.toPath());

        MojoTestReflection.setField(mojo, "project", project);
        MojoTestReflection.setField(mojo, "reportsDir", reports);
        MojoTestReflection.setField(mojo, "pluginArtifacts", List.of());
        MojoTestReflection.setField(mojo, "journalEnabled", false);
    }

    private static List<String> simulatedForkClasspath() {
        return List.of(
                Path.of("target", "test-classes").toAbsolutePath().normalize().toString(),
                modulePath("phoenixfire-core", "classes"),
                modulePath("phoenixfire-api", "classes"),
                modulePath("phoenixfire-fork-runner", "classes"));
    }

    private static String modulePath(String module, String output) {
        return Path.of("..", module, "target", output).toAbsolutePath().normalize().toString();
    }

    private static File jarPath(Class<?> anchor) throws Exception {
        URL location = anchor.getProtectionDomain().getCodeSource().getLocation();
        return Path.of(location.toURI()).toFile();
    }

    private static DefaultArtifact pluginArtifact(String groupId, String artifactId, File file) {
        DefaultArtifactHandler handler = new DefaultArtifactHandler("jar");
        DefaultArtifact artifact = new DefaultArtifact(
                groupId, artifactId, "1.0", "runtime", "jar", null, handler);
        artifact.setFile(file);
        return artifact;
    }

    private static MavenProject project(ExposingMojo mojo) throws Exception {
        return (MavenProject) MojoTestReflection.findField(AbstractPhoenixfireMojo.class, "project").get(mojo);
    }

    private static void restoreProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }

    /** Exposes protected configuration hooks for unit tests. */
    static final class ExposingMojo extends PhoenixfireTestMojo {
        PhoenixfireConfiguration exposeBuildConfiguration() throws MojoExecutionException {
            return buildConfiguration();
        }
    }

    static final class FaultInjectingMojo extends PhoenixfireTestMojo {
        @Override
        protected void handleResult(ExecutionSummary summary) {
            throw new RuntimeException("simulated");
        }
    }

    static final class CloseFailMojo extends PhoenixfireTestMojo {
        @Override
        protected URLClassLoader createSpiClassLoader(List<String> classpath) throws MojoExecutionException {
            return new CloseFailingClassLoader(super.createSpiClassLoader(classpath));
        }
    }

    static final class CloseFailingClassLoader extends URLClassLoader {
        CloseFailingClassLoader(URLClassLoader delegate) {
            super(delegate.getURLs(), delegate.getParent());
        }

        @Override
        public void close() throws IOException {
            throw new IOException("close failed");
        }
    }

    private static final class FixedClasspathProject extends MavenProject {
        private final List<String> testClasspath;

        FixedClasspathProject(List<String> testClasspath) {
            this.testClasspath = testClasspath;
        }

        @Override
        public List<String> getTestClasspathElements() {
            return testClasspath;
        }
    }

    private static final class UnresolvableClasspathProject extends MavenProject {
        @Override
        public List<String> getTestClasspathElements() throws DependencyResolutionRequiredException {
            throw new DependencyResolutionRequiredException(
                    new DefaultArtifact("g", "a", "1.0", "test", "jar", null, new DefaultArtifactHandler("jar")));
        }
    }
}
