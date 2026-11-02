package gg.nova.core.mods;

import gg.nova.core.NovaCanvas;
import gg.nova.core.cosmetic.Json;
import gg.nova.core.social.NovaSocial;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Die Module des Mod-Menüs: Registrierung, Speichern in {@code config/nova-mods.json}, Spieltakt (Sprint,
 * Optionen), Zoom und Klickzähler. Die Versions-Schicht ruft {@link #init}, {@link #tick}, {@link #renderHud}
 * und {@link #fov} auf.
 */
public final class NovaMods {
	private static final Charset UTF8 = Charset.forName("UTF-8");
	private static final File CONFIG = new File("config", "nova-mods.json");

	private static final List<Module> MODULES = new ArrayList<Module>();
	private static NovaGame game;
	private static final Map<NovaGame.Tweak, Boolean> APPLIED = new EnumMap<NovaGame.Tweak, Boolean>(NovaGame.Tweak.class);
	private static boolean sprinting;
	private static boolean attackDown;
	private static boolean useDown;
	private static final Deque<Long> LEFT_CLICKS = new ArrayDeque<Long>();
	private static final Deque<Long> RIGHT_CLICKS = new ArrayDeque<Long>();
	private static float zoom = 1.0F;
	private static long lastFrame;

	public static final Module KEYSTROKES = add(new Module("keystrokes", "Keystrokes", ModIcons.KEYSTROKES, true, true, 0.0F, 0.42F,
		Module.Category.HUD, Module.Category.PVP).bool("mouse", "nova.mods.setting.mouse", true).bool("space", "nova.mods.setting.space", true));
	public static final Module CPS = add(new Module("cps", "CPS Display", ModIcons.CPS, true, true, 0.0F, 0.16F,
		Module.Category.HUD, Module.Category.PVP).bool("right", "nova.mods.setting.right", true));
	public static final Module FPS = add(new Module("fps", "FPS Display", ModIcons.FPS, true, true, 0.0F, 0.0F,
		Module.Category.HUD, Module.Category.PERFORMANCE));
	public static final Module ARMOR = add(new Module("armor", "Armor Status", ModIcons.ARMOR, true, true, 1.0F, 0.55F,
		Module.Category.HUD, Module.Category.PVP).choice("durability", "nova.mods.setting.durability", "value", "value", "percent", "none"));
	public static final Module EFFECTS = add(new Module("effects", "Potion Effects", ModIcons.EFFECTS, true, false, 1.0F, 0.12F,
		Module.Category.HUD, Module.Category.PVP));
	public static final Module COORDS = add(new Module("coords", "Coordinates", ModIcons.COORDS, true, true, 0.0F, 0.26F,
		Module.Category.HUD, Module.Category.UTILITY).bool("direction", "nova.mods.setting.direction", true));
	public static final Module ZOOM = add(new Module("zoom", "Zoom", ModIcons.ZOOM, false, true, 0.0F, 0.0F,
		Module.Category.VISUAL, Module.Category.UTILITY).choice("factor", "nova.mods.setting.factor", "4", "2", "4", "8").bool("smooth", "nova.mods.setting.smooth", true));
	public static final Module SPRINT = add(new Module("sprint", "Toggle Sprint", ModIcons.SPRINT, false, true, 0.0F, 0.0F,
		Module.Category.PVP, Module.Category.UTILITY));
	public static final Module FULLBRIGHT = add(new Module("fullbright", "Fullbright", ModIcons.FULLBRIGHT, false, false, 0.0F, 0.0F,
		Module.Category.VISUAL));
	public static final Module CLOCK = add(new Module("clock", "Clock", ModIcons.CLOCK, true, false, 1.0F, 0.0F,
		Module.Category.HUD, Module.Category.UTILITY).bool("seconds", "nova.mods.setting.seconds", false));
	public static final Module PING = add(new Module("ping", "Ping Display", ModIcons.PING, true, true, 0.0F, 0.08F,
		Module.Category.HUD, Module.Category.PVP));
	public static final Module INVENTORY = add(new Module("inventory", "Inventory HUD", ModIcons.INVENTORY, true, false, 0.5F, 0.0F,
		Module.Category.HUD, Module.Category.UTILITY));
	public static final Module CLOUDS = add(new Module("clouds", "No Clouds", ModIcons.CLOUDS, false, false, 0.0F, 0.0F,
		Module.Category.PERFORMANCE, Module.Category.VISUAL));
	public static final Module PARTICLES = add(new Module("particles", "Fewer Particles", ModIcons.PARTICLES, false, false, 0.0F, 0.0F,
		Module.Category.PERFORMANCE));
	public static final Module SHADOWS = add(new Module("shadows", "No Shadows", ModIcons.SHADOWS, false, false, 0.0F, 0.0F,
		Module.Category.PERFORMANCE, Module.Category.VISUAL));

	private NovaMods() {
	}

	private static Module add(Module module) {
		MODULES.add(module);
		return module;
	}

	public static List<Module> modules() {
		return Collections.unmodifiableList(MODULES);
	}

	public static NovaGame game() {
		return game;
	}

	public static int enabledCount() {
		int count = 0;
		for (Module module : MODULES) {
			if (module.enabled) {
				count++;
			}
		}
		return count;
	}

	/** Beim Start der Mod: Spiel-Anbindung setzen, gespeicherte Einstellungen laden. */
	public static void init(NovaGame adapter) {
		game = adapter;
		load();
	}

	/* ========================================
	   SPIELTAKT, ZOOM, KLICKS
	======================================== */

	/** Einmal pro Spieltakt: Sprint halten und Optionen der Performance-/Visual-Module anwenden. */
	public static void tick() {
		if (game == null) {
			return;
		}
		boolean sprint = SPRINT.enabled && game.inWorld() && !game.screenOpen()
			&& game.key(NovaGame.Key.FORWARD) && !game.key(NovaGame.Key.SNEAK);
		if (sprint || sprinting) {
			game.setSprint(sprint);
			sprinting = sprint;
		}
		apply(NovaGame.Tweak.FULLBRIGHT, FULLBRIGHT.enabled);
		apply(NovaGame.Tweak.NO_CLOUDS, CLOUDS.enabled);
		apply(NovaGame.Tweak.FEW_PARTICLES, PARTICLES.enabled);
		apply(NovaGame.Tweak.NO_SHADOWS, SHADOWS.enabled);
	}

	private static void apply(NovaGame.Tweak tweak, boolean enabled) {
		Boolean applied = APPLIED.get(tweak);
		if (applied == null ? enabled : applied != enabled) {
			game.tweak(tweak, enabled);
			APPLIED.put(tweak, enabled);
		}
	}

	/** Sichtfeld mit Zoom (C gedrückt halten); bei "weich" gleitet der Zoom über einige Bilder. */
	public static double fov(double fov) {
		if (game == null || !ZOOM.enabled) {
			return fov;
		}
		boolean zooming = game.inWorld() && !game.screenOpen() && game.key(NovaGame.Key.ZOOM);
		float target = zooming ? Float.parseFloat(ZOOM.setting("factor").value) : 1.0F;
		if (ZOOM.on("smooth")) {
			long now = System.nanoTime();
			float seconds = lastFrame == 0 ? 0.05F : Math.min(0.1F, (now - lastFrame) / 1.0E9F);
			lastFrame = now;
			zoom += (target - zoom) * Math.min(1.0F, seconds * 14.0F);
		} else {
			zoom = target;
		}
		return fov / zoom;
	}

	/** Klicks pro Sekunde (links, rechts), gemessen an den Tastenzuständen jedes Bildes. */
	static int[] cps() {
		long now = System.currentTimeMillis();
		boolean attack = game.key(NovaGame.Key.ATTACK);
		boolean use = game.key(NovaGame.Key.USE);
		if (attack && !attackDown) {
			LEFT_CLICKS.add(now);
		}
		if (use && !useDown) {
			RIGHT_CLICKS.add(now);
		}
		attackDown = attack;
		useDown = use;
		while (!LEFT_CLICKS.isEmpty() && now - LEFT_CLICKS.peekFirst() > 1000L) {
			LEFT_CLICKS.removeFirst();
		}
		while (!RIGHT_CLICKS.isEmpty() && now - RIGHT_CLICKS.peekFirst() > 1000L) {
			RIGHT_CLICKS.removeFirst();
		}
		return new int[] {LEFT_CLICKS.size(), RIGHT_CLICKS.size()};
	}

	/** HUD im Spiel zeichnen (nach dem Vanilla-HUD). */
	public static void renderHud(NovaCanvas canvas) {
		if (game != null && game.inWorld()) {
			NovaHud.render(canvas, game, false);
		}
	}

	/* ========================================
	   SPEICHERN
	======================================== */

	private static void load() {
		if (!CONFIG.isFile()) {
			return;
		}
		try {
			InputStream in = new FileInputStream(CONFIG);
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			try {
				byte[] buffer = new byte[4096];
				int read;
				while ((read = in.read(buffer)) != -1) {
					bytes.write(buffer, 0, read);
				}
			} finally {
				in.close();
			}
			Map<String, Object> root = Json.object(Json.parse(new String(bytes.toByteArray(), UTF8)));
			NovaSocial.setChatAlertsQuietly(!Boolean.FALSE.equals(root.get("chatAlerts")));
			Map<String, Object> modules = Json.object(root.get("modules"));
			for (Module module : MODULES) {
				Map<String, Object> saved = Json.object(modules.get(module.id));
				if (saved.isEmpty()) {
					continue;
				}
				module.enabled = Boolean.TRUE.equals(saved.get("enabled"));
				module.x = clamp(Json.number(saved.get("x"), module.defaultX));
				module.y = clamp(Json.number(saved.get("y"), module.defaultY));
				Map<String, Object> settings = Json.object(saved.get("settings"));
				for (Module.Setting setting : module.settings()) {
					String value = Json.string(settings.get(setting.id), null);
					if (value != null && setting.values.contains(value)) {
						setting.value = value;
					}
				}
			}
		} catch (IOException | RuntimeException error) {
			System.err.println("[Nova] " + CONFIG + " konnte nicht gelesen werden: " + error);
		}
	}

	public static void save() {
		StringBuilder json = new StringBuilder("{\"chatAlerts\":").append(NovaSocial.chatAlerts()).append(",\"modules\":{");
		for (int index = 0; index < MODULES.size(); index++) {
			Module module = MODULES.get(index);
			json.append(index > 0 ? "," : "").append(Json.quote(module.id)).append(":{\"enabled\":").append(module.enabled)
				.append(",\"x\":").append(module.x).append(",\"y\":").append(module.y).append(",\"settings\":{");
			List<Module.Setting> settings = module.settings();
			for (int s = 0; s < settings.size(); s++) {
				json.append(s > 0 ? "," : "").append(Json.quote(settings.get(s).id)).append(':').append(Json.quote(settings.get(s).value));
			}
			json.append("}}");
		}
		json.append("}}");
		try {
			CONFIG.getParentFile().mkdirs();
			OutputStream out = new FileOutputStream(CONFIG);
			try {
				out.write(json.toString().getBytes(UTF8));
			} finally {
				out.close();
			}
		} catch (IOException error) {
			System.err.println("[Nova] " + CONFIG + " konnte nicht gespeichert werden: " + error);
		}
	}

	/** Alle Module auf Standard (an/aus, Position, Einstellungen). */
	public static void reset() {
		for (Module module : MODULES) {
			module.enabled = module.defaultEnabled;
			module.x = module.defaultX;
			module.y = module.defaultY;
		}
		save();
	}

	static float clamp(float value) {
		return Math.max(0.0F, Math.min(1.0F, value));
	}
}
