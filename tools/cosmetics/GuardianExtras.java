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
 * Erzeugt die Effekte und Kopf-Cosmetics zum Purpur-Wächter-Set (Palette wie GuardianCape.java):
 *
 *   java tools/cosmetics/GuardianExtras.java public/cosmetics
 *
 * - glut-spuren:     glühende Abdrücke beim Laufen (TrailBehavior im Mod-Kern)
 * - purpur-halo:     dunkler Ring über dem Kopf mit rot glühender Innenkante, dreht sich und schwebt
 * - purpur-hoerner:  gebogene Hörner aus Pflaume-Metall mit roten, glühenden Spitzen
 *
 * Alle Texturen 128 × 128 in Pixel-Art; die Spuren hängen am Modellraum ("attach": "root").
 */
public final class GuardianExtras {
	static final int TEX = 128;

	static final int BLACK = 0x0f0b13;
	static final int[] PLUM = {0x251b2c, 0x2a1f31, 0x2f2337, 0x352841};
	static final int PLUM_LIGHT = 0x5e4470;
	static final int VIOLET = 0x8a5cc2;
	static final int RED_DEEP = 0x5a1018;
	static final int RED_DARK = 0x8a1420;
	static final int RED = 0xb81c2a;
	static final int RED_BRIGHT = 0xe8323f;
	static final int RED_LIGHT = 0xff6a70;
	static final int GOLD = 0xf0b43c;

	public static void main(String[] args) throws IOException {
		File root = new File(args.length > 0 ? args[0] : "public/cosmetics");
		trail(new File(root, "glut-spuren"));
		halo(new File(root, "purpur-halo"));
		horns(new File(root, "purpur-hoerner"));
		System.out.println("Purpur-Wächter-Extras geschrieben nach " + root);
	}

	/* ========================================
	   GEMEINSAM
	======================================== */

	interface Painter {
		int color(int x, int y);
	}

