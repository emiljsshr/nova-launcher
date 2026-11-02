package gg.nova.core.menu;

import gg.nova.core.NovaCanvas;
import gg.nova.core.NovaDraw;
import gg.nova.core.NovaSprites;
import gg.nova.core.Sprite;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.UnaryOperator;

/**
 * Layout und Darstellung des Nova-Hauptmenüs (zentriert, wie das Vanilla-Menü).
 * Versionsunabhängig – die Versions-Schicht liefert Panorama, Spielerkopf und Server-Ping und
 * erzeugt Widgets aus {@link #buttons()}.
 */
public final class NovaMenu {
	public static final int HEAD_SIZE = 18;
	private static final int BUTTON_HEIGHT = 20;
	private static final int GAP = 4;
	private static final int TOOL_HEIGHT = 18;

	/**
	 * Laufzeitdaten fürs Zeichnen.
	 *
	 * @param partnerPlayers Summe der Spieler auf erreichbaren Partner-Servern, -1 wenn unbekannt
	 * @param translate      übersetzt Sprachschlüssel (z. B. I18n.translate)
	 */
	public static final class State {
		private final float alpha;
		private final String playerName;
		private final boolean microsoftAccount;
		private final int partnerPlayers;
		private final String minecraftVersion;
		private final String novaVersion;
		private final boolean hideSplash;
		private final int copyrightWidth;
		private final UnaryOperator<String> translate;

		public State(float alpha, String playerName, boolean microsoftAccount, int partnerPlayers, String minecraftVersion, String novaVersion, boolean hideSplash, int copyrightWidth, UnaryOperator<String> translate) {
			this.alpha = alpha;
			this.playerName = playerName;
			this.microsoftAccount = microsoftAccount;
			this.partnerPlayers = partnerPlayers;
			this.minecraftVersion = minecraftVersion;
			this.novaVersion = novaVersion;
			this.hideSplash = hideSplash;
			this.copyrightWidth = copyrightWidth;
			this.translate = translate;
		}

		public float alpha() {
			return this.alpha;
		}

		public String playerName() {
			return this.playerName;
		}

		public boolean microsoftAccount() {
			return this.microsoftAccount;
		}

		public int partnerPlayers() {
			return this.partnerPlayers;
		}

		public String minecraftVersion() {
			return this.minecraftVersion;
		}

		public String novaVersion() {
			return this.novaVersion;
		}

		public boolean hideSplash() {
			return this.hideSplash;
		}

		public int copyrightWidth() {
			return this.copyrightWidth;
		}

		public UnaryOperator<String> translate() {
			return this.translate;
		}
	}

	private final int width;
	private final int height;
	private final int logoX;
	private final int logoY;
	private final int logoWidth;
	private final int ribbonY;
	private final int cardWidth;
	private final boolean compact;
	private final List<MenuButton> buttons = new ArrayList<>();

