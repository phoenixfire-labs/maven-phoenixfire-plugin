package io.phoenixfire.core.isolation;

import io.phoenixfire.api.spi.ForkConfig;
import io.phoenixfire.api.spi.IsolationContext;

/** Small helpers shared by the built-in isolation strategies. */
final class Isolations {

    private Isolations() {
    }

    static ForkConfig forkConfigFrom(IsolationContext context) {
        return new ForkConfig(context.baseJvmArgs(), context.systemProperties(), context.environment());
    }
}