	/** Füllt einen Bereich; 0 = durchsichtig, Farben ohne Alpha werden deckend. */
	static void fill(BufferedImage image, int x0, int y0, int w, int h, Painter painter) {
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int color = painter.color(x, y);
				if (color != 0) {
					image.setRGB(x0 + x, y0 + y, (color >>> 24) == 0 ? 0xFF000000 | color : color);
				}
			}
		}
	}

	static double noise(int x, int y, int salt) {
		double value = Math.sin(x * 12.9898 + y * 78.233 + salt * 37.719) * 43758.5453;
		return value - Math.floor(value);
	}

	static int glow(int alpha, int rgb) {
		return Math.max(1, Math.min(255, alpha)) << 24 | rgb;
	}

	static String face(int u, int v, int w, int h) {
		return "{\"uv\": [" + u + ", " + v + "], \"uv_size\": [" + w + ", " + h + "]}";
	}

	static String allFaces(int u, int v, int w, int h) {
		String f = face(u, v, w, h);
		return "{\"north\": " + f + ", \"east\": " + f + ", \"south\": " + f + ", \"west\": " + f + ", \"up\": " + f + ", \"down\": " + f + "}";
	}

	static String vec(double... v) {
		return String.format(Locale.ROOT, "[%s, %s, %s]", num(v[0]), num(v[1]), num(v[2]));
	}

	static String num(double value) {
		String text = String.format(Locale.ROOT, "%.3f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
		return text.equals("-0") ? "0" : text;
	}

	static String cube(double[] origin, double[] size, String uv, double[] pivot, double[] rotation) {
		StringBuilder json = new StringBuilder("{\"origin\": ").append(vec(origin)).append(", \"size\": ").append(vec(size));
		if (pivot != null) {
			json.append(", \"pivot\": ").append(vec(pivot)).append(", \"rotation\": ").append(vec(rotation));
		}
		return json.append(", \"uv\": ").append(uv).append("}").toString();
	}

	/** Bedrock-Geometrie aus Knochen: je Eintrag {name, parent, pivot-JSON, cubes...}. */
	static String geometry(String identifier, List<String[]> bones) {
		StringBuilder json = new StringBuilder();
		json.append("{\n  \"format_version\": \"1.12.0\",\n  \"minecraft:geometry\": [\n    {\n");
		json.append("      \"description\": {\n");
		json.append("        \"identifier\": \"geometry.nova.").append(identifier).append("\",\n");
		json.append("        \"texture_width\": ").append(TEX).append(",\n        \"texture_height\": ").append(TEX).append(",\n");
		json.append("        \"visible_bounds_width\": 3,\n        \"visible_bounds_height\": 3,\n");
		json.append("        \"visible_bounds_offset\": [0, 1, 0]\n      },\n");
		json.append("      \"bones\": [\n");
		for (int b = 0; b < bones.size(); b++) {
			String[] bone = bones.get(b);
			json.append("        {\"name\": \"").append(bone[0]).append("\"");
			if (bone[1] != null) {
				json.append(", \"parent\": \"").append(bone[1]).append("\"");
			}
			json.append(", \"pivot\": ").append(bone[2]);
			if (bone.length > 3) {
				json.append(", \"cubes\": [\n");
				for (int c = 3; c < bone.length; c++) {
					json.append("          ").append(bone[c]).append(c < bone.length - 1 ? ",\n" : "\n");
				}
				json.append("        ]");
			}
			json.append("}").append(b < bones.size() - 1 ? ",\n" : "\n");
		}
		json.append("      ]\n    }\n  ]\n}\n");
		return json.toString();
	}

	static void save(File dir, BufferedImage base, BufferedImage glowImage, String geometry, String animation, String manifest)
			throws IOException {
		dir.mkdirs();
		ImageIO.write(base, "png", new File(dir, "texture.png"));
		if (glowImage != null) {
			ImageIO.write(glowImage, "png", new File(dir, "glow.png"));
		}
		Files.writeString(new File(dir, "model.geo.json").toPath(), geometry, StandardCharsets.UTF_8);
		if (animation != null) {
			Files.writeString(new File(dir, "animation.json").toPath(), animation, StandardCharsets.UTF_8);
		}
		Files.writeString(new File(dir, "cosmetic.json").toPath(), manifest, StandardCharsets.UTF_8);
	}

	static BufferedImage image() {
		return new BufferedImage(TEX, TEX, BufferedImage.TYPE_INT_ARGB);
	}

	/* ========================================
	   SPUREN: glühende Abdrücke
	======================================== */

	// Stiefelabdruck 3 × 5 Pixel in vierfacher Auflösung (12 × 20), Spitze oben (= vorne)
	static int footprint(int x, int y, boolean glowLayer) {
		double cx = 5.5;
		double half;
		if (y < 11) {
			half = 5.2 * Math.sqrt(Math.max(0, 1 - Math.pow((y - 6.5) / 6.5, 2)));
		} else if (y < 13) {
			half = 2.4;
		} else {
			half = 4.4 * Math.sqrt(Math.max(0, 1 - Math.pow((y - 16.2) / 3.8, 2)));
		}
		double d = Math.abs(x - cx);
		if (d > half) {
			return 0;
		}
		boolean edge = d > half - 1.5 || y < 2 || y > 18;
		if (glowLayer) {
			return edge ? glow(200, 0xff3a2a) : glow(90 + (int) (noise(x, y, 4) * 60), 0xff6a2a);
		}
		if (edge) {
			return RED;
		}
		// Sohle: dunkle Glut mit Profilrillen
		return y % 3 == 0 ? RED_DEEP : (noise(x, y, 5) > 0.7 ? RED_DARK : 0x3a0a10);
	}

	static void trail(File dir) throws IOException {
		BufferedImage base = image(), glowImage = image();
		fill(base, 0, 0, 12, 20, (x, y) -> footprint(x, y, false));
		fill(glowImage, 0, 0, 12, 20, (x, y) -> footprint(x, y, true));
		List<String[]> bones = new ArrayList<>();
		bones.add(new String[] {"root", null, vec(0, 0, 0)});
		String manifest = "{\n"
			+ "  \"id\": \"glut-spuren\",\n  \"name\": \"Glutspuren\",\n  \"type\": \"TRAIL\",\n  \"rarity\": \"RARE\",\n"
			+ "  \"description\": \"Bei jedem Schritt glimmt ein roter Abdruck auf und verglüht langsam.\",\n"
			+ "  \"attach\": \"root\",\n  \"model\": \"model.geo.json\",\n  \"texture\": \"texture.png\",\n"
			+ "  \"glow\": {\"texture\": \"glow.png\", \"intensity\": 0.9},\n"
			+ "  \"trail\": {\"stride\": 0.8, \"life\": 3.5, \"spread\": 0.14, \"width\": 3, \"length\": 5, \"uv\": [0, 0, 12, 20]},\n"
			+ "  \"thumbnail\": {\"seconds\": 0, \"yaw\": 25, \"pitch\": -65}\n}\n";
		save(dir, base, glowImage, geometry("glut_spuren", bones), null, manifest);
	}

	/* ========================================
	   HALO
	======================================== */

	static void halo(File dir) throws IOException {
		BufferedImage base = image(), glowImage = image();
		// Segment: dunkles Metall außen, rot glühende Mittellinie; 16 × 8 Texturpixel
		fill(base, 0, 0, 16, 8, (x, y) -> y == 3 || y == 4 ? RED_BRIGHT : y == 0 || y == 7 ? BLACK : PLUM[(int) (noise(x, y, 1) * 3.999)]);
		fill(glowImage, 0, 0, 16, 8, (x, y) -> y == 3 || y == 4 ? glow(220, 0xff3a46) : y == 2 || y == 5 ? glow(80, 0xff2a3a) : 0);
		fill(base, 16, 0, 8, 8, (x, y) -> Math.abs(x - 3.5) + Math.abs(y - 3.5) < 2 ? RED_LIGHT : RED);
		fill(glowImage, 16, 0, 8, 8, (x, y) -> glow(210, 0xff3a46));

		int segments = 16;
		double radius = 5.2, height = 39.5;
		List<String> cubes = new ArrayList<>();
		for (int i = 0; i < segments; i++) {
			double a = 2 * Math.PI * i / segments;
			double cx = Math.cos(a) * radius, cz = Math.sin(a) * radius;
			double length = 2 * Math.PI * radius / segments + 0.25;
			// Lokale X-Achse entlang der Tangente: Drehung um Y um -(a + 90°)
			double turn = -(Math.toDegrees(a) + 90);
			cubes.add(cube(new double[] {cx - length / 2, height - 0.45, cz - 0.45}, new double[] {length, 0.9, 0.9},
				allFaces(0, 0, 16, 8), new double[] {cx, height, cz}, new double[] {0, turn, 0}));
		}
		// Vier kleine Steine auf dem Ring
		for (int i = 0; i < 4; i++) {
			double a = 2 * Math.PI * i / 4 + Math.PI / 4;
			double cx = Math.cos(a) * radius, cz = Math.sin(a) * radius;
			cubes.add(cube(new double[] {cx - 0.6, height - 0.6, cz - 0.6}, new double[] {1.2, 1.2, 1.2}, allFaces(16, 0, 8, 8),
				new double[] {cx, height, cz}, new double[] {45, 0, 45}));
		}
		List<String[]> bones = new ArrayList<>();
		bones.add(new String[] {"head", null, vec(0, 24, 0)});
		List<String> halo = new ArrayList<>();
		halo.add("halo");
		halo.add("head");
		halo.add(vec(0, height, 0));
		halo.addAll(cubes);
		bones.add(halo.toArray(new String[0]));
		String animation = "{\n  \"format_version\": \"1.8.0\",\n  \"animations\": {\n"
			+ "    \"animation.nova.purpur_halo.idle\": {\n      \"loop\": true,\n      \"animation_length\": 18,\n      \"bones\": {\n"
			+ "        \"halo\": {\"rotation\": [0, \"query.anim_time * 20\", 0], \"position\": [0, \"math.sin(query.anim_time * 60) * 0.35\", 0]}\n"
			+ "      }\n    }\n  }\n}\n";
		String manifest = "{\n"
			+ "  \"id\": \"purpur-halo\",\n  \"name\": \"Purpur-Halo\",\n  \"type\": \"HEAD\",\n  \"rarity\": \"EPIC\",\n"
			+ "  \"description\": \"Ein dunkler Ring mit rot glühender Kante schwebt über deinem Kopf und dreht sich langsam.\",\n"
			+ "  \"attach\": \"head\",\n  \"model\": \"model.geo.json\",\n  \"texture\": \"texture.png\",\n"
			+ "  \"glow\": {\"texture\": \"glow.png\", \"intensity\": \"0.75 + math.sin(query.anim_time * 60) * 0.2\"},\n"
			+ "  \"thumbnail\": {\"seconds\": 0, \"yaw\": 20, \"pitch\": -30},\n"
			+ "  \"animationFile\": \"animation.json\",\n  \"animations\": {\"idle\": \"animation.nova.purpur_halo.idle\"}\n}\n";
		save(dir, base, glowImage, geometry("purpur_halo", bones), animation, manifest);
	}

	/* ========================================
	   HÖRNER
	======================================== */

	static void horns(File dir) throws IOException {
		BufferedImage base = image(), glowImage = image();
		// Vier Ringe (Metall hell/dunkel, Rot, Spitze), je 8 × 8 Texturpixel
		fill(base, 0, 0, 8, 8, (x, y) -> y == 0 ? PLUM_LIGHT : PLUM[(int) (noise(x, y, 2) * 3.999)]);
		fill(base, 8, 0, 8, 8, (x, y) -> y == 0 ? PLUM[3] : y == 7 ? BLACK : PLUM[0]);
		fill(base, 16, 0, 8, 8, (x, y) -> y < 2 ? RED_BRIGHT : RED);
		fill(base, 24, 0, 8, 8, (x, y) -> y < 3 ? RED_LIGHT : RED_BRIGHT);
		fill(glowImage, 16, 0, 8, 8, (x, y) -> glow(90, 0xff2a3a));
		fill(glowImage, 24, 0, 8, 8, (x, y) -> glow(200, 0xff3a46));

		// Bahn des linken Horns (+X): vom Kopf schräg nach oben außen, dann nach hinten gebogen
		int segments = 7;
		double[][] points = new double[segments + 1][];
		for (int i = 0; i <= segments; i++) {
			double t = (double) i / segments;
			points[i] = new double[] {2.6 + 3.2 * t + 0.6 * t * t, 31.2 + 5.4 * t - 1.0 * t * t, -1.2 + 3.6 * t * t};
		}
		List<String> left = new ArrayList<>();
		List<String> right = new ArrayList<>();
		for (int i = 0; i < segments; i++) {
			double[] p = points[i], q = points[i + 1];
			double dx = q[0] - p[0], dy = q[1] - p[1], dz = q[2] - p[2];
			double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
			dx /= length;
			dy /= length;
			dz /= length;
			// Lokale Y-Achse auf die Richtung drehen: erst um X (Neigung nach hinten), dann um Z
			double pitch = Math.toDegrees(Math.asin(dz));
			double roll = Math.toDegrees(Math.atan2(-dx, dy));
			double thickness = 1.7 - 1.1 * i / (segments - 1.0);
			int uv = i == segments - 1 ? 24 : i == segments - 2 ? 16 : i % 2 == 0 ? 0 : 8;
			String faces = allFaces(uv, 0, 8, 8);
			double extra = 0.25;
			left.add(cube(new double[] {p[0] - thickness / 2, p[1], p[2] - thickness / 2}, new double[] {thickness, length + extra, thickness},
				faces, p, new double[] {pitch, 0, roll}));
			right.add(cube(new double[] {-p[0] - thickness / 2, p[1], p[2] - thickness / 2}, new double[] {thickness, length + extra, thickness},
				faces, new double[] {-p[0], p[1], p[2]}, new double[] {pitch, 0, -roll}));
		}
		List<String[]> bones = new ArrayList<>();
		bones.add(new String[] {"head", null, vec(0, 24, 0)});
		List<String> l = new ArrayList<>(List.of("horn_left", "head", vec(2.6, 31.2, -1.2)));
		l.addAll(left);
		List<String> r = new ArrayList<>(List.of("horn_right", "head", vec(-2.6, 31.2, -1.2)));
		r.addAll(right);
		bones.add(l.toArray(new String[0]));
		bones.add(r.toArray(new String[0]));
		String manifest = "{\n"
			+ "  \"id\": \"purpur-hoerner\",\n  \"name\": \"Purpur-Hörner\",\n  \"type\": \"HEAD\",\n  \"rarity\": \"EPIC\",\n"
			+ "  \"description\": \"Gebogene Hörner aus Pflaume-Metall, die Spitzen glühen rot.\",\n"
			+ "  \"attach\": \"head\",\n  \"model\": \"model.geo.json\",\n  \"texture\": \"texture.png\",\n"
			+ "  \"glow\": {\"texture\": \"glow.png\", \"intensity\": \"0.8 + math.sin(query.anim_time * 90) * 0.15\"},\n"
			+ "  \"thumbnail\": {\"seconds\": 0, \"yaw\": 210, \"pitch\": -12}\n}\n";
		save(dir, base, glowImage, geometry("purpur_hoerner", bones), null, manifest);
	}
}
