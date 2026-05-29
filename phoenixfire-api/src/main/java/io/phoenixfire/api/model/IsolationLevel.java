package io.phoenixfire.api.model;

/**
 * Escalating degrees of test isolation. Ordered from cheapest/fastest to most isolated/slowest.
 *
 * <p>Phoenixfire escalates along this ladder when a fork fails: a crash in the shared pool causes
 * affected tests to be retried in a fresh fork, then (if still failing) one fork per test class -
 * the strongest level, since one-fork-per-test rarely justifies its JVM-startup cost.
 */
public enum IsolationLevel {
    /** Standard execution in the shared, possibly parallel, fork pool. */
    SHARED_FORK_POOL,
    /** A brand new, dedicated JVM fork for the affected batch. */
    FRESH_FORK,
    /** One dedicated JVM fork per test class (strongest isolation level). */
    ONE_FORK_PER_CLASS;

    /** The next stronger isolation level, or {@code this} if already at the maximum. */
    public IsolationLevel next() {
        IsolationLevel[] values = values();
        int idx = Math.min(ordinal() + 1, values.length - 1);
        return values[idx];
    }

    public boolean isMaximum() {
        return this == ONE_FORK_PER_CLASS;
    }
}
