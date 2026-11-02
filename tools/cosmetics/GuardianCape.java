import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;
import javax.imageio.ImageIO;

/**
 * Erzeugt das Cape „Purpur-Wächter" (public/cosmetics/purpur-waechter/): Pixeltextur und Leucht-Textur
 * (128×128), Bedrock-Geometrie, Animation und cosmetic.json.
 *
 *   java tools/cosmetics/GuardianCape.java public/cosmetics/purpur-waechter
 *
 * Form und Größe wie das Minecraft-Cape (10 × 16 Pixel, 1 dick), Textur in vierfacher Auflösung
 * (40 × 64 je Seite). Außen dunkles Pflaume-Lila mit roter Einfassung und Zierlinien, in der Mitte ein Wappen
 * mit Schwert und goldenem Knauf, darunter zwei rote Winkel, unten gezackte Spitzen (ausgeschnitten). Nur die
 * Klinge leuchtet leicht. Das Cape besteht aus vier Streifen untereinander, damit es sich im Wind biegen kann;
 * das Anheben beim Laufen macht CapeBehavior im Mod-Kern.
 * Dazu eine passende Elytra-Textur (elytra.png, Vanilla-Elytra-Layout in vierfacher Auflösung): Trägt der
 * Spieler eine Elytra, nimmt die Mod diese Textur und blendet das Cape aus – wie bei Minecraft-Capes.
 */
public final class GuardianCape {
	static final int TEX = 128;
	/** Texturpixel pro Modellpixel. */
	static final int SCALE = 4;
	static final int W = 10 * SCALE, H = 16 * SCALE;
	static final int SEGMENTS = 4;

	// Atlas (Texturpixel): Außenseite, Innenseite, Kantenstreifen, Oberkante
	static final int[] OUTER = {0, 0};
	static final int[] INNER = {44, 0};
	static final int[] SIDE = {88, 0};
	static final int[] TOP = {0, 68};

	// Rüstungs-Palette aus dem Entwurf
	static final int BLACK = 0x0f0b13;
	static final int[] PLUM = {0x251b2c, 0x2a1f31, 0x2f2337, 0x352841};
	static final int PLUM_DARK = 0x1d1424;
	static final int LINING = 0x1a1220;
	static final int RED_DEEP = 0x5a1018;
	static final int RED_DARK = 0x8a1420;
	static final int RED = 0xb81c2a;
	static final int RED_BRIGHT = 0xe8323f;
	static final int BLADE_LIGHT = 0xff6a70;
	static final int GOLD = 0xf0b43c;
	static final int GOLD_DARK = 0xc88a22;

	public static void main(String[] args) throws IOException {
		File out = new File(args.length > 0 ? args[0] : "public/cosmetics/purpur-waechter");
		out.mkdirs();

		BufferedImage base = new BufferedImage(TEX, TEX, BufferedImage.TYPE_INT_ARGB);
		BufferedImage glow = new BufferedImage(TEX, TEX, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < H; y++) {
			for (int x = 0; x < W; x++) {
				set(base, OUTER[0] + x, OUTER[1] + y, outer(x, y));
				set(base, INNER[0] + x, INNER[1] + y, inner(x, y));
				set(glow, OUTER[0] + x, OUTER[1] + y, bladeGlow(x, y));
			}
			for (int x = 0; x < SCALE; x++) {
				set(base, SIDE[0] + x, SIDE[1] + y, 0xFF000000 | (x == 0 || x == SCALE - 1 ? RED_DEEP : RED_DARK));
			}
		}
		for (int y = 0; y < SCALE; y++) {
			for (int x = 0; x < W; x++) {
				set(base, TOP[0] + x, TOP[1] + y, 0xFF000000 | (y == 0 ? RED_DARK : BLACK));
			}
		}

		ImageIO.write(elytra(), "png", new File(out, "elytra.png"));
		ImageIO.write(base, "png", new File(out, "texture.png"));
		ImageIO.write(glow, "png", new File(out, "glow.png"));
		write(new File(out, "model.geo.json"), geometry());
		write(new File(out, "animation.json"), animation());
		write(new File(out, "cosmetic.json"), manifest());
		System.out.println("Purpur-Wächter geschrieben nach " + out);
	}

