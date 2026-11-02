package gg.nova.core.menu;

import gg.nova.core.NovaCanvas;
import gg.nova.core.NovaDraw;
import gg.nova.core.Sprite;

/**
 * Button im Nova-Design (Schieferblau mit Kante, beim Überfahren blau mit weißem Rahmen).
 * Position, Aktion und Aussehen sind versionsunabhängig; die Versions-Schicht erzeugt daraus ein
 * Minecraft-Widget und ruft {@link #render} auf.
 */
public final class MenuButton {
	private final int x;
	private final int y;
	private final int width;
	private final int height;
	private final Action action;
	private final Style style;
	private final Sprite icon;
	private final String labelKey;
	private final String tooltipKey;
	private final int partnerIndex;

	public MenuButton(int x, int y, int width, int height, Action action, Style style, Sprite icon, String labelKey, String tooltipKey, int partnerIndex) {
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
		this.action = action;
		this.style = style;
		this.icon = icon;
		this.labelKey = labelKey;
		this.tooltipKey = tooltipKey;
		this.partnerIndex = partnerIndex;
	}

	public int x() {
		return this.x;
	}

	public int y() {
		return this.y;
	}

	public int width() {
		return this.width;
	}

	public int height() {
		return this.height;
	}

	public Action action() {
		return this.action;
	}

	public Style style() {
		return this.style;
	}

	public Sprite icon() {
		return this.icon;
	}

	public String labelKey() {
		return this.labelKey;
	}

	public String tooltipKey() {
		return this.tooltipKey;
	}

	public int partnerIndex() {
		return this.partnerIndex;
	}
	public enum Action {
		SINGLEPLAYER,
		MULTIPLAYER,
		NOVA_SERVERS,
		MODS_FOLDER,
		COSMETICS,
		SCREENSHOTS_FOLDER,
		OPTIONS,
		LANGUAGE,
		ACCESSIBILITY,
		QUIT,
		JOIN_PARTNER,
		BACK;

		/** Aktionen, die Mehrspieler voraussetzen (deaktiviert bei gesperrtem Mehrspieler). */
		public boolean needsMultiplayer() {
			return this == MULTIPLAYER || this == NOVA_SERVERS || this == JOIN_PARTNER;
		}

		/** Noch nicht verfügbare Funktionen. */
		public boolean comingSoon() {
			return false;
		}
	}

	public enum Style {
		/** Großer Menü-Button, Text zentriert. */
		MAIN,
		/** Kleiner Button mit Icon und Text links (Ecken oben links). */
		TOOL,
		/** Quadratischer Icon-Button. */
		ICON,
		/** Kleiner blauer Button (Beitreten). */
		ACCENT
	}

	/** Icons werden auf 75 % verkleinert (16 → 12 Pixel), damit sie in die Buttons passen. */
	private static final float ICON_SCALE = 0.75F;

	private static final int FACE = 0xE639393F;
	private static final int FACE_TOP = 0xFF6C6C73;
	private static final int FACE_BOTTOM = 0xFF1B1B21;
	private static final int HOVER = 0xFF3D3D44;
	private static final int HOVER_TOP = 0xFF5E5E66;

