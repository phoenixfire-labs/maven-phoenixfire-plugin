package io.phoenixfire.core;

import io.phoenixfire.api.model.FailureMode;
import io.phoenixfire.core.supervisor.DefaultFailureClassifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DefaultFailureClassifierTest {

    private final DefaultFailureClassifier classifier = new DefaultFailureClassifier();

    @Test
    void classifiesHeartbeatAndHandshake() {
        assertEquals(FailureMode.HEARTBEAT_TIMEOUT,
                classifier.classify(1, true, true, null));
        assertEquals(FailureMode.HANDSHAKE_FAILURE,
                classifier.classify(0, false, false, null));
    }

    @Test
    void classifiesOomFromDiagnostic() {
        assertEquals(FailureMode.OOM,
                classifier.classify(1, false, true, "java.lang.OutOfMemoryError"));
        assertEquals(FailureMode.OOM,
                classifier.classify(1, false, true, "GC overhead limit exceeded"));
        assertEquals(FailureMode.OOM,
                classifier.classify(1, false, true, "ran out of memory"));
    }

    @Test
    void classifiesExitCodes() {
        assertEquals(FailureMode.NONE, classifier.classify(0, false, true, null));
        assertEquals(FailureMode.SIGKILL, classifier.classify(137, false, true, null));
        assertEquals(FailureMode.SIGTERM, classifier.classify(143, false, true, null));
        assertEquals(FailureMode.UNKNOWN,
                classifier.classify(DefaultFailureClassifier.UNKNOWN_EXIT, false, true, null));
        assertEquals(FailureMode.ABNORMAL_EXIT, classifier.classify(42, false, true, null));
    }
}
