package io.phoenixfire.core.discovery;

/**
 * Raised when test discovery cannot complete, typically due to JUnit Platform launcher/engine skew.
 */
public final class DiscoveryFailedException extends RuntimeException {

    public DiscoveryFailedException(String message) {
        super(message);
    }
}
