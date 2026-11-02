import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;

/**
 * Masken für den Ladebildschirm der Mod (mod/core/src/main/resources/assets/nova/loading/*.mask).
 * Beim Laden gibt es noch keine Schriften und Texturen, darum zeichnet die Mod Logo und Texte als Deckungs-Masken:
 * Zeile "breite höhe", dann je Bildzeile "x,länge,deckung" (Deckung 1–15) durch Leerzeichen getrennt.
 *
 *   logo.mask        weißes Wappen (tools/branding/emblem-white.png) in 202 × 218
 *   title.mask       "NOVA CLIENT" aus dem Entwurf (tools/branding/loading-mockup.png)
 *   status-de.mask   "Lade Ressourcen ..." aus dem Entwurf
 *   status-en.mask   "Loading resources ..." in ähnlicher Schrift, gleiche Größe
 *
 *   java tools/branding/LoadingMasks.java
 */
public class LoadingMasks {
	/** Hintergrund im Entwurf (#6D28D9): Grünkanal 0x28. */
	private static final int BACKGROUND_GREEN = 0x28;

	public static void main(String[] args) throws IOException {
		File root = new File(args.length > 0 ? args[0] : ".");
		File out = new File(root, "mod/core/src/main/resources/assets/nova/loading");
		out.mkdirs();
		BufferedImage mockup = ImageIO.read(new File(root, "tools/branding/loading-mockup.png"));

		BufferedImage emblem = ImageIO.read(new File(root, "tools/branding/emblem-white.png"));
		write(alphaMask(scale(crop(emblem), 202, 218)), new File(out, "logo.mask"));
		// Weiß auf Lila: Deckung aus dem Grünkanal (Hintergrund 0x28, Weiß 0xFF)
		write(fromMockup(mockup, 655, 583, 614, 55, 0xFF), new File(out, "title.mask"));
		// Statustext #D3BFF4: Grün 0xBF
		write(fromMockup(mockup, 858, 852, 202, 24, 0xBF), new File(out, "status-de.mask"));
		write(rendered("Loading resources ...", 193, 24), new File(out, "status-en.mask"));
	}

	private static int[][] fromMockup(BufferedImage image, int x, int y, int width, int height, int fullGreen) {
		int[][] mask = new int[height][width];
		for (int row = 0; row < height; row++) {
			for (int column = 0; column < width; column++) {
				int green = image.getRGB(x + column, y + row) >> 8 & 0xFF;
				float coverage = (green - BACKGROUND_GREEN) / (float) (fullGreen - BACKGROUND_GREEN);
				mask[row][column] = Math.max(0, Math.min(15, Math.round(coverage * 15)));
			}
		}
		return mask;
	}

	/** Text mit Kantenglättung in eine Maske, Schriftgröße so, dass die Breite passt. */
	private static int[][] rendered(String text, int targetWidth, int height) {
		Font font = new Font("Segoe UI", Font.PLAIN, 20);
		for (float size = 12; size < 40; size += 0.25F) {
			Font candidate = new Font("Segoe UI", Font.PLAIN, 1).deriveFont(size);
			BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
			int width = probe.createGraphics().getFontMetrics(candidate).stringWidth(text);
			if (width > targetWidth) {
				break;
			}
			font = candidate;
		}
		BufferedImage image = new BufferedImage(targetWidth + 20, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
		g.setFont(font);
		g.setColor(java.awt.Color.WHITE);
		g.drawString(text, 5, height - 6);
		g.dispose();
		return alphaMask(image);
	}

	private static int[][] alphaMask(BufferedImage image) {
		int[][] mask = new int[image.getHeight()][image.getWidth()];
		for (int row = 0; row < image.getHeight(); row++) {
			for (int column = 0; column < image.getWidth(); column++) {
				mask[row][column] = Math.round((image.getRGB(column, row) >>> 24) * 15 / 255.0F);
			}
		}
		return mask;
	}

	private static void write(int[][] mask, File file) throws IOException {
		try (PrintWriter out = new PrintWriter(file, StandardCharsets.UTF_8)) {
			out.println(mask[0].length + " " + mask.length);
			for (int[] row : mask) {
				StringBuilder line = new StringBuilder();
				int column = 0;
				while (column < row.length) {
					int level = row[column];
					int run = 1;
					while (column + run < row.length && row[column + run] == level) {
						run++;
					}
					if (level > 0) {
						line.append(line.length() > 0 ? " " : "").append(column).append(',').append(run).append(',').append(level);
					}
					column += run;
				}
				out.println(line);
			}
		}
		System.out.println(file + " " + mask[0].length + "x" + mask.length);
	}

	private static BufferedImage crop(BufferedImage image) {
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
		return image.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1);
	}

	private static BufferedImage scale(BufferedImage image, int width, int height) {
		BufferedImage current = image;
		while (current.getWidth() / 2 >= width && current.getHeight() / 2 >= height) {
			current = resize(current, current.getWidth() / 2, current.getHeight() / 2);
		}
		return resize(current, width, height);
	}

	private static BufferedImage resize(BufferedImage image, int width, int height) {
		BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g.drawImage(image, 0, 0, width, height, null);
		g.dispose();
		return out;
	}
}
