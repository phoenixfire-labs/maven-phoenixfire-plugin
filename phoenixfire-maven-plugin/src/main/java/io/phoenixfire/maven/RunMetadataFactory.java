package io.phoenixfire.maven;

import io.phoenixfire.api.run.RunMetadata;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Assembles {@link RunMetadata} for a build in a deliberately vendor-agnostic way.
 *
 * <p>Precedence per field is: explicit override (e.g. {@code -Dphoenixfire.git.sha}) wins, then a
 * best-effort local {@code git} fallback for commit/branch/dirty, otherwise the field is omitted. No
 * CI-vendor-specific environment variables are read; pipelines map their own variables onto the
 * neutral {@code phoenixfire.*} overrides. Project coordinates come straight from the Maven model.
 */
final class RunMetadataFactory {

    private RunMetadataFactory() {
    }

    static RunMetadata build(Overrides overrides, File basedir, boolean collectGit) {
        String sha = overrides.gitSha;
        String branch = overrides.gitBranch;
        Boolean dirty = null;

        if (collectGit && basedir != null) {
            if (isBlank(sha)) {
                sha = git(basedir, "rev-parse", "HEAD");
            }
            if (isBlank(branch)) {
                String b = git(basedir, "rev-parse", "--abbrev-ref", "HEAD");
                // A detached HEAD (common in CI checkouts) reports "HEAD"; that is not a useful branch.
                branch = "HEAD".equals(b) ? null : b;
            }
            String status = git(basedir, "status", "--porcelain");
            if (status != null) {
                dirty = !status.isBlank();
            }
        }

        return RunMetadata.builder()
                .gitSha(sha)
                .gitBranch(branch)
                .gitDirty(dirty)
                .ciProvider(overrides.ciProvider)
                .ciBuildId(overrides.ciBuildId)
                .ciBuildUrl(overrides.ciBuildUrl)
                .projectGroupId(overrides.groupId)
                .projectArtifactId(overrides.artifactId)
                .projectVersion(overrides.version)
                .labels(overrides.labels)
                .build();
    }

    /** Runs a git command in {@code dir}, returning trimmed stdout, or {@code null} on any failure. */
    private static String git(File dir, String... args) {
        try {
            String[] command = new String[args.length + 1];
            command[0] = "git";
            System.arraycopy(args, 0, command, 1, args.length);
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(dir);
            pb.redirectErrorStream(false);
            Process process = pb.start();
            StringBuilder out = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    out.append(line).append('\n');
                }
            }
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return null;
            }
            if (process.exitValue() != 0) {
                return null;
            }
            return out.toString().trim();
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /** Externally-supplied overrides gathered from Mojo parameters / the Maven project. */
    static final class Overrides {
        String gitSha;
        String gitBranch;
        String ciProvider;
        String ciBuildId;
        String ciBuildUrl;
        String groupId;
        String artifactId;
        String version;
        Map<String, String> labels = Map.of();
    }
}
