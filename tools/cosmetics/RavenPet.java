import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 * Erzeugt das Pet „Rabe" (public/cosmetics/raven/): Pixeltextur (128×128), Bedrock-Geometrie,
 * Animation und cosmetic.json.
 *
 *   java tools/cosmetics/RavenPet.java public/cosmetics/raven
 *   java tools/cosmetics/RavenPet.java public/cosmetics/purpur-rabe purpur
 *
 * Variante "purpur": Gefieder in Pflaume-Lila passend zum Purpur-Wächter-Set, rot glühende Augen.
 *
 * Der Rabe fliegt rechts über dem Kopf des Spielers: Flügel aus Arm, Hand und abgespreizten Schwungfedern,
 * Kopf mit Schnabel, Fächerschwanz, Beine. Blickrichtung wie der Spieler (-Z).
 * Verhalten ("pet" im Manifest, siehe PetBehavior im Mod-Kern): ab und zu eine Runde um den Spieler mit
 * Herzen über seinem Kopf, nach 10 Sekunden Stillstand landet er neben dem rechten Fuß.
 * Blockig wie in Minecraft, mit 2 Texturpixeln pro Modellpixel: schuppige kleine Federn, lange Federn mit
 * hellem Schaft, blau-violetter Schimmer, Auge mit Glanzpunkt.
 */
public final class RavenPet {
	static final int TEX = 128;
	/** Texturpixel pro Modellpixel. */
	static final int SCALE = 2;
	/** Schwebepunkt rechts über dem Kopf, über den Spitzen großer Flügel-Cosmetics (Bedrock: +X ist links vom Spieler, Rücken bei +Z). */
	static final double[] HOVER = {-14, 45, 0};

	// Fast schwarzes Gefieder mit blau-violettem Schimmer, graublauer Schnabel
	static int[] FEATHER = {0x15171b, 0x1a1d22, 0x1f2329, 0x252a31};
	static int[] SHEEN = {0x34446a, 0x3f3866, 0x2c4f60};
	static final int EDGE = 0x0b0c0f;
	static final int[] BEAK = {0x5d6873, 0x75818c, 0x454e57};
	static final int LEG = 0x4b5159;
	static int EYE = 0x6e4a36;
	static int PUPIL = 0x070708;
	static int EYE_GLINT = 0xd8dde3;
	/** Variante "purpur": lila Gefieder, rote Augen, die leuchten. */
	static boolean purpur;
	/** Leucht-Textur (nur bei glühenden Augen). */
	static BufferedImage glow;
	/** Herz für die Runde um den Spieler: Lage im Atlas (x, y, Breite, Höhe in Texturpixeln). */
	static final int[] HEART = {0, 96, 16, 16};
	static final String[] HEART_PIXELS = {
		".##.##.",
		"#hhrrr#",
		"#hrrrr#",
		".#rrr#.",
		"..#r#..",
		"...#...",
	};

	enum Paint { BODY, HEAD, BEAK, LEG, WING, PRIMARY, TAIL }

	record Cube(String bone, double[] origin, int[] size, Paint paint) {
	}

	static final List<Cube> cubes = new ArrayList<>();

	static void cube(String bone, double x, double y, double z, int w, int h, int d, Paint paint) {
		cubes.add(new Cube(bone, new double[] {HOVER[0] + x, HOVER[1] + y, HOVER[2] + z}, new int[] {w, h, d}, paint));
	}

