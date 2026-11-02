package gg.nova.core.mods;

import java.util.List;

/**
 * Was die Versions-Schicht dem Mod-System liefert: Spielzustand fürs HUD und ein paar Eingriffe (Sprint, Optionen).
 * Gegenstände sind Versions-Objekte (ItemStack), gezeichnet über {@link gg.nova.core.NovaCanvas#item}.
 */
public interface NovaGame {
	enum Key {
		FORWARD,
		BACK,
		LEFT,
		RIGHT,
		JUMP,
		SNEAK,
		ATTACK,
		USE,
		/** Physische Taste C für den Zoom */
		ZOOM
	}

	/** Spiel-Optionen, die Module umschalten; die Version merkt sich den alten Wert und stellt ihn wieder her. */
	enum Tweak {
		FULLBRIGHT,
		NO_CLOUDS,
		FEW_PARTICLES,
		NO_SHADOWS
	}

	final class Effect {
		public final String name;
		public final int amplifier;
		/** Restdauer in Ticks, -1 = unbegrenzt */
		public final int ticks;

		public Effect(String name, int amplifier, int ticks) {
			this.name = name;
			this.amplifier = amplifier;
			this.ticks = ticks;
		}
	}

	/** Sprachschlüssel übersetzen (Sprache des Spiels). */
	String translate(String key);

	boolean inWorld();

	/** Ein Menü oder der Chat ist offen (dann kein Zoom und kein Sprint). */
	boolean screenOpen();

	int fps();

	/** Ping zum Server in ms, im Einzelspieler 0. */
	int ping();

	double x();

	double y();

	double z();

	float yaw();

	boolean key(Key key);

	/** Rüstung von Kopf bis Füße, leere Plätze null. */
	List<Object> armor();

	/** Hauptinventar ohne Hotbar (27 Plätze), leere Plätze null. */
	List<Object> inventory();

	/** Haltbarkeit als {verbleibend, maximal}, oder null, wenn der Gegenstand keine hat. */
	int[] durability(Object stack);

	List<Effect> effects();

	/** Sprint-Taste gedrückt halten (Toggle Sprint) oder loslassen. */
	void setSprint(boolean pressed);

	void tweak(Tweak tweak, boolean enabled);
}
