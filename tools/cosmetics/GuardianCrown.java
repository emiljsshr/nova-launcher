import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;

/**
 * Erzeugt die Krone „Purpur-Krone" (public/cosmetics/purpur-krone/): Pixeltextur und Leucht-Textur (128×128),
 * Bedrock-Geometrie und cosmetic.json. Passend zum Cape „Purpur-Wächter" (GuardianCape.java).
 *
 *   java tools/cosmetics/GuardianCrown.java public/cosmetics/purpur-krone
 *
 * Sitzt auf dem Kopf ("attach": "head"): ein Reif aus dunklem Pflaume-Metall mit roten Kanten und goldenen
 * Nieten, darüber gezackte Spitzen mit roten Enden (ausgeschnitten), innen ein Samtpolster in Lila, oben ein
 * goldener Knauf mit rotem Stein, vorne ein roter Edelstein. Die Steine und Spitzen glühen leicht.
 * Textur in vierfacher Auflösung wie beim Cape.
 */
public final class GuardianCrown {
	static final int TEX = 128;
	/** So weit sitzt die Krone tiefer als bündig auf dem Kopf (Pixel). */
	static final double DROP = 1;
	/** Texturpixel pro Modellpixel. */
	static final int SCALE = 4;

	// Rüstungs-Palette wie beim Cape
	static final int BLACK = 0x0f0b13;
	static final int[] PLUM = {0x251b2c, 0x2a1f31, 0x2f2337, 0x352841};
	static final int[] VELVET = {0x3a2846, 0x43304f, 0x4b3459};
	static final int LINING = 0x1a1220;
	static final int RED_DEEP = 0x5a1018;
	static final int RED_DARK = 0x8a1420;
	static final int RED = 0xb81c2a;
	static final int RED_BRIGHT = 0xe8323f;
	static final int RED_LIGHT = 0xff6a70;
	static final int GOLD = 0xf0b43c;
	static final int GOLD_DARK = 0xc88a22;

	// Atlas (x, y, Breite, Höhe in Texturpixeln)
	static final int[] BAND_LONG = {0, 0, 36, 12};
	static final int[] BAND_SHORT = {40, 0, 28, 12};
	static final int[] BAND_INNER = {72, 0, 36, 12};
	static final int[] BAND_EDGE = {112, 0, 4, 12};
	static final int[] BAND_TOP_LONG = {0, 14, 36, 4};
	static final int[] BAND_TOP_SHORT = {116, 0, 4, 28};
	static final int[] POINTS_LONG = {0, 20, 36, 16};
	static final int[] POINTS_SHORT = {40, 20, 28, 16};
	static final int[] VELVET_TOP = {0, 40, 28, 28};
	static final int[] VELVET_SIDE = {32, 40, 28, 8};
	static final int[] GEM = {64, 40, 8, 8};
	static final int[] ORB = {76, 40, 6, 6};

	public static void main(String[] args) throws IOException {
		File out = new File(args.length > 0 ? args[0] : "public/cosmetics/purpur-krone");
		out.mkdirs();

		BufferedImage base = new BufferedImage(TEX, TEX, BufferedImage.TYPE_INT_ARGB);
		BufferedImage glow = new BufferedImage(TEX, TEX, BufferedImage.TYPE_INT_ARGB);
		fill(base, BAND_LONG, (x, y) -> band(x, y, BAND_LONG[2]));
		fill(base, BAND_SHORT, (x, y) -> band(x, y, BAND_SHORT[2]));
		fill(base, BAND_INNER, (x, y) -> noise(x, y, 4) > 0.8 ? PLUM[0] : LINING);
		fill(base, BAND_EDGE, (x, y) -> y < 2 || y > 9 ? RED : RED_DARK);
		fill(base, BAND_TOP_LONG, (x, y) -> y == 0 ? RED_BRIGHT : RED_DARK);
		fill(base, BAND_TOP_SHORT, (x, y) -> x == 0 ? RED_BRIGHT : RED_DARK);
		fill(base, POINTS_LONG, (x, y) -> points(x, y, POINTS_LONG[2], new int[] {6, 18, 30}, new int[] {12, 16, 12}));
		fill(base, POINTS_SHORT, (x, y) -> points(x, y, POINTS_SHORT[2], new int[] {7, 21}, new int[] {13, 13}));
		fill(base, VELVET_TOP, (x, y) -> velvet(x, y, true));
		fill(base, VELVET_SIDE, (x, y) -> velvet(x, y, false));
		fill(base, GEM, GuardianCrown::gem);
		fill(base, ORB, (x, y) -> x + y < 4 ? GOLD : (x + y > 7 ? GOLD_DARK : 0xd89e2c));

		// Leuchten: Edelsteine und die roten Enden der Spitzen
		fill(glow, GEM, (x, y) -> 0xD8000000 | 0xff3a46);
		fill(glow, POINTS_LONG, (x, y) -> tipGlow(x, y, POINTS_LONG[2], new int[] {6, 18, 30}, new int[] {12, 16, 12}));
		fill(glow, POINTS_SHORT, (x, y) -> tipGlow(x, y, POINTS_SHORT[2], new int[] {7, 21}, new int[] {13, 13}));

		ImageIO.write(base, "png", new File(out, "texture.png"));
		ImageIO.write(glow, "png", new File(out, "glow.png"));
		write(new File(out, "model.geo.json"), geometry());
		write(new File(out, "cosmetic.json"), manifest());
		System.out.println("Purpur-Krone geschrieben nach " + out);
	}

