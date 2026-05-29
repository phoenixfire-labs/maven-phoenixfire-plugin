package io.phoenixfire.core.config;

import io.phoenixfire.api.model.IsolationLevel;
import io.phoenixfire.api.run.RunMetadata;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable runtime configuration for one Phoenixfire execution, assembled by the Maven layer and
 * consumed by the core engine. Provides Surefire-parity options plus Phoenixfire-specific
 * resilience controls.
 */
public final class PhoenixfireConfiguration {

    private final List<String> classpath;
    private final List<String> scanRoots;
    private final File javaExecutable;
    private final List<String> includes;
    private final List<String> excludes;
    private final List<String> baseJvmArgs;
    private final Map<String, String> systemProperties;
    private final Map<String, String> environment;

    private final int forkCount;
    private final int maxAttempts;
    private final int rerunFailingTestsCount;
    private final long heartbeatIntervalMillis;
    private final long heartbeatTimeoutMillis;
    private final long backoffMillis;
    private final List<IsolationLevel> escalationLadder;

    private final boolean journalEnabled;
    private final File reportsDirectory;
    private final File journalFile;
    private final boolean testFailureIgnore;
    private final String workingDirectory;
    private final RunMetadata runMetadata;

    private PhoenixfireConfiguration(Builder b) {
        this.classpath = List.copyOf(b.classpath);
        this.scanRoots = List.copyOf(b.scanRoots);
        this.javaExecutable = b.javaExecutable;
        this.includes = List.copyOf(b.includes);
        this.excludes = List.copyOf(b.excludes);
        this.baseJvmArgs = List.copyOf(b.baseJvmArgs);
        this.systemProperties = Map.copyOf(b.systemProperties);
        this.environment = Map.copyOf(b.environment);
        this.forkCount = b.forkCount;
        this.maxAttempts = b.maxAttempts;
        this.rerunFailingTestsCount = b.rerunFailingTestsCount;
        this.heartbeatIntervalMillis = b.heartbeatIntervalMillis;
        this.heartbeatTimeoutMillis = b.heartbeatTimeoutMillis;
        this.backoffMillis = b.backoffMillis;
        this.escalationLadder = List.copyOf(b.escalationLadder);
        this.journalEnabled = b.journalEnabled;
        this.reportsDirectory = b.reportsDirectory;
        this.journalFile = b.journalFile;
        this.testFailureIgnore = b.testFailureIgnore;
        this.workingDirectory = b.workingDirectory;
        this.runMetadata = b.runMetadata == null ? RunMetadata.empty() : b.runMetadata;
    }

    public List<String> classpath() {
        return classpath;
    }

    public List<String> scanRoots() {
        return scanRoots;
    }

    public File javaExecutable() {
        return javaExecutable;
    }

    public List<String> includes() {
        return includes;
    }

    public List<String> excludes() {
        return excludes;
    }

    public List<String> baseJvmArgs() {
        return baseJvmArgs;
    }

    public Map<String, String> systemProperties() {
        return systemProperties;
    }

    public Map<String, String> environment() {
        return environment;
    }

    public int forkCount() {
        return forkCount;
    }

    public int maxAttempts() {
        return maxAttempts;
    }

    public int rerunFailingTestsCount() {
        return rerunFailingTestsCount;
    }

    public long heartbeatIntervalMillis() {
        return heartbeatIntervalMillis;
    }

    public long heartbeatTimeoutMillis() {
        return heartbeatTimeoutMillis;
    }

    public long backoffMillis() {
        return backoffMillis;
    }

    public List<IsolationLevel> escalationLadder() {
        return escalationLadder;
    }

    public boolean journalEnabled() {
        return journalEnabled;
    }

    public File reportsDirectory() {
        return reportsDirectory;
    }

    public File journalFile() {
        return journalFile;
    }

    public boolean testFailureIgnore() {
        return testFailureIgnore;
    }

    public String workingDirectory() {
        return workingDirectory;
    }

