package io.phoenixfire.maven;

import io.phoenixfire.api.run.RunMetadata;

import java.util.Map;

/**
 * Assembles {@link RunMetadata} for a build in a deliberately vendor-agnostic way.
 *
 * <p>All fields come from explicit Maven configuration: {@code -Dphoenixfire.*} properties and/or
 * {@code <configuration>} on the Phoenixfire plugin (including {@code ${env.VAR}} mappings in the
 * consumer POM). Unset fields are omitted from reports. The plugin does not invoke {@code git} or
 * read CI-vendor-specific environment variables itself.
 */
final class RunMetadataFactory {

    private RunMetadataFactory() {
    }

    static RunMetadata build(Overrides overrides) {
        return RunMetadata.builder()
                .gitSha(overrides.gitSha)
                .gitBranch(overrides.gitBranch)
                .gitDirty(overrides.gitDirty)
                .ciProvider(overrides.ciProvider)
                .ciBuildId(overrides.ciBuildId)
                .ciBuildUrl(overrides.ciBuildUrl)
                .projectGroupId(overrides.groupId)
                .projectArtifactId(overrides.artifactId)
                .projectVersion(overrides.version)
                .labels(overrides.labels)
                .build();
    }

    /** Externally-supplied overrides gathered from Mojo parameters / the Maven project. */
    static final class Overrides {
        String gitSha;
        String gitBranch;
        Boolean gitDirty;
        String ciProvider;
        String ciBuildId;
        String ciBuildUrl;
        String groupId;
        String artifactId;
        String version;
        Map<String, String> labels = Map.of();
    }
}
