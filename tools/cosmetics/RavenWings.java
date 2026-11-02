import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;
import javax.imageio.ImageIO;

/**
 * Erzeugt die Rabenschwingen (public/cosmetics/raven-wings/): Pixeltextur (128×128), Bedrock-Geometrie,
 * Animation und cosmetic.json.
 *
 *   java tools/cosmetics/RavenWings.java public/cosmetics/raven-wings
 *
 * Große Pixel-Flügel: je Flügel zwei dünne Flächen hintereinander – hinten eine größere, etwas hellere Lage,
 * davor eine kleinere, dunklere. Der Umriss steckt in der Transparenz: oben ein Bogen von der Schulter über
 * Kopfhöhe nach außen, unten ausgefranste Federspitzen bis zu den Oberschenkeln. Gefieder aus senkrechten
 * Strähnen in Schwarz und Anthrazit, in Reihen gestaffelt, mit wenigen kleinen dunkelroten Federn.
 * Die Flügel liegen am Rücken an ("closed") und breiten sich aus, sobald der Spieler schnell rennt oder fliegt
 * ("open", siehe WingBehavior im Mod-Kern).
 */
public final class RavenWings {
	static final int TEX = 128;
	/** Texturpixel pro Modellpixel. */
	static final int SCALE = 2;

	/** Eine Lage: Lage im Raum (Bedrock-Pixel, linker Flügel) und im Atlas (Texturpixel). */
	record Layer(double x, double y, double z, int width, int height, int u, int v, boolean front) {
	}

	// Hinten: breit und hoch, von der Schulter bis zu den Oberschenkeln. Vorne: kleiner, näher am Körper.
	static final Layer REAR = new Layer(2, 7, 4.0, 18, 29, 0, 0, false);
	static final Layer FRONT = new Layer(2, 12, 3.5, 11, 20, 40, 0, true);

	static final int[] DARK = {0x0b0c0e, 0x111316, 0x17191d, 0x1d2025, 0x24272d};
	static final int[] GREY = {0x1d2025, 0x24272d, 0x2c3038, 0x353a44, 0x414754};
	static final int HIGHLIGHT = 0x5f6674;
	static final int[] RED = {0x4a0b12, 0x5e0f17};
	static final int OUTLINE = 0x07080a;

	public static void main(String[] args) throws IOException {
		File out = new File(args.length > 0 ? args[0] : "public/cosmetics/raven-wings");
		out.mkdirs();

		BufferedImage texture = new BufferedImage(TEX, TEX, BufferedImage.TYPE_INT_ARGB);
		paint(texture, REAR, 101L);
		paint(texture, FRONT, 202L);

		ImageIO.write(texture, "png", new File(out, "texture.png"));
		write(new File(out, "model.geo.json"), geometry());
		write(new File(out, "animation.json"), animation());
		write(new File(out, "cosmetic.json"), manifest());
		System.out.println("Rabenschwingen geschrieben nach " + out);
	}

	/* ========================================
	   TEXTUR
	======================================== */

	static double noise(long seed, int x, int y) {
		double value = Math.sin(seed * 0.013 + x * 12.9898 + y * 78.233) * 43758.5453;
		return value - Math.floor(value);
	}

	/** Oberkante in Texturzeilen: Bogen, innen an der Schulter tiefer, außen wieder etwas tiefer. */
	static double top(Layer layer, double s) {
		double drop = layer.front() ? 14 : 20;
		return drop * (s - 0.55) * (s - 0.55) * SCALE;
	}

	/** Unterkante in Texturzeilen je Federspalte: außen tiefer, Spitzen unregelmäßig ausgefranst. */
	static double bottom(Layer layer, int column, long seed) {
		double s = (column + 0.5) * 2.0 / (layer.width() * SCALE);
		double rise = (layer.front() ? 5 : 4) * (1 - s) * SCALE;
		double jag = Math.floor(noise(seed + 17, column, 0) * 4) * SCALE / 2.0;
		return layer.height() * SCALE - 1 - rise - jag;
	}

