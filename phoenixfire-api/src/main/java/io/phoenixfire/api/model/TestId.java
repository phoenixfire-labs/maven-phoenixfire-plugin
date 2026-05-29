package io.phoenixfire.api.model;

import java.util.Objects;

/**
 * Stable, deterministic identity of a single executable test.
 *
 * <p>The {@link #uniqueId()} is the JUnit Platform unique id string (for example
 * {@code [engine:junit-jupiter]/[class:com.acme.FooTest]/[method:bar()]}). It is the canonical key
 * used everywhere in the journal and IPC protocol. {@link #className()} and {@link #displayName()}
 * are denormalised for convenient, framework-friendly reporting.
 */
public final class TestId implements Comparable<TestId> {

    private final String uniqueId;
    private final String className;
    private final String displayName;

    public TestId(String uniqueId, String className, String displayName) {
        this.uniqueId = Objects.requireNonNull(uniqueId, "uniqueId");
        this.className = className == null ? "" : className;
        this.displayName = displayName == null ? uniqueId : displayName;
    }

    public String uniqueId() {
        return uniqueId;
    }

    public String className() {
        return className;
    }

    public String displayName() {
        return displayName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TestId)) {
            return false;
        }
        return uniqueId.equals(((TestId) o).uniqueId);
    }

    @Override
    public int hashCode() {
        return uniqueId.hashCode();
    }

    @Override
    public int compareTo(TestId other) {
        return uniqueId.compareTo(other.uniqueId);
    }

    @Override
    public String toString() {
        return uniqueId;
    }
}