	public static void main(String[] args) throws IOException {
		File out = new File(args.length > 0 ? args[0] : "public/cosmetics/raven");
		purpur = args.length > 1 && args[1].equals("purpur");
		if (purpur) {
			FEATHER = new int[] {0x261c2d, 0x2c2134, 0x33263c, 0x3b2c46};
			SHEEN = new int[] {0x5e4470, 0x6a2a40, 0x4b3459};
			EYE = 0xe8323f;
			PUPIL = 0x5a1018;
			EYE_GLINT = 0xff9aa0;
			glow = new BufferedImage(TEX, TEX, BufferedImage.TYPE_INT_ARGB);
		}
		out.mkdirs();

		// Rumpf und Brust
		cube("raven", -2, -2, -3.5, 4, 4, 7, Paint.BODY);
		cube("raven", -1.5, -2.5, -3, 3, 1, 4, Paint.BODY);
		// Kopf mit kräftigem Schnabel und Hakenspitze
		cube("head", -2, -1, -7.5, 4, 4, 4, Paint.HEAD);
		cube("head", -1, 0, -10.5, 2, 2, 3, Paint.BEAK);
		cube("head", -1, -1, -10.5, 2, 1, 1, Paint.BEAK);
		// Fächerschwanz
		cube("tail", -2, -1, 3.5, 4, 1, 4, Paint.TAIL);
		cube("tail", -1.5, -1, 7.5, 3, 1, 1, Paint.TAIL);
		// Beine: im Flug nach hinten angezogen, im Sitzen nach unten gedreht
		cube("legs", -1.5, -3, 1, 1, 1, 2, Paint.LEG);
		cube("legs", 0.5, -3, 1, 1, 1, 2, Paint.LEG);
		// Linker Flügel (+X): Arm mit Armschwingen, Hand mit gespreizten Schwungfedern
		cube("wing_left", 2, 1, -3, 5, 1, 5, Paint.WING);
		cube("wing_left", 2.5, 1, 2, 1, 1, 2, Paint.PRIMARY);
		cube("wing_left", 4, 1, 2, 1, 1, 2, Paint.PRIMARY);
		cube("wing_left", 5.5, 1, 2, 1, 1, 2, Paint.PRIMARY);
		cube("wing_left_outer", 7, 1, -3, 3, 1, 4, Paint.WING);
		double[] primaryZ = {-3, -1.8, -0.6, 0.6};
		int[] primaryLength = {5, 5, 4, 3};
		for (int i = 0; i < primaryZ.length; i++) {
			cube("wing_left_outer", 10, 1, primaryZ[i], primaryLength[i], 1, 1, Paint.PRIMARY);
		}

		// Je Würfelgröße + Bemalung ein Bereich im Box-Layout (in Modellpixeln, mit Abstand gegen Ausbluten)
		BufferedImage texture = new BufferedImage(TEX, TEX, BufferedImage.TYPE_INT_ARGB);
		Map<String, int[][]> faces = new LinkedHashMap<>();
		int units = TEX / SCALE;
		int cx = 0, cy = 0, shelf = 0;
		for (Cube cube : cubes) {
			String key = key(cube);
			if (faces.containsKey(key)) {
				continue;
			}
			int w = cube.size()[0], h = cube.size()[1], d = cube.size()[2];
			int rw = 2 * (w + d) + 1, rh = d + h + 1;
			if (cx + rw > units) {
				cx = 0;
				cy += shelf;
				shelf = 0;
			}
			if (cy + rh > units) {
				throw new IllegalStateException("Textur zu klein");
			}
			int[][] rects = layout(cx, cy, w, h, d);
			faces.put(key, rects);
			paint(texture, cube, rects, faces.size() * 7919L);
			cx += rw;
			shelf = Math.max(shelf, rh);
		}

		heart(texture, HEART[0], HEART[1]);
		ImageIO.write(texture, "png", new File(out, "texture.png"));
		if (glow != null) {
			ImageIO.write(glow, "png", new File(out, "glow.png"));
		}
		write(new File(out, "model.geo.json"), geometry(faces));
		write(new File(out, "animation.json"), animation());
		write(new File(out, "cosmetic.json"), manifest());
		System.out.println("Rabe geschrieben nach " + out);
	}

	static String key(Cube cube) {
		return cube.paint() + Arrays.toString(cube.size());
	}

	/* ========================================
	   TEXTUR
	======================================== */

	static final String[] FACE_NAMES = {"north", "east", "south", "west", "up", "down"};

	// Ecken jeder Fläche von außen gesehen (oben links, oben rechts, unten links) – wie im Mod-Kern
	static final int[][][] CORNERS = {
		{{1, 1, 0}, {0, 1, 0}, {1, 0, 0}},
		{{1, 1, 1}, {1, 1, 0}, {1, 0, 1}},
		{{0, 1, 1}, {1, 1, 1}, {0, 0, 1}},
		{{0, 1, 0}, {0, 1, 1}, {0, 0, 0}},
		{{0, 1, 0}, {1, 1, 0}, {0, 1, 1}},
		{{1, 0, 0}, {0, 0, 0}, {1, 0, 1}},
	};