	/**
	 * @param badge optionaler Text rechts im Button (z. B. Spielerzahl), sonst {@code null}
	 */
	public void render(NovaCanvas canvas, String label, String badge, boolean hovered, boolean active, float alpha) {
		boolean highlighted = active && hovered;
		float drawAlpha = alpha * (active ? 1.0F : 0.45F);
		int x1 = this.x;
		int y1 = this.y;
		int x2 = this.x + this.width;
		int y2 = this.y + this.height;

		if (this.style == Style.ACCENT) {
			canvas.fill(x1, y1, x2, y2, NovaDraw.fade(highlighted ? HOVER_TOP : HOVER, drawAlpha));
			canvas.fill(x1, y2 - 1, x2, y2, NovaDraw.fade(0xFF46464D, drawAlpha));
		} else if (highlighted) {
			// Blau mit weißem Rahmen, wie der markierte Button im Design
			canvas.gradient(x1, y1, x2, y2, NovaDraw.fade(HOVER_TOP, drawAlpha), NovaDraw.fade(HOVER, drawAlpha));
			int frame = NovaDraw.fade(NovaDraw.WHITE, drawAlpha);
			canvas.fill(x1, y1, x2, y1 + 1, frame);
			canvas.fill(x1, y2 - 1, x2, y2, frame);
			canvas.fill(x1, y1, x1 + 1, y2, frame);
			canvas.fill(x2 - 1, y1, x2, y2, frame);
		} else {
			// Schieferblau mit heller Oberkante und dunkler Unterkante (Minecraft-Optik)
			canvas.fill(x1, y1, x2, y2, NovaDraw.fade(FACE, drawAlpha));
			canvas.fill(x1, y1, x2, y1 + 1, NovaDraw.fade(FACE_TOP, drawAlpha));
			canvas.fill(x1, y2 - 2, x2, y2, NovaDraw.fade(FACE_BOTTOM, drawAlpha));
			canvas.fill(x1, y1, x1 + 1, y2 - 2, NovaDraw.fade(0xFF494950, drawAlpha));
		}

		int iconWidth = this.icon == null ? 0 : Math.round(this.icon.width() * ICON_SCALE);
		int iconHeight = this.icon == null ? 0 : Math.round(this.icon.height() * ICON_SCALE);
		int iconY = y1 + (this.height - iconHeight) / 2 - 1;
		int textColor = NovaDraw.fade(highlighted ? 0xFFFFFFA0 : NovaDraw.WHITE, drawAlpha);
		int textY = y1 + (this.height - canvas.fontHeight()) / 2 + 1;

		if (this.style == Style.ICON) {
			if (this.icon != null) {
				this.drawIcon(canvas, x1 + (this.width - iconWidth) / 2, iconY, drawAlpha);
			}
		} else if (this.style == Style.TOOL) {
			int textX = x1 + 6;
			if (this.icon != null) {
				this.drawIcon(canvas, textX, iconY, drawAlpha);
				textX += iconWidth + 6;
			}
			canvas.text(NovaDraw.trim(canvas, label, x2 - textX - 4), textX, textY, textColor, true);
		} else if (this.style == Style.ACCENT) {
			int textWidth = canvas.textWidth(label);
			canvas.text(label, x1 + (this.width - textWidth) / 2, textY, NovaDraw.fade(NovaDraw.WHITE, drawAlpha), false);
		} else {
			if (this.icon != null) {
				this.drawIcon(canvas, x1 + 6, iconY, drawAlpha);
			}
			int badgeWidth = 0;
			if (badge != null) {
				badgeWidth = canvas.textWidth(badge) + 10;
				int badgeX = x2 - 6 - canvas.textWidth(badge);
				canvas.fill(badgeX - 7, textY + 1, badgeX - 3, textY + 5, NovaDraw.fade(NovaDraw.ONLINE, drawAlpha));
				canvas.text(badge, badgeX, textY, NovaDraw.fade(0xFFE4E4ED, drawAlpha), false);
			}
			// Zentriert, solange Icon und Badge frei bleiben – sonst mittig im freien Bereich
			int left = x1 + (this.icon == null ? 4 : 6 + iconWidth + 4);
			int right = x2 - (badge == null ? 4 : badgeWidth + 4);
			String text = NovaDraw.trim(canvas, label, right - left);
			int textWidth = canvas.textWidth(text);
			int textX = x1 + (this.width - textWidth) / 2;
			if (textX < left || textX + textWidth > right) {
				textX = left + (right - left - textWidth) / 2;
			}
			canvas.text(text, textX, textY, textColor, true);
		}
	}

	private void drawIcon(NovaCanvas canvas, int x, int y, float alpha) {
		canvas.push();
		canvas.translate(x, y);
		canvas.scale(ICON_SCALE);
		this.icon.drawShadowed(canvas, 0, 0, 1, alpha);
		canvas.pop();
	}
}
