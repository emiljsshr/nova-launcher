package gg.nova.core.loading;

import gg.nova.core.NovaCanvas;
import gg.nova.core.NovaDraw;
import gg.nova.core.NovaScenery;
import gg.nova.core.NovaSprites;
import gg.nova.core.PixelFont;

/**
 * Nova-Ladebildschirm: Nachthimmel mit Polarlicht, Logo, Fortschrittsbalken, Ladephasen und Tipps.
 * Zeichnet nur mit {@link PixelFont}, weil Minecrafts Schriften beim Start noch nicht geladen sind.
 */
public final class NovaLoadingRenderer {
	private static final long TIP_DURATION = 6000L;

	/** Echte Minecraft-Tastenkürzel: Taste und Text (DE, EN). */
	private static final String[][] TIPS = {
		{"F3 + T", "LÄDT RESSOURCENPAKETE NEU", "RELOADS RESOURCE PACKS"},
		{"F2", "SPEICHERT EINEN SCREENSHOT", "SAVES A SCREENSHOT"},
		{"F11", "WECHSELT IN DEN VOLLBILDMODUS", "TOGGLES FULLSCREEN"},
		{"F1", "BLENDET DIE BENUTZEROBERFLÄCHE AUS", "HIDES THE INTERFACE"},
		{"F3 + B", "ZEIGT HITBOXEN AN", "SHOWS HITBOXES"},
		{"F5", "WECHSELT DIE KAMERAPERSPEKTIVE", "SWITCHES THE CAMERA VIEW"},
	};

	/** Größe eines Schrift-Pixels in GUI-Pixeln – feine Schrift wie im Design. */
	private final float textScale;
	private final NovaCanvas canvas;

	private NovaLoadingRenderer(NovaCanvas canvas) {
		this.canvas = canvas;
		// Kleine Fenster: volle GUI-Pixel, sonst halbe – bleibt immer lesbar
		this.textScale = canvas.height() < 220 ? 1.0F : 0.5F;
	}

	public static void render(NovaCanvas canvas, LoadingInfo info) {
		new NovaLoadingRenderer(canvas).draw(info);
	}

	/* ========================================
	   TEXT-HELFER (skalierte Pixelschrift)
	======================================== */

	private int textWidth(String text, int spacing) {
		return Math.round(PixelFont.width(text, spacing) * this.textScale);
	}

	private int textHeight() {
		return Math.round(PixelFont.HEIGHT * this.textScale);
	}

	private void text(String text, float x, float y, int color, int spacing) {
		this.canvas.push();
		this.canvas.translate(x, y);
		this.canvas.scale(this.textScale);
		PixelFont.draw(this.canvas, text, 0, 0, color, spacing);
		this.canvas.pop();
	}

	private void textRight(String text, float right, float y, int color, int spacing) {
		this.text(text, right - this.textWidth(text, spacing), y, color, spacing);
	}

	private void textCentered(String text, float centerX, float y, int color, int spacing) {
		this.text(text, centerX - this.textWidth(text, spacing) / 2.0F, y, color, spacing);
	}

	/* ========================================
	   AUFBAU
	======================================== */

	private void draw(LoadingInfo info) {
		int width = this.canvas.width();
		int height = this.canvas.height();
		float background = info.backgroundAlpha();
		float content = info.contentAlpha();
		// Minecrafts Fortschritt läuft nach – ist das Laden fertig, zeigen wir 100 %.
		// Negativ = Fortschritt unbekannt (1.8.9 meldet keinen Ladefortschritt).
		float progress = info.complete() ? 1.0F : info.progress();

		NovaScenery.sky(this.canvas, 0, 0, width, height, background);
		NovaScenery.aurora(this.canvas, width, height, background);
		NovaScenery.stars(this.canvas, 0, 0, width, (int) (height * 0.78F), 1312, 0.9F, background);
		int landHeight = (int) (height * 0.27F);
		NovaScenery.landscape(this.canvas, 0, height - landHeight, width, landHeight, background);
		NovaScenery.snow(this.canvas, width, height, Math.max(30, width * height / 3000), background);

		if (content <= 0.01F) {
			return;
		}

		// Logo mit weichem, ovalem Schein dahinter
		int logoHeight = Math.min((int) (height * 0.24F), Math.min(120, (int) (width * 0.3F)));
		int logoWidth = NovaDraw.logoWidth(logoHeight);
		int logoX = (width - logoWidth) / 2;
		int logoY = (int) (height * 0.34F) - logoHeight / 2;
		this.glow(width / 2, logoY + logoHeight / 2, logoHeight * 2, background);
		NovaDraw.logo(this.canvas, logoX, logoY, logoWidth, content);

		int subtitleY = logoY + logoHeight + 6;
		this.textCentered("CLIENT · FROST SEASON", width / 2.0F, subtitleY, NovaDraw.fade(NovaDraw.FROST, content), 4);

		int barWidth = Math.min((int) (width * 0.38F), 340);
		int barX = (width - barWidth) / 2;
		int barY = subtitleY + this.textHeight() + Math.max(12, height / 18);
		this.progressBar(barX, barY, barWidth, progress, info.barAlpha());

		int labelY = barY + 10;
		this.text(stageLabel(info), barX, labelY, NovaDraw.fade(NovaDraw.SOFT, info.barAlpha()), 2);
		if (progress >= 0.0F) {
			this.textRight(Math.round(progress * 100) + "%", barX + barWidth, labelY, NovaDraw.fade(NovaDraw.FROST, info.barAlpha()), 2);
		}

		this.stages(width / 2, labelY + this.textHeight() + 10, info, content);
		this.corners(info, width, content);
		this.tip(info, width, height - landHeight / 2, content);
		this.footer(info, width, height, content);
	}

