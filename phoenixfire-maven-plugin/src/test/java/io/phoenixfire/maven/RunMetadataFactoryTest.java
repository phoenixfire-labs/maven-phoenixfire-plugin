package io.phoenixfire.maven;

import io.phoenixfire.api.run.RunMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RunMetadataFactoryTest {

    @TempDir
    File basedir;

    @Test
    void usesOverridesWithoutGit() {
        RunMetadataFactory.Overrides o = new RunMetadataFactory.Overrides();
        o.gitSha = "abc";
        o.gitBranch = "main";
        o.groupId = "g";
        o.artifactId = "a";
        o.version = "1.0";
        o.labels = Map.of("k", "v");
        RunMetadata meta = RunMetadataFactory.build(o, basedir, false);
        assertEquals("abc", meta.gitSha());
        assertEquals("g", meta.projectGroupId());
    }

    @Test
    void collectGitWhenEnabled() {
        RunMetadataFactory.Overrides o = new RunMetadataFactory.Overrides();
        RunMetadata meta = RunMetadataFactory.build(o, basedir, true);
        // may be null if git unavailable in test env
        assertNull(meta.gitSha());
    }
}
