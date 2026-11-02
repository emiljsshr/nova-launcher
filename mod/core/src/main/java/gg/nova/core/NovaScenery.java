package gg.nova.core;

/** Pixelige Winternacht: Himmel, Sterne, Polarlicht, Schneefall, Berge mit Schneekappen und Tannen. */
public final class NovaScenery {
	/** Gipfel: Mitte (Anteil der Breite), Höhe (Anteil der Fläche), Steigung. */
	private static final float[][] FAR_PEAKS = {{0.06F, 0.78F, 1.1F}, {0.36F, 0.84F, 1.0F}, {0.65F, 0.74F, 1.05F}, {0.94F, 0.80F, 1.0F}};
	private static final float[][] NEAR_PEAKS = {{0.17F, 0.42F, 1.3F}, {0.36F, 0.36F, 1.4F}, {0.56F, 0.40F, 1.3F}, {0.75F, 0.38F, 1.4F}};
	private static final float[] TREES = {0.04F, 0.06F, 0.09F, 0.12F, 0.86F, 0.89F, 0.92F, 0.96F};

	private NovaScenery() {
	}

	private static long now() {
		return System.currentTimeMillis();
	}

	public static void sky(NovaCanvas canvas, int x, int y, int width, int height, float alpha) {
		canvas.gradient(x, y, x + width, y + height, NovaDraw.fade(0xFF0D0D12, alpha), NovaDraw.fade(0xFF26262D, alpha));
	}

	/** Funkelnde Sterne; {@code density} ≈ Sterne pro 1000 Pixel². */
	public static void stars(NovaCanvas canvas, int x, int y, int width, int height, int seed, float density, float alpha) {
		int count = Math.max(8, Math.round(width * height / 1000.0F * density));
		long time = now();

		for (int index = 0; index < count; index++) {
			int starX = x + (int) (NovaDraw.hash(seed + index * 3) * width);
			int starY = y + (int) (NovaDraw.hash(seed + index * 3 + 1) * height);
			float phase = NovaDraw.hash(seed + index * 3 + 2);
			float twinkle = 0.45F + 0.55F * (0.5F + 0.5F * (float) Math.sin(time / 700.0 + phase * 6.283));
			int color = NovaDraw.fade(phase > 0.8F ? NovaDraw.FROST : NovaDraw.WHITE, twinkle * alpha);

			if (phase > 0.95F) {
				canvas.fill(starX - 1, starY, starX + 2, starY + 1, color);
				canvas.fill(starX, starY - 1, starX + 1, starY + 2, color);
			} else {
				canvas.fill(starX, starY, starX + 1, starY + 1, color);
			}
		}
	}

	/** Weiches, langsam wanderndes Polarlicht als Band über den oberen Bildschirm. */
	public static void aurora(NovaCanvas canvas, int width, int height, float alpha) {
		double time = now() / 1000.0;
		int step = Math.max(1, width / 320);
		int thickness = Math.max(12, height / 7);
		int layers = 10;

		for (int column = 0; column < width; column += step) {
			double wave = Math.sin(column * 0.008 + time * 0.25) * 0.045 + Math.sin(column * 0.021 - time * 0.4) * 0.02;
			int center = (int) (height * (0.17 + wave));
			// Helligkeit wandert entlang des Bandes – wirkt wie wehende Vorhänge
			float intensity = 0.55F + 0.45F * (float) Math.sin(column * 0.015 + time * 0.6);
			for (int layer = 0; layer < layers; layer++) {
				float spread = 1.0F - layer / (float) layers;
				int top = center - (int) (thickness * spread);
				int bottom = center + (int) (thickness * 0.45F * spread);
				int color = layer < layers / 2 ? 0x0A585860 : 0x0CB9B9C2;
				canvas.fill(column, top, column + step, bottom, NovaDraw.fade(color, alpha * intensity));
			}
		}
	}

	/** Fallender Schnee mit leichtem Seitwärtsdrift. */
	public static void snow(NovaCanvas canvas, int width, int height, int count, float alpha) {
		double time = now() / 1000.0;
		for (int index = 0; index < count; index++) {
			float seedX = NovaDraw.hash(index * 7 + 1);
			float speed = 8.0F + NovaDraw.hash(index * 7 + 2) * 14.0F;
			float offset = NovaDraw.hash(index * 7 + 3) * height;
			int flakeY = (int) ((offset + time * speed) % height);
			int flakeX = (int) (seedX * width + Math.sin(time * 0.8 + index) * 4.0);
			boolean big = NovaDraw.hash(index * 7 + 4) > 0.82F;
			int color = NovaDraw.fade(big ? 0xCCFFFFFF : 0x99E4E4ED, alpha);
			canvas.fill(flakeX, flakeY, flakeX + (big ? 2 : 1), flakeY + (big ? 2 : 1), color);
		}
	}