	/** Java-Hauptversion, auch unter Java 8 ("1.8" → 8). */
	private static int javaVersion() {
		String spec = System.getProperty("java.specification.version", "8");
		String major = spec.startsWith("1.") ? spec.substring(2) : spec;
		try {
			return Integer.parseInt(major.replaceAll("\\D.*", ""));
		} catch (NumberFormatException exception) {
			return 8;
		}
	}

	private static String stageLabel(LoadingInfo info) {
		if (info.complete()) {
			return info.german() ? "FERTIG" : "DONE";
		}
		if (info.reloading()) {
			return info.german() ? "RESSOURCENPAKETE WERDEN NEU GELADEN" : "RELOADING RESOURCE PACKS";
		}
		return info.german() ? "RESSOURCEN WERDEN GELADEN" : "LOADING RESOURCES";
	}

	/** Ovaler Schein aus Zeilen – mehrere Schichten mit geringer Deckkraft ergeben einen weichen Rand. */
	private void glow(int centerX, int centerY, int size, float alpha) {
		for (int ring = 0; ring < 8; ring++) {
			int radius = size * (12 - ring) / 20;
			int radiusY = radius / 2;
			for (int row = -radiusY; row <= radiusY; row++) {
				float t = row / (float) radiusY;
				int half = (int) (radius * Math.sqrt(1.0F - t * t));
				this.canvas.fill(centerX - half, centerY + row, centerX + half, centerY + row + 1, NovaDraw.fade(0x065E5E66, alpha));
			}
		}
	}

	private void progressBar(int x, int y, int width, float progress, float alpha) {
		if (alpha <= 0.01F) {
			return;
		}
		int height = 5;
		NovaDraw.panel(this.canvas, x - 2, y - 2, width + 4, height + 4, NovaDraw.fade(0xFF37373E, alpha));
		this.canvas.fill(x - 1, y - 1, x + width + 1, y + height + 1, NovaDraw.fade(0xFF17171D, alpha));

		int segment = 4;
		int gap = 1;
		int segments = (width + gap) / (segment + gap);
		int filled = (int) Math.ceil(segments * NovaDraw.clamp(progress, 0.0F, 1.0F));
		// Unbekannter Fortschritt (z. B. 1.8.9): ein Block aus Segmenten wandert durch den Balken
		boolean unknown = progress < 0.0F;
		int runStart = (int) ((System.currentTimeMillis() / 90L) % (segments + 6)) - 6;
		for (int index = 0; index < segments; index++) {
			int segmentX = x + index * (segment + gap);
			int color;
			if (unknown) {
				color = index >= runStart && index < runStart + 6 ? NovaDraw.FROST : 0xFF24242A;
			} else if (index < filled - 1) {
				color = NovaDraw.FROST;
			} else if (index == filled - 1) {
				// Vorderstes Segment pulsiert
				float pulse = 0.55F + 0.45F * (float) Math.abs(Math.sin(System.currentTimeMillis() / 300.0));
				color = NovaDraw.fade(NovaDraw.FROST_DEEP, pulse);
			} else {
				color = 0xFF24242A;
			}
			this.canvas.fill(segmentX, y, segmentX + segment, y + height, NovaDraw.fade(color, alpha));
		}
	}

