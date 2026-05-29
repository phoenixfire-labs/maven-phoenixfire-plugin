package io.phoenixfire.core;

import io.phoenixfire.api.json.Json;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonTest {

    @Test
    void roundTripsObjectsArraysAndScalars() {
        Map<String, Object> obj = new LinkedHashMap<>();
        obj.put("type", "TEST_FINISHED");
        obj.put("durationMillis", 1234L);
        obj.put("ok", Boolean.TRUE);
        obj.put("nothing", null);
        obj.put("ids", List.of("a", "b", "c"));

        String encoded = Json.encode(obj);
        Map<String, Object> parsed = Json.parseObject(encoded);

        assertEquals("TEST_FINISHED", parsed.get("type"));
        assertEquals(1234L, parsed.get("durationMillis"));
        assertEquals(Boolean.TRUE, parsed.get("ok"));
        assertTrue(parsed.containsKey("nothing"));
        assertEquals(List.of("a", "b", "c"), parsed.get("ids"));
    }

    @Test
    void escapesSpecialCharactersInStrings() {
        Map<String, Object> obj = new LinkedHashMap<>();
        obj.put("msg", "line1\nline2\t\"quoted\" \\slash");

        String encoded = Json.encode(obj);
        Map<String, Object> parsed = Json.parseObject(encoded);

        assertEquals("line1\nline2\t\"quoted\" \\slash", parsed.get("msg"));
    }
}