	static void set(BufferedImage image, int x, int y, int argb) {
		if (argb != 0) {
			image.setRGB(x, y, argb);
		}
	}

	/* ========================================
	   TEXTUR
	======================================== */

	static double noise(int x, int y, int salt) {
		double value = Math.sin(x * 12.9898 + y * 78.233 + salt * 37.719) * 43758.5453;
		return value - Math.floor(value);
	}

	/** Unterkante der Zacken: fünf Spitzen, je 8 Pixel breit, 7 Pixel tief. */
	static int zigzag(int x) {
		double phase = x % 8;
		return H - 8 + (7 - (int) Math.round(Math.abs(phase - 3.5) * 2));
	}

	/** Halbe Breite des Wappens in Zeile y (Mitte bei 19,5), 0 außerhalb. */
	static double shieldHalf(int y) {
		if (y < 12 || y > 39) {
			return 0;
		}
		if (y == 12) {
			return 7.5;
		}
		if (y == 13) {
			return 8.5;
		}
		if (y <= 30) {
			return 9.5;
		}
		return 9.5 - (y - 30) * 1.05;
	}

	static boolean inShield(int x, int y) {
		return Math.abs(x - 19.5) < shieldHalf(y);
	}

	/** Klinge, Parierstange und Knauf des Schwerts (Farbe oder 0). */
	static int sword(int x, int y) {
		boolean center = x == 19 || x == 20;
		if (center && (y == 14 || y == 15)) {
			return y == 14 ? GOLD : GOLD_DARK;
		}
		if (center && y >= 16 && y <= 18) {
			return RED_DEEP;
		}
		if ((y == 19 || y == 20) && x >= 14 && x <= 25) {
			return y == 19 ? RED_BRIGHT : RED;
		}
		if (center && y >= 21 && y <= 36) {
			return x == 19 ? BLADE_LIGHT : RED_BRIGHT;
		}
		if (y == 37 && center) {
			return RED_BRIGHT;
		}
		return 0;
	}

	/** Zwei rote Winkel über den Zacken, Spitze in der Mitte. */
	static int chevron(int x, int y) {
		int depth = (int) Math.round(Math.abs(x - 19.5) * 0.33);
		if (y == 50 - depth || y == 49 - depth && (x == 19 || x == 20)) {
			return RED;
		}
		if (y == 54 - depth) {
			return RED_DARK;
		}
		return 0;
	}

	static int outer(int x, int y) {
		int edge = zigzag(x);
		if (y > edge) {
			return 0;
		}
		int color;
		if (y == edge) {
			color = RED_BRIGHT;
		} else if (y == edge - 1) {
			color = RED;
		} else if (y >= edge - 3) {
			color = BLACK;
		} else if (x == 0 || x == W - 1) {
			// Rote Einfassung links und rechts
			color = RED_DARK;
		} else if (x == 1 || x == W - 2) {
			color = RED;
		} else if (y == 0) {
			color = BLACK;
		} else if (y <= 2) {
			color = PLUM[3];
		} else if (y == 3) {
			color = RED;
		} else if (y == 4 && (x % 6 == 1 || x % 6 == 4) || y == 5 && (x % 6 == 2 || x % 6 == 3)) {
			// Zierbogen unter der Kopfleiste
			color = RED_DARK;
		} else if (sword(x, y) != 0 && inShield(x, y)) {
			color = sword(x, y);
		} else if (inShield(x, y) && (!inShield(x - 1, y) || !inShield(x + 1, y) || !inShield(x, y - 1) || !inShield(x, y + 1))) {
			color = RED;
		} else if (inShield(x, y) && (!inShield(x - 2, y) || !inShield(x + 2, y) || !inShield(x, y - 2) || !inShield(x, y + 2))) {
			color = RED_DEEP;
		} else if (inShield(x, y)) {
			color = noise(x, y, 3) > 0.85 ? PLUM[0] : PLUM_DARK;
		} else if (chevron(x, y) != 0) {
			color = chevron(x, y);
		} else if ((x == 4 || x == W - 5) && y >= 9 && y <= 43
				|| y == 8 && (x == 5 || x == 6 || x == W - 6 || x == W - 7)
				|| y == 44 && (x == 5 || x == W - 6)) {
			// Feine Zierlinien innen mit kleinen Haken oben und unten
			color = RED_DARK;
		} else {
			// Stoff: Lila mit leichtem Rauschen und feinen senkrechten Fäden
			color = PLUM[(int) (noise(x, y, 1) * PLUM.length * 0.999)];
			if (x % 2 == 1 && noise(x, y, 2) > 0.5) {
				color = PLUM[Math.max(0, (int) (noise(x, y, 1) * PLUM.length * 0.999) - 1)];
			}
		}
		return 0xFF000000 | color;
	}