	/** Ladephasen: abgeschlossen (grün), aktiv (blau), ausstehend (grau). */
	private void stages(int centerX, int y, LoadingInfo info, float alpha) {
		boolean german = info.german();
		String[] labels = info.reloading()
			? new String[] {german ? "RESSOURCENPAKETE" : "RESOURCE PACKS", german ? "SPIEL" : "GAME"}
			: new String[] {"JAVA", "MODS · " + info.modCount(), german ? "RESSOURCEN" : "RESOURCES", german ? "MENÜ" : "MENU"};
		int activeIndex = info.reloading() ? (info.complete() ? 1 : 0) : (info.complete() ? 3 : 2);

		int square = Math.max(3, this.textHeight());
		int gap = 12;
		int total = 0;
		for (String label : labels) {
			total += square + 4 + this.textWidth(label, 2);
		}
		total += gap * (labels.length - 1);

		int cursor = centerX - total / 2;
		for (int index = 0; index < labels.length; index++) {
			boolean done = index < activeIndex;
			boolean active = index == activeIndex;
			int squareColor = done ? NovaDraw.ONLINE : active ? NovaDraw.FROST : 0xFF3C3C43;
			int textColor = done || active ? NovaDraw.SOFT : 0xFF5E5E66;
			this.canvas.fill(cursor, y, cursor + square, y + square, NovaDraw.fade(squareColor, alpha));
			this.text(labels[index], cursor + square + 4, y, NovaDraw.fade(textColor, alpha), 2);
			cursor += square + 4 + this.textWidth(labels[index], 2) + gap;
		}
	}

	private void corners(LoadingInfo info, int width, float alpha) {
		long maxMemory = Runtime.getRuntime().maxMemory() / (1024L * 1024L);
		String memory = maxMemory >= 1024 ? Math.round(maxMemory / 1024.0F) + " GB RAM" : maxMemory + " MB RAM";
		this.text("JAVA " + javaVersion() + " · " + memory, 12, 12, NovaDraw.fade(NovaDraw.MUTED, alpha), 2);
		this.text(info.loaderName() + " " + info.loaderVersion(), 12, 12 + this.textHeight() + 4, NovaDraw.fade(NovaDraw.SOFT, alpha), 2);

		// Lade-Indikator oben rechts: hüpfender Schneeblock
		String status = info.complete()
			? (info.german() ? "BEREIT" : "READY")
			: (info.german() ? "LADE RESSOURCEN" : "LOADING RESOURCES");
		int blockScale = this.textScale < 1.0F ? 1 : 2;
		int blockX = width - 12 - NovaSprites.SNOW_BLOCK.width() * blockScale;
		int bob = info.complete() ? 0 : (int) Math.round(Math.sin(System.currentTimeMillis() / 250.0) * 1.5);
		NovaSprites.SNOW_BLOCK.draw(this.canvas, blockX, 10 + bob, blockScale, alpha);
		this.textRight(status, blockX - 6, 10 + (NovaSprites.SNOW_BLOCK.height() * blockScale - this.textHeight()) / 2.0F, NovaDraw.fade(NovaDraw.MUTED, alpha), 2);
	}

	private void tip(LoadingInfo info, int width, int centerY, float alpha) {
		String[] tip = TIPS[(int) ((System.currentTimeMillis() / TIP_DURATION) % TIPS.length)];
		String badge = info.german() ? "TIPP" : "TIP";
		String text = info.german() ? tip[1] : tip[2];

		int padding = 4;
		int badgeWidth = this.textWidth(badge, 2) + padding * 2;
		int keyWidth = this.textWidth(tip[0], 1) + padding * 2;
		int boxWidth = padding + badgeWidth + 6 + keyWidth + 6 + this.textWidth(text, 1) + padding * 2;
		int boxHeight = this.textHeight() + 12;
		int x = (width - boxWidth) / 2;
		int y = centerY - boxHeight / 2;
		float textY = y + (boxHeight - this.textHeight()) / 2.0F;

		NovaDraw.panel(this.canvas, x, y, boxWidth, boxHeight, NovaDraw.fade(0xE617171D, alpha));
		int cursor = x + padding;
		this.canvas.fill(cursor, y + 3, cursor + badgeWidth, y + boxHeight - 3, NovaDraw.fade(NovaDraw.FROST, alpha));
		this.text(badge, cursor + padding, textY, NovaDraw.fade(NovaDraw.INK, alpha), 2);
		cursor += badgeWidth + 6;
		this.canvas.fill(cursor, y + 3, cursor + keyWidth, y + boxHeight - 3, NovaDraw.fade(0xFF37373E, alpha));
		this.text(tip[0], cursor + padding, textY, NovaDraw.fade(NovaDraw.WHITE, alpha), 1);
		cursor += keyWidth + 6;
		this.text(text, cursor, textY, NovaDraw.fade(NovaDraw.SOFT, alpha), 1);
	}

	private void footer(LoadingInfo info, int width, int height, float alpha) {
		float y = height - 8 - this.textHeight();
		this.text("NOVA CLIENT " + info.novaVersion() + " · MINECRAFT " + info.minecraftVersion(), 12, y, NovaDraw.fade(NovaDraw.MUTED, alpha), 2);
		String unofficial = info.german() ? "NICHT OFFIZIELL MIT MOJANG VERBUNDEN" : "NOT AN OFFICIAL MINECRAFT PRODUCT";
		this.textRight(unofficial, width - 12, y, NovaDraw.fade(NovaDraw.MUTED, alpha), 2);
	}
}
