package gg.nova.core;

/**
 * Entwickler-Hilfe: Mit {@code -Dnova.capture=true} speichert die Mod je einen Screenshot vom
 * Ladebildschirm und vom Hauptmenü (run/screenshots/) und beendet das Spiel – zum Prüfen jeder Version.
 * Mit {@code -Dnova.capture=world} erstellt sie danach eine Testwelt und fotografiert den Spieler von hinten
 * (nova-world.png, nach 25 Sekunden noch einmal: nova-world-still.png) – zum Prüfen der Cosmetics
 * und Pets. Bisher nur im Build für 1.21.4.
 */
public final class NovaDebug {
	public static final long TITLE_CAPTURE_DELAY = 6000L;
	public static final float LOADING_CAPTURE_PROGRESS = 0.45F;
	/** Wartezeit nach dem Betreten der Testwelt, bis Chunks und Animationen laufen. */
	public static final long WORLD_CAPTURE_DELAY = 6000L;
	/** Zweites Bild, wenn ein Pet nach 10 Sekunden Stillstand gelandet ist. */
	public static final long WORLD_CAPTURE_STILL = 25000L;

	private NovaDebug() {
	}

	public static boolean captureEnabled() {
		return System.getProperty("nova.capture") != null;
	}

	public static boolean captureWorld() {
		return "world".equals(System.getProperty("nova.capture"));
	}
}