    public RunMetadata runMetadata() {
        return runMetadata;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Mutable builder with sensible defaults. */
    public static final class Builder {
        private List<String> classpath = new ArrayList<>();
        private List<String> scanRoots = new ArrayList<>();
        private File javaExecutable;
        private List<String> includes = new ArrayList<>();
        private List<String> excludes = new ArrayList<>();
        private List<String> baseJvmArgs = new ArrayList<>();
        private Map<String, String> systemProperties = new LinkedHashMap<>();
        private Map<String, String> environment = new LinkedHashMap<>();
        private int forkCount = 1;
        private int maxAttempts = 3;
        private int rerunFailingTestsCount = 0;
        private long heartbeatIntervalMillis = 2_000L;
        private long heartbeatTimeoutMillis = 30_000L;
        private long backoffMillis = 0L;
        private List<IsolationLevel> escalationLadder = new ArrayList<>(List.of(
                IsolationLevel.SHARED_FORK_POOL,
                IsolationLevel.FRESH_FORK,
                IsolationLevel.ONE_FORK_PER_CLASS));
        private boolean journalEnabled = true;
        private File reportsDirectory;
        private File journalFile;
        private boolean testFailureIgnore = false;
        private String workingDirectory;
        private RunMetadata runMetadata;

        public Builder classpath(List<String> classpath) {
            this.classpath = new ArrayList<>(classpath);
            return this;
        }

        public Builder scanRoots(List<String> scanRoots) {
            this.scanRoots = new ArrayList<>(scanRoots);
            return this;
        }

        public Builder javaExecutable(File javaExecutable) {
            this.javaExecutable = javaExecutable;
            return this;
        }

        public Builder includes(List<String> includes) {
            this.includes = new ArrayList<>(includes);
            return this;
        }

        public Builder excludes(List<String> excludes) {
            this.excludes = new ArrayList<>(excludes);
            return this;
        }

        public Builder baseJvmArgs(List<String> baseJvmArgs) {
            this.baseJvmArgs = new ArrayList<>(baseJvmArgs);
            return this;
        }

        public Builder systemProperties(Map<String, String> systemProperties) {
            this.systemProperties = new LinkedHashMap<>(systemProperties);
            return this;
        }

        public Builder environment(Map<String, String> environment) {
            this.environment = new LinkedHashMap<>(environment);
            return this;
        }

        public Builder forkCount(int forkCount) {
            this.forkCount = Math.max(1, forkCount);
            return this;
        }

        public Builder maxAttempts(int maxAttempts) {
            this.maxAttempts = Math.max(1, maxAttempts);
            return this;
        }

        public Builder rerunFailingTestsCount(int rerunFailingTestsCount) {
            this.rerunFailingTestsCount = Math.max(0, rerunFailingTestsCount);
            return this;
        }

        public Builder heartbeatIntervalMillis(long heartbeatIntervalMillis) {
            this.heartbeatIntervalMillis = heartbeatIntervalMillis;
            return this;
        }

        public Builder heartbeatTimeoutMillis(long heartbeatTimeoutMillis) {
            this.heartbeatTimeoutMillis = heartbeatTimeoutMillis;
            return this;
        }

        public Builder backoffMillis(long backoffMillis) {
            this.backoffMillis = backoffMillis;
            return this;
        }

        public Builder escalationLadder(List<IsolationLevel> escalationLadder) {
            if (escalationLadder != null && !escalationLadder.isEmpty()) {
                this.escalationLadder = new ArrayList<>(escalationLadder);
            }
            return this;
        }

        public Builder journalEnabled(boolean journalEnabled) {
            this.journalEnabled = journalEnabled;
            return this;
        }

        public Builder reportsDirectory(File reportsDirectory) {
            this.reportsDirectory = reportsDirectory;
            return this;
        }

        public Builder journalFile(File journalFile) {
            this.journalFile = journalFile;
            return this;
        }

        public Builder testFailureIgnore(boolean testFailureIgnore) {
            this.testFailureIgnore = testFailureIgnore;
            return this;
        }

        public Builder workingDirectory(String workingDirectory) {
            this.workingDirectory = workingDirectory;
            return this;
        }

        public Builder runMetadata(RunMetadata runMetadata) {
            this.runMetadata = runMetadata;
            return this;
        }

        public PhoenixfireConfiguration build() {
            return new PhoenixfireConfiguration(this);
        }
    }
}