	private NovaMenu(int width, int height, UnaryOperator<String> translate, NovaCanvas measure) {
		this.width = width;
		this.height = height;
		this.compact = width < 430;

		int buttonWidth = clamp((int) (width * 0.25F), 200, 260);
		// Wappen etwa so hoch wie früher der Schriftzug plus ein Drittel
		int logoHeight = clamp((int) (height * 0.22F), 62, 100);
		this.logoWidth = NovaDraw.logoWidth(logoHeight);

		// Gesamthöhe: Logo, Band, Abstand, drei Haupt-Buttons, Abstand, untere Reihe
		int stackHeight = logoHeight + 4 + 13 + 18 + 3 * (BUTTON_HEIGHT + GAP) + 8 + BUTTON_HEIGHT;
		this.logoY = Math.max(TOOL_HEIGHT + 14, (height - stackHeight) / 2 - 6);
		this.logoX = (width - this.logoWidth) / 2;
		this.ribbonY = this.logoY + logoHeight + 4;

		int x = (width - buttonWidth) / 2;
		int y = this.ribbonY + 13 + 18;
		this.buttons.add(new MenuButton(x, y, buttonWidth, BUTTON_HEIGHT, MenuButton.Action.SINGLEPLAYER,
			MenuButton.Style.MAIN, NovaSprites.PERSON, "menu.singleplayer", null, -1));
		y += BUTTON_HEIGHT + GAP;
		this.buttons.add(new MenuButton(x, y, buttonWidth, BUTTON_HEIGHT, MenuButton.Action.MULTIPLAYER,
			MenuButton.Style.MAIN, NovaSprites.PEOPLE, "menu.multiplayer", null, -1));
		y += BUTTON_HEIGHT + GAP;
		this.buttons.add(new MenuButton(x, y, buttonWidth, BUTTON_HEIGHT, MenuButton.Action.NOVA_SERVERS,
			MenuButton.Style.MAIN, NovaSprites.SERVER, "nova.menu.servers", null, -1));

		// Untere Reihe wie im Vanilla-Menü: Sprache · Optionen · Beenden · Barrierefreiheit
		y += BUTTON_HEIGHT + GAP + 8;
		int half = (buttonWidth - 2 * (BUTTON_HEIGHT + GAP) - GAP) / 2;
		this.buttons.add(new MenuButton(x, y, BUTTON_HEIGHT, BUTTON_HEIGHT, MenuButton.Action.LANGUAGE,
			MenuButton.Style.ICON, NovaSprites.GLOBE, "options.language", "options.language", -1));
		int innerX = x + BUTTON_HEIGHT + GAP;
		this.buttons.add(new MenuButton(innerX, y, half, BUTTON_HEIGHT, MenuButton.Action.OPTIONS,
			MenuButton.Style.MAIN, NovaSprites.GEAR, "menu.options", null, -1));
		this.buttons.add(new MenuButton(innerX + half + GAP, y, buttonWidth - 2 * (BUTTON_HEIGHT + GAP) - half - GAP, BUTTON_HEIGHT,
			MenuButton.Action.QUIT, MenuButton.Style.MAIN, null, "menu.quit", null, -1));
		this.buttons.add(new MenuButton(x + buttonWidth - BUTTON_HEIGHT, y, BUTTON_HEIGHT, BUTTON_HEIGHT, MenuButton.Action.ACCESSIBILITY,
			MenuButton.Style.ICON, NovaSprites.ACCESS, "options.accessibility", "options.accessibility", -1));

		// Werkzeuge oben links: Mods · Cosmetics · Screenshots
		MenuButton.Action[] tools = {MenuButton.Action.MODS_FOLDER, MenuButton.Action.COSMETICS, MenuButton.Action.SCREENSHOTS_FOLDER};
		String[] toolKeys = {"nova.menu.mods", "nova.menu.cosmetics", "nova.menu.screenshots"};
		String[] toolTips = {"nova.menu.mods.tooltip", "nova.menu.cosmetics.soon", "nova.menu.screenshots.tooltip"};
		List<Sprite> toolIcons = Arrays.asList(NovaSprites.MODS, NovaSprites.CAPE, NovaSprites.CAM);
		int toolX = 8;
		for (int index = 0; index < tools.length; index++) {
			int toolWidth = this.compact
				? TOOL_HEIGHT + 4
				: 6 + Math.round(toolIcons.get(index).width() * 0.75F) + 6 + measure.textWidth(translate.apply(toolKeys[index])) + 8;
			this.buttons.add(new MenuButton(toolX, 8, toolWidth, TOOL_HEIGHT, tools[index],
				this.compact ? MenuButton.Style.ICON : MenuButton.Style.TOOL, toolIcons.get(index), toolKeys[index], toolTips[index], -1));
			toolX += toolWidth + GAP;
		}

		this.cardWidth = this.compact ? 0 : Math.min(150, Math.max(110, width / 4));
	}

