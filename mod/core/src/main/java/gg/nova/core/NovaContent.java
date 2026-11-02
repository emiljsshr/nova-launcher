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

	public static final class Partner {
		private final String name;
		private final String address;
		private final IconStyle icon;

		public Partner(String name, String address, IconStyle icon) {
			this.name = name;
			this.address = address;
			this.icon = icon;
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
		new Partner("Glacier Network", "glacier.example", IconStyle.ICE),
		new Partner("Nova Arena", "arena.example", IconStyle.ARENA),
		new Partner("Blockhaven", "blockhaven.example", IconStyle.GRASS)
	));
}
