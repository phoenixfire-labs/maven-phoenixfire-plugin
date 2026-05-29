package io.phoenixfire.api.json;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Minimal, dependency-free JSON encoder/decoder.
 *
 * <p>Phoenixfire deliberately avoids pulling a JSON library onto the forked test classpath, so this
 * small implementation handles exactly the shapes used by the IPC protocol and report writers:
 * objects ({@link Map}), arrays ({@link List}), strings, numbers, booleans and {@code null}.
 *
 * <p>It is not a general-purpose, spec-exhaustive parser, but it is correct for the controlled
 * messages exchanged inside Phoenixfire.
 */
public final class Json {

    private Json() {
    }

    public static String encode(Object value) {
        StringBuilder sb = new StringBuilder();
        write(sb, value);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void write(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof Map) {
            writeObject(sb, (Map<String, Object>) value);
        } else if (value instanceof List) {
            writeArray(sb, (List<Object>) value);
        } else if (value instanceof CharSequence || value instanceof Character) {
            writeString(sb, value.toString());
        } else if (value instanceof Boolean) {
            sb.append(value.toString());
        } else if (value instanceof Number) {
            sb.append(value.toString());
        } else {
            // Fall back to a quoted string representation for unknown types.
            writeString(sb, value.toString());
        }
    }

    private static void writeObject(StringBuilder sb, Map<String, Object> map) {
        sb.append('{');
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            writeString(sb, entry.getKey());
            sb.append(':');
            write(sb, entry.getValue());
        }
        sb.append('}');
    }

    private static void writeArray(StringBuilder sb, List<Object> list) {
        sb.append('[');
        boolean first = true;
        for (Object item : list) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            write(sb, item);
        }
        sb.append(']');
    }

    private static void writeString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
    }

    public static Object parse(String text) {
        Parser parser = new Parser(text);
        parser.skipWhitespace();
        Object value = parser.readValue();
        parser.skipWhitespace();
        if (!parser.atEnd()) {
            throw new JsonException("Trailing content after JSON value at position " + parser.pos);
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String text) {
        Object value = parse(text);
        if (!(value instanceof Map)) {
            throw new JsonException("Expected a JSON object but got: "
                    + (value == null ? "null" : value.getClass().getSimpleName()));
        }
        return (Map<String, Object>) value;
    }

    /** Exception thrown for malformed JSON. */
    public static final class JsonException extends RuntimeException {
        public JsonException(String message) {
            super(message);
        }
    }

    private static final class Parser {
        private final String text;
        private int pos;

        Parser(String text) {
            this.text = text;
        }

        boolean atEnd() {
            return pos >= text.length();
        }

        void skipWhitespace() {
            while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
                pos++;
            }
        }

        Object readValue() {
            skipWhitespace();
            if (atEnd()) {
                throw new JsonException("Unexpected end of input");
            }
            char c = text.charAt(pos);
            switch (c) {
                case '{':
                    return readObject();
                case '[':
                    return readArray();
                case '"':
                    return readString();
                case 't':
                case 'f':
                    return readBoolean();
                case 'n':
                    return readNull();
                default:
                    return readNumber();
            }
        }

        private Map<String, Object> readObject() {
            expect('{');
            Map<String, Object> map = new java.util.LinkedHashMap<>();
            skipWhitespace();
            if (peek() == '}') {
                pos++;
                return map;
            }
            while (true) {
                skipWhitespace();
                String key = readString();
                skipWhitespace();
                expect(':');
                Object value = readValue();
                map.put(key, value);
                skipWhitespace();
                char c = next();
                if (c == '}') {
                    return map;
                }
                if (c != ',') {
                    throw new JsonException("Expected ',' or '}' at position " + (pos - 1));
                }
            }
        }

        private List<Object> readArray() {
            expect('[');
            List<Object> list = new ArrayList<>();
            skipWhitespace();
            if (peek() == ']') {
                pos++;
                return list;
            }
            while (true) {
                Object value = readValue();
                list.add(value);
                skipWhitespace();
                char c = next();
                if (c == ']') {
                    return list;
                }
                if (c != ',') {
                    throw new JsonException("Expected ',' or ']' at position " + (pos - 1));
                }
            }
        }

        private String readString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (atEnd()) {
                    throw new JsonException("Unterminated string");
                }
                char c = text.charAt(pos++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\') {
                    char esc = text.charAt(pos++);
                    switch (esc) {
                        case '"':
                            sb.append('"');
                            break;
                        case '\\':
                            sb.append('\\');
                            break;
                        case '/':
                            sb.append('/');
                            break;
                        case 'n':
                            sb.append('\n');
                            break;
                        case 'r':
                            sb.append('\r');
                            break;
                        case 't':
                            sb.append('\t');
                            break;
                        case 'b':
                            sb.append('\b');
                            break;
                        case 'f':
                            sb.append('\f');
                            break;
                        case 'u':
                            String hex = text.substring(pos, pos + 4);
                            sb.append((char) Integer.parseInt(hex, 16));
                            pos += 4;
                            break;
                        default:
                            throw new JsonException("Invalid escape \\" + esc);
                    }
                } else {
                    sb.append(c);
                }
            }
        }

        private Boolean readBoolean() {
            if (text.startsWith("true", pos)) {
                pos += 4;
                return Boolean.TRUE;
            }
            if (text.startsWith("false", pos)) {
                pos += 5;
                return Boolean.FALSE;
            }
            throw new JsonException("Invalid literal at position " + pos);
        }

        private Object readNull() {
            if (text.startsWith("null", pos)) {
                pos += 4;
                return null;
            }
            throw new JsonException("Invalid literal at position " + pos);
        }

        private Object readNumber() {
            int start = pos;
            boolean floating = false;
            while (pos < text.length()) {
                char c = text.charAt(pos);
                if (c == '-' || c == '+' || (c >= '0' && c <= '9')) {
                    pos++;
                } else if (c == '.' || c == 'e' || c == 'E') {
                    floating = true;
                    pos++;
                } else {
                    break;
                }
            }
            String num = text.substring(start, pos);
            if (num.isEmpty()) {
                throw new JsonException("Invalid number at position " + start);
            }
            if (floating) {
                return Double.parseDouble(num);
            }
            try {
                return Long.parseLong(num);
            } catch (NumberFormatException e) {
                return Double.parseDouble(num);
            }
        }

        private char peek() {
            if (atEnd()) {
                throw new JsonException("Unexpected end of input");
            }
            return text.charAt(pos);
        }

        private char next() {
            if (atEnd()) {
                throw new JsonException("Unexpected end of input");
            }
            return text.charAt(pos++);
        }

        private void expect(char expected) {
            char c = next();
            if (c != expected) {
                throw new JsonException("Expected '" + expected + "' but got '" + c + "' at position " + (pos - 1));
            }
        }
    }
}
