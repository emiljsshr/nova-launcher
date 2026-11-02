package gg.nova.core;

/** Farben und Zeichen-Helfer im Nova-Stil. */
public final class NovaDraw {
	public static final int FROST = 0xFFBEBEC7;
	public static final int FROST_LIGHT = 0xFFCDCDD5;
	public static final int FROST_DEEP = 0xFF5E5E66;
	public static final int FROST_STRONG = 0xFF47474F;
	public static final int NAVY = 0xFF131319;
	public static final int NAVY_LIGHT = 0xFF2C2C33;
	public static final int INK = 0xFF17171D;
	public static final int MUTED = 0xFF9A9AA2;
	public static final int SOFT = 0xFFC6C6CE;
	public static final int WHITE = 0xFFFFFFFF;
	public static final int ONLINE = 0xFF3FBF5A;
	public static final int GOLD = 0xFFFFD84A;
	public static final int PANEL = 0xD91D1D23;
	public static final int PANEL_HOVER = 0xE636363D;

	/** Nova-Wappen (textures/gui/logo.png, aus tools/branding/Branding.java). */
	public static final int LOGO_WIDTH = 228;
	public static final int LOGO_HEIGHT = 256;

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

	/** Rechteck mit abgerundeten Ecken (Radius in Pixeln, stufenweise angenähert). */
	public static void rounded(NovaCanvas canvas, int x, int y, int width, int height, int radius, int color) {
		radius = Math.min(radius, Math.min(width, height) / 2);
		if (radius <= 0) {
			canvas.fill(x, y, x + width, y + height, color);
			return;
		}
		canvas.fill(x, y + radius, x + width, y + height - radius, color);
		for (int row = 0; row < radius; row++) {
			// Einzug der Zeile auf dem Viertelkreis
			double dy = radius - row - 0.5;
			int inset = (int) Math.round(radius - Math.sqrt(Math.max(0.0, radius * radius - dy * dy)));
			canvas.fill(x + inset, y + row, x + width - inset, y + row + 1, color);
			canvas.fill(x + inset, y + height - row - 1, x + width - inset, y + height - row, color);
		}
	}

	/** Rahmen mit abgerundeten Ecken: außen {@code color}, innen {@code fill}. */
	public static void roundedFrame(NovaCanvas canvas, int x, int y, int width, int height, int radius, int color, int fill) {
		rounded(canvas, x, y, width, height, radius, color);
		rounded(canvas, x + 1, y + 1, width - 2, height - 2, Math.max(0, radius - 1), fill);
	}

	/** Flächiges Panel ohne Pixel-Ecken. */
	public static void panel(NovaCanvas canvas, int x, int y, int width, int height, int color) {
		canvas.fill(x, y, x + width, y + height, color);
	}

	public static int logoHeight(int width) {
		return Math.round(width * (float) LOGO_HEIGHT / LOGO_WIDTH);
	}

	public static int logoWidth(int height) {
		return Math.round(height * (float) LOGO_WIDTH / LOGO_HEIGHT);
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