	/** Box-Layout in Texturpixeln, je Fläche (north, east, south, west, up, down): x, y, Breite, Höhe. */
	static int[][] layout(int u, int v, int w, int h, int d) {
		int[][] units = {
			{u + d, v + d, w, h},
			{u, v + d, d, h},
			{u + 2 * d + w, v + d, w, h},
			{u + d + w, v + d, d, h},
			{u + d, v, w, d},
			{u + d + w, v, w, d},
		};
		int[][] pixels = new int[6][];
		for (int i = 0; i < 6; i++) {
			pixels[i] = new int[] {units[i][0] * SCALE, units[i][1] * SCALE, units[i][2] * SCALE, units[i][3] * SCALE};
		}
		return pixels;
	}

	enum Direction { DOWN, UP, LEFT, RIGHT }

	/** In welche Bildrichtung eine Feder auf dieser Fläche zeigt, wenn sie im Raum entlang {@code axis} liegt. */
	static Direction direction(int face, double[] axis) {
		int[][] c = CORNERS[face];
		double[] right = {c[1][0] - c[0][0], c[1][1] - c[0][1], c[1][2] - c[0][2]};
		double[] down = {c[2][0] - c[0][0], c[2][1] - c[0][1], c[2][2] - c[0][2]};
		double dx = right[0] * axis[0] + right[1] * axis[1] + right[2] * axis[2];
		double dy = down[0] * axis[0] + down[1] * axis[1] + down[2] * axis[2];
		if (Math.abs(dx) < 1e-6 && Math.abs(dy) < 1e-6) {
			return Direction.DOWN;
		}
		if (Math.abs(dx) > Math.abs(dy)) {
			return dx > 0 ? Direction.RIGHT : Direction.LEFT;
		}
		return dy > 0 ? Direction.DOWN : Direction.UP;
	}

	static double noise(long seed, int x, int y) {
		double value = Math.sin(seed * 0.013 + x * 12.9898 + y * 78.233) * 43758.5453;
		return value - Math.floor(value);
	}

	static int shade(int rgb, double factor) {
		int r = (int) Math.min(255, ((rgb >> 16) & 255) * factor);
		int g = (int) Math.min(255, ((rgb >> 8) & 255) * factor);
		int b = (int) Math.min(255, (rgb & 255) * factor);
		return (r << 16) | (g << 8) | b;
	}

	static int featherTone(long seed, int x, int y) {
		double n = noise(seed, x, y);
		if (n > 0.94) {
			return SHEEN[(int) (noise(seed + 5, x, y) * SHEEN.length * 0.999)];
		}
		return FEATHER[(int) (n * FEATHER.length * 0.999)];
	}

