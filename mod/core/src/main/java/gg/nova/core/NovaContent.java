package gg.nova.core;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Partner-Server für Hauptmenü und Mehrspielermenü (gleich wie im Launcher, src/data/launcherData.ts).
 */
public final class NovaContent {
	private NovaContent() {
	}

	public enum IconStyle {
		ICE,
		ARENA,
		GRASS,
		/** Nova-Wappen */
		NOVA
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
		private final String bannerTexture;
		private final int bannerWidth;
		private final int bannerHeight;

		public Partner(String name, String address, IconStyle icon, Badge badge, Banner banner, String description) {
			this(name, address, icon, badge, banner, description, null, 0, 0);
		}

		/** @param bannerTexture Bild-Banner im Namespace nova (z. B. textures/gui/partners/x.png) statt Pixel-Landschaft */
		public Partner(String name, String address, IconStyle icon, Badge badge, Banner banner, String description,
				String bannerTexture, int bannerWidth, int bannerHeight) {
			this.name = name;
			this.address = address;
			this.icon = icon;
			this.badge = badge;
			this.banner = banner;
			this.description = description;
			this.bannerTexture = bannerTexture;
			this.bannerWidth = bannerWidth;
			this.bannerHeight = bannerHeight;
		}

		public String bannerTexture() {
			return this.bannerTexture;
		}

		public int bannerWidth() {
			return this.bannerWidth;
		}

		public int bannerHeight() {
			return this.bannerHeight;
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

	public static final List<Partner> PARTNERS = Collections.unmodifiableList(Arrays.asList(
		new Partner("Nova Development", "mc.nova-development.de", IconStyle.NOVA, Badge.VERIFIED, Banner.NIGHT,
			"Paper Build Server · Build · Create · Together", "textures/gui/partners/nova-development.png", 1400, 120)
	));
}
