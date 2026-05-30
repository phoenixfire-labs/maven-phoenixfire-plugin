package io.phoenixfire.core.report;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class XmlTest {

    @Test
    void escapesNullAndEntities() {
        assertEquals("", Xml.escape(null));
        assertEquals("&amp;&lt;&gt;&quot;&apos;", Xml.escape("&<>\"'"));
    }

    @Test
    void stripsIllegalControlCharacters() {
        assertEquals("a b", Xml.escape("a\u0001b"));
        assertEquals("tab\there", Xml.escape("tab\there"));
    }
}