	interface Painter {
		int color(int x, int y);
	}

	/** Füllt einen Atlasbereich; Rückgabe 0 = durchsichtig, Farben ohne Alpha werden deckend. */
	static void fill(BufferedImage image, int[] r, Painter painter) {
		for (int y = 0; y < r[3]; y++) {
			for (int x = 0; x < r[2]; x++) {
				int color = painter.color(x, y);
				if (color == 0) {
					continue;
				}
				image.setRGB(r[0] + x, r[1] + y, (color >>> 24) == 0 ? 0xFF000000 | color : color);
			}
		}
	}

	static double noise(int x, int y, int salt) {
		double value = Math.sin(x * 12.9898 + y * 78.233 + salt * 37.719) * 43758.5453;
		return value - Math.floor(value);
	}

	/* ========================================
	   TEXTUR
	======================================== */

	/** Reif: rote Kanten oben und unten, dunkles Metall, goldene Nieten alle 9 Pixel. */
	static int band(int x, int y, int width) {
		if (y <= 1) {
			return y == 0 ? RED_BRIGHT : RED;
		}
		if (y >= 10) {
			return y == 11 ? RED_DARK : RED;
		}
		if (y == 2 || y == 9) {
			return BLACK;
		}
		int stud = (x + 4) % 9;
		if ((stud == 0 || stud == 1) && (y == 5 || y == 6)) {
			return stud == 0 && y == 5 ? GOLD : GOLD_DARK;
		}
		return PLUM[(int) (noise(x, y, 1) * PLUM.length * 0.999)];
	}

	/** Höhe der Spitzen in Spalte x: Dreiecke um die Mitten, dazwischen 4 Pixel Sockel, in 2er-Stufen. */
	static int pointHeight(int x, int[] centers, int[] peaks) {
		int step = x / 2 * 2 + 1;
		int height = 4;
		for (int i = 0; i < centers.length; i++) {
			double h = peaks[i] - Math.abs(step - centers[i]) * (peaks[i] - 4) / 6.0;
			height = Math.max(height, (int) Math.round(h));
		}
		return height;
	}

	static int points(int x, int y, int width, int[] centers, int[] peaks) {
		int height = 16;
		int top = height - pointHeight(x, centers, peaks);
		if (y < top) {
			return 0;
		}
		boolean edge = y == top || y == top + 1 || y < height - 1 && height - pointHeight(x - 1, centers, peaks) > y
			|| height - pointHeight(x + 1, centers, peaks) > y;
		// Rote Enden der Spitzen, rote Kontur, darunter Metall; in jeder Spitze eine goldene Niete
		boolean tip = y < top + 3 && pointHeight(x, centers, peaks) > 8;
		if (tip) {
			return y == top ? RED_LIGHT : RED_BRIGHT;
		}
		if (edge) {
			return RED;
		}
		for (int c : centers) {
			if ((x == c - 1 || x == c) && (y == 10 || y == 11)) {
				return x == c - 1 && y == 10 ? GOLD : GOLD_DARK;
			}
		}
		return PLUM[(int) (noise(x, y, 2) * PLUM.length * 0.999)];
	}

	static int tipGlow(int x, int y, int width, int[] centers, int[] peaks) {
		int top = 16 - pointHeight(x, centers, peaks);
		if (y < top || pointHeight(x, centers, peaks) <= 8 || y >= top + 4) {
			return 0;
		}
		int alpha = y < top + 2 ? 0x90 : 0x40;
		return alpha << 24 | 0xff2a3a;
	}

