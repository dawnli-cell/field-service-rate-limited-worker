package example.fieldservice;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class Json {
    private Json() {}

    static String write(Object value) {
        if (value == null) return "null";
        if (value instanceof String s) return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof Map<?, ?> map) {
            List<String> parts = new ArrayList<>();
            map.forEach((key, item) -> parts.add(write(key.toString()) + ":" + write(item)));
            return "{" + String.join(",", parts) + "}";
        }
        if (value instanceof Iterable<?> items) {
            List<String> parts = new ArrayList<>();
            items.forEach(item -> parts.add(write(item)));
            return "[" + String.join(",", parts) + "]";
        }
        throw new IllegalArgumentException("Unsupported JSON value: " + value.getClass().getName());
    }

    static Object read(String source) {
        Parser parser = new Parser(source);
        Object value = parser.value();
        parser.space();
        if (parser.position != source.length()) throw new IllegalArgumentException("Trailing JSON content");
        return value;
    }

    private static final class Parser {
        private final String source;
        private int position;

        private Parser(String source) { this.source = source; }

        private Object value() {
            space();
            if (position >= source.length()) throw new IllegalArgumentException("Empty JSON input");
            return switch (source.charAt(position)) {
                case '{' -> object();
                case '[' -> array();
                case '"' -> string();
                case 't' -> literal("true", true);
                case 'f' -> literal("false", false);
                case 'n' -> literal("null", null);
                default -> number();
            };
        }

        private Map<String, Object> object() {
            position++;
            Map<String, Object> result = new LinkedHashMap<>();
            space();
            if (take('}')) return result;
            do {
                space();
                String key = string();
                space();
                expect(':');
                result.put(key, value());
                space();
            } while (take(','));
            expect('}');
            return result;
        }

        private List<Object> array() {
            position++;
            List<Object> result = new ArrayList<>();
            space();
            if (take(']')) return result;
            do { result.add(value()); space(); } while (take(','));
            expect(']');
            return result;
        }

        private String string() {
            expect('"');
            StringBuilder result = new StringBuilder();
            while (position < source.length()) {
                char c = source.charAt(position++);
                if (c == '"') return result.toString();
                if (c == '\\') {
                    char escaped = source.charAt(position++);
                    result.append(switch (escaped) {
                        case '"', '\\', '/' -> escaped;
                        case 'b' -> '\b'; case 'f' -> '\f'; case 'n' -> '\n'; case 'r' -> '\r'; case 't' -> '\t';
                        case 'u' -> (char) Integer.parseInt(source.substring(position, position += 4), 16);
                        default -> throw new IllegalArgumentException("Invalid JSON escape");
                    });
                } else result.append(c);
            }
            throw new IllegalArgumentException("Unterminated JSON string");
        }

        private Object number() {
            int start = position;
            while (position < source.length() && "-+0123456789.eE".indexOf(source.charAt(position)) >= 0) position++;
            String token = source.substring(start, position);
            return token.contains(".") || token.contains("e") || token.contains("E") ? Double.valueOf(token) : Long.valueOf(token);
        }

        private Object literal(String token, Object value) {
            if (!source.startsWith(token, position)) throw new IllegalArgumentException("Invalid JSON literal");
            position += token.length();
            return value;
        }

        private void space() { while (position < source.length() && Character.isWhitespace(source.charAt(position))) position++; }
        private boolean take(char expected) { if (position < source.length() && source.charAt(position) == expected) { position++; return true; } return false; }
        private void expect(char expected) { if (!take(expected)) throw new IllegalArgumentException("Expected " + expected); }
    }
}
