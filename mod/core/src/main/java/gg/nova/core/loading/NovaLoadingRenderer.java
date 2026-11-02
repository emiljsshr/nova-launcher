package gg.nova.core.loading;

import gg.nova.core.NovaCanvas;
import gg.nova.core.NovaDraw;

/**
 * Nova-Ladebildschirm: lila Fläche, weißes Wappen, "NOVA CLIENT", umrandeter Ladebalken und Statuszeile
 * (Entwurf tools/branding/loading-mockup.png). Gezeichnet in den Pixeln des Entwurfs (1920 × 1080) und auf das
 * Fenster skaliert. Logo und Texte sind Masken ({@link Mask}), weil beim Start noch keine Schriften geladen sind.
 */
public final class NovaLoadingRenderer {
	private static final int REFERENCE_WIDTH = 1920;
	private static final int REFERENCE_HEIGHT = 1080;
	private static final int BACKGROUND = 0x6D28D9;
	private static final int WHITE = 0xFFFFFF;
	private static final int STATUS = 0xD3BFF4;

	// Maße aus dem Entwurf
	private static final int BAR_X = 520;
	private static final int BAR_Y = 800;
	private static final int BAR_WIDTH = 880;
	private static final int BAR_HEIGHT = 30;
	private static final int BAR_BORDER = 4;
	private static final int BAR_GAP = 4;

	private final NovaCanvas canvas;

	private NovaLoadingRenderer(NovaCanvas canvas) {
		this.canvas = canvas;
	}

	public static void render(NovaCanvas canvas, LoadingInfo info) {
		new NovaLoadingRenderer(canvas).draw(info);
	}

	private void draw(LoadingInfo info) {
		int width = this.canvas.width();
		int height = this.canvas.height();
		float background = info.backgroundAlpha();
		float content = info.contentAlpha();
		// Minecrafts Fortschritt läuft nach – ist das Laden fertig, zeigen wir 100 %.
		// Negativ = Fortschritt unbekannt (1.8.9 meldet keinen Ladefortschritt).
		float progress = info.complete() ? 1.0F : info.progress();

		this.canvas.fill(0, 0, width, height, NovaDraw.fade(0xFF000000 | BACKGROUND, background));
		if (content <= 0.01F) {
			return;
		}

		// Entwurf mittig einpassen, Seitenverhältnis bleibt
		float scale = Math.min(width / (float) REFERENCE_WIDTH, height / (float) REFERENCE_HEIGHT);
		this.canvas.push();
		this.canvas.translate((width - REFERENCE_WIDTH * scale) / 2.0F, (height - REFERENCE_HEIGHT * scale) / 2.0F);
		this.canvas.scale(scale);

		Mask.get("logo").draw(this.canvas, 859, 311, WHITE, content);
		Mask.get("title").draw(this.canvas, 655, 583, WHITE, content);

		float bar = content * info.barAlpha();
		if (bar > 0.01F) {
			this.progressBar(progress, bar);
			Mask status = Mask.get(info.german() ? "status-de" : "status-en");
			status.draw(this.canvas, info.german() ? 858 : (REFERENCE_WIDTH - status.width) / 2, 852, STATUS, bar);
		}
		this.canvas.pop();
	}

	/** Weißer Rahmen, Abstand, weiße Füllung; ohne bekannten Fortschritt gleitet ein Stück hin und her. */
	private void progressBar(float progress, float alpha) {
		int color = NovaDraw.fade(0xFF000000 | WHITE, alpha);
		int x2 = BAR_X + BAR_WIDTH;
		int y2 = BAR_Y + BAR_HEIGHT;
		this.canvas.fill(BAR_X, BAR_Y, x2, BAR_Y + BAR_BORDER, color);
		this.canvas.fill(BAR_X, y2 - BAR_BORDER, x2, y2, color);
		this.canvas.fill(BAR_X, BAR_Y + BAR_BORDER, BAR_X + BAR_BORDER, y2 - BAR_BORDER, color);
		this.canvas.fill(x2 - BAR_BORDER, BAR_Y + BAR_BORDER, x2, y2 - BAR_BORDER, color);

		int inset = BAR_BORDER + BAR_GAP;
		int innerX = BAR_X + inset;
		int innerY = BAR_Y + inset;
		int innerWidth = BAR_WIDTH - 2 * inset;
		int innerHeight = BAR_HEIGHT - 2 * inset;
		if (progress < 0.0F) {
			int segment = innerWidth / 4;
			double phase = (System.currentTimeMillis() % 1800L) / 1800.0;
			int offset = (int) Math.round((0.5 - 0.5 * Math.cos(phase * 2.0 * Math.PI)) * (innerWidth - segment));
			this.canvas.fill(innerX + offset, innerY, innerX + offset + segment, innerY + innerHeight, color);
		} else {
			int filled = Math.round(innerWidth * NovaDraw.clamp(progress, 0.0F, 1.0F));
			this.canvas.fill(innerX, innerY, innerX + filled, innerY + innerHeight, color);
		}
	}
}
