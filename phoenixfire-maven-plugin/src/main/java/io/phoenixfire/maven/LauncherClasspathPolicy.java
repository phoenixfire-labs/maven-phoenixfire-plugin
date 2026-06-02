package io.phoenixfire.maven;

import org.apache.maven.artifact.Artifact;

import java.io.File;
import java.util.List;
import java.util.Locale;

/**
 * Rules for placing {@code junit-platform-launcher} on the fork classpath.
 */
final class LauncherClasspathPolicy {

    private LauncherClasspathPolicy() {
    }

    static boolean testClasspathContainsLauncher(List<String> testClasspath) {
        for (String entry : testClasspath) {
            if (isLauncherJarPath(entry)) {
                return true;
            }
        }
        return false;
    }

    static boolean isLauncherJarPath(String classpathEntry) {
        if (classpathEntry == null || classpathEntry.isBlank()) {
            return false;
        }
        String name = new File(classpathEntry).getName().toLowerCase(Locale.ROOT);
        return name.startsWith("junit-platform-launcher-") && name.endsWith(".jar");
    }

    static boolean shouldAppendPluginArtifact(Artifact artifact, boolean testClasspathHasLauncher) {
        if (artifact.getFile() == null) {
            return false;
        }
        if (isPluginLauncherArtifact(artifact)) {
            return !testClasspathHasLauncher;
        }
        return "io.github.phoenixfire-labs".equals(artifact.getGroupId());
    }

    private static boolean isPluginLauncherArtifact(Artifact artifact) {
        return "org.junit.platform".equals(artifact.getGroupId())
                && "junit-platform-launcher".equals(artifact.getArtifactId());
    }
}
