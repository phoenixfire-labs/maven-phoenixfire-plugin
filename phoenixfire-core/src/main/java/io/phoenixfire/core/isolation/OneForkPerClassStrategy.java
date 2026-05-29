package io.phoenixfire.core.isolation;

import io.phoenixfire.api.model.IsolationLevel;
import io.phoenixfire.api.model.TestId;
import io.phoenixfire.api.spi.ForkConfig;
import io.phoenixfire.api.spi.IsolationContext;
import io.phoenixfire.api.spi.IsolationStrategy;
import io.phoenixfire.api.spi.WorkUnit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One dedicated JVM fork per test class. */
public final class OneForkPerClassStrategy implements IsolationStrategy {

    @Override
    public IsolationLevel level() {
        return IsolationLevel.ONE_FORK_PER_CLASS;
    }

    @Override
    public List<WorkUnit> plan(List<TestId> tests, IsolationContext context) {
        Map<String, List<TestId>> byClass = new LinkedHashMap<>();
        for (TestId t : tests) {
            byClass.computeIfAbsent(t.className(), k -> new ArrayList<>()).add(t);
        }
        ForkConfig forkConfig = Isolations.forkConfigFrom(context);
        List<WorkUnit> units = new ArrayList<>();
        int index = 0;
        for (Map.Entry<String, List<TestId>> e : byClass.entrySet()) {
            units.add(new WorkUnit("class-" + (index++) + "-" + safe(e.getKey()), level(), e.getValue(), forkConfig));
        }
        return units;
    }

    private static String safe(String className) {
        return className == null ? "unknown" : className.replaceAll("[^A-Za-z0-9_.-]", "_");
    }
}
