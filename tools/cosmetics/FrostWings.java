import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import javax.imageio.ImageIO;

/**
 * Erzeugt die Frostblitz-Schwingen (public/cosmetics/frost-wings/): HD-Textur und Leucht-Textur (Java2D),
 * Bedrock-Geometrie, Animation und cosmetic.json.
 *
 *   java tools/cosmetics/FrostWings.java public/cosmetics/frost-wings
 *
 * Aufbau je Flügel: ein Eisbogen aus gedrehten Segmenten mit Kristallzacken und Juwelen, darunter drei
 * Reihen Federn als dünne Flächen (Schwungfedern, Armschwingen, Deckfedern). Die Federform steckt in der
 * Transparenz der Textur. Der rechte Flügel ist der gespiegelte linke.
 */
public final class FrostWings {
	static final int TEX_W = 1024;
	static final int TEX_H = 512;

	// Atlas: x, y, Breite, Höhe in Texturpixeln
	static final int[][] PRIMARY = {{0, 0, 88, 360}, {88, 0, 88, 360}, {176, 0, 88, 360}};
	static final int[][] SECONDARY = {{264, 0, 88, 272}, {352, 0, 88, 272}, {440, 0, 88, 272}};
	static final int[][] COVERT = {{528, 0, 80, 136}, {608, 0, 80, 136}};
	static final int[] MARGINAL = {688, 0, 64, 80};
	static final int[] SILVER = {768, 0, 64, 64};
	static final int[] CRYSTAL = {832, 0, 64, 64};
	static final int[] SHARD = {896, 0, 64, 64};
	static final int[] SPARKLE = {960, 0, 64, 64};

	static final Color ICE_WHITE = new Color(0xf4f9ff);
	static final Color CYAN = new Color(0x6ff0ff);
	static final Color OUTLINE = new Color(0x183a85);

	public static void main(String[] args) throws IOException {
		File out = new File(args.length > 0 ? args[0] : "public/cosmetics/frost-wings");
		out.mkdirs();

		BufferedImage base = new BufferedImage(TEX_W, TEX_H, BufferedImage.TYPE_INT_ARGB);
		BufferedImage glow = new BufferedImage(TEX_W, TEX_H, BufferedImage.TYPE_INT_ARGB);
		Graphics2D b = setup(base);
		Graphics2D g = setup(glow);
		// Leucht-Textur: Farbe mit Deckkraft als Lichtstärke (transparent = kein Licht). So passt sie zu
		// transluzenter Mischung (Minecraft ab 1.21.3) wie zu additiver mit Alpha (1.8.9, Launcher)

		for (int i = 0; i < PRIMARY.length; i++) {
			feather(b, g, PRIMARY[i], Feather.PRIMARY, 11 + i);
		}
		for (int i = 0; i < SECONDARY.length; i++) {
			feather(b, g, SECONDARY[i], Feather.SECONDARY, 31 + i);
		}
		for (int i = 0; i < COVERT.length; i++) {
			feather(b, g, COVERT[i], Feather.COVERT, 51 + i);
		}
		feather(b, g, MARGINAL, Feather.MARGINAL, 71);
		silver(b, g, SILVER);
		crystal(b, g, CRYSTAL);
		shard(b, g, SHARD);
		sparkle(g, SPARKLE);
		b.dispose();
		g.dispose();

		ImageIO.write(base, "png", new File(out, "texture.png"));
		ImageIO.write(glow, "png", new File(out, "glow.png"));
		write(new File(out, "model.geo.json"), geometry());
		write(new File(out, "animation.json"), animation());
		write(new File(out, "cosmetic.json"), manifest());
		System.out.println("Frostblitz-Schwingen geschrieben nach " + out);
	}

	/* ========================================
	   TEXTUREN
	======================================== */

	enum Feather {
		PRIMARY(true, 0.92F), SECONDARY(true, 0.95F), COVERT(false, 1.0F), MARGINAL(false, 1.0F);

		final boolean lightning;
		final float fullness;

		Feather(boolean lightning, float fullness) {
			this.lightning = lightning;
			this.fullness = fullness;
		}
	}

	static Graphics2D setup(BufferedImage image) {
		Graphics2D graphics = image.createGraphics();
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
		graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		graphics.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
		return graphics;
	}

