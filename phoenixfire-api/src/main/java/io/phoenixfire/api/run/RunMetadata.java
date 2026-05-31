package io.phoenixfire.api.run;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Externally-sourced facts about a build that the engine cannot determine on its own: VCS commit and
 * branch, CI build coordinates, the Maven project identity, and arbitrary user labels.
 *
 * <p>This is the "bring your own" half of the run envelope. The Maven layer populates it from
 * neutral, vendor-agnostic overrides ({@code -Dphoenixfire.git.sha=...} and plugin
 * {@code <configuration>}, including {@code ${env.*}} mappings in the consumer POM). No
 * {@code git} subprocess is invoked. Every field is optional; unset fields are simply omitted from
 * reports.
 */
public final class RunMetadata {

    private final String gitSha;
    private final String gitBranch;
    private final Boolean gitDirty;
    private final String ciProvider;
    private final String ciBuildId;
    private final String ciBuildUrl;
    private final String projectGroupId;
    private final String projectArtifactId;
    private final String projectVersion;
    private final Map<String, String> labels;

    private RunMetadata(Builder b) {
        this.gitSha = b.gitSha;
        this.gitBranch = b.gitBranch;
        this.gitDirty = b.gitDirty;
        this.ciProvider = b.ciProvider;
        this.ciBuildId = b.ciBuildId;
        this.ciBuildUrl = b.ciBuildUrl;
        this.projectGroupId = b.projectGroupId;
        this.projectArtifactId = b.projectArtifactId;
        this.projectVersion = b.projectVersion;
        this.labels = Map.copyOf(b.labels);
    }

    public String gitSha() {
        return gitSha;
    }

    public String gitBranch() {
        return gitBranch;
    }

    public Boolean gitDirty() {
        return gitDirty;
    }

    public String ciProvider() {
        return ciProvider;
    }

    public String ciBuildId() {
        return ciBuildId;
    }

    public String ciBuildUrl() {
        return ciBuildUrl;
    }

    public String projectGroupId() {
        return projectGroupId;
    }

    public String projectArtifactId() {
        return projectArtifactId;
    }

    public String projectVersion() {
        return projectVersion;
    }

    public Map<String, String> labels() {
        return labels;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** A metadata instance with nothing set. */
    public static RunMetadata empty() {
        return builder().build();
    }

    /** Mutable builder; all fields optional. Blank strings are normalised to {@code null}. */
    public static final class Builder {
        private String gitSha;
        private String gitBranch;
        private Boolean gitDirty;
        private String ciProvider;
        private String ciBuildId;
        private String ciBuildUrl;
        private String projectGroupId;
        private String projectArtifactId;
        private String projectVersion;
        private Map<String, String> labels = new LinkedHashMap<>();

        public Builder gitSha(String v) {
            this.gitSha = blankToNull(v);
            return this;
        }

        public Builder gitBranch(String v) {
            this.gitBranch = blankToNull(v);
            return this;
        }

        public Builder gitDirty(Boolean v) {
            this.gitDirty = v;
            return this;
        }

        public Builder ciProvider(String v) {
            this.ciProvider = blankToNull(v);
            return this;
        }

        public Builder ciBuildId(String v) {
            this.ciBuildId = blankToNull(v);
            return this;
        }

        public Builder ciBuildUrl(String v) {
            this.ciBuildUrl = blankToNull(v);
            return this;
        }

        public Builder projectGroupId(String v) {
            this.projectGroupId = blankToNull(v);
            return this;
        }

        public Builder projectArtifactId(String v) {
            this.projectArtifactId = blankToNull(v);
            return this;
        }

        public Builder projectVersion(String v) {
            this.projectVersion = blankToNull(v);
            return this;
        }

        public Builder labels(Map<String, String> v) {
            this.labels = v == null ? new LinkedHashMap<>() : new LinkedHashMap<>(v);
            return this;
        }

        public RunMetadata build() {
            return new RunMetadata(this);
        }

        private static String blankToNull(String v) {
            return v == null || v.isBlank() ? null : v.trim();
        }
    }
}
