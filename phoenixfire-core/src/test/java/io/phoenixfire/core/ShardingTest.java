package io.phoenixfire.core;

import io.phoenixfire.api.model.TestId;
import io.phoenixfire.core.select.Sharding;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShardingTest {

    private static TestId test(String fqcn, String method) {
        String uid = "[class:" + fqcn + "]/[method:" + method + "()]";
        return new TestId(uid, fqcn, method + "()");
    }

    private static List<TestId> suite() {
        List<TestId> tests = new ArrayList<>();
        for (int c = 0; c < 10; c++) {
            String cls = "com.acme.Test" + c;
            tests.add(test(cls, "a"));
            tests.add(test(cls, "b"));
        }
        return tests;
    }

    @Test
    void disabledForCountOneOrLess() {
        List<TestId> suite = suite();
        assertSame(suite, Sharding.of(1, 1).partition(suite));
        assertSame(suite, Sharding.of(1, 0).partition(suite));
        assertFalseEnabled();
    }

    private void assertFalseEnabled() {
        assertTrue(!Sharding.of(1, 1).enabled());
        assertTrue(Sharding.of(1, 3).enabled());
    }

    @Test
    void shardsArePartitionOfTheWhole() {
        List<TestId> suite = suite();
        int count = 3;
        Set<TestId> union = new HashSet<>();
        int totalKept = 0;
        for (int i = 1; i <= count; i++) {
            List<TestId> shard = Sharding.of(i, count).partition(suite);
            totalKept += shard.size();
            union.addAll(shard);
        }
        // Disjoint (sizes sum to total) and complete (union equals the suite).
        assertEquals(suite.size(), totalKept);
        assertEquals(new HashSet<>(suite), union);
    }

    @Test
    void allMethodsOfAClassStayTogether() {
        List<TestId> suite = suite();
        int count = 4;
        for (int i = 1; i <= count; i++) {
            List<TestId> shard = Sharding.of(i, count).partition(suite);
            Set<String> classesHere = new HashSet<>();
            for (TestId t : shard) {
                classesHere.add(t.className());
            }
            // Every test of a class present in this shard must be in this shard (both a and b).
            for (String cls : classesHere) {
                long inShard = shard.stream().filter(t -> t.className().equals(cls)).count();
                long inSuite = suite.stream().filter(t -> t.className().equals(cls)).count();
                assertEquals(inSuite, inShard, "class " + cls + " was split across shards");
            }
        }
    }

    @Test
    void partitionIsDeterministicRegardlessOfInputOrder() {
        List<TestId> suite = suite();
        List<TestId> reversed = new ArrayList<>(suite);
        java.util.Collections.reverse(reversed);
        Set<TestId> a = new HashSet<>(Sharding.of(2, 3).partition(suite));
        Set<TestId> b = new HashSet<>(Sharding.of(2, 3).partition(reversed));
        assertEquals(a, b);
    }

    @Test
    void validateRejectsOutOfRangeIndex() {
        assertThrows(IllegalArgumentException.class, () -> Sharding.of(0, 3).validate());
        assertThrows(IllegalArgumentException.class, () -> Sharding.of(4, 3).validate());
        assertThrows(IllegalArgumentException.class, () -> Sharding.of(1, 0).validate());
        Sharding.of(3, 3).validate();
    }

    @Test
    void usesUniqueIdWhenClassNameMissing() {
        TestId bare = new TestId("uid-only", null, "m");
        List<TestId> suite = List.of(bare, test("com.A", "a"));
        List<TestId> shard = Sharding.of(1, 2).partition(suite);
        assertEquals(1, shard.size());
        assertEquals(1, Sharding.of(1, 2).index());
        assertEquals(2, Sharding.of(1, 2).count());
    }
}
