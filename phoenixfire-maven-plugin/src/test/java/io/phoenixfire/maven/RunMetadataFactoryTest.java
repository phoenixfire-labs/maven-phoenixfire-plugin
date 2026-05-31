package io.phoenixfire.maven;

import io.phoenixfire.api.run.RunMetadata;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunMetadataFactoryTest {

    @Test
    void buildsFromExplicitOverrides() {
        RunMetadataFactory.Overrides o = new RunMetadataFactory.Overrides();
        o.gitSha = "deadbeef";
        o.gitBranch = "main";
        o.gitDirty = true;
        o.groupId = "com.example";
        o.artifactId = "demo";
        o.version = "1.2.3";
        o.ciProvider = "github";
        o.ciBuildId = "99";
        o.ciBuildUrl = "https://ci.example/run/99";
        o.labels = Map.of("team", "platform");

        RunMetadata meta = RunMetadataFactory.build(o);
        assertEquals("deadbeef", meta.gitSha());
        assertEquals("main", meta.gitBranch());
        assertTrue(meta.gitDirty());
        assertEquals("com.example", meta.projectGroupId());
        assertEquals("demo", meta.projectArtifactId());
        assertEquals("1.2.3", meta.projectVersion());
        assertEquals("github", meta.ciProvider());
        assertEquals("99", meta.ciBuildId());
        assertEquals("https://ci.example/run/99", meta.ciBuildUrl());
        assertEquals("platform", meta.labels().get("team"));
    }

    @Test
    void omitsUnsetGitAndCiFields() {
        RunMetadataFactory.Overrides o = new RunMetadataFactory.Overrides();
        o.groupId = "g";
        o.artifactId = "a";
        o.version = "0.1";

        RunMetadata meta = RunMetadataFactory.build(o);
        assertNull(meta.gitSha());
        assertNull(meta.gitBranch());
        assertNull(meta.gitDirty());
        assertNull(meta.ciProvider());
        assertNull(meta.ciBuildId());
        assertNull(meta.ciBuildUrl());
        assertEquals("g", meta.projectGroupId());
    }

    @Test
    void blankGitShaIsOmitted() {
        RunMetadataFactory.Overrides o = new RunMetadataFactory.Overrides();
        o.gitSha = "   ";
        o.gitBranch = "feature/x";

        RunMetadata meta = RunMetadataFactory.build(o);
        assertNull(meta.gitSha());
        assertEquals("feature/x", meta.gitBranch());
    }

    @Test
    void gitDirtyFalseIsPreserved() {
        RunMetadataFactory.Overrides o = new RunMetadataFactory.Overrides();
        o.gitSha = "abc";
        o.gitDirty = false;

        RunMetadata meta = RunMetadataFactory.build(o);
        assertEquals("abc", meta.gitSha());
        assertFalse(meta.gitDirty());
    }
}