	static void paint(BufferedImage texture, Cube cube, int[][] rects, long seed) {
		int[] size = cube.size();
		// Federn zeigen nach hinten (+Z); Schwungfedern der Hand nach außen (+X)
		double[] axis = cube.paint() == Paint.PRIMARY && size[0] > size[2] ? new double[] {1, 0, 0} : new double[] {0, 0, 1};
		for (int face = 0; face < 6; face++) {
			int[] r = rects[face];
			if (r[2] == 0 || r[3] == 0) {
				continue;
			}
			Direction direction = direction(face, axis);
			boolean sideways = direction == Direction.LEFT || direction == Direction.RIGHT;
			// Jede Fläche wird mit Federn nach unten gemalt und dann in ihre Richtung gedreht
			int width = sideways ? r[3] : r[2];
			int height = sideways ? r[2] : r[3];
			int[][] canvas = new int[height][width];
			long faceSeed = seed * 31 + face;
			boolean underside = face == 5;
			for (int y = 0; y < height; y++) {
				for (int x = 0; x < width; x++) {
					int color = switch (cube.paint()) {
						case BEAK -> beak(faceSeed, x, y, height, face == 4);
						case LEG -> (y % 2 == 1) ? shade(LEG, 0.8) : LEG;
						case PRIMARY -> longFeather(faceSeed, x, y, width, height, SCALE);
						case TAIL -> longFeather(faceSeed, x, y, width, height, SCALE * 2);
						case WING -> (face == 4 || face == 5) && y >= height * 0.4
							? longFeather(faceSeed, x, y, width, height, SCALE * 2)
							: scales(faceSeed, x, y);
						default -> scales(faceSeed, x, y);
					};
					canvas[y][x] = underside ? shade(color, 0.8) : color;
				}
			}
			for (int y = 0; y < height; y++) {
				for (int x = 0; x < width; x++) {
					// Gedreht in die Fläche schreiben: unten → oben, links oder rechts
					int tx, ty;
					switch (direction) {
						case UP -> { tx = r[2] - 1 - x; ty = r[3] - 1 - y; }
						case LEFT -> { tx = r[2] - 1 - y; ty = x; }
						case RIGHT -> { tx = y; ty = r[3] - 1 - x; }
						default -> { tx = x; ty = y; }
					}
					texture.setRGB(r[0] + tx, r[1] + ty, 0xFF000000 | canvas[y][x]);
				}
			}

			if (cube.paint() == Paint.HEAD && (face == 1 || face == 3)) {
				// Auge (2×2) im vorderen Drittel der Seitenflächen (+X: vorne rechts, -X: vorne links)
				int ex = r[0] + (face == 1 ? r[2] - 3 * SCALE / 2 - 1 : SCALE / 2);
				int ey = r[1] + SCALE;
				texture.setRGB(ex, ey, 0xFF000000 | (face == 1 ? EYE_GLINT : PUPIL));
				texture.setRGB(ex + 1, ey, 0xFF000000 | (face == 1 ? PUPIL : EYE_GLINT));
				texture.setRGB(ex, ey + 1, 0xFF000000 | EYE);
				texture.setRGB(ex + 1, ey + 1, 0xFF000000 | EYE);
				if (glow != null) {
					for (int gy = 0; gy < 2; gy++) {
						for (int gx = 0; gx < 2; gx++) {
							glow.setRGB(ex + gx, ey + gy, 0xE0000000 | 0xff3a46);
						}
					}
				}
			}
		}
	}

	/** Pixel-Herz, doppelt so groß gezeichnet und im 16er-Feld zentriert; der Rest bleibt durchsichtig. */
	static void heart(BufferedImage texture, int x0, int y0) {
		int ox = x0 + (HEART[2] - HEART_PIXELS[0].length() * 2) / 2;
		int oy = y0 + (HEART[3] - HEART_PIXELS.length * 2) / 2;
		for (int y = 0; y < HEART_PIXELS.length; y++) {
			for (int x = 0; x < HEART_PIXELS[y].length(); x++) {
				int color = switch (HEART_PIXELS[y].charAt(x)) {
					case '#' -> 0x7a1424;
					case 'h' -> 0xff9aa8;
					case 'r' -> 0xe33a4f;
					default -> -1;
				};
				if (color < 0) {
					continue;
				}
				for (int dy = 0; dy < 2; dy++) {
					for (int dx = 0; dx < 2; dx++) {
						texture.setRGB(ox + x * 2 + dx, oy + y * 2 + dy, 0xFF000000 | color);
					}
				}
			}
		}
	}

	/** Schuppig überlappende kleine Federn: Reihen zu 3 Pixeln, versetzt, unterste Zeile jeder Feder dunkel. */
	static int scales(long seed, int x, int y) {
		int row = y / 3;
		int column = (x + (row % 2) * 2) / 4;
		int tone = featherTone(seed, column, row);
		if (y % 3 == 2) {
			return shade(tone, 0.6);
		}
		if ((x + (row % 2) * 2) % 4 == 3) {
			return shade(tone, 0.8);
		}
		return noise(seed, x, y) > 0.85 ? shade(tone, 1.15) : tone;
	}

	/** Lange Federn nebeneinander: heller Schaft in der Mitte, dunkle Kanten und Spitze. */
	static int longFeather(long seed, int x, int y, int width, int height, int featherWidth) {
		int column = x / featherWidth;
		int inside = x % featherWidth;
		int tone = featherTone(seed, column, 0);
		if (y == height - 1) {
			return EDGE;
		}
		if (featherWidth > 2 && inside == featherWidth / 2) {
			return shade(tone, 1.35);
		}
		if (inside == featherWidth - 1) {
			return shade(tone, 0.65);
		}
		return noise(seed, x, y) > 0.9 ? shade(tone, 1.15) : tone;
	}

