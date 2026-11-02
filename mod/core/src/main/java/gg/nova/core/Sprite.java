package gg.nova.core;

/**
 * Kleines Pixel-Sprite aus Textzeilen: jedes Zeichen verweist auf eine Farbe aus {@code palette},
 * "." ist transparent.
 */
public final class Sprite {

	private final String[] rows;

	private final String keys;

	private final int[] palette;


	public Sprite(String[] rows, String keys, int[] palette) {

		this.rows = rows;

		this.keys = keys;

		this.palette = palette;

	}


	public String[] rows() {

		return this.rows;

	}


	public String keys() {

		return this.keys;

	}


	public int[] palette() {

		return this.palette;

	}
	public int width() {
		return rows[0].length();
	}

	public int height() {
		return rows.length;
	}

	public void draw(NovaCanvas canvas, int x, int y, int scale, float alpha) {
		for (int row = 0; row < rows.length; row++) {
			String line = rows[row];
			int column = 0;
			while (column < line.length()) {
				char cell = line.charAt(column);
				int index = keys.indexOf(cell);
				if (cell == '.' || index < 0) {
					column++;
					continue;
				}

				// Gleiche Pixel einer Zeile zu einem Rechteck zusammenfassen
				int run = 1;
				while (column + run < line.length() && line.charAt(column + run) == cell) {
					run++;
				}
				int color = NovaDraw.fade(palette[index], alpha);
				canvas.fill(x + column * scale, y + row * scale, x + (column + run) * scale, y + (row + 1) * scale, color);
				column += run;
			}
		}
	}

	/** Zeichnet erst einen Schlagschatten (1 Pixel nach rechts unten), dann das Sprite. */
	public void drawShadowed(NovaCanvas canvas, int x, int y, int scale, float alpha) {
		int shadow = NovaDraw.fade(0xB30B1229, alpha);
		for (int row = 0; row < this.rows.length; row++) {
			String line = this.rows[row];
			for (int column = 0; column < line.length(); column++) {
				if (line.charAt(column) != '.') {
					int sx = x + (column + 1) * scale;
					int sy = y + (row + 1) * scale;
					canvas.fill(sx, sy, sx + scale, sy + scale, shadow);
				}
			}
		}
		this.draw(canvas, x, y, scale, alpha);
	}

	/** Einfarbiges Sprite ("a" = Farbe). */
	public static Sprite mono(int color, String... rows) {
		return new Sprite(rows, "a", new int[] {color});
	}
}