	/**
	 * Malt eine Lage: Federsträhnen von je 2 Texturpixeln Breite laufen senkrecht, alle 6 Pixel endet eine Reihe
	 * mit dunkler, leicht versetzter Kante. Ränder bekommen eine dunkle Kontur, außerhalb bleibt alles durchsichtig.
	 */
	static void paint(BufferedImage image, Layer layer, long seed) {
		int width = layer.width() * SCALE, height = layer.height() * SCALE;
		boolean[][] inside = new boolean[height][width];
		for (int x = 0; x < width; x++) {
			double s = (double) x / width;
			int column = x / 2;
			int from = (int) Math.ceil(top(layer, s));
			int to = (int) bottom(layer, column, seed);
			// Innen und außen leicht ausgefranst
			if ((x == 0 || x == width - 1) && noise(seed + 23, x, 0) > 0.5) {
				from += SCALE;
			}
			for (int y = Math.max(0, from); y <= Math.min(height - 1, to); y++) {
				inside[y][x] = true;
			}
		}

		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				if (!inside[y][x]) {
					continue;
				}
				boolean edge = x == 0 || y == 0 || x == width - 1 || y == height - 1
					|| !inside[y - 1][x] || !inside[y + 1][x] || !inside[y][x - 1] || !inside[y][x + 1];
				int color;
				if (edge) {
					color = OUTLINE;
				} else {
					color = feather(layer, seed, x, y);
				}
				image.setRGB(layer.u() + x, layer.v() + y, 0xFF000000 | color);
			}
		}
	}

	/**
	 * Federsträhnen: je 2 Texturpixel breit, senkrecht. Jede Strähne hat ihre eigene Segmentlänge und ihren
	 * eigenen Versatz, so entsteht kein Ziegelmuster; zwischen den Segmenten liegt ein schwarzer Pixel.
	 */
	static int feather(Layer layer, long seed, int x, int y) {
		int column = x / 2;
		int length = 4 + (int) (noise(seed + 51, column, 0) * 5);
		int position = y + (int) (noise(seed + 31, column, 0) * length);
		int segment = position / length;
		int inSegment = position % length;
		if (inSegment == length - 1) {
			return DARK[0];
		}
		double n = noise(seed, column, segment);
		// Nur hinten und selten: eine kleine dunkelrote Federspitze (2 Pixel)
		if (!layer.front() && n > 0.975 && inSegment >= length - 3) {
			return RED[(int) (noise(seed + 41, column, segment) * RED.length * 0.999)];
		}
		// Hintere Lage grauer, vordere fast schwarz; einzelne helle Striche wie Federkiele
		int[] palette = layer.front() ? DARK : GREY;
		int base = palette[(int) (n * palette.length * 0.999)];
		if (x % 2 == 1) {
			base = darker(base);
		}
		if (noise(seed + 7, x, y) > 0.96) {
			return layer.front() ? GREY[2] : HIGHLIGHT;
		}
		return base;
	}

	static int darker(int rgb) {
		int r = (int) (((rgb >> 16) & 255) * 0.78);
		int g = (int) (((rgb >> 8) & 255) * 0.78);
		int b = (int) ((rgb & 255) * 0.78);
		return (r << 16) | (g << 8) | b;
	}

	/* ========================================
	   GEOMETRIE (Bedrock, Pixel, y oben, Rücken +Z)
	======================================== */

	/** Dünne Fläche: Rückseite (south) wie gemalt, Vorderseite (north) gespiegelt – so passt der Umriss von beiden Seiten. */
	static String plane(Layer layer, boolean mirror) {
		double ox = mirror ? -(layer.x() + layer.width()) : layer.x();
		int u = layer.u(), uw = layer.width() * SCALE, vh = layer.height() * SCALE;
		String south = face(mirror ? u + uw : u, layer.v(), mirror ? -uw : uw, vh);
		String north = face(mirror ? u : u + uw, layer.v(), mirror ? uw : -uw, vh);
		return "{\"origin\": " + vec(new double[] {ox, layer.y(), layer.z()}) + ", \"size\": "
			+ vec(new double[] {layer.width(), layer.height(), 0}) + ", \"uv\": {\"north\": " + north + ", \"south\": " + south + "}}";
	}

	static String face(int u, int v, int w, int h) {
		return "{\"uv\": [" + u + ", " + v + "], \"uv_size\": [" + w + ", " + h + "]}";
	}

	static String geometry() {
		StringBuilder json = new StringBuilder();
		json.append("{\n  \"format_version\": \"1.12.0\",\n  \"minecraft:geometry\": [\n    {\n");
		json.append("      \"description\": {\n");
		json.append("        \"identifier\": \"geometry.nova.raven_wings\",\n");
		json.append("        \"texture_width\": ").append(TEX).append(",\n");
		json.append("        \"texture_height\": ").append(TEX).append(",\n");
		json.append("        \"visible_bounds_width\": 4,\n        \"visible_bounds_height\": 3,\n");
		json.append("        \"visible_bounds_offset\": [0, 1.5, 0]\n      },\n");
		json.append("      \"bones\": [\n");
		json.append("        {\"name\": \"body\", \"pivot\": [0, 24, 0]},\n");
		json.append("        {\"name\": \"wings\", \"parent\": \"body\", \"pivot\": [0, 22, 3.5]}");
		for (boolean mirror : new boolean[] {false, true}) {
			double sign = mirror ? -1 : 1;
			// Ruhelage = offen: leicht nach hinten geschwenkt
			json.append(",\n        {\"name\": \"wing_").append(mirror ? "right" : "left").append("\", \"parent\": \"wings\", \"pivot\": ")
				.append(vec(new double[] {sign * 2, 22, 3.5})).append(", \"rotation\": ").append(vec(new double[] {0, -14 * sign, 9 * sign}))
				.append(", \"cubes\": [\n          ").append(plane(REAR, mirror)).append(",\n          ").append(plane(FRONT, mirror))
				.append("\n        ]}");
		}
		json.append("\n      ]\n    }\n  ]\n}\n");
		return json.toString();
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

	// Positives Z hebt die +X-Seite, negatives Y schwenkt sie nach hinten (rechts gespiegelt).
	// "open": ruhiger, weiter Flügelschlag. "closed": Flügel weit nach hinten an den Rücken gelegt und
	// leicht gesenkt, dazu leichtes Atmen.
	static String animation() {
		String t = "query.anim_time";
		String beat = "math.sin(" + t + " * 180)";
		String breath = "math.sin(" + t + " * 90)";
		return "{\n  \"format_version\": \"1.8.0\",\n  \"animations\": {\n"
			+ "    \"animation.nova.raven_wings.open\": {\n"
			+ "      \"loop\": true,\n"
			+ "      \"animation_length\": 2,\n"
			+ "      \"bones\": {\n"
			+ "        \"wings\": {\"position\": [0, \"" + beat + " * 0.3\", 0]},\n"
			+ "        \"wing_left\": {\"rotation\": [0, \"-6 - " + beat + " * 8\", \"" + beat + " * 6\"]},\n"
			+ "        \"wing_right\": {\"rotation\": [0, \"6 + " + beat + " * 8\", \"-" + beat + " * 6\"]}\n"
			+ "      }\n"
			+ "    },\n"
			+ "    \"animation.nova.raven_wings.closed\": {\n"
			+ "      \"loop\": true,\n"
			+ "      \"animation_length\": 4,\n"
			+ "      \"bones\": {\n"
			+ "        \"wings\": {\"position\": [0, \"" + breath + " * 0.15\", 0]},\n"
			+ "        \"wing_left\": {\"rotation\": [0, \"-52 + " + breath + " * 2\", -10]},\n"
			+ "        \"wing_right\": {\"rotation\": [0, \"52 - " + breath + " * 2\", 10]}\n"
			+ "      }\n"
			+ "    }\n"
			+ "  }\n}\n";
	}

	static String manifest() {
		return "{\n"
			+ "  \"id\": \"raven-wings\",\n"
			+ "  \"name\": \"Rabenschwingen\",\n"
			+ "  \"type\": \"WINGS\",\n"
			+ "  \"rarity\": \"EPIC\",\n"
			+ "  \"description\": \"Große schwarze Federflügel mit wenigen dunkelroten Federn. Angelegt am Rücken, beim Sprinten und Fliegen breiten sie sich aus.\",\n"
			+ "  \"attach\": \"body\",\n"
			+ "  \"model\": \"model.geo.json\",\n"
			+ "  \"texture\": \"texture.png\",\n"
			+ "  \"wings\": {\"openSpeed\": 5.0, \"closeSpeed\": 4.4, \"blend\": 0.35},\n"
			+ "  \"thumbnail\": {\"seconds\": 0, \"yaw\": 15, \"pitch\": 6},\n"
			+ "  \"animationFile\": \"animation.json\",\n"
			+ "  \"animations\": {\n"
			+ "    \"idle\": \"animation.nova.raven_wings.open\",\n"
			+ "    \"open\": \"animation.nova.raven_wings.open\",\n"
			+ "    \"closed\": \"animation.nova.raven_wings.closed\"\n"
			+ "  }\n"
			+ "}\n";
	}

	static void write(File file, String text) throws IOException {
		Files.writeString(file.toPath(), text, StandardCharsets.UTF_8);
	}
}
