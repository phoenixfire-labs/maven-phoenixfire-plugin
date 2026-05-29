package io.phoenixfire.api.run;

import java.util.ArrayList;
import java.util.List;

/**
 * Identity and context for a single Phoenixfire run, attached to the report so downstream tools can
 * join results across runs and slice by environment.
 *
 * <p>It combines engine-known facts (a generated {@code runId}, the host/OS/JVM the controller ran
 * on, and the resilience configuration in effect) with externally-supplied {@link RunMetadata}
 * (git/CI/project/labels). This is the dimension table that makes "consistent crashers over time" and
 * "fails with fork reuse" answerable without the plugin assuming any specific CI vendor.
 */
public final class RunEnvelope {

    private final String runId;
    private final String host;
    private final String osName;
    private final String osArch;
    private final String jvm;
    private final int maxAttempts;
    private final List<String> escalationLadder;
    private final int forkCount;
    private final RunMetadata metadata;

    private RunEnvelope(Builder b) {
        this.runId = b.runId;
        this.host = b.host;
        this.osName = b.osName;
        this.osArch = b.osArch;
        this.jvm = b.jvm;
        this.maxAttempts = b.maxAttempts;
        this.escalationLadder = List.copyOf(b.escalationLadder);
        this.forkCount = b.forkCount;
        this.metadata = b.metadata == null ? RunMetadata.empty() : b.metadata;
    }

    public String runId() {
        return runId;
    }

    public String host() {
        return host;
    }

    public String osName() {
        return osName;
    }

    public String osArch() {
        return osArch;
    }

    public String jvm() {
        return jvm;
    }

    public int maxAttempts() {
        return maxAttempts;
    }

    public List<String> escalationLadder() {
        return escalationLadder;
    }

    public int forkCount() {
        return forkCount;
    }

    public RunMetadata metadata() {
        return metadata;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Mutable builder. */
    public static final class Builder {
        private String runId;
        private String host;
        private String osName;
        private String osArch;
        private String jvm;
        private int maxAttempts;
        private List<String> escalationLadder = new ArrayList<>();
        private int forkCount;
        private RunMetadata metadata;

        public Builder runId(String v) {
            this.runId = v;
            return this;
        }

        public Builder host(String v) {
            this.host = v;
            return this;
        }

        public Builder osName(String v) {
            this.osName = v;
            return this;
        }

        public Builder osArch(String v) {
            this.osArch = v;
            return this;
        }

        public Builder jvm(String v) {
            this.jvm = v;
            return this;
        }

        public Builder maxAttempts(int v) {
            this.maxAttempts = v;
            return this;
        }

        public Builder escalationLadder(List<String> v) {
            this.escalationLadder = v == null ? new ArrayList<>() : new ArrayList<>(v);
            return this;
        }

        public Builder forkCount(int v) {
            this.forkCount = v;
            return this;
        }

        public Builder metadata(RunMetadata v) {
            this.metadata = v;
            return this;
        }

        public RunEnvelope build() {
            return new RunEnvelope(this);
        }
    }
}