	static Color alpha(Color color, float alpha) {
		return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.round(Math.max(0, Math.min(1, alpha)) * 255));
	}

	/** Halbe Fahnenbreite entlang der Feder (s = 0 Ansatz, 1 Spitze), relativ zur maximalen Breite. */
	static double profile(double s, Feather type, boolean left) {
		if (s <= 0 || s >= 1) {
			return 0;
		}
		double shape = Math.pow(Math.sin(Math.PI * Math.pow(s, type == Feather.MARGINAL ? 0.55 : 0.7)), 0.5);
		// Kleine Einkerbungen, wo sich die Fahne teilt – wie bei echten Federn
		double notch = left ? 0.45 : 0.68;
		shape *= 1 - 0.14 * Math.exp(-Math.pow((s - notch) / 0.018, 2));
		return shape * (left ? 0.86 : 1.0) * type.fullness;
	}

	static void feather(Graphics2D base, Graphics2D glow, int[] r, Feather type, long seed) {
		Random random = new Random(seed);
		double x0 = r[0], y0 = r[1], w = r[2], h = r[3];
		double cx = x0 + w / 2;
		double maxHalf = w / 2 - 3;
		double vaneTop = y0 + h * (type == Feather.MARGINAL ? 0.04 : 0.07);
		double vaneBottom = y0 + h - 2;
		double vaneLength = vaneBottom - vaneTop;

		// Umriss der Fahne
		Path2D.Double vane = new Path2D.Double();
		int steps = 120;
		for (int i = 0; i <= steps; i++) {
			double s = (double) i / steps;
			double x = cx - maxHalf * profile(s, type, true);
			double y = vaneTop + s * vaneLength;
			if (i == 0) {
				vane.moveTo(x, y);
			} else {
				vane.lineTo(x, y);
			}
		}
		for (int i = steps; i >= 0; i--) {
			double s = (double) i / steps;
			vane.lineTo(cx + maxHalf * profile(s, type, false), vaneTop + s * vaneLength);
		}
		vane.closePath();

		// Grundverlauf: Eisweiß am Ansatz, tiefes Blau, leuchtend cyanfarbene Spitze
		float[] stops;
		Color[] colors;
		if (type.lightning) {
			stops = new float[] {0f, 0.2f, 0.45f, 0.7f, 0.88f, 0.96f, 1f};
			colors = new Color[] {
				new Color(0xeaf3ff), new Color(0xb9d4ff), new Color(0x7aa7f0), new Color(0x4378dc),
				new Color(0x2f5fc4), new Color(0x7fe3ff), new Color(0xc8fbff),
			};
		} else {
			stops = new float[] {0f, 0.35f, 0.75f, 0.92f, 1f};
			colors = new Color[] {
				new Color(0xffffff), new Color(0xd6e6ff), new Color(0x8fb5f2), new Color(0x7fd8ff), new Color(0xcff8ff),
			};
		}
		base.setPaint(new LinearGradientPaint(new Point2D.Double(cx, vaneTop), new Point2D.Double(cx, vaneBottom), stops, colors));
		base.fill(vane);

		Shape oldClip = base.getClip();
		base.setClip(vane);

		// Rundung: Ränder dunkler, links ein heller Glanzstreifen
		base.setPaint(new LinearGradientPaint(
			new Point2D.Double(cx - maxHalf, 0), new Point2D.Double(cx + maxHalf, 0),
			new float[] {0f, 0.22f, 0.42f, 0.5f, 0.75f, 1f},
			new Color[] {
				alpha(new Color(0x0e2a6b), 0.42f), alpha(Color.WHITE, 0.22f), alpha(Color.WHITE, 0f),
				alpha(Color.WHITE, 0f), alpha(new Color(0x0e2a6b), 0.12f), alpha(new Color(0x0e2a6b), 0.45f),
			}));
		base.fill(vane);

		// Äste (Barben): feine schräge Linien vom Kiel nach außen
		base.setStroke(new BasicStroke(1.0f));
		double spacing = type == Feather.MARGINAL ? 2.6 : 2.3;
		int line = 0;
		for (double y = vaneTop + 4; y < vaneBottom; y += spacing, line++) {
			double slope = w * 0.42;
			base.setColor(line % 2 == 0 ? alpha(Color.WHITE, 0.16f) : alpha(new Color(0x0c2460), 0.08f));
			base.draw(new java.awt.geom.Line2D.Double(cx, y, cx - maxHalf, y + slope));
			base.draw(new java.awt.geom.Line2D.Double(cx, y, cx + maxHalf, y + slope));
		}

		// Kiel: hell, nach unten schmaler
		Path2D.Double shaft = new Path2D.Double();
		double shaftTop = y0 + 1, shaftBottom = vaneTop + vaneLength * 0.93;
		double topHalf = w * 0.05, bottomHalf = 0.5;
		shaft.moveTo(cx - topHalf, shaftTop);
		shaft.lineTo(cx + topHalf, shaftTop);
		shaft.lineTo(cx + bottomHalf, shaftBottom);
		shaft.lineTo(cx - bottomHalf, shaftBottom);
		shaft.closePath();
		base.setClip(oldClip);
		base.setPaint(new LinearGradientPaint(new Point2D.Double(cx, shaftTop), new Point2D.Double(cx, shaftBottom),
			new float[] {0f, 1f}, new Color[] {new Color(0xffffff), new Color(0xcde6ff)}));
		base.fill(shaft);
		base.setColor(alpha(new Color(0x1c3f8f), 0.35f));
		base.setStroke(new BasicStroke(0.8f));
		base.draw(new java.awt.geom.Line2D.Double(cx + topHalf + 0.6, shaftTop + 4, cx + bottomHalf + 0.6, shaftBottom));

		// Kontur
		base.setColor(alpha(OUTLINE, 0.85f));
		base.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		base.draw(vane);

		// Leuchten: Spitze und unterer Rand
		Shape glowClip = glow.getClip();
		glow.setClip(vane);
		glow.setPaint(new LinearGradientPaint(
			new Point2D.Double(cx, vaneTop + vaneLength * 0.78), new Point2D.Double(cx, vaneBottom),
			new float[] {0f, 1f}, new Color[] {alpha(CYAN, 0f), alpha(CYAN, type.lightning ? 0.6f : 0.3f)}));
		glow.fill(vane);
		glow.setClip(glowClip);
		glow.setColor(alpha(CYAN, 0.07f));
		glow.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		glow.draw(vane);

		if (type.lightning) {
			lightning(base, glow, vane, cx, vaneTop, vaneLength, maxHalf, w, random, type);
		}
	}

	/** Frost-Blitz im Zickzack entlang der Feder, mit zwei kurzen Verästelungen. */
	static void lightning(Graphics2D base, Graphics2D glow, Shape vane, double cx, double vaneTop, double vaneLength,
			double maxHalf, double w, Random random, Feather type) {
		List<Point2D.Double> points = new ArrayList<>();
		double start = 0.16, end = 0.9;
		int segments = 7 + random.nextInt(3);
		int side = random.nextBoolean() ? 1 : -1;
		for (int i = 0; i <= segments; i++) {
			double s = start + (end - start) * i / segments + (random.nextDouble() - 0.5) * 0.02;
			double room = maxHalf * Math.min(profile(s, type, side < 0), profile(s, type, side > 0));
			double amplitude = room * (0.35 + random.nextDouble() * 0.3);
			double x = cx + (i == 0 || i == segments ? 0 : side * amplitude);
			points.add(new Point2D.Double(x, vaneTop + s * vaneLength));
			side = -side;
		}
		GeneralPath bolt = path(points);

		List<GeneralPath> branches = new ArrayList<>();
		for (int k = 0; k < 2; k++) {
			Point2D.Double from = points.get(2 + random.nextInt(points.size() - 4));
			double direction = from.x < cx ? -1 : 1;
			List<Point2D.Double> branch = new ArrayList<>();
			branch.add(from);
			branch.add(new Point2D.Double(from.x + direction * w * 0.12, from.y + w * 0.18));
			branch.add(new Point2D.Double(from.x + direction * w * 0.06, from.y + w * 0.32));
			branches.add(path(branch));
		}

		Shape clip = base.getClip();
		base.setClip(vane);
		stroke(base, bolt, w * 0.13, alpha(new Color(0x8eeeff), 0.55f));
		stroke(base, bolt, w * 0.055, new Color(0xf0feff));
		for (GeneralPath branch : branches) {
			stroke(base, branch, w * 0.07, alpha(new Color(0x8eeeff), 0.5f));
			stroke(base, branch, w * 0.03, new Color(0xe8fdff));
		}
		base.setClip(clip);

		// Leucht-Textur: weicher Lichthof um den Blitz, darf über die Feder hinausstrahlen
		stroke(glow, bolt, w * 0.6, alpha(CYAN, 0.05f));
		stroke(glow, bolt, w * 0.34, alpha(CYAN, 0.1f));
		stroke(glow, bolt, w * 0.16, alpha(CYAN, 0.32f));
		stroke(glow, bolt, w * 0.07, alpha(new Color(0xe8ffff), 0.85f));
		for (GeneralPath branch : branches) {
			stroke(glow, branch, w * 0.2, alpha(CYAN, 0.12f));
			stroke(glow, branch, w * 0.04, alpha(new Color(0xe8ffff), 0.7f));
		}
	}

	static GeneralPath path(List<Point2D.Double> points) {
		GeneralPath path = new GeneralPath();
		path.moveTo(points.get(0).x, points.get(0).y);
		for (int i = 1; i < points.size(); i++) {
			path.lineTo(points.get(i).x, points.get(i).y);
		}
		return path;
	}

	static void stroke(Graphics2D graphics, Shape shape, double width, Color color) {
		graphics.setColor(color);
		graphics.setStroke(new BasicStroke((float) width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_MITER, 4f));
		graphics.draw(shape);
	}

	/** Eisbogen: polierter Silberverlauf mit hellem Glanz. */
	static void silver(Graphics2D base, Graphics2D glow, int[] r) {
		Rectangle2D.Double rect = new Rectangle2D.Double(r[0], r[1], r[2], r[3]);
		base.setPaint(new LinearGradientPaint(new Point2D.Double(0, r[1]), new Point2D.Double(0, r[1] + r[3]),
			new float[] {0f, 0.3f, 0.5f, 0.75f, 1f},
			new Color[] {new Color(0xffffff), new Color(0xe4eefb), new Color(0xb3c8e6), new Color(0x8ea8d2), new Color(0xd9e8fb)}));
		base.fill(rect);
		base.setColor(alpha(Color.WHITE, 0.9f));
		base.fill(new Rectangle2D.Double(r[0], r[1] + r[3] * 0.18, r[2], r[3] * 0.08));
		glow.setPaint(new LinearGradientPaint(new Point2D.Double(0, r[1]), new Point2D.Double(0, r[1] + r[3]),
			new float[] {0f, 0.22f, 0.5f, 1f},
			new Color[] {alpha(Color.WHITE, 0.25f), alpha(Color.WHITE, 0.5f), alpha(CYAN, 0.08f), alpha(CYAN, 0.0f)}));
		glow.fill(rect);
	}

	/** Kristall-Juwel: Facetten in Cyan und Weiß. */
	static void crystal(Graphics2D base, Graphics2D glow, int[] r) {
		double x = r[0], y = r[1], s = r[2], c = s / 2;
		base.setColor(new Color(0x37b9f0));
		base.fill(new Rectangle2D.Double(x, y, s, s));
		Color[] facets = {new Color(0xa9f4ff), new Color(0x5fd8ff), new Color(0x2a8fd6), new Color(0x7fe6ff)};
		double[][] corners = {{x, y}, {x + s, y}, {x + s, y + s}, {x, y + s}};
		for (int i = 0; i < 4; i++) {
			GeneralPath facet = new GeneralPath();
			facet.moveTo(x + c, y + c);
			facet.lineTo(corners[i][0], corners[i][1]);
			facet.lineTo(corners[(i + 1) % 4][0], corners[(i + 1) % 4][1]);
			facet.closePath();
			base.setColor(facets[i]);
			base.fill(facet);
		}
		base.setPaint(new RadialGradientPaint(new Point2D.Double(x + c * 0.8, y + c * 0.8), (float) (s * 0.35),
			new float[] {0f, 1f}, new Color[] {alpha(Color.WHITE, 0.95f), alpha(Color.WHITE, 0f)}));
		base.fill(new Rectangle2D.Double(x, y, s, s));
		glow.setPaint(new RadialGradientPaint(new Point2D.Double(x + c, y + c), (float) (s * 0.75),
			new float[] {0f, 0.5f, 1f}, new Color[] {alpha(new Color(0xe8ffff), 1f), alpha(CYAN, 0.75f), alpha(CYAN, 0.35f)}));
		glow.fill(new Rectangle2D.Double(x, y, s, s));
	}

	/** Eiszacken: Weiß zur Spitze, Cyan zum Ansatz. */
	static void shard(Graphics2D base, Graphics2D glow, int[] r) {
		Rectangle2D.Double rect = new Rectangle2D.Double(r[0], r[1], r[2], r[3]);
		base.setPaint(new LinearGradientPaint(new Point2D.Double(0, r[1]), new Point2D.Double(0, r[1] + r[3]),
			new float[] {0f, 0.35f, 1f}, new Color[] {new Color(0xe8fdff), new Color(0x8ff0ff), new Color(0x3fbfef)}));
		base.fill(rect);
		glow.setPaint(new LinearGradientPaint(new Point2D.Double(0, r[1]), new Point2D.Double(0, r[1] + r[3]),
			new float[] {0f, 1f}, new Color[] {alpha(new Color(0xe8ffff), 0.95f), alpha(CYAN, 0.5f)}));
		glow.fill(rect);
	}

	/** Weicher Lichtpunkt für die Eiskristall-Partikel (nur Leucht-Textur). */
	static void sparkle(Graphics2D glow, int[] r) {
		double c = r[2] / 2.0;
		glow.setPaint(new RadialGradientPaint(new Point2D.Double(r[0] + c, r[1] + c), (float) c,
			new float[] {0f, 0.25f, 0.6f, 1f},
			new Color[] {Color.WHITE, alpha(new Color(0xbff8ff), 0.9f), alpha(CYAN, 0.3f), alpha(CYAN, 0f)}));
		glow.fill(new Ellipse2D.Double(r[0], r[1], r[2], r[3]));
	}

	/* ========================================
	   GEOMETRIE (Bedrock, Pixel, y oben, Rücken +Z)
	======================================== */

	/** Bogen des linken Flügels (+X) als kubische Bézierkurve in der Ebene hinter dem Rücken. */
	static final double[][] ARC = {{1.5, 20.5}, {6, 29}, {13.5, 35.5}, {22.5, 37.5}};
	static final double WRIST = 0.55;

	static double[] arc(double t) {
		double u = 1 - t;
		double x = u * u * u * ARC[0][0] + 3 * u * u * t * ARC[1][0] + 3 * u * t * t * ARC[2][0] + t * t * t * ARC[3][0];
		double y = u * u * u * ARC[0][1] + 3 * u * u * t * ARC[1][1] + 3 * u * t * t * ARC[2][1] + t * t * t * ARC[3][1];
		return new double[] {x, y};
	}

	/** Steigungswinkel des Bogens in Grad (0 = waagerecht nach außen). */
	static double arcAngle(double t) {
		double[] a = arc(Math.max(0, t - 0.01));
		double[] b = arc(Math.min(1, t + 0.01));
		return Math.toDegrees(Math.atan2(b[1] - a[1], b[0] - a[0]));
	}

	record Cube(double[] origin, double[] size, double[] pivot, double[] rotation, int[] uv, boolean plane) {
	}

	static List<Cube> inner = new ArrayList<>();
	static List<Cube> outer = new ArrayList<>();

	static void addFeather(double t, double length, double width, double angle, double drop, double z, int[] uv) {
		double[] a = arc(t);
		double ax = a[0], ay = a[1] - drop;
		Cube cube = new Cube(
			new double[] {ax - width / 2, ay - length, z},
			new double[] {width, length, 0},
			new double[] {ax, ay, z},
			new double[] {0, 0, angle},
			uv, true);
		(t < WRIST ? inner : outer).add(cube);
	}

	static void addBlock(double t, double[] origin, double[] size, double[] pivot, double[] rotation, int[] uv) {
		(t < WRIST ? inner : outer).add(new Cube(origin, size, pivot, rotation, uv, false));
	}

	static String geometry() {
		inner.clear();
		outer.clear();

		// Schwungfedern: lang, nach außen gefächert
		int primaries = 7;
		double[] primaryLength = {17, 19, 21, 22, 21, 19, 16};
		for (int i = 0; i < primaries; i++) {
			double t = 0.56 + 0.44 * i / (primaries - 1);
			addFeather(t, primaryLength[i], 5.2, 10 + i * 6, 0.5, 3.0 + i * 0.04, PRIMARY[i % 3]);
		}
		// Armschwingen: senkrecht nach unten bis zur Hüfte
		int secondaries = 8;
		for (int i = 0; i < secondaries; i++) {
			double t = 0.04 + 0.5 * i / (secondaries - 1);
			addFeather(t, 11 + 6 * i / (double) (secondaries - 1), 4.8, i * 1.4, 0.6, 3.3 + i * 0.04, SECONDARY[i % 3]);
		}
		// Deckfedern: kürzer, überdecken den Ansatz der großen Federn
		int coverts = 11;
		for (int i = 0; i < coverts; i++) {
			double t = 0.05 + 0.9 * i / (coverts - 1);
			addFeather(t, 6.5 + 1.8 * i / (coverts - 1), 4.2, 4 + i * 3.0, 0.4, 3.62 + i * 0.03, COVERT[i % 2]);
		}
		int marginals = 10;
		for (int i = 0; i < marginals; i++) {
			double t = 0.07 + 0.86 * i / (marginals - 1);
			addFeather(t, 4.0, 3.4, 5 + i * 2.8, 0.15, 3.98 + i * 0.02, MARGINAL);
		}

		// Eisbogen aus Segmenten, zur Spitze dünner
		int segments = 16;
		for (int i = 0; i < segments; i++) {
			double t0 = (double) i / segments, t1 = (double) (i + 1) / segments;
			double[] a = arc(t0), c = arc(t1);
			double length = Math.hypot(c[0] - a[0], c[1] - a[1]) + 0.2;
			double thickness = 1.6 - 0.75 * t0;
			addBlock(t0, new double[] {a[0], a[1] - thickness / 2, 3.25}, new double[] {length, thickness, 1.4},
				new double[] {a[0], a[1], 3.95}, new double[] {0, 0, Math.toDegrees(Math.atan2(c[1] - a[1], c[0] - a[0]))}, SILVER);
		}

		// Kristallzacken auf dem Bogen, leicht nach außen geneigt; die letzte verlängert die Spitze
		double[] shardAt = {0.16, 0.34, 0.52, 0.7, 0.86};
		for (int i = 0; i < shardAt.length; i++) {
			double t = shardAt[i];
			double[] a = arc(t);
			double height = 1.2 + i * 0.25;
			addBlock(t, new double[] {a[0] - 0.3, a[1], 3.65}, new double[] {0.6, height, 0.6},
				new double[] {a[0], a[1], 3.95}, new double[] {0, 45, arcAngle(t) - 25}, SHARD);
		}
		double[] tip = arc(1);
		addBlock(1, new double[] {tip[0] - 0.35, tip[1], 3.6}, new double[] {0.7, 2.4, 0.7},
			new double[] {tip[0], tip[1], 3.95}, new double[] {0, 45, arcAngle(1) - 70}, SHARD);

		// Juwelen: um 45° gedrehte Würfel, die vorne aus dem Bogen ragen
		double[] jewelAt = {0.08, 0.42, 0.78};
		for (double t : jewelAt) {
			double[] a = arc(t);
			double s = 1.5 - t * 0.4;
			addBlock(t, new double[] {a[0] - s / 2, a[1] - s / 2, 4.1}, new double[] {s, s, s},
				new double[] {a[0], a[1], 4.1 + s / 2}, new double[] {0, 0, 45}, CRYSTAL);
		}

		double[] wrist = arc(WRIST);
		StringBuilder json = new StringBuilder();
		json.append("{\n  \"format_version\": \"1.12.0\",\n  \"minecraft:geometry\": [\n    {\n");
		json.append("      \"description\": {\n");
		json.append("        \"identifier\": \"geometry.nova.frost_wings\",\n");
		json.append("        \"texture_width\": ").append(TEX_W).append(",\n");
		json.append("        \"texture_height\": ").append(TEX_H).append(",\n");
		json.append("        \"visible_bounds_width\": 5,\n        \"visible_bounds_height\": 4,\n");
		json.append("        \"visible_bounds_offset\": [0, 1.5, 0]\n      },\n");
		json.append("      \"bones\": [\n");
		json.append("        {\"name\": \"body\", \"pivot\": [0, 24, 0]},\n");
		// Halterung am Rücken mit Juwel
		json.append("        {\"name\": \"wings\", \"parent\": \"body\", \"pivot\": [0, 21, 3], \"cubes\": [\n");
		json.append("          ").append(cube(new Cube(new double[] {-1.5, 19.5, 2}, new double[] {3, 3, 2.2},
			new double[] {0, 21, 3}, new double[] {0, 0, 0}, SILVER, false), false)).append(",\n");
		json.append("          ").append(cube(new Cube(new double[] {-0.9, 20.1, 3.6}, new double[] {1.8, 1.8, 1.8},
			new double[] {0, 21, 4.5}, new double[] {0, 0, 45}, CRYSTAL, false), false)).append("\n");
		json.append("        ]},\n");
		bone(json, "wing_left", "wings", new double[] {1.5, 21, 3.6}, new double[] {0, -8, 0}, inner, false);
		json.append(",\n");
		bone(json, "wing_left_outer", "wing_left", new double[] {wrist[0], wrist[1], 3.6}, new double[] {0, 0, 0}, outer, false);
		json.append(",\n");
		bone(json, "wing_right", "wings", new double[] {-1.5, 21, 3.6}, new double[] {0, 8, 0}, inner, true);
		json.append(",\n");
		bone(json, "wing_right_outer", "wing_right", new double[] {-wrist[0], wrist[1], 3.6}, new double[] {0, 0, 0}, outer, true);
		json.append("\n      ]\n    }\n  ]\n}\n");
		return json.toString();
	}

	static void bone(StringBuilder json, String name, String parent, double[] pivot, double[] rotation, List<Cube> cubes, boolean mirror) {
		json.append("        {\"name\": \"").append(name).append("\", \"parent\": \"").append(parent).append("\", ");
		json.append("\"pivot\": ").append(vec(pivot)).append(", ");
		json.append("\"rotation\": ").append(vec(rotation)).append(", \"cubes\": [\n");
		for (int i = 0; i < cubes.size(); i++) {
			json.append("          ").append(cube(cubes.get(i), mirror)).append(i < cubes.size() - 1 ? ",\n" : "\n");
		}
		json.append("        ]}");
	}

	// Rechte Seite: an x = 0 gespiegelt, Drehungen um Y und Z umgekehrt
	static String cube(Cube cube, boolean mirror) {
		double[] origin = cube.origin().clone();
		double[] pivot = cube.pivot().clone();
		double[] rotation = cube.rotation().clone();
		if (mirror) {
			origin[0] = -(origin[0] + cube.size()[0]);
			pivot[0] = -pivot[0];
			rotation[1] = -rotation[1];
			rotation[2] = -rotation[2];
		}
		int[] r = cube.uv();
		String face = "{\"uv\": [" + r[0] + ", " + r[1] + "], \"uv_size\": [" + r[2] + ", " + r[3] + "]}";
		String uv = cube.plane()
			? "{\"north\": " + face + ", \"south\": " + face + "}"
			: "{\"north\": " + face + ", \"east\": " + face + ", \"south\": " + face + ", \"west\": " + face
				+ ", \"up\": " + face + ", \"down\": " + face + "}";
		return "{\"origin\": " + vec(origin) + ", \"size\": " + vec(cube.size()) + ", \"pivot\": " + vec(pivot)
			+ ", \"rotation\": " + vec(rotation) + ", \"uv\": " + uv + "}";
	}

	static String vec(double[] v) {
		return String.format(Locale.ROOT, "[%s, %s, %s]", num(v[0]), num(v[1]), num(v[2]));
	}

	static String num(double value) {
		String text = String.format(Locale.ROOT, "%.3f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
		return text.equals("-0") ? "0" : text;
	}

	/* ========================================
	   ANIMATION & MANIFEST
	======================================== */

	// Positives Y dreht die +X-Seite nach vorne, positives Z hebt sie an. Links schlägt also mit
	// negativem Y nach hinten; rechts ist gespiegelt.
	static String flap(double speed, double sweep, double lift, double lag) {
		String t = "query.anim_time";
		String inner = "math.sin(" + t + " * " + num(speed) + ")";
		String outer = "math.sin((" + t + " - " + num(lag) + ") * " + num(speed) + ")";
		return "{\n      \"loop\": true,\n      \"animation_length\": " + num(360 / speed) + ",\n      \"bones\": {\n"
			+ "        \"wings\": {\"position\": [0, \"math.sin(" + t + " * " + num(speed * 2) + ") * 0.25\", 0]},\n"
			+ "        \"wing_left\": {\"rotation\": [0, \"-" + num(sweep) + " - " + inner + " * " + num(sweep) + "\", \"" + inner + " * " + num(lift) + "\"]},\n"
			+ "        \"wing_right\": {\"rotation\": [0, \"" + num(sweep) + " + " + inner + " * " + num(sweep) + "\", \"-" + inner + " * " + num(lift) + "\"]},\n"
			+ "        \"wing_left_outer\": {\"rotation\": [0, \"-" + num(sweep * 0.6) + " - " + outer + " * " + num(sweep * 0.6) + "\", \"" + outer + " * " + num(lift * 1.5) + "\"]},\n"
			+ "        \"wing_right_outer\": {\"rotation\": [0, \"" + num(sweep * 0.6) + " + " + outer + " * " + num(sweep * 0.6) + "\", \"-" + outer + " * " + num(lift * 1.5) + "\"]}\n"
			+ "      }\n    }";
	}

	static String animation() {
		return "{\n  \"format_version\": \"1.8.0\",\n  \"animations\": {\n"
			+ "    \"animation.nova.frost_wings.idle\": " + flap(90, 6, 4, 0.35) + ",\n"
			+ "    \"animation.nova.frost_wings.fly\": " + flap(360, 14, 10, 0.1) + "\n"
			+ "  }\n}\n";
	}

	static String manifest() {
		return "{\n"
			+ "  \"id\": \"frost-wings\",\n"
			+ "  \"name\": \"Frostblitz-Schwingen\",\n"
			+ "  \"type\": \"WINGS\",\n"
			+ "  \"rarity\": \"LEGENDARY\",\n"
			+ "  \"description\": \"Glatte Federn mit Verlauf von Eisweiß zu tiefem Blau. Durch jede Schwungfeder zuckt ein leuchtender Frost-Blitz, am Flügelbogen sitzen Eiskristalle.\",\n"
			+ "  \"attach\": \"body\",\n"
			+ "  \"model\": \"model.geo.json\",\n"
			+ "  \"texture\": \"texture.png\",\n"
			+ "  \"glow\": {\n"
			+ "    \"texture\": \"glow.png\",\n"
			+ "    \"intensity\": \"0.78 + math.sin(query.anim_time * 180) * 0.22\"\n"
			+ "  },\n"
			+ "  \"sparkles\": {\n"
			+ "    \"count\": 34,\n"
			+ "    \"min\": [-30, 6, 2],\n"
			+ "    \"max\": [30, 46, 12],\n"
			+ "    \"size\": 1.6,\n"
			+ "    \"rise\": 3.5,\n"
			+ "    \"uv\": [" + SPARKLE[0] + ", " + SPARKLE[1] + ", " + SPARKLE[2] + ", " + SPARKLE[3] + "]\n"
			+ "  },\n"
			+ "  \"animationFile\": \"animation.json\",\n"
			+ "  \"animations\": {\n"
			+ "    \"idle\": \"animation.nova.frost_wings.idle\",\n"
			+ "    \"fly\": \"animation.nova.frost_wings.fly\"\n"
			+ "  }\n"
			+ "}\n";
	}

	static void write(File file, String text) throws IOException {
		Files.writeString(file.toPath(), text, StandardCharsets.UTF_8);
	}
}
