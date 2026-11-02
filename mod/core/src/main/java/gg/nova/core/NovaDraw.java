package gg.nova.core;

/** Farben und Zeichen-Helfer im Nova-Stil. */
public final class NovaDraw {
	public static final int FROST = 0xFF9CC2F7;
	public static final int FROST_LIGHT = 0xFFB2D0F9;
	public static final int FROST_DEEP = 0xFF6F9BE0;
	public static final int FROST_STRONG = 0xFF4A7FD6;
	public static final int NAVY = 0xFF0B1229;
	public static final int NAVY_LIGHT = 0xFF1A2A55;
	public static final int INK = 0xFF0D1631;
	public static final int MUTED = 0xFF8F9BB7;
	public static final int SOFT = 0xFFB9C7E4;
	public static final int WHITE = 0xFFFFFFFF;
	public static final int ONLINE = 0xFF3FBF5A;
	public static final int GOLD = 0xFFFFD84A;
	public static final int PANEL = 0xD9141C36;
	public static final int PANEL_HOVER = 0xE6243462;

	public static final int LOGO_WIDTH = 590;
	public static final int LOGO_HEIGHT = 181;

	private NovaDraw() {
	}

	public static float clamp(float value, float min, float max) {
		return Math.max(min, Math.min(max, value));
	}

	/** Multipliziert die Deckkraft einer ARGB-Farbe. */
	public static int fade(int color, float alpha) {
		int a = Math.round((color >>> 24) * clamp(alpha, 0.0F, 1.0F));
		return a << 24 | color & 0xFFFFFF;
	}

	public static int white(float alpha) {
		return fade(WHITE, alpha);
	}

	/** Flächiges Panel ohne Pixel-Ecken. */
	public static void panel(NovaCanvas canvas, int x, int y, int width, int height, int color) {
		canvas.fill(x, y, x + width, y + height, color);
	}

	public static int logoHeight(int width) {
		return Math.round(width * (float) LOGO_HEIGHT / LOGO_WIDTH);
	}

	public static void logo(NovaCanvas canvas, int x, int y, int width, float alpha) {
		canvas.logo(x, y, width, logoHeight(width), white(alpha));
	}

	public static int spacedWidth(NovaCanvas canvas, String text, int spacing) {
		if (text.isEmpty()) {
			return 0;
		}
		return canvas.textWidth(text) + spacing * (text.codePointCount(0, text.length()) - 1);
	}

	/** Minecraft-Text mit Buchstabenabstand (ersetzt "letter-spacing" aus dem Launcher-Design). */
	public static void spacedText(NovaCanvas canvas, String text, int x, int y, int color, int spacing, boolean shadow) {
		if (spacing == 0) {
			canvas.text(text, x, y, color, shadow);
			return;
		}
		int cursor = x;
		for (int index = 0; index < text.length(); ) {
			int codePoint = text.codePointAt(index);
			String glyph = new String(Character.toChars(codePoint));
			canvas.text(glyph, cursor, y, color, shadow);
			cursor += canvas.textWidth(glyph) + spacing;
			index += Character.charCount(codePoint);
		}
	}

	/** Kleiner Text (75 %) – für Adressen, Hinweise, Fußzeilen. */
	public static void smallText(NovaCanvas canvas, String text, float x, float y, int color, int spacing) {
		canvas.push();
		canvas.translate(x, y);
		canvas.scale(0.75F);
		spacedText(canvas, text, 0, 0, color, spacing, false);
		canvas.pop();
	}

	public static int smallWidth(NovaCanvas canvas, String text, int spacing) {
		return (int) Math.ceil(spacedWidth(canvas, text, spacing) * 0.75F);
	}

	/** Kürzt Text mit "…" auf die angegebene Breite. */
	public static String trim(NovaCanvas canvas, String text, int maxWidth) {
		if (canvas.textWidth(text) <= maxWidth) {
			return text;
		}
		String ellipsis = "…";
		int end = text.length();
		while (end > 0 && canvas.textWidth(text.substring(0, end) + ellipsis) > maxWidth) {
			end--;
		}
		return text.substring(0, end) + ellipsis;
	}

	/** Deterministischer Zufall (0–1), damit Sterne und Mosaike bei jedem Frame gleich aussehen. */
	public static float hash(int seed) {
		int value = seed * 0x9E3779B1;
		value ^= value >>> 15;
		value *= 0x85EBCA77;
		value ^= value >>> 13;
		return (value & 0xFFFFFF) / (float) 0x1000000;
	}
}
