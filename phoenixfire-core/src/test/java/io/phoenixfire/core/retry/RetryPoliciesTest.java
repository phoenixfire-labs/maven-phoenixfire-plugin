package io.phoenixfire.core.retry;

import io.phoenixfire.core.config.PhoenixfireConfiguration;
import io.phoenixfire.core.util.PhoenixfireLogger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class RetryPoliciesTest {

    @Test
    void resolvesDefaultPolicy() {
        assertNotNull(RetryPolicies.resolve(
                PhoenixfireConfiguration.builder().build(),
                PhoenixfireLogger.console(),
                null));
    }
}
