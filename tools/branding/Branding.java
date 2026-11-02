import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * Erzeugt alle Logo-Dateien aus dem Wappen (tools/branding/emblem.png) und dem Schriftzug
 * (tools/branding/wordmark.png, 590 × 181):
 *   src-tauri/app-icon.png                 1024², Quelle für "npx tauri icon"
 *   public/favicon.png                     256², Browser-/Fenster-Icon
 *   src/assets/nova-logo.png               1180 × 362, Wappen + Schriftzug (Launcher-Kopfzeile)
 *   mod/.../textures/gui/logo.png          590 × 181, dasselbe für Ladebildschirm und Hauptmenü der Mod
 *   mod/.../assets/nova/icon.png           128², Mod-Icon (Mod-Liste)
 *
 *   java tools/branding/Branding.java
 */
public class Branding {
	public static void main(String[] args) throws IOException {
		File root = new File(args.length > 0 ? args[0] : ".");
		BufferedImage emblem = crop(ImageIO.read(new File(root, "tools/branding/emblem.png")));
		BufferedImage wordmark = crop(ImageIO.read(new File(root, "tools/branding/wordmark.png")));

		write(square(emblem, 1024, 0.06), new File(root, "src-tauri/app-icon.png"));
		write(square(emblem, 256, 0.04), new File(root, "public/favicon.png"));
		write(square(emblem, 128, 0.04), new File(root, "mod/core/src/main/resources/assets/nova/icon.png"));
		write(lockup(emblem, wordmark, 1180, 362), new File(root, "src/assets/nova-logo.png"));
		write(lockup(emblem, wordmark, 590, 181), new File(root, "mod/core/src/main/resources/assets/nova/textures/gui/logo.png"));
	}

	/** Wappen links in voller Höhe, Schriftzug rechts daneben, senkrecht mittig. */
	static BufferedImage lockup(BufferedImage emblem, BufferedImage wordmark, int width, int height) {
		BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = graphics(out);
		int emblemHeight = height;
		int emblemWidth = Math.round(emblem.getWidth() * (float) emblemHeight / emblem.getHeight());
		g.drawImage(scale(emblem, emblemWidth, emblemHeight), 0, 0, null);
		int gap = Math.round(height * 0.08F);
		int textWidth = width - emblemWidth - gap;
		int textHeight = Math.round(wordmark.getHeight() * (float) textWidth / wordmark.getWidth());
		g.drawImage(scale(wordmark, textWidth, textHeight), emblemWidth + gap, (height - textHeight) / 2, null);
		g.dispose();
		return out;
	}

	/** Mittig in ein transparentes Quadrat mit Rand ({@code margin} als Anteil der Kante). */
	static BufferedImage square(BufferedImage image, int size, double margin) {
		BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		int inner = (int) Math.round(size * (1 - 2 * margin));
		float factor = Math.min(inner / (float) image.getWidth(), inner / (float) image.getHeight());
		int width = Math.round(image.getWidth() * factor);
		int height = Math.round(image.getHeight() * factor);
		Graphics2D g = graphics(out);
		g.drawImage(scale(image, width, height), (size - width) / 2, (size - height) / 2, null);
		g.dispose();
		return out;
	}

	/** Schrittweise halbieren, dann auf Zielgröße – bleibt scharf ohne Treppen. */
	static BufferedImage scale(BufferedImage image, int width, int height) {
		BufferedImage current = image;
		while (current.getWidth() / 2 >= width && current.getHeight() / 2 >= height) {
			current = resize(current, current.getWidth() / 2, current.getHeight() / 2);
		}
		return resize(current, width, height);
	}

	static BufferedImage resize(BufferedImage image, int width, int height) {
		BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = graphics(out);
		g.drawImage(image, 0, 0, width, height, null);
		g.dispose();
		return out;
	}

	/** Auf die nicht transparenten Pixel zuschneiden. */
	static BufferedImage crop(BufferedImage image) {
		int minX = image.getWidth(), minY = image.getHeight(), maxX = -1, maxY = -1;
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				if ((image.getRGB(x, y) >>> 24) > 8) {
					minX = Math.min(minX, x);
					minY = Math.min(minY, y);
					maxX = Math.max(maxX, x);
					maxY = Math.max(maxY, y);
				}
			}
		}
		BufferedImage out = new BufferedImage(maxX - minX + 1, maxY - minY + 1, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		g.drawImage(image, -minX, -minY, null);
		g.dispose();
		return out;
	}

	static Graphics2D graphics(BufferedImage image) {
		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		return g;
	}

	static void write(BufferedImage image, File file) throws IOException {
		file.getParentFile().mkdirs();
		ImageIO.write(image, "png", file);
		System.out.println(file + " " + image.getWidth() + "x" + image.getHeight());
	}
}