	/** Vollständige Landschaft für den Ladebildschirm (Fläche = unterer Bildschirmbereich). */
	public static void landscape(NovaCanvas canvas, int x, int y, int width, int height, float alpha) {
		range(canvas, x, y, width, height, FAR_PEAKS, 0xFF404047, 0xFF35353C, 0xFFB1B1B9, 0xFF94949C, 0.7F, alpha);
		range(canvas, x, y, width, height, NEAR_PEAKS, 0xFF2C2C33, 0xFF25252C, 0xFF85858D, 0xFF6F6F77, 0.55F, alpha);

		int groundY = y + height - Math.max(6, height / 10);
		for (int index = 0; index < TREES.length; index++) {
			int treeHeight = (int) (height * (0.30F + NovaDraw.hash(index * 5 + 9) * 0.25F));
			tree(canvas, x + (int) (width * TREES[index]), groundY, treeHeight, alpha);
		}

		canvas.fill(x, groundY, x + width, y + height, NovaDraw.fade(0xFF17171D, alpha));
		canvas.fill(x, groundY, x + width, groundY + 1, NovaDraw.fade(0xFF3C3C43, alpha));
	}

	/** Kleine Variante ohne Tannen, z. B. für Banner im Menü. */
	public static void mountains(NovaCanvas canvas, int x, int y, int width, int height, float alpha) {
		range(canvas, x, y, width, height, FAR_PEAKS, 0xFF404047, 0xFF35353C, 0xFFD5D5DC, 0xFFB1B1B9, 0.55F, alpha);
	}

	private static void range(
		NovaCanvas canvas, int x, int y, int width, int height, float[][] peaks,
		int light, int dark, int snowLight, int snowDark, float snowLine, float alpha
	) {
		int step = Math.max(2, width / 220);
		int bottom = y + height;

		for (int column = 0; column < width; column += step) {
			float position = column / (float) width;
			float best = 0.0F;
			float bestPeak = 0.0F;
			boolean leftSide = true;

			for (float[] peak : peaks) {
				float elevation = peak[1] - Math.abs(position - peak[0]) * peak[2] * (width / (float) height) * 0.55F;
				if (elevation > best) {
					best = elevation;
					bestPeak = peak[1];
					leftSide = position <= peak[0];
				}
			}
			if (best <= 0.0F) {
				continue;
			}

			// In Stufen runden, damit die Hänge pixelig wirken
			int top = bottom - (int) (best * height);
			top = y + Math.max(0, (top - y) / step * step);
			int columnEnd = Math.min(x + column + step, x + width);
			canvas.fill(x + column, top, columnEnd, bottom, NovaDraw.fade(leftSide ? light : dark, alpha));

			// Schneekappe: nur oberhalb der Schneegrenze des jeweiligen Gipfels, mit gezacktem Rand
			float capLimit = bestPeak * snowLine;
			if (best > capLimit) {
				int jag = (int) (NovaDraw.hash(column * 13 + peaks.length) * 3) * step / 2;
				int snowBottom = bottom - (int) (capLimit * height) + jag;
				canvas.fill(x + column, top, columnEnd, Math.min(snowBottom, bottom), NovaDraw.fade(leftSide ? snowLight : snowDark, alpha));
			}
		}
	}

	private static void tree(NovaCanvas canvas, int centerX, int groundY, int height, float alpha) {
		int color = NovaDraw.fade(0xFF14141A, alpha);
		int top = groundY - height;
		for (int row = 0; row < height; row += 2) {
			// Drei gestufte Etagen wie bei einer Pixel-Tanne
			float progress = row / (float) height;
			float tier = (progress * 3.0F) % 1.0F;
			int half = (int) ((0.25F + tier * 0.75F) * (2 + progress * height * 0.22F));
			canvas.fill(centerX - half, top + row, centerX + half + 1, top + row + 2, color);
		}
		canvas.fill(centerX - 1, groundY - 3, centerX + 2, groundY, color);
		canvas.fill(centerX, top - 2, centerX + 1, top, NovaDraw.fade(0xFFE4E4ED, alpha * 0.8F));
	}
}
