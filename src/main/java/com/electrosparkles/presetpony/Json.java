package com.electrosparkles.presetpony;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal dependency-free JSON reader/writer — just enough for PedalboardStore's own
 * files (set files + the recent-files list). Not a general-purpose JSON library: no
 * streaming, no custom (de)serializers. Values are plain Map&lt;String,Object&gt; / List&lt;Object&gt; /
 * String / Double / Boolean / null, same shape {@code parse()} hands back either way.
 * Same "hand-roll it, no new deps" convention as FusePresetBackup's manifest JSON.
 */
final class Json {

    private Json() {
    }

    // ---- Writing ----

    static String write(Object value) {
        StringBuilder sb = new StringBuilder();
        writeValue(value, sb, 0);
        return sb.toString();
    }

    private static void writeValue(Object value, StringBuilder sb, int indent) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String s) {
            writeString(s, sb);
        } else if (value instanceof Boolean b) {
            sb.append(b);
        } else if (value instanceof Number n) {
            sb.append(n);
        } else if (value instanceof Map<?, ?> map) {
            writeObject(map, sb, indent);
        } else if (value instanceof List<?> list) {
            writeArray(list, sb, indent);
        } else {
            throw new IllegalArgumentException("Unsupported JSON value type: " + value.getClass());
        }
    }

    private static void writeObject(Map<?, ?> map, StringBuilder sb, int indent) {
        if (map.isEmpty()) {
            sb.append("{}");
            return;
        }
        sb.append("{\n");
        int i = 0;
        for (Map.Entry<?, ?> e : map.entrySet()) {
            indent(sb, indent + 1);
            writeString(String.valueOf(e.getKey()), sb);
            sb.append(": ");
            writeValue(e.getValue(), sb, indent + 1);
            if (++i < map.size()) sb.append(',');
            sb.append('\n');
        }
        indent(sb, indent);
        sb.append('}');
    }

    private static void writeArray(List<?> list, StringBuilder sb, int indent) {
        if (list.isEmpty()) {
            sb.append("[]");
            return;
        }
        sb.append("[\n");
        for (int i = 0; i < list.size(); i++) {
            indent(sb, indent + 1);
            writeValue(list.get(i), sb, indent + 1);
            if (i + 1 < list.size()) sb.append(',');
            sb.append('\n');
        }
        indent(sb, indent);
        sb.append(']');
    }

    private static void indent(StringBuilder sb, int level) {
        sb.append("  ".repeat(level));
    }

    private static void writeString(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    // ---- Reading ----

    static Object parse(String text) {
        Parser p = new Parser(text);
        p.skipWs();
        Object v = p.parseValue();
        p.skipWs();
        if (!p.atEnd()) {
            throw new IllegalArgumentException("Trailing content after JSON value at position " + p.pos);
        }
        return v;
    }

    private static final class Parser {
        final String s;
        int pos = 0;

        Parser(String s) {
            this.s = s;
        }

        boolean atEnd() {
            return pos >= s.length();
        }

        char peek() {
            return s.charAt(pos);
        }

        void skipWs() {
            while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) pos++;
        }

        Object parseValue() {
            skipWs();
            if (atEnd()) throw new IllegalArgumentException("Unexpected end of JSON input");
            char c = peek();
            return switch (c) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '"' -> parseString();
                case 't', 'f' -> parseBoolean();
                case 'n' -> parseNull();
                default -> parseNumber();
            };
        }

        Map<String, Object> parseObject() {
            expect('{');
            Map<String, Object> map = new LinkedHashMap<>();
            skipWs();
            if (!atEnd() && peek() == '}') {
                pos++;
                return map;
            }
            while (true) {
                skipWs();
                String key = parseString();
                skipWs();
                expect(':');
                Object value = parseValue();
                map.put(key, value);
                skipWs();
                if (!atEnd() && peek() == ',') {
                    pos++;
                    continue;
                }
                break;
            }
            skipWs();
            expect('}');
            return map;
        }

        List<Object> parseArray() {
            expect('[');
            List<Object> list = new ArrayList<>();
            skipWs();
            if (!atEnd() && peek() == ']') {
                pos++;
                return list;
            }
            while (true) {
                list.add(parseValue());
                skipWs();
                if (!atEnd() && peek() == ',') {
                    pos++;
                    continue;
                }
                break;
            }
            skipWs();
            expect(']');
            return list;
        }

        String parseString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (atEnd()) throw new IllegalArgumentException("Unterminated string in JSON");
                char c = s.charAt(pos++);
                if (c == '"') break;
                if (c == '\\') {
                    char esc = s.charAt(pos++);
                    switch (esc) {
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case '/' -> sb.append('/');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'u' -> {
                            String hex = s.substring(pos, pos + 4);
                            sb.append((char) Integer.parseInt(hex, 16));
                            pos += 4;
                        }
                        default -> throw new IllegalArgumentException("Unknown escape \\" + esc + " in JSON string");
                    }
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }

        Boolean parseBoolean() {
            if (s.startsWith("true", pos)) {
                pos += 4;
                return Boolean.TRUE;
            }
            if (s.startsWith("false", pos)) {
                pos += 5;
                return Boolean.FALSE;
            }
            throw new IllegalArgumentException("Invalid literal at position " + pos);
        }

        Object parseNull() {
            if (s.startsWith("null", pos)) {
                pos += 4;
                return null;
            }
            throw new IllegalArgumentException("Invalid literal at position " + pos);
        }

        Double parseNumber() {
            int start = pos;
            if (!atEnd() && (peek() == '-' || peek() == '+')) pos++;
            while (!atEnd() && (Character.isDigit(peek()) || peek() == '.' || peek() == 'e' || peek() == 'E'
                    || peek() == '-' || peek() == '+')) {
                pos++;
            }
            if (pos == start) throw new IllegalArgumentException("Invalid number at position " + pos);
            return Double.parseDouble(s.substring(start, pos));
        }

        void expect(char c) {
            if (atEnd() || peek() != c) {
                throw new IllegalArgumentException("Expected '" + c + "' at position " + pos);
            }
            pos++;
        }
    }
}
