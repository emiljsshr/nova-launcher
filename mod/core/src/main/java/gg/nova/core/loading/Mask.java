package gg.nova.core.loading;

import gg.nova.core.NovaCanvas;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

/**
 * Deckungs-Maske aus assets/nova/loading/*.mask (erzeugt von tools/branding/LoadingMasks.java): Logo und Texte des
 * Ladebildschirms als Läufe gleicher Deckung. Braucht weder Schriften noch Texturen, die beim Start noch fehlen.
 */
final class Mask {
	private static final Map<String, Mask> CACHE = new HashMap<String, Mask>();

	final int width;
	final int height;
	/** Je Zeile Tripel aus x, Länge, Deckung (1–15). */
	private final int[][] rows;

	private Mask(int width, int height, int[][] rows) {
		this.width = width;
		this.height = height;
		this.rows = rows;
	}

	static synchronized Mask get(String name) {
		Mask mask = CACHE.get(name);
		if (mask == null) {
			mask = load(name);
			CACHE.put(name, mask);
		}
		return mask;
	}

	private static Mask load(String name) {
		InputStream stream = Mask.class.getResourceAsStream("/assets/nova/loading/" + name + ".mask");
		if (stream == null) {
			return new Mask(0, 0, new int[0][]);
		}
		try {
			BufferedReader reader = new BufferedReader(new InputStreamReader(stream, Charset.forName("UTF-8")));
			try {
				String[] size = reader.readLine().trim().split(" ");
				int width = Integer.parseInt(size[0]);
				int height = Integer.parseInt(size[1]);
				int[][] rows = new int[height][];
				for (int row = 0; row < height; row++) {
					String line = reader.readLine();
					String[] runs = line == null || line.trim().isEmpty() ? new String[0] : line.trim().split(" ");
					rows[row] = new int[runs.length * 3];
					for (int run = 0; run < runs.length; run++) {
						String[] parts = runs[run].split(",");
						rows[row][run * 3] = Integer.parseInt(parts[0]);
						rows[row][run * 3 + 1] = Integer.parseInt(parts[1]);
						rows[row][run * 3 + 2] = Integer.parseInt(parts[2]);
					}
				}
				return new Mask(width, height, rows);
			} finally {
				reader.close();
			}
		} catch (IOException | RuntimeException error) {
			System.err.println("[Nova] Ladebildschirm-Maske " + name + " fehlt: " + error);
			return new Mask(0, 0, new int[0][]);
		}
	}

	/** Zeichnet die Maske in {@code rgb}; {@code alpha} blendet das Ganze ein und aus. */
	void draw(NovaCanvas canvas, int x, int y, int rgb, float alpha) {
		for (int row = 0; row < this.rows.length; row++) {
			int[] runs = this.rows[row];
			for (int index = 0; index < runs.length; index += 3) {
				int a = Math.round(runs[index + 2] / 15.0F * alpha * 255.0F);
				if (a <= 0) {
					continue;
				}
				int start = x + runs[index];
				canvas.fill(start, y + row, start + runs[index + 1], y + row + 1, a << 24 | rgb & 0xFFFFFF);
			}
		}
	}
}
