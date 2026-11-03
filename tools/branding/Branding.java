import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * Erzeugt alle Logo-Dateien aus dem Wappen (tools/branding/emblem.png):
 *   src-tauri/app-icon.png                 1024², weißes Wappen auf lila Quadrat (wie der Ladebildschirm),
 *                                          Quelle für "npx tauri icon"
 *   public/favicon.png                     256², Browser-/Fenster-Icon
 *   src/assets/nova-logo.png               512 hoch, Launcher-Kopfzeile und Willkommensbildschirm
 *   mod/.../textures/gui/logo.png          256 hoch, Ladebildschirm und Hauptmenü der Mod
 *                                          (Größe muss zu NovaDraw.LOGO_WIDTH/LOGO_HEIGHT passen)
 *   mod/.../assets/nova/icon.png           128², Mod-Icon (Mod-Liste)
 *
 *   java tools/branding/Branding.java
 */
public class Branding {
	public static void main(String[] args) throws IOException {
		File root = new File(args.length > 0 ? args[0] : ".");
		BufferedImage emblem = crop(ImageIO.read(new File(root, "tools/branding/emblem.png")));

		BufferedImage white = crop(ImageIO.read(new File(root, "tools/branding/emblem-white.png")));
		write(appIcon(white, 1024), new File(root, "src-tauri/app-icon.png"));
		write(square(emblem, 256, 0.04), new File(root, "public/favicon.png"));
		write(square(emblem, 128, 0.04), new File(root, "mod/core/src/main/resources/assets/nova/icon.png"));
		write(byHeight(emblem, 512), new File(root, "src/assets/nova-logo.png"));
		write(byHeight(emblem, 256), new File(root, "mod/core/src/main/resources/assets/nova/textures/gui/logo.png"));
	}

	static BufferedImage byHeight(BufferedImage image, int height) {
		return scale(image, Math.round(image.getWidth() * (float) height / image.getHeight()), height);
	}

	/** Lila Quadrat mit runden Ecken (#6D28D9 wie der Ladebildschirm), weißes Wappen in der Mitte. */
	static BufferedImage appIcon(BufferedImage emblem, int size) {
		BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = graphics(out);
		g.setColor(new java.awt.Color(0x6D28D9));
		int radius = Math.round(size * 0.22F);
		g.fillRoundRect(0, 0, size, size, radius * 2, radius * 2);
		BufferedImage inner = square(emblem, Math.round(size * 0.66F), 0.0);
		// Wappen optisch mittig: der Stern ragt nach rechts oben, darum leicht nach links unten
		int offset = (size - inner.getWidth()) / 2;
		g.drawImage(inner, offset - Math.round(size * 0.01F), offset + Math.round(size * 0.015F), null);
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