	/** Samt: Lila mit weichen Falten (oben strahlenförmig zur Mitte). */
	static int velvet(int x, int y, boolean top) {
		double fold = top ? Math.sin(Math.atan2(y - 13.5, x - 13.5) * 8) : Math.sin(x * 0.9);
		int index = fold > 0.4 ? 2 : fold > -0.4 ? 1 : 0;
		return noise(x, y, 3) > 0.9 ? VELVET[Math.max(0, index - 1)] : VELVET[index];
	}

	/** Edelstein: rote Facetten, heller Kern. */
	static int gem(int x, int y) {
		double dx = Math.abs(x - 3.5), dy = Math.abs(y - 3.5);
		if (dx + dy < 1.5) {
			return RED_LIGHT;
		}
		if (dx + dy < 3.5) {
			return (x < 4) == (y < 4) ? RED_BRIGHT : RED;
		}
		return RED_DARK;
	}

	/* ========================================
	   GEOMETRIE (Bedrock, Pixel; Kopf von y 24 bis 32, x und z von -4 bis 4)
	======================================== */

	record Box(double[] origin, double[] size, String uv, double[] pivot, double[] rotation) {
	}

	static String face(int[] r) {
		return face(r[0], r[1], r[2], r[3]);
	}

	static String face(int u, int v, int w, int h) {
		return "{\"uv\": [" + u + ", " + v + "], \"uv_size\": [" + w + ", " + h + "]}";
	}

	static String faces(String... pairs) {
		StringBuilder json = new StringBuilder("{");
		for (int i = 0; i < pairs.length; i += 2) {
			json.append(i > 0 ? ", " : "").append("\"").append(pairs[i]).append("\": ").append(pairs[i + 1]);
		}
		return json.append("}").toString();
	}

