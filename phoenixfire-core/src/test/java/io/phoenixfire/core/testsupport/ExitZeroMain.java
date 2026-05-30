package io.phoenixfire.core.testsupport;

/** Minimal main class for {@link io.phoenixfire.core.supervisor.ForkLauncher} unit tests. */
public final class ExitZeroMain {
    public static void main(String[] args) {
        System.exit(0);
    }

    private ExitZeroMain() {
    }
}
