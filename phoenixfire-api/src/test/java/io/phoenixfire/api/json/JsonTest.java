package io.phoenixfire.api.json;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Test
    void encodesNonStandardTypesAsStrings() {
        Map<String, Object> obj = new LinkedHashMap<>();
        obj.put("uuid", java.util.UUID.fromString("00000000-0000-0000-0000-000000000001"));
        String encoded = Json.encode(obj);
        assertTrue(encoded.contains("00000000-0000-0000-0000-000000000001"));
    }

    @Test
    void encodesControlCharactersAndUnknownTypes() {
        Map<String, Object> obj = new LinkedHashMap<>();
        obj.put("bell", "\u0007");
        obj.put("custom", new StringBuilder("built"));

        String encoded = Json.encode(obj);
        assertTrue(encoded.contains("\\u0007"));
        assertTrue(encoded.contains("\"built\""));

        Map<String, Object> parsed = Json.parseObject(encoded);
        assertEquals("\u0007", parsed.get("bell"));
        assertEquals("built", parsed.get("custom"));
    }

    @Test
    void encodesCharacterAndFloatingNumbers() {
        assertEquals("true", Json.encode(true));
        assertEquals("42", Json.encode(42));
        assertEquals("3.14", Json.encode(3.14));
        assertEquals("\"z\"", Json.encode('z'));

        Object parsed = Json.parse("3.14");
        assertEquals(3.14, parsed);
    }

    @Test
    void parsesEmptyObjectAndArray() {
        assertEquals(Map.of(), Json.parseObject("{}"));
        assertEquals(List.of(), Json.parse("[]"));
    }

    @Test
    void parsesUnicodeEscapeAndSlash() {
        Map<String, Object> parsed = Json.parseObject("{\"x\":\"\\u0041\\/\"}");
        assertEquals("A/", parsed.get("x"));
    }

    @Test
    void rejectsMalformedJson() {
        assertThrows(Json.JsonException.class, () -> Json.parse("{"));
        assertThrows(Json.JsonException.class, () -> Json.parse("{\"a\":1} trailing"));
        assertThrows(Json.JsonException.class, () -> Json.parseObject("[]"));
        assertThrows(Json.JsonException.class, () -> Json.parse("\"unterminated"));
        assertThrows(Json.JsonException.class, () -> Json.parse("{key:1}"));
        assertThrows(Json.JsonException.class, () -> Json.parse("{\"a\":1,}"));
        assertThrows(Json.JsonException.class, () -> Json.parse("[1,]"));
        assertThrows(Json.JsonException.class, () -> Json.parse("{\"a\":1,,}"));
        assertThrows(Json.JsonException.class, () -> Json.parse("\"bad\\q\""));
        assertThrows(Json.JsonException.class, () -> Json.parse("{\"n\":}"));
        assertThrows(NumberFormatException.class, () -> Json.parse("{\"n\":1.2.3}"));
        assertThrows(NumberFormatException.class, () -> Json.parse("{\"n\":-}"));
    }

    @Test
    void parsesIntegerAndLargeLong() {
        assertEquals(42L, Json.parse("42"));
        assertEquals(9223372036854775807L, Json.parse("9223372036854775807"));
    }

    @Test
    void parsesFalseAndNull() {
        assertEquals(Boolean.FALSE, Json.parse("false"));
        assertNull(Json.parse("null"));
    }

    @Test
    void encodesOtherEscapes() {
        Map<String, Object> obj = Map.of("x", "\r\b\f");
        String encoded = Json.encode(obj);
        assertTrue(encoded.contains("\\r"));
        assertTrue(encoded.contains("\\b"));
        assertTrue(encoded.contains("\\f"));
    }

    @Test
    void parserHandlesUnexpectedEndInStructures() {
        assertThrows(Json.JsonException.class, () -> Json.parse("{\"a\":"));
        assertThrows(Json.JsonException.class, () -> Json.parse("[1"));
        assertThrows(Json.JsonException.class, () -> Json.parse("{\"a\":1,"));
        assertThrows(Json.JsonException.class, () -> Json.parse("[1,"));
    }

    @Test
    void parserRejectsInvalidLiteralsAndNumbers() {
        assertThrows(Json.JsonException.class, () -> Json.parse("nope"));
        assertThrows(Json.JsonException.class, () -> Json.parse("tru"));
        assertThrows(Json.JsonException.class, () -> Json.parse("nul"));
        assertThrows(NumberFormatException.class, () -> Json.parse("."));
        assertThrows(NumberFormatException.class, () -> Json.parse("--1"));
    }

    @Test
    void parserAcceptsScientificNotation() {
        assertEquals(1.5e3, Json.parse("1.5e3"));
        assertEquals(2.0, Json.parse("2E0"));
    }

    @Test
    void parserFallsBackLongToDouble() {
        Object value = Json.parse("9223372036854775808");
        assertInstanceOf(Double.class, value);
    }

    @Test
    void parserDecodesAdditionalEscapes() {
        Map<String, Object> parsed = Json.parseObject("{\"x\":\"\\r\\b\\f\"}");
        assertEquals("\r\b\f", parsed.get("x"));
    }

    @Test
    void parserExpectsMatchingDelimiters() {
        assertThrows(Json.JsonException.class, () -> Json.parse("{\"a\":1]"));
        assertThrows(Json.JsonException.class, () -> Json.parse("[1}"));
        assertThrows(Json.JsonException.class, () -> Json.parse("{"));
    }
}