	static String geometry() {
		List<Box> boxes = new ArrayList<>();
		String edge = face(BAND_EDGE);
		// Reif: vorne und hinten 9 breit, links und rechts dazwischen 7 tief; außen Motiv, innen Futter
		boxes.add(new Box(new double[] {-4.5, 31, -4.5}, new double[] {9, 3, 1},
			faces("north", face(BAND_LONG), "south", face(BAND_INNER), "east", edge, "west", edge,
				"up", face(BAND_TOP_LONG), "down", face(BAND_TOP_LONG)), null, null));
		boxes.add(new Box(new double[] {-4.5, 31, 3.5}, new double[] {9, 3, 1},
			faces("south", face(BAND_LONG), "north", face(BAND_INNER), "east", edge, "west", edge,
				"up", face(BAND_TOP_LONG), "down", face(BAND_TOP_LONG)), null, null));
		int[] innerShort = {BAND_INNER[0], BAND_INNER[1], BAND_SHORT[2], BAND_SHORT[3]};
		boxes.add(new Box(new double[] {3.5, 31, -3.5}, new double[] {1, 3, 7},
			faces("east", face(BAND_SHORT), "west", face(innerShort), "up", face(BAND_TOP_SHORT), "down", face(BAND_TOP_SHORT)), null, null));
		boxes.add(new Box(new double[] {-4.5, 31, -3.5}, new double[] {1, 3, 7},
			faces("west", face(BAND_SHORT), "east", face(innerShort), "up", face(BAND_TOP_SHORT), "down", face(BAND_TOP_SHORT)), null, null));
		// Spitzen: dünne Flächen außen auf dem Reif, Umriss in der Transparenz
		boxes.add(new Box(new double[] {-4.5, 34, -4.5}, new double[] {9, 4, 0},
			faces("north", face(POINTS_LONG), "south", face(POINTS_LONG)), null, null));
		boxes.add(new Box(new double[] {-4.5, 34, 4.5}, new double[] {9, 4, 0},
			faces("north", face(POINTS_LONG), "south", face(POINTS_LONG)), null, null));
		boxes.add(new Box(new double[] {4.5, 34, -3.5}, new double[] {0, 4, 7},
			faces("east", face(POINTS_SHORT), "west", face(POINTS_SHORT)), null, null));
		boxes.add(new Box(new double[] {-4.5, 34, -3.5}, new double[] {0, 4, 7},
			faces("east", face(POINTS_SHORT), "west", face(POINTS_SHORT)), null, null));
		// Samtpolster, goldener Knauf mit rotem Stein, Edelstein vorne
		String velvetSide = face(VELVET_SIDE);
		boxes.add(new Box(new double[] {-3.5, 32, -3.5}, new double[] {7, 2, 7},
			faces("up", face(VELVET_TOP), "north", velvetSide, "south", velvetSide, "east", velvetSide, "west", velvetSide), null, null));
		String orb = face(ORB);
		boxes.add(new Box(new double[] {-0.75, 34, -0.75}, new double[] {1.5, 1.5, 1.5},
			faces("north", orb, "south", orb, "east", orb, "west", orb, "up", orb), null, null));
		String gem = face(GEM);
		boxes.add(new Box(new double[] {-0.6, 35.5, -0.6}, new double[] {1.2, 1.2, 1.2},
			faces("north", gem, "south", gem, "east", gem, "west", gem, "up", gem), new double[] {0, 36.1, 0}, new double[] {0, 45, 0}));
		boxes.add(new Box(new double[] {-0.8, 31.7, -5.3}, new double[] {1.6, 1.6, 0.8},
			faces("north", gem, "east", gem, "west", gem, "up", gem, "down", gem), new double[] {0, 32.5, -4.9}, new double[] {0, 0, 45}));

		StringBuilder json = new StringBuilder();
		json.append("{\n  \"format_version\": \"1.12.0\",\n  \"minecraft:geometry\": [\n    {\n");
		json.append("      \"description\": {\n");
		json.append("        \"identifier\": \"geometry.nova.purpur_krone\",\n");
		json.append("        \"texture_width\": ").append(TEX).append(",\n");
		json.append("        \"texture_height\": ").append(TEX).append(",\n");
		json.append("        \"visible_bounds_width\": 2,\n        \"visible_bounds_height\": 1,\n");
		json.append("        \"visible_bounds_offset\": [0, 2, 0]\n      },\n");
		json.append("      \"bones\": [\n");
		json.append("        {\"name\": \"head\", \"pivot\": [0, 24, 0]},\n");
		json.append("        {\"name\": \"crown\", \"parent\": \"head\", \"pivot\": [0, 32, 0], \"cubes\": [\n");
		for (int i = 0; i < boxes.size(); i++) {
			Box box = boxes.get(i);
			double[] origin = {box.origin()[0], box.origin()[1] - DROP, box.origin()[2]};
			json.append("          {\"origin\": ").append(vec(origin)).append(", \"size\": ").append(vec(box.size()));
			if (box.rotation() != null) {
				json.append(", \"pivot\": ").append(vec(new double[] {box.pivot()[0], box.pivot()[1] - DROP, box.pivot()[2]})).append(", \"rotation\": ").append(vec(box.rotation()));
			}
			json.append(", \"uv\": ").append(box.uv()).append("}").append(i < boxes.size() - 1 ? ",\n" : "\n");
		}
		json.append("        ]}\n      ]\n    }\n  ]\n}\n");
		return json.toString();
	}

	static String vec(double[] v) {
		return String.format(Locale.ROOT, "[%s, %s, %s]", num(v[0]), num(v[1]), num(v[2]));
	}

	static String num(double value) {
		String text = String.format(Locale.ROOT, "%.3f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
		return text.equals("-0") ? "0" : text;
	}

	static String manifest() {
		return "{\n"
			+ "  \"id\": \"purpur-krone\",\n"
			+ "  \"name\": \"Purpur-Krone\",\n"
			+ "  \"type\": \"HEAD\",\n"
			+ "  \"rarity\": \"LEGENDARY\",\n"
			+ "  \"description\": \"Reif aus dunklem Pflaume-Metall mit roten Kanten und goldenen Nieten, Samtpolster und glühenden roten Steinen.\",\n"
			+ "  \"attach\": \"head\",\n"
			+ "  \"model\": \"model.geo.json\",\n"
			+ "  \"texture\": \"texture.png\",\n"
			+ "  \"glow\": {\"texture\": \"glow.png\", \"intensity\": \"0.75 + math.sin(query.anim_time * 90) * 0.15\"},\n"
			+ "  \"thumbnail\": {\"seconds\": 0, \"yaw\": 200, \"pitch\": -28}\n"
			+ "}\n";
	}

	static void write(File file, String text) throws IOException {
		Files.writeString(file.toPath(), text, StandardCharsets.UTF_8);
	}
}
