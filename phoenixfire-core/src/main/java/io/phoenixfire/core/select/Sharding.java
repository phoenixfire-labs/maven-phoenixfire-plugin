package io.phoenixfire.core.select;

import io.phoenixfire.api.model.TestId;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Indexable test sharding (Jest {@code --shard=index/count} style) for splitting a suite across CI
 * nodes.
 *
 * <p>Sharding is by <b>class</b>, never by method: all methods of a class stay together on one shard,
 * preserving class-level fixtures and the shared-fork pollution model. Classes are sorted by name and
 * assigned round-robin to shards, so every shard computes the same partition independently (no
 * coordination) and class counts stay balanced. The {@code index} is 1-based, like Jest.
 */
public final class Sharding {

    private final int index;
    private final int count;

    private Sharding(int index, int count) {
        this.index = index;
        this.count = count;
    }

    public static Sharding of(int index, int count) {
        return new Sharding(index, count);
    }

    /** Sharding only takes effect when splitting into more than one shard. */
    public boolean enabled() {
        return count > 1;
    }

    public void validate() {
        if (count < 1) {
            throw new IllegalArgumentException("shard.count must be >= 1, was " + count);
        }
        if (index < 1 || index > count) {
            throw new IllegalArgumentException(
                    "shard.index must be in [1, " + count + "], was " + index);
        }
    }

    /** Keep only the tests whose class is assigned to this shard, preserving input order. */
    public List<TestId> partition(List<TestId> tests) {
        if (!enabled()) {
            return tests;
        }
        TreeSet<String> classes = new TreeSet<>();
        for (TestId t : tests) {
            classes.add(classKey(t));
        }
        Set<String> mine = new HashSet<>();
        int pos = 0;
        for (String cls : classes) {
            if (pos % count == index - 1) {
                mine.add(cls);
            }
            pos++;
        }
        List<TestId> result = new ArrayList<>();
        for (TestId t : tests) {
            if (mine.contains(classKey(t))) {
                result.add(t);
            }
        }
        return result;
    }

    private static String classKey(TestId t) {
        String c = t.className();
        return c == null || c.isEmpty() ? t.uniqueId() : c;
    }

    public int index() {
        return index;
    }

    public int count() {
        return count;
    }
}
