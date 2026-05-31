package io.phoenixfire.core.testsupport;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Loads test SPI registrations from {@code src/test/resources/custom-spi}. */
public final class SpiTestClassLoader {

    private SpiTestClassLoader() {
    }

    public static ClassLoader create(ClassLoader parent) throws Exception {
        return create(parent, Path.of("src", "test", "resources", "custom-spi"));
    }

    /** Loads SPI from {@code custom-spi} plus an optional single implementation override. */
    public static ClassLoader createWithService(ClassLoader parent, Class<?> serviceType,
                                                Class<?> implementation) throws Exception {
        Path overlay = overlayFor(serviceType, implementation);
        return create(parent, Path.of("src", "test", "resources", "custom-spi"), overlay);
    }

    /** Like {@link #createWithService} but without the shared {@code custom-spi} registrations. */
    public static ClassLoader createWithOnlyService(ClassLoader parent, Class<?> serviceType,
                                                    Class<?> implementation) throws Exception {
        return create(parent, overlayFor(serviceType, implementation));
    }

    private static Path overlayFor(Class<?> serviceType, Class<?> implementation) throws Exception {
        Path overlay = Files.createTempDirectory("phoenixfire-spi-");
        Path services = overlay.resolve("META-INF").resolve("services");
        Files.createDirectories(services);
        Files.writeString(services.resolve(serviceType.getName()),
                implementation.getName() + System.lineSeparator(), StandardCharsets.UTF_8);
        return overlay;
    }

    private static ClassLoader create(ClassLoader parent, Path... resourceRoots) throws Exception {
        Path testClasses = Path.of("target", "test-classes").toAbsolutePath();
        URL[] urls = new URL[resourceRoots.length + 1];
        urls[0] = testClasses.toUri().toURL();
        for (int i = 0; i < resourceRoots.length; i++) {
            urls[i + 1] = resourceRoots[i].toAbsolutePath().toUri().toURL();
        }
        return new URLClassLoader("phoenixfire-test-spi", urls, parent);
    }
}