	static int beak(long seed, int x, int y, int height, boolean top) {
		int base = top ? BEAK[1] : y < height / 2 ? BEAK[0] : BEAK[2];
		return noise(seed, x, y) > 0.8 ? shade(base, 0.9) : base;
	}

	/* ========================================
	   GEOMETRIE, ANIMATION, MANIFEST
	======================================== */

	static String geometry(Map<String, int[][]> faces) {
		double[] h = HOVER;
		StringBuilder json = new StringBuilder();
		json.append("{\n  \"format_version\": \"1.12.0\",\n  \"minecraft:geometry\": [\n    {\n");
		json.append("      \"description\": {\n");
		json.append("        \"identifier\": \"geometry.nova.raven\",\n");
		json.append("        \"texture_width\": ").append(TEX).append(",\n");
		json.append("        \"texture_height\": ").append(TEX).append(",\n");
		json.append("        \"visible_bounds_width\": 4,\n        \"visible_bounds_height\": 4,\n");
		json.append("        \"visible_bounds_offset\": [0, 1.5, 0]\n      },\n");
		json.append("      \"bones\": [\n");
		json.append("        {\"name\": \"body\", \"pivot\": [0, 24, 0]}");
		bone(json, "raven", "body", new double[] {h[0], h[1], h[2]}, false, faces);
		bone(json, "head", "raven", new double[] {h[0], h[1] + 1, h[2] - 3.5}, false, faces);
		bone(json, "tail", "raven", new double[] {h[0], h[1] - 0.5, h[2] + 3.5}, false, faces);
		bone(json, "legs", "raven", new double[] {h[0], h[1] - 2.5, h[2] + 1}, false, faces);
		bone(json, "wing_left", "raven", new double[] {h[0] + 2, h[1] + 1.5, h[2] - 0.5}, false, faces);
		bone(json, "wing_left_outer", "wing_left", new double[] {h[0] + 7, h[1] + 1.5, h[2] - 0.5}, false, faces);
		bone(json, "wing_right", "raven", new double[] {h[0] - 2, h[1] + 1.5, h[2] - 0.5}, true, faces);
		bone(json, "wing_right_outer", "wing_right", new double[] {h[0] - 7, h[1] + 1.5, h[2] - 0.5}, true, faces);
		json.append("\n      ]\n    }\n  ]\n}\n");
		return json.toString();
	}

	// Rechter Flügel: Würfel des linken an der Mittelachse des Raben gespiegelt. Flächen-UV kennt kein
	// "mirror": Seitenflächen tauschen, und jede Fläche zeigt ihren Ausschnitt waagerecht gespiegelt.
	static void bone(StringBuilder json, String name, String parent, double[] pivot, boolean mirror, Map<String, int[][]> faces) {
		String source = mirror ? name.replace("right", "left") : name;
		json.append(",\n        {\"name\": \"").append(name).append("\", \"parent\": \"").append(parent)
			.append("\", \"pivot\": ").append(vec(pivot)).append(", \"cubes\": [");
		boolean first = true;
		for (Cube cube : cubes) {
			if (!cube.bone().equals(source)) {
				continue;
			}
			double[] origin = cube.origin().clone();
			if (mirror) {
				origin[0] = 2 * HOVER[0] - (origin[0] + cube.size()[0]);
			}
			int[][] rects = faces.get(key(cube));
			StringBuilder uv = new StringBuilder("{");
			for (int face = 0; face < 6; face++) {
				int[] r = rects[mirror && (face == 1 || face == 3) ? 4 - face : face];
				int u = mirror ? r[0] + r[2] : r[0];
				int w = mirror ? -r[2] : r[2];
				uv.append(face > 0 ? ", " : "").append("\"").append(FACE_NAMES[face]).append("\": {\"uv\": [")
					.append(u).append(", ").append(r[1]).append("], \"uv_size\": [").append(w).append(", ").append(r[3]).append("]}");
			}
			uv.append("}");
			json.append(first ? "\n" : ",\n").append("          {\"origin\": ").append(vec(origin))
				.append(", \"size\": [").append(cube.size()[0]).append(", ").append(cube.size()[1]).append(", ").append(cube.size()[2])
				.append("], \"uv\": ").append(uv).append("}");
			first = false;
		}
		json.append(first ? "]}" : "\n        ]}");
	}

