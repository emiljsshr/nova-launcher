package gg.nova.core.cosmetic;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Die angelegten Cosmetics des eigenen Spielers. Der Launcher übergibt sie beim Start als
 * {@code -Dnova.cosmetics=frost-wings,…}; Modell, Textur und Animation liegen in der Mod unter
 * {@code assets/nova/cosmetics/<id>/} (beim Bauen aus public/cosmetics/ des Launchers kopiert).
 * Im Nova-Menü lassen sie sich ohne Neustart wechseln ({@link #toggle}); die Auswahl landet in
 * {@code config/nova-cosmetics.txt}. Ohne Nova Launcher (z. B. Lunar) gilt beim nächsten Start diese Datei.
 */
public final class Cosmetics {
	public static final String NAMESPACE = "nova";
	/** Gepacktes Licht für die Leucht-Ebene: volle Helligkeit unabhängig von Tageszeit und Höhle. */
	public static final int FULL_BRIGHT = 0xF000F0;
	/** Über einem Helm (1 Pixel größer als der Kopf) sitzen Kopf-Cosmetics so viel größer, damit sie außen liegen. */
	public static final float HELMET_SCALE = 1.15F;
	/**
	 * Aufmaß der Elytra-Flügel in der Dicke, wenn ein Cosmetic eine eigene Elytra mitbringt: Vanilla nimmt +1
	 * (2 Pixel dick + 2 = 4); −0,75 ergibt 0,5 Pixel in der Mitte des alten Blocks. Die Texturkoordinaten
	 * bleiben gleich. Das Modell teilen sich alle Spieler, auf diesem Rechner werden also alle Elytren dünn.
	 */
	public static final float ELYTRA_DEPTH_DILATION = -0.75F;

	private static final Charset UTF8 = Charset.forName("UTF-8");
	private static final File SAVED = new File("config", "nova-cosmetics.txt");

	private static List<Cosmetic> equipped;
	private static List<String> equippedIds;
	private static List<Entry> catalog;
	private static final long START = System.nanoTime();
	/** Letzte Position, an der sich der Spieler bewegt hat, und wann. */
	private static double[] anchor;
	private static double lastMove;
	/** Tempo über Grund in Blöcken pro Sekunde, gemessen über mindestens 0,1 Sekunden. */
	private static double speed;
	/** Aktuelle (interpolierte) Position und Körperdrehung des Spielers, für Spuren. */
	private static double playerX, playerY, playerZ;
	private static float playerYaw;
	private static boolean sneaking;
	/** Gleitet der Spieler gerade mit einer Elytra? */
	private static boolean gliding;
	/** Wann das letzte Ducken begann (für Emotes). */
	private static double sneakStart = Double.NEGATIVE_INFINITY;
	private static double[] sample;
	private static double sampleTime;

	/** Ein angelegtes Cosmetic mit 3D-Modell. */
	public static final class Cosmetic {
		public final String id;
		/** Pfad der Textur im Namespace {@link #NAMESPACE}, z. B. {@code cosmetics/frost-wings/texture.png}. */
		public final String texturePath;
		/** Leucht-Textur (additiv, ohne Schatten) oder null. */
		public final String glowTexturePath;
		public final CosmeticModel model;
		/** Verhalten eines Pets oder null. */
		private final PetBehavior behavior;
		/** Flügel, die sich nach Tempo öffnen, oder null. */
		private final WingBehavior wings;
		/** Cape, das sich nach Tempo hebt und flattert, oder null. */
		private final CapeBehavior cape;
		/** Passende Elytra-Textur (Vanilla-Layout) oder null. */
		public final String elytraTexturePath;
		/** Liegt am Rücken wie ein Cape: unter einer Elytra ausblenden, wie bei Minecraft-Capes. */
		public final boolean hiddenByElytra;
		/** Hängt am Kopf statt am Körper ("attach": "head"); über einem Helm etwas größer ({@link #HELMET_SCALE}). */
		public final boolean onHead;
		/** Liegt im Modellraum ohne Körperteil ("attach": "root"): bleibt beim Ducken waagerecht am Boden. */
		public final boolean onRoot;
		private final EmoteBehavior emote;
		private final TrailBehavior trail;

		Cosmetic(String id, String texturePath, String glowTexturePath, CosmeticModel model, PetBehavior behavior,
				WingBehavior wings, CapeBehavior cape, String elytraTexturePath, String attach, EmoteBehavior emote,
				TrailBehavior trail) {
			this.id = id;
			this.texturePath = texturePath;
			this.glowTexturePath = glowTexturePath;
			this.model = model;
			this.behavior = behavior;
			this.wings = wings;
			this.cape = cape;
			this.elytraTexturePath = elytraTexturePath;
			this.hiddenByElytra = cape != null;
			this.onHead = "head".equals(attach);
			this.onRoot = "root".equals(attach);
			this.emote = emote;
			this.trail = trail;
		}

		/** Zeichnet einen Durchgang; Pets, Flügel, Capes, Emotes und Spuren nach ihrem Verhalten. */
		public void render(CosmeticModel.QuadSink sink, float seconds, CosmeticModel.Pass pass) {
			double now = time();
			// Beim Ducken senkt Minecraft das Modell um 2 Pixel (1/8 Block); Boden-Cosmetics gleichen das aus
			if (onRoot && sneaking) {
				final CosmeticModel.QuadSink target = sink;
				final float lift = (float) (0.125 / 0.9375);
				sink = (x, y, z, u, v, nx, ny, nz) -> target.vertex(x, y - lift, z, u, v, nx, ny, nz);
			}
			if (trail != null) {
				if (pass == CosmeticModel.Pass.BASE || glowTexturePath != null) {
					model.renderDecals(sink, trail.decals(now, playerX, playerY, playerZ, playerYaw), model.uvRect(trail.uv));
				}
				return;
			}
			if (emote != null) {
				CosmeticModel.Pose pose = emote.pose(now, sneakStart);
				if (pose != null) {
					model.render(sink, seconds, pass, pose);
				}
				return;
			}
			if (behavior != null) {
				model.render(sink, seconds, pass, behavior.pose(now, now - lastMove, gliding));
			} else if (wings != null) {
				model.render(sink, seconds, pass, wings.pose(now, speed));
			} else if (cape != null) {
				model.render(sink, seconds, pass, cape.pose(now, speed));
			} else {
				model.render(sink, seconds, pass);
			}
		}
	}

	/** Eintrag im Katalog aller Cosmetics mit 3D-Modell (assets/nova/cosmetics/index.json, beim Bauen erzeugt). */
	public static final class Entry {
		public final String id;
		public final String name;
		/** CAPE, HEAD, WINGS, PET oder TRAIL – wie im Launcher. */
		public final String type;

		Entry(String id, String name, String type) {
			this.id = id;
			this.name = name;
			this.type = type;
		}
	}

	private Cosmetics() {
	}

	public static synchronized List<Cosmetic> equipped() {
		if (equipped == null) {
			setEquipped(startIds());
		}
		return equipped;
	}

	/** IDs der angelegten Cosmetics in Anlege-Reihenfolge. */
	public static synchronized List<String> equippedIds() {
		equipped();
		return equippedIds;
	}

	public static synchronized List<Entry> catalog() {
		if (catalog == null) {
			List<Entry> entries = new ArrayList<Entry>();
			try {
				String index = read("cosmetics/index.json");
				for (Object item : Json.list(index != null ? Json.parse(index) : null)) {
					Map<String, Object> entry = Json.object(item);
					String id = Json.string(entry.get("id"), null);
					if (id != null) {
						entries.add(new Entry(id, Json.string(entry.get("name"), id), Json.string(entry.get("type"), "CAPE")));
					}
				}
			} catch (RuntimeException | IOException error) {
				System.err.println("[Nova] Cosmetic-Katalog konnte nicht gelesen werden: " + error);
			}
			catalog = Collections.unmodifiableList(entries);
		}
		return catalog;
	}

	/** Wie viele Cosmetics eines Typs gleichzeitig passen (wie slotLimits im Launcher). */
	public static int slotLimit(String type) {
		return "HEAD".equals(type) ? 3 : 1;
	}

	/**
	 * Anlegen oder ablegen wie in der Garderobe des Launchers: Ist der Typ voll, fliegt das am längsten
	 * angelegte Cosmetic dieses Typs raus. Speichert die Auswahl und gibt die neuen IDs zurück.
	 */
	public static synchronized List<String> toggle(String id) {
		List<String> ids = new ArrayList<String>(equippedIds());
		if (!ids.remove(id)) {
			String type = typeOf(id);
			List<String> sameType = new ArrayList<String>();
			for (String other : ids) {
				if (type.equals(typeOf(other))) {
					sameType.add(other);
				}
			}
			for (int i = 0; i <= sameType.size() - slotLimit(type); i++) {
				ids.remove(sameType.get(i));
			}
			ids.add(id);
		}
		setEquipped(ids);
		save(ids);
		return equippedIds;
	}

	private static Cosmetic find(String id) {
		if (equipped != null) {
			for (Cosmetic cosmetic : equipped) {
				if (cosmetic.id.equals(id)) {
					return cosmetic;
				}
			}
		}
		return null;
	}

	private static String typeOf(String id) {
		for (Entry entry : catalog()) {
			if (entry.id.equals(id)) {
				return entry.type;
			}
		}
		return "";
	}

	private static void setEquipped(List<String> ids) {
		// Schon angelegte behalten ihren Zustand (Pet sitzt weiter, Spuren bleiben liegen)
		List<String> missing = new ArrayList<String>();
		for (String id : ids) {
			if (find(id) == null) {
				missing.add(id);
			}
		}
		List<Cosmetic> fresh = load(missing);
		List<Cosmetic> loaded = new ArrayList<Cosmetic>();
		for (String id : ids) {
			Cosmetic cosmetic = find(id);
			for (Cosmetic candidate : fresh) {
				if (candidate.id.equals(id)) {
					cosmetic = candidate;
				}
			}
			if (cosmetic != null) {
				loaded.add(cosmetic);
			}
		}
		List<String> loadedIds = new ArrayList<String>();
		for (Cosmetic cosmetic : loaded) {
			loadedIds.add(cosmetic.id);
		}
		equipped = Collections.unmodifiableList(loaded);
		equippedIds = Collections.unmodifiableList(loadedIds);
	}

	/**
	 * Startet das Spiel über den Nova Launcher (mit Launcher-Verbindung), gilt dessen Loadout. Sonst hat die
	 * im Spiel gespeicherte Auswahl Vorrang vor {@code -Dnova.cosmetics}, damit Änderungen einen Neustart überleben.
	 */
	private static List<String> startIds() {
		String property = System.getProperty("nova.cosmetics");
		if (System.getProperty("nova.bridge") == null && SAVED.isFile()) {
			try {
				return split(new String(readAll(new FileInputStream(SAVED)), UTF8));
			} catch (IOException error) {
				System.err.println("[Nova] " + SAVED + " konnte nicht gelesen werden: " + error);
			}
		}
		return split(property != null ? property : "");
	}

	private static List<String> split(String ids) {
		List<String> result = new ArrayList<String>();
		for (String id : ids.split(",")) {
			id = id.trim();
			if (id.matches("[a-z0-9-]+") && !result.contains(id)) {
				result.add(id);
			}
		}
		return result;
	}

	private static void save(List<String> ids) {
		StringBuilder text = new StringBuilder();
		for (String id : ids) {
			text.append(text.length() > 0 ? "," : "").append(id);
		}
		try {
			SAVED.getParentFile().mkdirs();
			OutputStream out = new FileOutputStream(SAVED);
			try {
				out.write(text.toString().getBytes(UTF8));
			} finally {
				out.close();
			}
		} catch (IOException error) {
			System.err.println("[Nova] " + SAVED + " konnte nicht gespeichert werden: " + error);
		}
	}

	/** Elytra-Textur des ersten angelegten Cosmetics, das eine mitbringt (Pfad im Namespace), oder null. */
	public static String elytraTexturePath() {
		for (Cosmetic cosmetic : equipped()) {
			if (cosmetic.elytraTexturePath != null) {
				return cosmetic.elytraTexturePath;
			}
		}
		return null;
	}

	/** Vertexfarbe (ARGB) der Leucht-Ebene: weiß, die Puls-Helligkeit steckt in der Deckkraft. */
	public static int glowColor(Cosmetic cosmetic, float seconds) {
		int level = Math.round(cosmetic.model.glowIntensity(seconds) * 255.0F);
		return level << 24 | 0xFFFFFF;
	}

	/** Sekunden seit dem Laden, unabhängig vom Spieltakt (läuft auch im Pausemenü weiter). */
	static double time() {
		return (System.nanoTime() - START) / 1.0E9;
	}

	/** Sekunden für Animationen; alle Schleifenlängen teilen eine Stunde. */
	public static float animationSeconds() {
		return (float) (time() % 3600.0);
	}

	/**
	 * Position des eigenen Spielers (Blöcke), einmal pro Bild. Pets merken daran, wie lange er stillsteht;
	 * kleine Bewegungen (unter einem Zehntelblock) zählen nicht.
	 */
	public static synchronized void trackPlayer(double x, double y, double z, float bodyYaw, boolean sneak, boolean glide) {
		gliding = glide;
		double now = time();
		double previousY = playerY;
		playerX = x;
		playerY = y;
		playerZ = z;
		playerYaw = bodyYaw;
		if (sneak && !sneaking) {
			sneakStart = now;
		}
		sneaking = sneak;
		// Spuren: nur auf dem Boden (kaum Höhenänderung seit dem letzten Bild)
		boolean grounded = Math.abs(y - previousY) < 0.02;
		for (Cosmetic cosmetic : equipped()) {
			if (cosmetic.trail != null) {
				cosmetic.trail.step(x, y, z, bodyYaw, now, grounded);
			}
		}
		if (anchor == null || Math.abs(x - anchor[0]) + Math.abs(y - anchor[1]) + Math.abs(z - anchor[2]) > 0.1) {
			anchor = new double[] {x, y, z};
			lastMove = now;
		}
		// Tempo über Grund; Sprünge über eine Sekunde (Teleport, Pause) zählen nicht
		double elapsed = now - sampleTime;
		if (sample == null || elapsed > 1.0) {
			sample = new double[] {x, z};
			sampleTime = now;
			speed = 0.0;
		} else if (elapsed >= 0.1) {
			speed = Math.hypot(x - sample[0], z - sample[1]) / elapsed;
			sample = new double[] {x, z};
			sampleTime = now;
		}
	}

	private static List<Cosmetic> load(List<String> ids) {
		List<Cosmetic> result = new ArrayList<Cosmetic>();
		for (String id : ids) {
			try {
				Cosmetic cosmetic = loadOne(id);
				if (cosmetic != null) {
					result.add(cosmetic);
				}
			} catch (RuntimeException | IOException error) {
				System.err.println("[Nova] Cosmetic " + id + " konnte nicht geladen werden: " + error);
			}
		}
		return result;
	}

	private static Cosmetic loadOne(String id) throws IOException {
		String base = "cosmetics/" + id + "/";
		String manifestJson = read(base + "cosmetic.json");
		// Cosmetics ohne 3D-Modell (noch) gibt es nur im Launcher
		if (manifestJson == null) {
			return null;
		}
		Map<String, Object> manifest = Json.object(Json.parse(manifestJson));
		String geometry = read(base + manifest.get("model"));
		if (geometry == null) {
			return null;
		}
		String animationFile = (String) manifest.get("animationFile");
		String animation = animationFile != null ? read(base + animationFile) : null;
		Object glow = Json.object(manifest.get("glow")).get("texture");
		CosmeticModel model = CosmeticModel.parse(geometry, animation, manifest);
		PetBehavior behavior = null;
		if (manifest.containsKey("pet") && model.rootBone != null && model.pivot(model.rootBone) != null) {
			behavior = new PetBehavior(Json.object(manifest.get("pet")), model.pivot(model.rootBone));
		}
		WingBehavior wings = manifest.containsKey("wings") ? new WingBehavior(Json.object(manifest.get("wings"))) : null;
		CapeBehavior cape = manifest.containsKey("cape") ? new CapeBehavior(Json.object(manifest.get("cape"))) : null;
		Object elytra = manifest.get("elytra");
		EmoteBehavior emote = manifest.containsKey("emote") ? new EmoteBehavior(Json.object(manifest.get("emote"))) : null;
		TrailBehavior trail = manifest.containsKey("trail") ? new TrailBehavior(Json.object(manifest.get("trail"))) : null;
		return new Cosmetic(id, base + manifest.get("texture"), glow != null ? base + glow : null, model, behavior, wings, cape,
			elytra != null ? base + elytra : null, String.valueOf(manifest.get("attach")), emote, trail);
	}

	private static String read(String path) throws IOException {
		InputStream stream = Cosmetics.class.getResourceAsStream("/assets/" + NAMESPACE + "/" + path);
		return stream == null ? null : new String(readAll(stream), UTF8);
	}

	private static byte[] readAll(InputStream stream) throws IOException {
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			byte[] buffer = new byte[8192];
			int read;
			while ((read = stream.read(buffer)) != -1) {
				out.write(buffer, 0, read);
			}
			return out.toByteArray();
		} finally {
			stream.close();
		}
	}
}
