package gg.nova.core;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Eigene 5×7-Pixelschrift (nur Großbuchstaben). Wird über {@link NovaCanvas#fill} gezeichnet und
 * funktioniert daher auch im Ladebildschirm, bevor Minecrafts Schriften geladen sind.
 */
public final class PixelFont {
	public static final int HEIGHT = 7;
	private static final int SPACE_WIDTH = 3;
	private static final Map<Character, String[]> GLYPHS = new HashMap<>();

	static {
		glyph('A', ".###.", "#...#", "#...#", "#####", "#...#", "#...#", "#...#");
		glyph('B', "####.", "#...#", "#...#", "####.", "#...#", "#...#", "####.");
		glyph('C', ".###.", "#...#", "#....", "#....", "#....", "#...#", ".###.");
		glyph('D', "####.", "#...#", "#...#", "#...#", "#...#", "#...#", "####.");
		glyph('E', "#####", "#....", "#....", "####.", "#....", "#....", "#####");
		glyph('F', "#####", "#....", "#....", "####.", "#....", "#....", "#....");
		glyph('G', ".###.", "#...#", "#....", "#.###", "#...#", "#...#", ".####");
		glyph('H', "#...#", "#...#", "#...#", "#####", "#...#", "#...#", "#...#");
		glyph('I', "###", ".#.", ".#.", ".#.", ".#.", ".#.", "###");
		glyph('J', "..###", "...#.", "...#.", "...#.", "#..#.", "#..#.", ".##..");
		glyph('K', "#...#", "#..#.", "#.#..", "##...", "#.#..", "#..#.", "#...#");
		glyph('L', "#....", "#....", "#....", "#....", "#....", "#....", "#####");
		glyph('M', "#...#", "##.##", "#.#.#", "#.#.#", "#...#", "#...#", "#...#");
		glyph('N', "#...#", "##..#", "#.#.#", "#..##", "#...#", "#...#", "#...#");
		glyph('O', ".###.", "#...#", "#...#", "#...#", "#...#", "#...#", ".###.");
		glyph('P', "####.", "#...#", "#...#", "####.", "#....", "#....", "#....");
		glyph('Q', ".###.", "#...#", "#...#", "#...#", "#.#.#", "#..#.", ".##.#");
		glyph('R', "####.", "#...#", "#...#", "####.", "#.#..", "#..#.", "#...#");
		glyph('S', ".####", "#....", "#....", ".###.", "....#", "....#", "####.");
		glyph('T', "#####", "..#..", "..#..", "..#..", "..#..", "..#..", "..#..");
		glyph('U', "#...#", "#...#", "#...#", "#...#", "#...#", "#...#", ".###.");
		glyph('V', "#...#", "#...#", "#...#", "#...#", "#...#", ".#.#.", "..#..");
		glyph('W', "#...#", "#...#", "#...#", "#.#.#", "#.#.#", "##.##", "#...#");
		glyph('X', "#...#", "#...#", ".#.#.", "..#..", ".#.#.", "#...#", "#...#");
		glyph('Y', "#...#", "#...#", ".#.#.", "..#..", "..#..", "..#..", "..#..");
		glyph('Z', "#####", "....#", "...#.", "..#..", ".#...", "#....", "#####");
		glyph('Ä', "#...#", ".###.", "#...#", "#...#", "#####", "#...#", "#...#");
		glyph('Ö', "#...#", ".###.", "#...#", "#...#", "#...#", "#...#", ".###.");
		glyph('Ü', "#...#", ".....", "#...#", "#...#", "#...#", "#...#", ".###.");
		glyph('0', ".###.", "#...#", "#..##", "#.#.#", "##..#", "#...#", ".###.");
		glyph('1', "..#..", ".##..", "..#..", "..#..", "..#..", "..#..", ".###.");
		glyph('2', ".###.", "#...#", "....#", "...#.", "..#..", ".#...", "#####");
		glyph('3', "####.", "....#", "....#", ".###.", "....#", "....#", "####.");
		glyph('4', "...#.", "..##.", ".#.#.", "#..#.", "#####", "...#.", "...#.");
		glyph('5', "#####", "#....", "####.", "....#", "....#", "#...#", ".###.");
		glyph('6', ".###.", "#....", "#....", "####.", "#...#", "#...#", ".###.");
		glyph('7', "#####", "....#", "...#.", "..#..", ".#...", ".#...", ".#...");
		glyph('8', ".###.", "#...#", "#...#", ".###.", "#...#", "#...#", ".###.");
		glyph('9', ".###.", "#...#", "#...#", ".####", "....#", "....#", ".###.");
		glyph('.', ".", ".", ".", ".", ".", ".", "#");
		glyph(',', ".", ".", ".", ".", ".", "#", "#");
		glyph(':', ".", "#", ".", ".", ".", "#", ".");
		glyph('·', ".", ".", ".", "#", ".", ".", ".");
		glyph('/', "....#", "...#.", "...#.", "..#..", ".#...", ".#...", "#....");
		glyph('%', "##..#", "##.#.", "...#.", "..#..", ".#...", ".#.##", "#..##");
		glyph('-', "...", "...", "...", "###", "...", "...", "...");
		glyph('+', ".....", "..#..", "..#..", "#####", "..#..", "..#..", ".....");
		glyph('!', "#", "#", "#", "#", "#", ".", "#");
		glyph('?', ".###.", "#...#", "....#", "...#.", "..#..", ".....", "..#..");
		glyph('(', ".#", "#.", "#.", "#.", "#.", "#.", ".#");
		glyph(')', "#.", ".#", ".#", ".#", ".#", ".#", "#.");
		glyph('\'', "#", "#", ".", ".", ".", ".", ".");
		glyph('_', ".....", ".....", ".....", ".....", ".....", ".....", "#####");
	}

	private PixelFont() {
	}

	private static void glyph(char character, String... rows) {
		GLYPHS.put(character, rows);
	}

	private static String normalize(String text) {
		return text.toUpperCase(Locale.ROOT).replace("ß", "SS");
	}

	public static int width(String text, int spacing) {
		String normalized = normalize(text);
		int width = 0;
		for (int index = 0; index < normalized.length(); index++) {
			width += glyphWidth(normalized.charAt(index));
			if (index < normalized.length() - 1) {
				width += spacing;
			}
		}
		return width;
	}

	private static int glyphWidth(char character) {
		String[] glyph = GLYPHS.get(character);
		return glyph == null ? SPACE_WIDTH : glyph[0].length();
	}

	/** Zeichnet Text; {@code spacing} ist der Abstand zwischen Buchstaben in Pixeln. */
	public static void draw(NovaCanvas canvas, String text, int x, int y, int color, int spacing) {
		String normalized = normalize(text);
		int cursor = x;
		for (int index = 0; index < normalized.length(); index++) {
			char character = normalized.charAt(index);
			String[] glyph = GLYPHS.get(character);
			if (glyph != null) {
				for (int row = 0; row < glyph.length; row++) {
					String line = glyph[row];
					int column = 0;
					while (column < line.length()) {
						if (line.charAt(column) != '#') {
							column++;
							continue;
						}
						int run = 1;
						while (column + run < line.length() && line.charAt(column + run) == '#') {
							run++;
						}
						canvas.fill(cursor + column, y + row, cursor + column + run, y + row + 1, color);
						column += run;
					}
				}
			}
			cursor += glyphWidth(character) + spacing;
		}
	}

	public static void drawRight(NovaCanvas canvas, String text, int right, int y, int color, int spacing) {
		draw(canvas, text, right - width(text, spacing), y, color, spacing);
	}

	public static void drawCentered(NovaCanvas canvas, String text, int centerX, int y, int color, int spacing) {
		draw(canvas, text, centerX - width(text, spacing) / 2, y, color, spacing);
	}
}
