package it;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Listed in {@code phoenixfire-excludes.txt}, so it must never be discovered or run. It fails loudly
 * if it ever executes, turning a broken excludesFile into a hard build failure.
 */
class ExcludedTest {

    @Test
    void mustNotRun() {
        fail("ExcludedTest ran but should have been excluded via excludesFile");
    }
}
