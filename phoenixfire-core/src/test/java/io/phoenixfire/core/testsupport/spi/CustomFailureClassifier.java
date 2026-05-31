package io.phoenixfire.core.testsupport.spi;

import io.phoenixfire.api.model.FailureMode;
import io.phoenixfire.api.spi.FailureClassifier;

/** Test SPI that always classifies fork death as OOM. */
public final class CustomFailureClassifier implements FailureClassifier {

    @Override
    public FailureMode classify(int exitCode, boolean heartbeatTimedOut, boolean handshakeCompleted,
                                String diagnostic) {
        return FailureMode.OOM;
    }
}
