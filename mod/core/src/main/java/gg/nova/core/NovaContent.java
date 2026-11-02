package gg.nova.core;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Platzhalter-Inhalte für das Hauptmenü (gleich wie im Launcher).
 * Werden ersetzt, sobald es ein Nova-Backend für Partner und News gibt.
 */
public final class NovaContent {
	private NovaContent() {
	}

	public enum IconStyle {
		ICE,
		ARENA,
		GRASS
	}

	/** Kennzeichnung im Mehrspielermenü. */
	public enum Badge {
		VERIFIED,
		PARTNER
	}

	/** Pixel-Landschaft über der Karte im Mehrspielermenü. */
	public enum Banner {
		NIGHT,
		SUNSET,
		BEACH
	}

	public static final class Partner {
		private final String name;
		private final String address;
		private final IconStyle icon;
		private final Badge badge;
		private final Banner banner;
		private final String description;

		public Partner(String name, String address, IconStyle icon, Badge badge, Banner banner, String description) {
			this.name = name;
			this.address = address;
			this.icon = icon;
			this.badge = badge;
			this.banner = banner;
			this.description = description;
		}

		public Badge badge() {
			return this.badge;
		}

		public Banner banner() {
			return this.banner;
		}

		/** Text unter dem Namen, solange der Server keine MOTD liefert. */
		public String description() {
			return this.description;
		}

		public String name() {
			return this.name;
		}

		public String address() {
			return this.address;
		}

		public IconStyle icon() {
			return this.icon;
		}
	}

	// Platzhalter-Adressen – echte Adressen kommen von den Partnern
	public static final List<Partner> PARTNERS = Collections.unmodifiableList(Arrays.asList(
		new Partner("Glacier Network", "glacier.example", IconStyle.ICE, Badge.VERIFIED, Banner.NIGHT, "Skyblock · Bedwars"),
		new Partner("Nova Arena", "arena.example", IconStyle.ARENA, Badge.PARTNER, Banner.SUNSET, "PvP · Practice"),
		new Partner("Blockhaven", "blockhaven.example", IconStyle.GRASS, Badge.PARTNER, Banner.BEACH, "Survival · SMP")
	));
}
