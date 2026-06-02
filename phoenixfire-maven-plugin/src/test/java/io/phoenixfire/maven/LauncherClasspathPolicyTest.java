package io.phoenixfire.maven;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.artifact.DefaultArtifact;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LauncherClasspathPolicyTest {

    @Test
    void detectsLauncherJarOnClasspath() {
        assertTrue(LauncherClasspathPolicy.isLauncherJarPath(
                "/repo/junit-platform-launcher-1.10.2.jar"));
        assertFalse(LauncherClasspathPolicy.isLauncherJarPath(
                "/repo/junit-platform-engine-1.10.2.jar"));
    }

    @Test
    void skipsPluginLauncherWhenProjectProvidesOne() {
        Artifact pluginLauncher = artifact("org.junit.platform", "junit-platform-launcher");
        assertFalse(LauncherClasspathPolicy.shouldAppendPluginArtifact(pluginLauncher, true));
        assertTrue(LauncherClasspathPolicy.shouldAppendPluginArtifact(pluginLauncher, false));
    }

    @Test
    void alwaysAppendsPhoenixfireModules() {
        Artifact core = artifact("io.github.phoenixfire-labs", "phoenixfire-core");
        assertTrue(LauncherClasspathPolicy.shouldAppendPluginArtifact(core, true));
        assertTrue(LauncherClasspathPolicy.shouldAppendPluginArtifact(core, false));
    }

    @Test
    void testClasspathContainsLauncherScansEntries() {
        assertTrue(LauncherClasspathPolicy.testClasspathContainsLauncher(List.of(
                Path.of("lib", "junit-platform-launcher-6.0.3.jar").toString())));
        assertFalse(LauncherClasspathPolicy.testClasspathContainsLauncher(List.of(
                Path.of("target", "test-classes").toString())));
    }

    private static Artifact artifact(String groupId, String artifactId) {
        DefaultArtifactHandler handler = new DefaultArtifactHandler("jar");
        DefaultArtifact artifact = new DefaultArtifact(
                groupId, artifactId, "1.0", "runtime", "jar", null, handler);
        artifact.setFile(new File(artifactId + ".jar"));
        return artifact;
    }
}
