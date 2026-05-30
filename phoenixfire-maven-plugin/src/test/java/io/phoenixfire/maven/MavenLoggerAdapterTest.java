package io.phoenixfire.maven;

import io.phoenixfire.core.util.PhoenixfireLogger;
import org.apache.maven.plugin.logging.Log;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MavenLoggerAdapterTest {

    @Test
    void delegatesToMavenLog() {
        RecordingLog log = new RecordingLog();
        PhoenixfireLogger adapter = new MavenLoggerAdapter(log);
        adapter.debug("d");
        adapter.info("i");
        adapter.warn("w");
        adapter.error("e");
        RuntimeException ex = new RuntimeException("x");
        adapter.error("e2", ex);
        assertEquals(List.of("d", "i", "w", "e", "e2"), log.messages);
        assertTrue(log.throwable == ex);
    }

    private static final class RecordingLog implements Log {
        final List<String> messages = new ArrayList<>();
        Throwable throwable;

        @Override
        public boolean isDebugEnabled() {
            return true;
        }

        @Override
        public void debug(CharSequence content) {
            messages.add(content.toString());
        }

        @Override
        public void debug(CharSequence content, Throwable error) {
            debug(content);
        }

        @Override
        public void debug(Throwable error) {
        }

        @Override
        public boolean isInfoEnabled() {
            return true;
        }

        @Override
        public void info(CharSequence content) {
            messages.add(content.toString());
        }

        @Override
        public void info(CharSequence content, Throwable error) {
            info(content);
        }

        @Override
        public void info(Throwable error) {
        }

        @Override
        public boolean isWarnEnabled() {
            return true;
        }

        @Override
        public void warn(CharSequence content) {
            messages.add(content.toString());
        }

        @Override
        public void warn(CharSequence content, Throwable error) {
            warn(content);
        }

        @Override
        public void warn(Throwable error) {
        }

        @Override
        public boolean isErrorEnabled() {
            return true;
        }

        @Override
        public void error(CharSequence content) {
            messages.add(content.toString());
        }

        @Override
        public void error(CharSequence content, Throwable error) {
            messages.add(content.toString());
            throwable = error;
        }

        @Override
        public void error(Throwable error) {
            throwable = error;
        }
    }
}
