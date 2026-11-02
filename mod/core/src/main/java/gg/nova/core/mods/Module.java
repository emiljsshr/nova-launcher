package gg.nova.core.mods;

import gg.nova.core.Sprite;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Ein Modul im Mod-Menü: an/aus, Kategorien, Einstellungen und – bei HUD-Modulen – eine Position. */
public final class Module {
	public enum Category {
		HUD("nova.mods.category.hud"),
		PVP("nova.mods.category.pvp"),
		VISUAL("nova.mods.category.visual"),
		UTILITY("nova.mods.category.utility"),
		PERFORMANCE("nova.mods.category.performance");

		public final String key;

		Category(String key) {
			this.key = key;
		}
	}

	/** Einstellung mit festen Werten; Ja/Nein ist eine Auswahl aus "true" und "false". */
	public static final class Setting {
		public final String id;
		public final String key;
		public final List<String> values;
		String value;

		Setting(String id, String key, String defaultValue, String... values) {
			this.id = id;
			this.key = key;
			this.values = Collections.unmodifiableList(Arrays.asList(values));
			this.value = defaultValue;
		}

		public String value() {
			return this.value;
		}

		public boolean on() {
			return "true".equals(this.value);
		}

		public boolean toggle() {
			return this.values.size() == 2 && this.values.contains("true");
		}

		/** Nächster Wert (zyklisch). */
		public void next() {
			int index = this.values.indexOf(this.value);
			this.value = this.values.get((index + 1) % this.values.size());
			NovaMods.save();
		}
	}

	public final String id;
	public final String name;
	public final Sprite icon;
	public final List<Category> categories;
	/** Wird im HUD gezeichnet und lässt sich im HUD-Editor verschieben. */
	public final boolean hud;
	final boolean defaultEnabled;
	boolean enabled;
	/** Position als Anteil des freien Platzes (0 = links/oben, 1 = rechts/unten). */
	float x;
	float y;
	final float defaultX;
	final float defaultY;
	private final List<Setting> settings = new ArrayList<Setting>();

	Module(String id, String name, Sprite icon, boolean hud, boolean enabled, float x, float y, Category... categories) {
		this.id = id;
		this.name = name;
		this.icon = icon;
		this.hud = hud;
		this.defaultEnabled = enabled;
		this.enabled = enabled;
		this.x = this.defaultX = x;
		this.y = this.defaultY = y;
		this.categories = Collections.unmodifiableList(Arrays.asList(categories));
		if (hud) {
			this.bool("background", "nova.mods.setting.background", true);
			this.choice("scale", "nova.mods.setting.scale", "100", "75", "100", "125", "150");
		}
	}

	Module bool(String id, String key, boolean defaultValue) {
		this.settings.add(new Setting(id, key, String.valueOf(defaultValue), "false", "true"));
		return this;
	}

	Module choice(String id, String key, String defaultValue, String... values) {
		this.settings.add(new Setting(id, key, defaultValue, values));
		return this;
	}

	public List<Setting> settings() {
		return Collections.unmodifiableList(this.settings);
	}

	public Setting setting(String id) {
		for (Setting setting : this.settings) {
			if (setting.id.equals(id)) {
				return setting;
			}
		}
		throw new IllegalArgumentException(id);
	}

	public boolean on(String setting) {
		return this.setting(setting).on();
	}

	public boolean enabled() {
		return this.enabled;
	}

	public void toggle() {
		this.enabled = !this.enabled;
		NovaMods.save();
	}

	public float scale() {
		return this.hud ? Integer.parseInt(this.setting("scale").value) / 100.0F : 1.0F;
	}
}
