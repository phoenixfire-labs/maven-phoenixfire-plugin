package io.phoenixfire.core.retry;

import io.phoenixfire.api.spi.RetryPolicy;
import io.phoenixfire.core.config.PhoenixfireConfiguration;
import io.phoenixfire.core.util.PhoenixfireLogger;

import java.util.Iterator;
import java.util.ServiceLoader;

/** Resolves the active {@link RetryPolicy}: a {@link ServiceLoader} override if present, else the default. */
public final class RetryPolicies {

    private RetryPolicies() {
    }

    public static RetryPolicy resolve(PhoenixfireConfiguration config, PhoenixfireLogger log, ClassLoader cl) {
        ClassLoader loader = cl != null ? cl : RetryPolicies.class.getClassLoader();
        Iterator<RetryPolicy> it = ServiceLoader.load(RetryPolicy.class, loader).iterator();
        if (it.hasNext()) {
            RetryPolicy custom = it.next();
            log.info("Using custom retry policy: " + custom.getClass().getName());
            return custom;
        }
        return new DefaultRetryPolicy(config.maxAttempts(), config.rerunFailingTestsCount(),
                config.backoffMillis(), config.escalationLadder());
    }
}