	/** @param measure Zeichenfläche nur zum Messen von Textbreiten */
	public static NovaMenu layout(int width, int height, UnaryOperator<String> translate, NovaCanvas measure) {
		return new NovaMenu(width, height, translate, measure);
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	public List<MenuButton> buttons() {
		return Collections.unmodifiableList(this.buttons);
	}

	/** Abstand zum Rand für Copyright und Fußzeile. */
	public int margin() {
		return 6;
	}

	public boolean showPlayerCard() {
		return this.cardWidth > 0;
	}

	/** Position des Spielerkopfs, den die Versions-Schicht mit Minecrafts Skin zeichnet. */
	public int headX() {
		return this.width - 8 - this.cardWidth + 5;
	}

	public int headY() {
		return 8 + 4;
	}

	/* ========================================
	   ZEICHNEN
	======================================== */

	/** Leichte Abdunklung oben und unten – nach dem Panorama, vor den Widgets. */
	public void renderBackground(NovaCanvas canvas, float alpha) {
		canvas.gradient(0, 0, this.width, this.height / 3, 0x66131319, 0x00131319);
		canvas.gradient(0, this.height * 2 / 3, this.width, this.height, 0x00131319, 0x80131319);

		if (this.showPlayerCard()) {
			int x = this.width - 8 - this.cardWidth;
			canvas.fill(x, 8, x + this.cardWidth, 8 + 26, NovaDraw.fade(0xE617171D, alpha));
			int frame = NovaDraw.fade(0xFF47474F, alpha);
			canvas.fill(x, 8, x + this.cardWidth, 9, frame);
			canvas.fill(x, 33, x + this.cardWidth, 34, frame);
			canvas.fill(x, 8, x + 1, 34, frame);
			canvas.fill(x + this.cardWidth - 1, 8, x + this.cardWidth, 34, frame);
		}
	}

	/** Logo, Band, Splash, Spielerkarte und Fußzeile – nach den Widgets. */
	public void renderOverlay(NovaCanvas canvas, State state) {
		float alpha = state.alpha();
		if (alpha <= 0.02F) {
			return;
		}
		NovaDraw.logo(canvas, this.logoX, this.logoY, this.logoWidth, alpha);
		this.renderRibbon(canvas, state);
		this.renderSplash(canvas, state);
		if (this.showPlayerCard()) {
			this.renderPlayerCard(canvas, state);
		}
		this.renderFooter(canvas, state);
	}

	/** Dunkles Band mit spitzen Enden: "CLIENT EDITION". */
	private void renderRibbon(NovaCanvas canvas, State state) {
		float alpha = state.alpha();
		String text = state.translate().apply("nova.menu.edition").toUpperCase(Locale.ROOT);
		int textWidth = NovaDraw.spacedWidth(canvas, text, 2);
		int ribbonWidth = textWidth + 24;
		int x = (this.width - ribbonWidth) / 2;
		int y = this.ribbonY;
		int ribbonHeight = 13;

		int face = NovaDraw.fade(0xF01D1D23, alpha);
		int edge = NovaDraw.fade(0xFF47474F, alpha);
		canvas.fill(x, y, x + ribbonWidth, y + ribbonHeight, face);
		canvas.fill(x, y, x + ribbonWidth, y + 1, edge);
		canvas.fill(x, y + ribbonHeight - 1, x + ribbonWidth, y + ribbonHeight, edge);
		// Spitze Enden in Treppenstufen
		for (int step = 0; step < ribbonHeight / 2; step++) {
			canvas.fill(x - step - 1, y + step + 1, x, y + ribbonHeight - step - 1, face);
			canvas.fill(x + ribbonWidth, y + step + 1, x + ribbonWidth + step + 1, y + ribbonHeight - step - 1, face);
		}
		NovaDraw.spacedText(canvas, text, x + 12, y + 3, NovaDraw.fade(0xFFE4E4ED, alpha), 2, false);
	}

	/** Gelber Splash-Text wie im Vanilla-Menü, schräg rechts neben dem Logo. */
	private void renderSplash(NovaCanvas canvas, State state) {
		if (state.hideSplash()) {
			return;
		}
		String splash = state.translate().apply("nova.menu.splash");
		float pulse = 1.0F - Math.abs((float) Math.sin((System.currentTimeMillis() % 1000L) / 1000.0 * Math.PI * 2)) * 0.08F;
		float anchorX = this.logoX + this.logoWidth * 0.97F;
		float anchorY = this.logoY + NovaDraw.logoHeight(this.logoWidth) * 0.62F;
		float textWidth = canvas.textWidth(splash) + 1.0F;
		// Breite bis zum Rand, Höhe bis unter Werkzeugleiste und Spielerkarte (Anstieg bei -20° ≈ 0,34)
		float byWidth = (this.width - anchorX - 12.0F) / (textWidth * 0.94F);
		float byHeight = (anchorY - 40.0F) / (textWidth * 0.34F);
		float scale = pulse * NovaDraw.clamp(Math.min(byWidth, byHeight), 0.45F, 1.15F);
		canvas.push();
		// Beginnt rechts neben der Logo-Klammer und steigt nach rechts an
		canvas.translate(anchorX, anchorY);
		canvas.rotate(-20.0F);
		canvas.scale(scale);
		canvas.text(splash, 0, -4, NovaDraw.fade(NovaDraw.GOLD, state.alpha()), true);
		canvas.pop();
	}

	private void renderPlayerCard(NovaCanvas canvas, State state) {
		float alpha = state.alpha();
		int x = this.headX() + HEAD_SIZE + 6;
		int maxWidth = this.width - 8 - 5 - x;
		canvas.text(NovaDraw.trim(canvas, state.playerName(), maxWidth), x, 12, NovaDraw.fade(NovaDraw.WHITE, alpha), true);

		boolean microsoft = state.microsoftAccount();
		int dot = NovaDraw.fade(microsoft ? NovaDraw.ONLINE : NovaDraw.MUTED, alpha);
		canvas.fill(x, 25, x + 3, 28, dot);
		String status = state.translate().apply(microsoft ? "nova.menu.account.online" : "nova.menu.account.offline");
		NovaDraw.smallText(canvas, NovaDraw.trim(canvas, status, (int) ((maxWidth - 6) / 0.75F)), x + 6, 23.5F,
			NovaDraw.fade(microsoft ? 0xFF7FD68E : NovaDraw.MUTED, alpha), 0);
	}

	private void renderFooter(NovaCanvas canvas, State state) {
		float alpha = state.alpha();
		int color = NovaDraw.fade(NovaDraw.WHITE, alpha);
		canvas.text("Minecraft " + state.minecraftVersion(), 4, this.height - 20, color, true);
		canvas.text("Nova Client " + state.novaVersion(), 4, this.height - 10, color, true);

		// Hinweis über dem Copyright (das Copyright ist ein klickbares Vanilla-Widget unten rechts)
		String unofficial = state.translate().apply("nova.menu.unofficial");
		int unofficialWidth = canvas.textWidth(unofficial);
		if (this.width - unofficialWidth - this.margin() > 4 + canvas.textWidth("Nova Client " + state.novaVersion()) + 8) {
			canvas.text(unofficial, this.width - unofficialWidth - this.margin(), this.height - 22, NovaDraw.fade(0xFFE4E4ED, alpha), true);
		}
	}

	/** Badge-Text für den Nova-Server-Button (Spielerzahl), sonst {@code null}. */
	public static String serverBadge(State state) {
		return state.partnerPlayers() >= 0 ? String.format(Locale.GERMANY, "%,d", state.partnerPlayers()) : null;
	}
}