	static int inner(int x, int y) {
		int edge = zigzag(x);
		if (y > edge) {
			return 0;
		}
		int color = y >= edge - 1 ? RED_DEEP : noise(x, y, 5) > 0.8 ? PLUM_DARK : LINING;
		return 0xFF000000 | color;
	}

	/** Leucht-Textur: Klinge und Parierstange mit weichem roten Schein, Rest durchsichtig. */
	static int bladeGlow(int x, int y) {
		double best = Double.MAX_VALUE;
		for (int dy = -3; dy <= 3; dy++) {
			for (int dx = -3; dx <= 3; dx++) {
				int s = sword(x + dx, y + dy);
				if (s != 0 && s != GOLD && s != GOLD_DARK && s != RED_DEEP) {
					best = Math.min(best, Math.hypot(dx, dy));
				}
			}
		}
		if (best > 3) {
			return 0;
		}
		double strength = best == 0 ? 0.85 : Math.max(0, 0.45 * (1 - best / 3.2));
		int alpha = (int) Math.round(strength * 255);
		return alpha << 24 | (best == 0 ? 0xff5a64 : 0xff2a3a);
	}

	/* ========================================
	   ELYTRA
	======================================== */

	/**
	 * Eigene Elytra-Textur im Vanilla-Layout (64 × 32, Flügel als Box-UV ab 22, 0 mit 10 × 20 × 2) in vierfacher
	 * Auflösung, im Stil des Capes. In der Textur liegt links die Außenkante, rechts die Innenkante an der
	 * Wirbelsäule, oben die Schulter (im Spiel mit einer Testtextur geprüft). Die Elytra wird mit Cutout gezeichnet.
	 */
	static BufferedImage elytra() {
		BufferedImage image = new BufferedImage(64 * SCALE, 32 * SCALE, BufferedImage.TYPE_INT_ARGB);
		int u = 22 * SCALE, v = 0, w = 10 * SCALE, h = 20 * SCALE, d = 2 * SCALE;
		// Die Mod macht die Flügel 0,5 Pixel dünn (ElytraModelMixin). Außen (south) das Motiv, innen (north)
		// dasselbe waagerecht gespiegelt, damit beide Umrisse übereinanderliegen. Die schmalen Kanten dunkel und
		// nur dort, wo der Flügel ist; die Unterseite bleibt durchsichtig.
		int innerHeight = elytraCut(w - 1, w, h);
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				set(image, u + 2 * d + w + x, v + d + y, elytraFace(x, y, w, h));
				set(image, u + d + x, v + d + y, elytraFace(w - 1 - x, y, w, h));
			}
			for (int x = 0; x < d; x++) {
				if (y <= innerHeight) {
					set(image, u + x, v + d + y, 0xFF000000 | PLUM_DARK);
				}
				set(image, u + d + w + x, v + d + y, 0xFF000000 | (y > h - 12 ? RED_DARK : BLACK));
			}
		}
		for (int y = 0; y < d; y++) {
			for (int x = 0; x < w; x++) {
				set(image, u + d + x, v + y, 0xFF000000 | PLUM_DARK);
			}
		}
		return image;
	}

	/** Unterkante einer Flügelspalte: innen auf 40 % Höhe, schräg nach außen unten, in Federstufen. */
	static int elytraCut(int x, int w, int h) {
		if (x < w * 0.28) {
			return h - 1;
		}
		double inner = (double) (w - 1 - x) / (w - 1 - w * 0.28);
		int cut = (int) (h * 0.4 + inner * h * 0.6);
		return Math.min(h - 1, cut + ((x / 4) % 2 == 0 ? 2 : 0));
	}

	/**
	 * Eine Flügelseite: Federn in Pflaume-Lila mit schrägen dunkelroten Federlinien, oben eine rote Schulterkante
	 * mit goldenen Nieten, außen ein roter Saum bis zur leuchtend roten Spitze unten links. Innen unten ist der
	 * Flügel schräg ausgeschnitten, die Kante in Stufen wie Federspitzen und rot gesäumt.
	 */
	static int elytraFace(int x, int y, int w, int h) {
		int cut = elytraCut(x, w, h);
		if (y > cut) {
			return 0;
		}
		int color;
		if (x >= w * 0.28 && y >= cut - 1) {
			// Ausschnitt: schmaler roter Saum an den Federstufen
			color = y == cut ? RED : RED_DEEP;
		} else if (y == 0) {
			color = RED_DARK;
		} else if (y == 1) {
			color = RED;
		} else if (y == 2) {
			color = BLACK;
		} else if (x == 0) {
			color = RED_DARK;
		} else if (y > h - 10 && x < 4) {
			// Spitze außen unten
			color = y > h - 4 ? RED_BRIGHT : RED;
		} else if ((y == 6 || y == 7) && (x == w - 6 || x == w - 5 || x == w - 14 || x == w - 13 || x == w - 22 || x == w - 21)) {
			color = y == 6 && x % 2 == 0 ? GOLD : GOLD_DARK;
		} else if (((x + y / 2) % 10) == 0) {
			// Federlinien schräg von innen oben nach außen unten
			color = RED_DEEP;
		} else if (((x + y / 2) % 10) == 1) {
			color = PLUM[3];
		} else {
			color = PLUM[(int) (noise(x, y, 7) * PLUM.length * 0.999)];
			if (y > h * 0.7 && noise(x, y, 8) > 0.6) {
				color = PLUM_DARK;
			}
		}
		return 0xFF000000 | color;
	}

	/* ========================================
	   GEOMETRIE, ANIMATION, MANIFEST
	======================================== */

	static String face(int u, int v, int w, int h) {
		return "{\"uv\": [" + u + ", " + v + "], \"uv_size\": [" + w + ", " + h + "]}";
	}

	// Vier Streifen untereinander, jeder hängt am unteren Rand des vorigen. Oben am Hals (y 24), Rücken bei z 2.
	static String geometry() {
		int stripe = H / SEGMENTS;
		int stripeHeight = 16 / SEGMENTS;
		StringBuilder json = new StringBuilder();
		json.append("{\n  \"format_version\": \"1.12.0\",\n  \"minecraft:geometry\": [\n    {\n");
		json.append("      \"description\": {\n");
		json.append("        \"identifier\": \"geometry.nova.purpur_waechter\",\n");
		json.append("        \"texture_width\": ").append(TEX).append(",\n");
		json.append("        \"texture_height\": ").append(TEX).append(",\n");
		json.append("        \"visible_bounds_width\": 3,\n        \"visible_bounds_height\": 3,\n");
		json.append("        \"visible_bounds_offset\": [0, 1, 0]\n      },\n");
		json.append("      \"bones\": [\n");
		json.append("        {\"name\": \"body\", \"pivot\": [0, 24, 0]}");
		for (int i = 0; i < SEGMENTS; i++) {
			String name = i == 0 ? "cape" : "cape_" + i;
			String parent = i == 0 ? "body" : i == 1 ? "cape" : "cape_" + (i - 1);
			double top = 24 - i * stripeHeight;
			StringBuilder uv = new StringBuilder("{");
			// Außen (south, vom Rücken weg), innen (north, zum Spieler), Kanten; oben nur am ersten Streifen.
			// Unten bleibt offen, sonst verdeckt eine gerade Kante die Zacken.
			uv.append("\"south\": ").append(face(OUTER[0], OUTER[1] + i * stripe, W, stripe));
			uv.append(", \"north\": ").append(face(INNER[0], INNER[1] + i * stripe, W, stripe));
			uv.append(", \"east\": ").append(face(SIDE[0], SIDE[1] + i * stripe, SCALE, stripe));
			uv.append(", \"west\": ").append(face(SIDE[0], SIDE[1] + i * stripe, SCALE, stripe));
			if (i == 0) {
				uv.append(", \"up\": ").append(face(TOP[0], TOP[1], W, SCALE));
			}
			uv.append("}");
			json.append(",\n        {\"name\": \"").append(name).append("\", \"parent\": \"").append(parent)
				.append("\", \"pivot\": ").append(vec(new double[] {0, top, 2})).append(", \"cubes\": [\n")
				.append("          {\"origin\": ").append(vec(new double[] {-5, top - stripeHeight, 2}))
				.append(", \"size\": [10, ").append(stripeHeight).append(", 1], \"uv\": ").append(uv).append("}\n        ]}");
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

	// Negatives X biegt einen Streifen nach hinten (vom Rücken weg). Jeder Streifen dreht relativ zum oberen,
	// mit Phasenversatz – so läuft eine Welle nach unten. "idle": langsam und leicht, "run": schnell und stärker.
	// Nach vorne (positiv) höchstens wenig, damit das Cape nicht in die Beine klappt.
	static String wave(double speed, double base, double amplitude, double lag) {
		StringBuilder bones = new StringBuilder();
		for (int i = 1; i < SEGMENTS; i++) {
			String phase = "math.sin((query.anim_time - " + num(lag * i) + ") * " + num(speed) + ")";
			bones.append(i > 1 ? ",\n" : "").append("        \"cape_").append(i).append("\": {\"rotation\": [\"")
				.append(num(-base * i)).append(" - ").append(phase).append(" * ").append(num(amplitude * i)).append("\", 0, 0]}");
		}
		return bones.toString();
	}

	static String animation() {
		return "{\n  \"format_version\": \"1.8.0\",\n  \"animations\": {\n"
			+ "    \"animation.nova.purpur_waechter.idle\": {\n"
			+ "      \"loop\": true,\n      \"animation_length\": 4,\n      \"bones\": {\n"
			+ "        \"cape\": {\"rotation\": [\"-1 - math.sin(query.anim_time * 90) * 1.5\", 0, \"math.sin(query.anim_time * 90) * 1\"]},\n"
			+ wave(90, 0.6, 1.2, 0.35) + "\n"
			+ "      }\n    },\n"
			+ "    \"animation.nova.purpur_waechter.run\": {\n"
			+ "      \"loop\": true,\n      \"animation_length\": 0.5,\n      \"bones\": {\n"
			+ "        \"cape\": {\"rotation\": [0, 0, \"math.sin(query.anim_time * 720) * 3\"]},\n"
			+ wave(720, 2.5, 3.5, 0.06) + "\n"
			+ "      }\n    }\n"
			+ "  }\n}\n";
	}

	static String manifest() {
		return "{\n"
			+ "  \"id\": \"purpur-waechter\",\n"
			+ "  \"name\": \"Purpur-Wächter\",\n"
			+ "  \"type\": \"CAPE\",\n"
			+ "  \"rarity\": \"LEGENDARY\",\n"
			+ "  \"description\": \"Dunkles Pflaume-Lila mit roten Kanten. In der Mitte ein Wappen mit glühender Klinge, unten gezackte Spitzen.\",\n"
			+ "  \"attach\": \"body\",\n"
			+ "  \"model\": \"model.geo.json\",\n"
			+ "  \"texture\": \"texture.png\",\n"
			+ "  \"glow\": {\"texture\": \"glow.png\", \"intensity\": \"0.75 + math.sin(query.anim_time * 90) * 0.15\"},\n"
			+ "  \"elytra\": \"elytra.png\",\n"
			+ "  \"cape\": {\"bone\": \"cape\", \"rest\": 6, \"perSpeed\": 9, \"max\": 70, \"runSpeed\": 5.6},\n"
			+ "  \"thumbnail\": {\"seconds\": 0, \"yaw\": 18, \"pitch\": 6},\n"
			+ "  \"animationFile\": \"animation.json\",\n"
			+ "  \"animations\": {\n"
			+ "    \"idle\": \"animation.nova.purpur_waechter.idle\",\n"
			+ "    \"run\": \"animation.nova.purpur_waechter.run\"\n"
			+ "  }\n"
			+ "}\n";
	}

	static void write(File file, String text) throws IOException {
		Files.writeString(file.toPath(), text, StandardCharsets.UTF_8);
	}
}