	static String vec(double[] v) {
		return String.format(Locale.ROOT, "[%s, %s, %s]", num(v[0]), num(v[1]), num(v[2]));
	}

	static String num(double value) {
		String text = String.format(Locale.ROOT, "%.3f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
		return text.equals("-0") ? "0" : text;
	}

	// Positives Z hebt die +X-Seite. "idle": Flügelschlag 2× pro Sekunde, Kraft schwankt über 4 Sekunden
	// (kräftig schlagen ↔ fast gleiten), dazu Schweben, Kreisen und Schräglage. "fly": nur kräftiger
	// Flügelschlag – Weg und Blickrichtung gibt das Verhalten vor. "sit": Flügel als senkrechte Flächen an den
	// Körper gelegt (90° um X, dann nach hinten), Beine nach unten, Kopf schaut umher.
	static String animation() {
		String t = "query.anim_time";
		String strength = "(26 + math.sin(" + t + " * 90) * 12)";
		String flap = "math.sin(" + t + " * 720) * " + strength;
		String flapOuter = "math.sin((" + t + " - 0.06) * 720) * " + strength + " * 0.8";
		return "{\n  \"format_version\": \"1.8.0\",\n  \"animations\": {\n"
			+ "    \"animation.nova.raven.idle\": {\n"
			+ "      \"loop\": true,\n"
			+ "      \"animation_length\": 4,\n"
			+ "      \"bones\": {\n"
			+ "        \"raven\": {\n"
			+ "          \"position\": [\"math.sin(" + t + " * 90) * 2.5\", \"math.sin(" + t + " * 180) * 1.2\", \"math.cos(" + t + " * 90) * 2\"],\n"
			+ "          \"rotation\": [\"math.sin(" + t + " * 180) * 6\", \"math.cos(" + t + " * 90) * 14\", \"-math.cos(" + t + " * 90) * 10\"]\n"
			+ "        },\n"
			+ "        \"head\": {\"rotation\": [\"math.sin(" + t + " * 360) * 6\", \"math.sin(" + t + " * 90) * 20\", 0]},\n"
			+ "        \"tail\": {\"rotation\": [\"math.sin(" + t + " * 720) * 5\", 0, 0]},\n"
			+ "        \"wing_left\": {\"rotation\": [0, 0, \"" + flap + "\"]},\n"
			+ "        \"wing_right\": {\"rotation\": [0, 0, \"-" + flap + "\"]},\n"
			+ "        \"wing_left_outer\": {\"rotation\": [0, 0, \"" + flapOuter + "\"]},\n"
			+ "        \"wing_right_outer\": {\"rotation\": [0, 0, \"-" + flapOuter + "\"]}\n"
			+ "      }\n"
			+ "    },\n"
			+ "    \"animation.nova.raven.fly\": {\n"
			+ "      \"loop\": true,\n"
			+ "      \"animation_length\": 1,\n"
			+ "      \"bones\": {\n"
			+ "        \"head\": {\"rotation\": [\"math.sin(" + t + " * 720) * 3\", 0, 0]},\n"
			+ "        \"tail\": {\"rotation\": [\"math.sin(" + t + " * 720) * 6\", 0, 0]},\n"
			+ "        \"wing_left\": {\"rotation\": [0, 0, \"math.sin(" + t + " * 720) * 40\"]},\n"
			+ "        \"wing_right\": {\"rotation\": [0, 0, \"-math.sin(" + t + " * 720) * 40\"]},\n"
			+ "        \"wing_left_outer\": {\"rotation\": [0, 0, \"math.sin((" + t + " - 0.05) * 720) * 30\"]},\n"
			+ "        \"wing_right_outer\": {\"rotation\": [0, 0, \"-math.sin((" + t + " - 0.05) * 720) * 30\"]}\n"
			+ "      }\n"
			+ "    },\n"
			+ "    \"animation.nova.raven.glide\": {\n"
			+ "      \"loop\": true,\n"
			+ "      \"animation_length\": 2,\n"
			+ "      \"bones\": {\n"
			+ "        \"tail\": {\"rotation\": [\"math.sin(" + t + " * 180) * 3\", 0, 0]},\n"
			+ "        \"wing_left\": {\"rotation\": [0, 0, \"4 + math.sin(" + t + " * 180) * 9\"]},\n"
			+ "        \"wing_right\": {\"rotation\": [0, 0, \"-4 - math.sin(" + t + " * 180) * 9\"]},\n"
			+ "        \"wing_left_outer\": {\"rotation\": [0, 0, \"math.sin((" + t + " - 0.1) * 180) * 7\"]},\n"
			+ "        \"wing_right_outer\": {\"rotation\": [0, 0, \"-math.sin((" + t + " - 0.1) * 180) * 7\"]}\n"
			+ "      }\n"
			+ "    },\n"
			+ "    \"animation.nova.raven.sit\": {\n"
			+ "      \"loop\": true,\n"
			+ "      \"animation_length\": 8,\n"
			+ "      \"bones\": {\n"
			+ "        \"raven\": {\"rotation\": [12, 0, 0]},\n"
			+ "        \"head\": {\"rotation\": [\"math.sin(" + t + " * 180) * 4 - 8\", \"math.sin(" + t + " * 45) * 35\", 0]},\n"
			+ "        \"tail\": {\"rotation\": [18, 0, 0]},\n"
			+ "        \"legs\": {\"rotation\": [90, 0, 0]},\n"
			+ "        \"wing_left\": {\"rotation\": [90, -88, 0]},\n"
			+ "        \"wing_right\": {\"rotation\": [90, 88, 0]}\n"
			+ "      }\n"
			+ "    }\n"
			+ "  }\n}\n";
	}

	static String manifest() {
		return "{\n"
			+ "  \"id\": \"" + (purpur ? "purpur-rabe" : "raven") + "\",\n"
			+ "  \"name\": \"" + (purpur ? "Purpur-Rabe" : "Rabe") + "\",\n"
			+ "  \"type\": \"PET\",\n"
			+ "  \"rarity\": \"EPIC\",\n"
			+ (purpur
				? "  \"description\": \"Pflaume-lila Gefieder und rot glühende Augen – der Begleiter zum Purpur-Wächter.\",\n"
					+ "  \"glow\": {\"texture\": \"glow.png\", \"intensity\": \"0.8 + math.sin(query.anim_time * 90) * 0.2\"},\n"
				: "  \"description\": \"Kreist schwarz schimmernd neben dir und lässt dich nie aus den Augen.\",\n")
			+ "  \"attach\": \"body\",\n"
			+ "  \"model\": \"model.geo.json\",\n"
			+ "  \"texture\": \"texture.png\",\n"
			+ "  \"thumbnail\": {\"seconds\": 0.5, \"yaw\": 245, \"pitch\": 18},\n"
			+ "  \"animationFile\": \"animation.json\",\n"
			+ "  \"animations\": {\n"
			+ "    \"idle\": \"animation.nova.raven.idle\",\n"
			+ "    \"fly\": \"animation.nova.raven.fly\",\n"
			+ "    \"glide\": \"animation.nova.raven.glide\",\n"
			+ "    \"sit\": \"animation.nova.raven.sit\"\n"
			+ "  },\n"
			+ "  \"pet\": {\n"
			+ "    \"root\": \"raven\",\n"
			+ "    \"sit\": {\"position\": [-10, 4.6, -2], \"after\": 10},\n"
			+ "    \"glide\": {\"position\": [-30, 20, 5], \"rotation\": [90, 0, 0]},\n"
			+ "    \"circle\": {\"first\": 4, \"every\": [18, 35], \"duration\": 4.5, \"widen\": 6, \"rise\": 4},\n"
			+ "    \"hearts\": {\"uv\": [" + HEART[0] + ", " + HEART[1] + ", " + HEART[2] + ", " + HEART[3] + "], "
			+ "\"interval\": 0.22, \"life\": 1.8, \"size\": 2.6, \"rise\": 7, \"spread\": 8, \"height\": 35}\n"
			+ "  }\n"
			+ "}\n";
	}

	static void write(File file, String text) throws IOException {
		Files.writeString(file.toPath(), text, StandardCharsets.UTF_8);
	}
}
