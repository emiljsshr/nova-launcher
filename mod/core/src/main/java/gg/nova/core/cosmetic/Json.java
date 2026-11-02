package gg.nova.core.cosmetic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Minimaler JSON-Leser (Cosmetic-Dateien, Launcher-Nachrichten): Objekte, Listen, Zahlen, Strings, true/false/null. */
public final class Json {
	private final String text;
	private int pos;

	private Json(String text) {
		this.text = text;
	}

	public static Object parse(String text) {
		Json json = new Json(text);
		Object value = json.value();
		json.skipSpace();
		if (json.pos != text.length()) {
			throw json.error("Unerwartete Zeichen am Ende");
		}
		return value;
	}

	@SuppressWarnings("unchecked")
	public static Map<String, Object> object(Object value) {
		return value instanceof Map ? (Map<String, Object>) value : new LinkedHashMap<String, Object>();
	}

	@SuppressWarnings("unchecked")
	public static List<Object> list(Object value) {
		return value instanceof List ? (List<Object>) value : new ArrayList<Object>();
	}

	public static float number(Object value, float fallback) {
		return value instanceof Number ? ((Number) value).floatValue() : fallback;
	}

	/** Text oder {@code fallback}, wenn der Wert kein String ist. */
	public static String string(Object value, String fallback) {
		return value instanceof String ? (String) value : fallback;
	}

	/** JSON-String mit Anführungszeichen und Escapes. */
	public static String quote(String text) {
		StringBuilder out = new StringBuilder(text.length() + 2).append('"');
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '"' || c == '\\') {
				out.append('\\').append(c);
			} else if (c < 0x20) {
				out.append(String.format("\\u%04x", (int) c));
			} else {
				out.append(c);
			}
		}
		return out.append('"').toString();
	}

	static float[] vec3(Object value, float fallback) {
		List<Object> list = list(value);
		float[] result = {fallback, fallback, fallback};
		for (int i = 0; i < 3 && i < list.size(); i++) {
			result[i] = number(list.get(i), fallback);
		}
		return result;
	}

	private Object value() {
		skipSpace();
		if (pos >= text.length()) {
			throw error("Unerwartetes Ende");
		}
		char c = text.charAt(pos);
		if (c == '{') {
			return parseObject();
		}
		if (c == '[') {
			return parseList();
		}
		if (c == '"') {
			return parseString();
		}
		if (text.startsWith("true", pos)) {
			pos += 4;
			return Boolean.TRUE;
		}
		if (text.startsWith("false", pos)) {
			pos += 5;
			return Boolean.FALSE;
		}
		if (text.startsWith("null", pos)) {
			pos += 4;
			return null;
		}
		return parseNumber();
	}

	private Map<String, Object> parseObject() {
		Map<String, Object> result = new LinkedHashMap<String, Object>();
		pos++;
		skipSpace();
		if (peek() == '}') {
			pos++;
			return result;
		}
		while (true) {
			skipSpace();
			String key = parseString();
			skipSpace();
			expect(':');
			result.put(key, value());
			skipSpace();
			if (peek() == ',') {
				pos++;
				continue;
			}
			expect('}');
			return result;
		}
	}

	private List<Object> parseList() {
		List<Object> result = new ArrayList<Object>();
		pos++;
		skipSpace();
		if (peek() == ']') {
			pos++;
			return result;
		}
		while (true) {
			result.add(value());
			skipSpace();
			if (peek() == ',') {
				pos++;
				continue;
			}
			expect(']');
			return result;
		}
	}

	private String parseString() {
		expect('"');
		StringBuilder builder = new StringBuilder();
		while (pos < text.length()) {
			char c = text.charAt(pos++);
			if (c == '"') {
				return builder.toString();
			}
			if (c != '\\') {
				builder.append(c);
				continue;
			}
			char escaped = text.charAt(pos++);
			switch (escaped) {
				case 'n': builder.append('\n'); break;
				case 't': builder.append('\t'); break;
				case 'r': builder.append('\r'); break;
				case 'b': builder.append('\b'); break;
				case 'f': builder.append('\f'); break;
				case 'u':
					builder.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
					pos += 4;
					break;
				default: builder.append(escaped);
			}
		}
		throw error("String nicht beendet");
	}

	private Double parseNumber() {
		int start = pos;
		while (pos < text.length() && "+-0123456789.eE".indexOf(text.charAt(pos)) >= 0) {
			pos++;
		}
		if (start == pos) {
			throw error("Unerwartetes Zeichen");
		}
		return Double.valueOf(text.substring(start, pos));
	}

	private void skipSpace() {
		while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
			pos++;
		}
	}

	private char peek() {
		return pos < text.length() ? text.charAt(pos) : '\0';
	}

	private void expect(char c) {
		if (peek() != c) {
			throw error("'" + c + "' erwartet");
		}
		pos++;
	}

	private IllegalArgumentException error(String message) {
		return new IllegalArgumentException(message + " (Position " + pos + ")");
	}
}
