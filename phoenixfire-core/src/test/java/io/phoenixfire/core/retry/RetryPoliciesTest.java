package io.phoenixfire.core.retry;

import io.phoenixfire.core.config.PhoenixfireConfiguration;
import io.phoenixfire.core.testsupport.SpiTestClassLoader;
import io.phoenixfire.core.util.PhoenixfireLogger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetryPoliciesTest {

    @Test
    void resolvesDefaultPolicy() {
        assertNotNull(RetryPolicies.resolve(
                PhoenixfireConfiguration.builder().build(),
                PhoenixfireLogger.console(),
                null));
    }

    @Test
    void resolvesCustomPolicyFromSpi() throws Exception {
        ClassLoader loader = SpiTestClassLoader.create(RetryPolicies.class.getClassLoader());
        var policy = RetryPolicies.resolve(
                PhoenixfireConfiguration.builder().build(),
                PhoenixfireLogger.console(),
                loader);
        assertTrue(policy.getClass().getName().contains("CustomRetryPolicy"));
    }
}
