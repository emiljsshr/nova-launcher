package gg.nova.core.menu;

import gg.nova.core.NovaCanvas;
import gg.nova.core.NovaDraw;
import gg.nova.core.Sprite;
import gg.nova.core.cosmetic.Cosmetics;
import gg.nova.core.mods.Module;
import gg.nova.core.mods.NovaGame;
import gg.nova.core.mods.NovaHud;
import gg.nova.core.mods.NovaMods;
import gg.nova.core.social.NovaSocial;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Nova-Menü im Spiel (Rechts-Shift): Mods an- und abschalten und einstellen, HUD-Editor, Cosmetics ohne
 * Neustart wechseln, Freunde und Chat. Zeichnen und Eingaben laufen komplett hier; die Versions-Schicht leitet
 * nur Maus und Tastatur weiter. Der Zustand (Bereich, offener Chat, Eingaben) bleibt zwischen zwei Öffnungen.
 */
public final class NovaModMenu {
	public enum Tab {
		MODS,
		COSMETICS,
		FRIENDS
	}

	/** Tasten, die das Menü selbst behandelt; alles andere geht als Zeichen über {@link #typed}. */
	public enum Key {
		ESCAPE,
		ENTER,
		BACKSPACE,
		OTHER
	}

	private enum Focus {
		NONE,
		ADD_FRIEND,
		CHAT,
		SEARCH
	}

	private static final int PANEL = 0xF5181B20;
	private static final int SIDEBAR = 0xFF14161A;
	private static final int CARD = 0xFF1F2328;
	private static final int CARD_HOVER = 0xFF252A30;
	private static final int ICON_TILE = 0xFF2A2F36;
	private static final int ICON_TILE_ON = 0xFF1C3A2C;
	private static final int GREEN = 0xFF3DDC84;
	private static final int TRACK_ON = 0xFF2FBF71;
	private static final int TRACK_OFF = 0xFF3A3F47;
	private static final int TEXT_DIM = 0xFF8B929C;
	private static final int TEXT_FAINT = 0xFF5F6670;
	private static final int DIVIDER = 0xFF2C3138;
	private static final int ACTIVE_ITEM = 0xFF2A2E35;
	private static final Sprite GEAR = Sprite.mono(0xFF8A8A92,
		"..a.a..",
		".aaaaa.",
		"aa...aa",
		".a...a.",
		"aa...aa",
		".aaaaa.",
		"..a.a..");

	private static final NovaModMenu INSTANCE = new NovaModMenu();

	private static final int BACKGROUND = 0xF0131319;
	private static final int HEADER = 0xFF1D1D23;
	private static final int TILE = 0xFF25252B;
	private static final int TILE_HOVER = 0xFF36363D;
	private static final int TILE_ACTIVE = 0xFF4A4A55;
	private static final int BORDER = 0xFF494950;
	private static final int OFFLINE = 0xFF63636B;
	private static final int HEADER_HEIGHT = 22;
	private static final int TILE_HEIGHT = 20;
	private static final int FRIEND_HEIGHT = 22;
	private static final int REQUEST_HEIGHT = 16;
	private static final int INPUT_HEIGHT = 16;
	private static final int NAME_LIMIT = 16;
	private static final int MESSAGE_LIMIT = 256;

	private static final String[] TYPES = {"CAPE", "HEAD", "WINGS", "PET", "TRAIL"};
	private static final String[] TYPE_LABELS = {"CAPE", "KOPF", "FLÜGEL", "PET", "SPUREN"};

	/** Klickbare Fläche aus dem letzten Bild. */
	private static final class Hit {
		final int x1;
		final int y1;
		final int x2;
		final int y2;
		final Runnable action;

		Hit(int x1, int y1, int x2, int y2, Runnable action) {
			this.x1 = x1;
			this.y1 = y1;
			this.x2 = x2;
			this.y2 = y2;
			this.action = action;
		}

		boolean contains(double x, double y) {
			return x >= this.x1 && x < this.x2 && y >= this.y1 && y < this.y2;
		}
	}

	private Tab tab = Tab.MODS;
	private Focus focus = Focus.NONE;
	/** Gewählte Kategorie, null = alle Mods. */
	private Module.Category category;
	/** Modul, dessen Einstellungen offen sind. */
	private Module settingsModule;
	private boolean globalSettings;
	private boolean hudEditor;
	/** X-Knopf geklickt: die Versions-Schicht schließt das Menü beim nächsten Bild. */
	private boolean closeRequested;
	private static String version;
	private final StringBuilder search = new StringBuilder();
	private int modsScroll;
	private String selectedFriend;
	private final StringBuilder friendName = new StringBuilder();
	private final StringBuilder draft = new StringBuilder();
	private int cosmeticScroll;
	private int friendScroll;
	/** Abstand vom unteren Ende des Verlaufs (0 = neueste Nachricht sichtbar). */
	private int chatScroll;
	private final List<Hit> hits = new ArrayList<Hit>();
	private final List<Hit> scrollAreas = new ArrayList<Hit>();
	private int[] scrollTargets = new int[0];

	private NovaModMenu() {
	}

	public static NovaModMenu get() {
		return INSTANCE;
	}

	/** Beim Öffnen; ein offener Chat lädt seinen Verlauf neu. */
	public void opened(Tab startTab) {
		if (startTab != null) {
			this.tab = startTab;
		}
		this.focus = this.tab == Tab.FRIENDS && this.selectedFriend != null ? Focus.CHAT : Focus.NONE;
		this.hudEditor = false;
		NovaSocial.openChat(this.tab == Tab.FRIENDS ? this.selectedFriend : null);
	}

	/** Einmal true, nachdem der Schließen-Knopf geklickt wurde. */
	public boolean consumeClose() {
		boolean close = this.closeRequested;
		this.closeRequested = false;
		return close;
	}

	/** Version der Mod (assets/nova/version.txt, beim Bauen gefüllt), ohne "+mc…". */
	private static String version() {
		if (version == null) {
			version = "";
			try {
				java.io.InputStream in = NovaModMenu.class.getResourceAsStream("/assets/nova/version.txt");
				if (in != null) {
					java.util.Scanner scanner = new java.util.Scanner(in, "UTF-8").useDelimiter("\\A");
					version = scanner.hasNext() ? scanner.next().trim().replaceAll("\\+.*", "") : "";
					in.close();
				}
			} catch (java.io.IOException | RuntimeException error) {
				version = "";
			}
		}
		return version;
	}

	/** HUD-Editor direkt öffnen. */
	public void openHudEditor() {
		this.tab = Tab.MODS;
		this.hudEditor = true;
	}

	/** Einstellungen eines Moduls direkt öffnen. */
	public void openSettings(Module module) {
		this.tab = Tab.MODS;
		this.settingsModule = module;
	}

	/** Wechselt zu den Freunden mit offenem Chat. */
	public void openChatWith(String friendId) {
		this.tab = Tab.FRIENDS;
		this.selectedFriend = friendId;
		this.chatScroll = 0;
		this.focus = Focus.CHAT;
		NovaSocial.openChat(friendId);
	}

	public void closed() {
		NovaSocial.openChat(null);
		this.focus = Focus.NONE;
		this.hudEditor = false;
		NovaHud.release();
	}

	// Eingaben

	public boolean click(double x, double y) {
		if (this.hudEditor) {
			for (int i = this.hits.size() - 1; i >= 0; i--) {
				if (this.hits.get(i).contains(x, y)) {
					this.hits.get(i).action.run();
					return true;
				}
			}
			return NovaHud.press(x, y);
		}
		for (int i = this.hits.size() - 1; i >= 0; i--) {
			Hit hit = this.hits.get(i);
			if (hit.contains(x, y)) {
				hit.action.run();
				return true;
			}
		}
		this.focus = Focus.NONE;
		return false;
	}

	/** Maustaste losgelassen (HUD-Editor: Modul ablegen). */
	public void release() {
		NovaHud.release();
	}

	/** @param amount positiv = nach oben scrollen */
	public boolean scroll(double x, double y, double amount) {
		int step = (int) Math.round(amount * 12);
		for (int i = 0; i < this.scrollAreas.size(); i++) {
			if (this.scrollAreas.get(i).contains(x, y)) {
				int target = this.scrollTargets[i];
				if (target == 0) {
					this.cosmeticScroll -= step;
				} else if (target == 1) {
					this.friendScroll -= step;
				} else if (target == 3) {
					this.modsScroll -= step;
				} else {
					this.chatScroll += step;
				}
				return true;
			}
		}
		return false;
	}

	/** @return true, wenn das Menü die Taste verbraucht hat (Escape ohne Eingabefeld schließt das Menü) */
	public boolean key(Key key) {
		if (key == Key.ESCAPE) {
			if (this.focus != Focus.NONE) {
				this.focus = Focus.NONE;
				return true;
			}
			// Erst Editor und Einstellungen verlassen, dann das Menü
			if (this.hudEditor || this.settingsModule != null || this.globalSettings) {
				this.hudEditor = false;
				this.settingsModule = null;
				this.globalSettings = false;
				NovaHud.release();
				return true;
			}
			return false;
		}
		StringBuilder field = this.field();
		if (field == null) {
			return false;
		}
		if (key == Key.BACKSPACE && field.length() > 0) {
			field.setLength(field.offsetByCodePoints(field.length(), -1));
		} else if (key == Key.ENTER) {
			this.submit();
		}
		return true;
	}

	/** Zeicheneingabe; true, wenn ein Eingabefeld sie aufgenommen hat. */
	public boolean typed(char character) {
		StringBuilder field = this.field();
		if (field == null || character < ' ' || character == '§' || character == 127) {
			return false;
		}
		if (this.focus == Focus.SEARCH) {
			if (field.length() < 24) {
				field.append(character);
				this.modsScroll = 0;
			}
		} else if (this.focus == Focus.ADD_FRIEND) {
			boolean allowed = Character.isLetterOrDigit(character) && character < 128 || character == '_';
			if (allowed && field.length() < NAME_LIMIT) {
				field.append(character);
			}
		} else if (field.length() < MESSAGE_LIMIT) {
			field.append(character);
		}
		return true;
	}

	private StringBuilder field() {
		return this.focus == Focus.ADD_FRIEND ? this.friendName : this.focus == Focus.CHAT ? this.draft
			: this.focus == Focus.SEARCH ? this.search : null;
	}

	private void submit() {
		if (this.focus == Focus.ADD_FRIEND && this.friendName.length() > 0) {
			NovaSocial.requestFriend(this.friendName.toString());
			this.friendName.setLength(0);
		} else if (this.focus == Focus.CHAT && this.selectedFriend != null) {
			String text = this.draft.toString().trim();
			if (!text.isEmpty()) {
				NovaSocial.sendMessage(this.selectedFriend, text);
				this.draft.setLength(0);
				this.chatScroll = 0;
			}
		}
	}

	// Zeichnen

	private static String t(String key) {
		NovaGame game = NovaMods.game();
		return game == null ? key : game.translate(key);
	}

	public void render(NovaCanvas canvas, int mouseX, int mouseY) {
		this.hits.clear();
		this.scrollAreas.clear();
		List<Integer> targets = new ArrayList<Integer>();

		if (this.hudEditor) {
			this.renderHudEditor(canvas, mouseX, mouseY);
			return;
		}

		int width = Math.min(730, canvas.width() - 24);
		int height = Math.min(430, canvas.height() - 36);
		int left = (canvas.width() - width) / 2;
		int top = (canvas.height() - height) / 2 - 6;

		canvas.fill(0, 0, canvas.width(), canvas.height(), 0x99000000);
		NovaDraw.rounded(canvas, left - 1, top - 1, width + 2, height + 2, 7, 0xFF262A30);
		NovaDraw.rounded(canvas, left, top, width, height, 6, PANEL);
		int sidebarWidth = Math.max(112, Math.min(130, width * 18 / 100));
		NovaDraw.rounded(canvas, left, top, sidebarWidth + 6, height, 6, SIDEBAR);
		canvas.fill(left + sidebarWidth, top, left + sidebarWidth + 6, top + height, PANEL);
		canvas.fill(left + sidebarWidth, top, left + sidebarWidth + 1, top + height, DIVIDER);
		this.renderSidebar(canvas, left, top, sidebarWidth, height, mouseX, mouseY);

		int contentX = left + sidebarWidth + 14;
		int contentY = top + 14;
		int contentWidth = width - sidebarWidth - 28;
		int contentHeight = height - 28;
		String notice = NovaSocial.notice();
		if (notice != null) {
			contentHeight -= 12;
			NovaDraw.smallText(canvas, NovaDraw.trim(canvas, notice, (int) (contentWidth / 0.75F)), contentX, top + height - 13, GREEN, 0);
		}

		// Schließen oben rechts
		int closeX = left + width - 14 - 21;
		boolean closeHovered = inside(mouseX, mouseY, closeX, contentY, closeX + 21, contentY + 21);
		NovaDraw.rounded(canvas, closeX, contentY, 21, 21, 4, closeHovered ? 0xFF353A42 : 0xFF262A30);
		for (int i = 0; i < 7; i++) {
			canvas.fill(closeX + 7 + i, contentY + 7 + i, closeX + 8 + i, contentY + 8 + i, NovaDraw.WHITE);
			canvas.fill(closeX + 13 - i, contentY + 7 + i, closeX + 14 - i, contentY + 8 + i, NovaDraw.WHITE);
		}
		this.hits.add(new Hit(closeX, contentY, closeX + 21, contentY + 21, new Runnable() {
			@Override
			public void run() {
				NovaModMenu.this.closeRequested = true;
			}
		}));

		if (this.tab == Tab.MODS) {
			if (this.settingsModule != null) {
				this.renderModuleSettings(canvas, this.settingsModule, contentX, contentY, contentWidth - 29, mouseX, mouseY);
			} else if (this.globalSettings) {
				this.renderGlobalSettings(canvas, contentX, contentY, contentWidth - 29, mouseX, mouseY);
			} else {
				this.renderMods(canvas, contentX, contentY, contentWidth, contentHeight, mouseX, mouseY, targets);
			}
		} else {
			String title = this.tab == Tab.COSMETICS ? t("nova.menu.cosmetics") : t("nova.mods.friends");
			this.title(canvas, title, null, contentX, contentY);
			int bodyY = contentY + 32;
			if (this.tab == Tab.COSMETICS) {
				this.renderCosmetics(canvas, contentX, bodyY, contentWidth, contentHeight - 32, mouseX, mouseY, targets);
			} else {
				this.renderFriends(canvas, contentX, bodyY, contentWidth, contentHeight - 32, mouseX, mouseY, targets);
			}
		}

		// Hinweis unter dem Fenster: "Press [Right Shift] to close"
		String[] hint = t("nova.mods.close").split("\\|");
		if (hint.length == 3) {
			int keyWidth = canvas.textWidth(hint[1]) + 8;
			int total = canvas.textWidth(hint[0]) + keyWidth + canvas.textWidth(hint[2]) + 8;
			int hintX = (canvas.width() - total) / 2;
			int hintY = top + height + 10;
			if (hintY + 12 <= canvas.height()) {
				canvas.text(hint[0], hintX, hintY + 2, TEXT_DIM, false);
				hintX += canvas.textWidth(hint[0]) + 4;
				NovaDraw.rounded(canvas, hintX, hintY - 1, keyWidth, 12, 2, 0xFF2A2E35);
				canvas.text(hint[1], hintX + 4, hintY + 2, NovaDraw.WHITE, false);
				canvas.text(hint[2], hintX + keyWidth + 4, hintY + 2, TEXT_DIM, false);
			}
		}

		this.scrollTargets = new int[targets.size()];
		for (int i = 0; i < targets.size(); i++) {
			this.scrollTargets[i] = targets.get(i);
		}
	}

	/** Überschrift (fett, größer) mit grauer Unterzeile. */
	private void title(NovaCanvas canvas, String title, String subtitle, int x, int y) {
		canvas.push();
		canvas.translate(x, y + 1);
		canvas.scale(1.5F);
		canvas.text("§l" + title, 0, 0, NovaDraw.WHITE, false);
		canvas.pop();
		if (subtitle != null) {
			canvas.text(subtitle, x, y + 17, TEXT_DIM, false);
		}
	}

	/* ========================================
	   SEITENLEISTE
	======================================== */

	private void renderSidebar(NovaCanvas canvas, int left, int top, int width, int height, int mouseX, int mouseY) {
		boolean compact = height < 300;
		// Wappen, "Nova Client", Version
		int emblemHeight = compact ? 14 : 18;
		NovaDraw.logo(canvas, left + 12, top + 12, NovaDraw.logoWidth(emblemHeight), 1.0F);
		int textX = left + 17 + NovaDraw.logoWidth(emblemHeight);
		canvas.text("§lNova Client", textX, top + 13, NovaDraw.WHITE, false);
		if (!compact) {
			NovaDraw.smallText(canvas, "v" + version(), textX, top + 24, TEXT_FAINT, 0);
		}

		int x = left + 9;
		int itemWidth = width - 18;
		int buttonHeight = compact ? 16 : 22;
		int buttonY = top + height - 10 - 2 * buttonHeight - 5;
		int y = top + (compact ? 30 : 42);
		int sections = 2;
		int items = Module.Category.values().length + 3;
		int step = Math.max(12, Math.min(24, (buttonY - 6 - y - sections * 12) / items));
		int itemHeight = Math.min(22, step - 2);

		NovaDraw.smallText(canvas, t("nova.mods.categories").toUpperCase(Locale.ROOT), x + 5, y, TEXT_FAINT, 1);
		y += 12;
		this.navItem(canvas, x, y, itemWidth, itemHeight, t("nova.mods.category.all"), NovaMods.modules().size(),
			this.tab == Tab.MODS && this.category == null && this.settingsModule == null && !this.globalSettings, mouseX, mouseY, Tab.MODS, null);
		y += step;
		for (Module.Category candidate : Module.Category.values()) {
			int count = 0;
			for (Module module : NovaMods.modules()) {
				if (module.categories.contains(candidate)) {
					count++;
				}
			}
			this.navItem(canvas, x, y, itemWidth, itemHeight, t(candidate.key), count,
				this.tab == Tab.MODS && this.category == candidate && this.settingsModule == null && !this.globalSettings, mouseX, mouseY, Tab.MODS, candidate);
			y += step;
		}
		y += 4;
		NovaDraw.smallText(canvas, "NOVA", x + 5, y, TEXT_FAINT, 1);
		y += 12;
		this.navItem(canvas, x, y, itemWidth, itemHeight, t("nova.menu.cosmetics"), -1, this.tab == Tab.COSMETICS, mouseX, mouseY, Tab.COSMETICS, null);
		y += step;
		int unread = NovaSocial.unreadTotal() + NovaSocial.incoming().size();
		this.navItem(canvas, x, y, itemWidth, itemHeight, t("nova.mods.friends"), unread > 0 ? unread : -1, this.tab == Tab.FRIENDS, mouseX, mouseY, Tab.FRIENDS, null);

		// Unten: HUD-Editor (hell) und Einstellungen (dunkel)
		this.button(canvas, x, buttonY, itemWidth, buttonHeight, t("nova.mods.editHud"), true, mouseX, mouseY, new Runnable() {
			@Override
			public void run() {
				NovaModMenu.this.hudEditor = true;
				NovaModMenu.this.focus = Focus.NONE;
			}
		});
		this.button(canvas, x, buttonY + buttonHeight + 5, itemWidth, buttonHeight, t("nova.mods.settings"), false, mouseX, mouseY, new Runnable() {
			@Override
			public void run() {
				NovaModMenu.this.tab = Tab.MODS;
				NovaModMenu.this.settingsModule = null;
				NovaModMenu.this.globalSettings = true;
			}
		});
	}

	private void navItem(NovaCanvas canvas, int x, int y, int width, int height, String label, int count, boolean active, int mouseX, int mouseY,
			final Tab target, final Module.Category targetCategory) {
		boolean hovered = inside(mouseX, mouseY, x, y, x + width, y + height);
		if (active || hovered) {
			NovaDraw.rounded(canvas, x, y, width, height, 4, active ? ACTIVE_ITEM : 0xFF1D2025);
		}
		int textY = y + (height - 8) / 2 + 1;
		canvas.text(NovaDraw.trim(canvas, (active ? "§l" : "") + label, width - 30), x + 7, textY, active ? NovaDraw.WHITE : 0xFFC9CED6, false);
		if (count >= 0) {
			String number = String.valueOf(count);
			NovaDraw.smallText(canvas, number, x + width - 7 - NovaDraw.smallWidth(canvas, number, 0), textY + 1, TEXT_FAINT, 0);
		}
		this.hits.add(new Hit(x, y, x + width, y + height, new Runnable() {
			@Override
			public void run() {
				NovaModMenu.this.settingsModule = null;
				NovaModMenu.this.globalSettings = false;
				NovaModMenu.this.category = targetCategory;
				NovaModMenu.this.modsScroll = 0;
				if (NovaModMenu.this.tab != target) {
					NovaModMenu.this.opened(target);
				}
			}
		}));
	}

	/** Flacher Button mit runden Ecken; {@code primary} = hell mit dunkler Schrift. */
	private void button(NovaCanvas canvas, int x, int y, int width, int height, String label, boolean primary, int mouseX, int mouseY, Runnable action) {
		boolean hovered = inside(mouseX, mouseY, x, y, x + width, y + height);
		int face = primary ? (hovered ? 0xFFE6E8EB : NovaDraw.WHITE) : (hovered ? 0xFF2E333A : 0xFF23272D);
		NovaDraw.rounded(canvas, x, y, width, height, 4, face);
		String text = NovaDraw.trim(canvas, (primary ? "§l" : "") + label, width - 8);
		canvas.text(text, x + (width - canvas.textWidth(text)) / 2, y + (height - 8) / 2 + 1, primary ? 0xFF14161A : NovaDraw.WHITE, false);
		this.hits.add(new Hit(x, y, x + width, y + height, action));
	}

	private static boolean inside(int mouseX, int mouseY, int x1, int y1, int x2, int y2) {
		return mouseX >= x1 && mouseX < x2 && mouseY >= y1 && mouseY < y2;
	}

	/** Schalter wie im Launcher: grün mit Knopf rechts oder grau mit Knopf links. */
	private static void toggle(NovaCanvas canvas, int x, int y, boolean on) {
		NovaDraw.rounded(canvas, x, y, 20, 11, 5, on ? TRACK_ON : TRACK_OFF);
		int knobX = on ? x + 10 : x + 1;
		NovaDraw.rounded(canvas, knobX, y + 1, 9, 9, 4, NovaDraw.WHITE);
	}

	/* ========================================
	   MODS
	======================================== */

	private void renderMods(NovaCanvas canvas, int x, int y, int width, int height, int mouseX, int mouseY, List<Integer> targets) {
		String heading = this.category == null ? t("nova.mods.category.all") : t(this.category.key);
		List<Module> shown = new ArrayList<Module>();
		String query = this.search.toString();
		String needle = query.toLowerCase(Locale.ROOT);
		int enabled = 0;
		int inCategoryCount = 0;
		for (Module module : NovaMods.modules()) {
			boolean inCategory = this.category == null || module.categories.contains(this.category);
			if (inCategory) {
				inCategoryCount++;
				enabled += module.enabled() ? 1 : 0;
			}
			if (inCategory && (needle.isEmpty() || module.name.toLowerCase(Locale.ROOT).contains(needle))) {
				shown.add(module);
			}
		}
		// Ohne Platzhalter übersetzen – Minecraft meldet sonst "Format error"
		this.title(canvas, heading, enabled + " " + t("nova.mods.of") + " " + inCategoryCount + " " + t("nova.mods.count"), x, y);

		// Suche oben rechts, links neben dem Schließen-Knopf
		int searchWidth = Math.min(170, width / 3);
		int searchX = x + width - 29 - searchWidth;
		boolean searching = this.focus == Focus.SEARCH;
		NovaDraw.roundedFrame(canvas, searchX, y, searchWidth, 21, 4, searching ? 0xFF4A505A : 0xFF262A30, 0xFF1D2025);
		// Lupe
		canvas.fill(searchX + 8, y + 7, searchX + 12, y + 8, TEXT_DIM);
		canvas.fill(searchX + 8, y + 11, searchX + 12, y + 12, TEXT_DIM);
		canvas.fill(searchX + 7, y + 8, searchX + 8, y + 11, TEXT_DIM);
		canvas.fill(searchX + 12, y + 8, searchX + 13, y + 11, TEXT_DIM);
		canvas.fill(searchX + 13, y + 12, searchX + 15, y + 14, TEXT_DIM);
		if (query.isEmpty() && !searching) {
			canvas.text(t("nova.mods.search"), searchX + 20, y + 7, TEXT_FAINT, false);
		} else {
			boolean caret = searching && System.currentTimeMillis() / 500 % 2 == 0;
			canvas.text(NovaDraw.trim(canvas, query, searchWidth - 30) + (caret ? "_" : ""), searchX + 20, y + 7, NovaDraw.WHITE, false);
		}
		this.hits.add(new Hit(searchX, y, searchX + searchWidth, y + 21, new Runnable() {
			@Override
			public void run() {
				NovaModMenu.this.focus = Focus.SEARCH;
			}
		}));

		// Karten
		int gridY = y + 34;
		int gridHeight = height - 34;
		int gap = 7;
		int columns = Math.max(2, Math.min(5, (width + gap) / (100 + gap)));
		int cardWidth = (width - (columns - 1) * gap) / columns;
		int cardHeight = 88;
		int rows = (shown.size() + columns - 1) / columns;
		int total = rows * (cardHeight + gap) - gap;
		this.modsScroll = Math.max(0, Math.min(this.modsScroll, total - gridHeight));
		this.scrollAreas.add(new Hit(x, gridY, x + width, gridY + gridHeight, null));
		targets.add(3);

		canvas.enableScissor(x - 2, gridY - 2, x + width + 2, gridY + gridHeight);
		for (int index = 0; index < shown.size(); index++) {
			int cardX = x + index % columns * (cardWidth + gap);
			int cardY = gridY + index / columns * (cardHeight + gap) - this.modsScroll;
			if (cardY + cardHeight < gridY || cardY > gridY + gridHeight) {
				continue;
			}
			this.card(canvas, shown.get(index), cardX, cardY, cardWidth, cardHeight, gridY, gridY + gridHeight, mouseX, mouseY);
		}
		canvas.disableScissor();
		if (shown.isEmpty()) {
			String empty = t("nova.mods.none");
			canvas.text(empty, x + (width - canvas.textWidth(empty)) / 2, gridY + 24, TEXT_DIM, false);
		}
	}

	private void card(NovaCanvas canvas, final Module module, int x, int y, int width, int height, int clipTop, int clipBottom, int mouseX, int mouseY) {
		boolean visible = mouseY >= clipTop && mouseY < clipBottom;
		boolean hovered = visible && inside(mouseX, mouseY, x, y, x + width, y + height);
		boolean on = module.enabled();
		if (hovered) {
			NovaDraw.roundedFrame(canvas, x, y, width, height, 5, NovaDraw.WHITE, CARD_HOVER);
		} else {
			NovaDraw.rounded(canvas, x, y, width, height, 5, CARD);
		}

		// Icon-Kachel oben links, grün getönt wenn aktiv
		int tile = 22;
		NovaDraw.rounded(canvas, x + 8, y + 8, tile, tile, 4, on ? ICON_TILE_ON : ICON_TILE);
		module.icon.drawTinted(canvas, x + 8 + (tile - module.icon.width()) / 2, y + 8 + (tile - 9) / 2, 1, on ? GREEN : 0xFFB4BAC2);

		// Name (fett) und erste Kategorie
		canvas.text(NovaDraw.trim(canvas, "§l" + module.name, width - 16), x + 8, y + 37, NovaDraw.WHITE, false);
		canvas.text(t(module.categories.get(0).key), x + 8, y + 48, TEXT_DIM, false);

		// Trennlinie, Status und Schalter
		canvas.fill(x + 8, y + height - 25, x + width - 8, y + height - 24, DIVIDER);
		String status = t(on ? "nova.mods.enabled" : "nova.mods.disabled");
		canvas.text(on ? "§l" + status : status, x + 8, y + height - 15, on ? GREEN : TEXT_DIM, false);
		toggle(canvas, x + width - 8 - 20, y + height - 17, on);

		// Zahnrad oben rechts öffnet die Einstellungen
		int gearX = x + width - 15;
		int gearY = y + 8;
		boolean gearHovered = visible && inside(mouseX, mouseY, gearX - 3, gearY - 3, gearX + 10, gearY + 10);
		if (gearHovered) {
			NovaDraw.rounded(canvas, gearX - 3, gearY - 3, 13, 13, 3, 0xFF353A42);
		}
		GEAR.drawTinted(canvas, gearX, gearY, 1, gearHovered ? NovaDraw.WHITE : TEXT_DIM);

		int top = Math.max(y, clipTop);
		int bottom = Math.min(y + height, clipBottom);
		if (bottom <= top) {
			return;
		}
		this.hits.add(new Hit(x, top, x + width, bottom, new Runnable() {
			@Override
			public void run() {
				module.toggle();
			}
		}));
		if (gearY - 3 >= top && gearY + 10 <= bottom) {
			this.hits.add(new Hit(gearX - 3, gearY - 3, gearX + 10, gearY + 10, new Runnable() {
				@Override
				public void run() {
					NovaModMenu.this.settingsModule = module;
				}
			}));
		}
	}

	/** Einstellungen eines Moduls: an/aus und seine Optionen, jede Zeile ein Schalter oder eine Auswahl. */
	private void renderModuleSettings(NovaCanvas canvas, final Module module, int x, int y, int width, int mouseX, int mouseY) {
		this.button(canvas, x, y + 1, 22, 20, "<", false, mouseX, mouseY, new Runnable() {
			@Override
			public void run() {
				NovaModMenu.this.settingsModule = null;
			}
		});
		this.title(canvas, module.name, t(module.categories.get(0).key), x + 30, y);

		int rowY = y + 38;
		rowY = this.settingRow(canvas, x, rowY, width, t("nova.mods.setting.enabled"), null, module.enabled(), mouseX, mouseY, new Runnable() {
			@Override
			public void run() {
				module.toggle();
			}
		});
		for (final Module.Setting setting : module.settings()) {
			rowY = this.settingRow(canvas, x, rowY, width, t(setting.key), setting.toggle() ? null : valueLabel(setting), setting.on(), mouseX, mouseY,
				new Runnable() {
					@Override
					public void run() {
						setting.next();
					}
				});
		}
		if (module.hud) {
			canvas.text(t("nova.mods.hudTip"), x, rowY + 6, TEXT_FAINT, false);
		}
	}

	private static String valueLabel(Module.Setting setting) {
		String value = setting.value();
		if ("scale".equals(setting.id)) {
			return value + " %";
		}
		if ("factor".equals(setting.id)) {
			return value + "×";
		}
		return t("nova.mods.value." + value);
	}

	/** Zeile mit Schalter ({@code value} null) oder Auswahl-Wert, der sich per Klick weiterdreht. */
	private int settingRow(NovaCanvas canvas, int x, int y, int width, String label, String value, boolean on, int mouseX, int mouseY, Runnable action) {
		boolean hovered = inside(mouseX, mouseY, x, y, x + width, y + 26);
		NovaDraw.rounded(canvas, x, y, width, 26, 4, hovered ? CARD_HOVER : CARD);
		canvas.text(label, x + 10, y + 9, NovaDraw.WHITE, false);
		if (value == null) {
			toggle(canvas, x + width - 10 - 20, y + 8, on);
		} else {
			int valueWidth = Math.max(54, canvas.textWidth(value) + 22);
			int valueX = x + width - 8 - valueWidth;
			NovaDraw.rounded(canvas, valueX, y + 5, valueWidth, 16, 3, ICON_TILE);
			canvas.text(value, valueX + 8, y + 9, NovaDraw.WHITE, false);
			canvas.text("›", valueX + valueWidth - 9, y + 9, TEXT_DIM, false);
		}
		this.hits.add(new Hit(x, y, x + width, y + 26, action));
		return y + 30;
	}

	private void renderGlobalSettings(NovaCanvas canvas, int x, int y, int width, int mouseX, int mouseY) {
		this.button(canvas, x, y + 1, 22, 20, "<", false, mouseX, mouseY, new Runnable() {
			@Override
			public void run() {
				NovaModMenu.this.globalSettings = false;
			}
		});
		this.title(canvas, t("nova.mods.settings"), "Nova Client v" + version(), x + 30, y);
		int rowY = y + 38;
		rowY = this.settingRow(canvas, x, rowY, width, t("nova.mods.friendAlerts"), null, NovaSocial.chatAlerts(), mouseX, mouseY, new Runnable() {
			@Override
			public void run() {
				NovaSocial.setChatAlerts(!NovaSocial.chatAlerts());
			}
		});
		this.settingRow(canvas, x, rowY, width, t("nova.mods.reset"), t("nova.mods.resetButton"), false, mouseX, mouseY, new Runnable() {
			@Override
			public void run() {
				NovaMods.reset();
			}
		});
	}

	/** HUD-Editor: nur die HUD-Module, verschiebbar; oben ein Hinweis und "Fertig". */
	private void renderHudEditor(NovaCanvas canvas, int mouseX, int mouseY) {
		NovaHud.renderEditor(canvas, mouseX, mouseY);
		String hint = t("nova.mods.hudHint");
		int width = canvas.textWidth(hint) + 16;
		int x = (canvas.width() - width) / 2;
		NovaDraw.rounded(canvas, x, 7, width, 16, 4, 0xD0181B20);
		canvas.text(hint, x + 8, 11, 0xFFC6C6CE, true);
		this.button(canvas, (canvas.width() - 80) / 2, 26, 80, 20, t("nova.mods.done"), true, mouseX, mouseY, new Runnable() {
			@Override
			public void run() {
				NovaModMenu.this.hudEditor = false;
			}
		});
	}

	private void renderCosmetics(NovaCanvas canvas, int x, int y, int width, int height, int mouseX, int mouseY, List<Integer> targets) {
		List<Cosmetics.Entry> catalog = Cosmetics.catalog();
		List<String> equipped = Cosmetics.equippedIds();
		int columns = width >= 300 ? 2 : 1;
		int tileWidth = (width - 6 * (columns - 1)) / columns;

		// Gesamthöhe für die Scroll-Grenzen
		int total = 0;
		for (String type : TYPES) {
			int count = countOfType(catalog, type);
			if (count > 0) {
				total += 14 + (count + columns - 1) / columns * (TILE_HEIGHT + 4) + 4;
			}
		}
		this.cosmeticScroll = Math.max(0, Math.min(this.cosmeticScroll, total - height));
		this.scrollAreas.add(new Hit(x, y, x + width, y + height, null));
		targets.add(0);

		canvas.enableScissor(x, y, x + width, y + height);
		int cursor = y - this.cosmeticScroll;
		for (int t = 0; t < TYPES.length; t++) {
			String type = TYPES[t];
			int count = countOfType(catalog, type);
			if (count == 0) {
				continue;
			}
			int limit = Cosmetics.slotLimit(type);
			String header = TYPE_LABELS[t] + (limit > 1 ? " · " + countEquipped(catalog, equipped, type) + "/" + limit : "");
			NovaDraw.smallText(canvas, header, x, cursor + 3, NovaDraw.MUTED, 1);
			cursor += 14;
			int index = 0;
			for (final Cosmetics.Entry entry : catalog) {
				if (!entry.type.equals(type)) {
					continue;
				}
				int tileX = x + index % columns * (tileWidth + 6);
				int tileY = cursor + index / columns * (TILE_HEIGHT + 4);
				boolean on = equipped.contains(entry.id);
				boolean hovered = mouseX >= tileX && mouseX < tileX + tileWidth && mouseY >= tileY && mouseY < tileY + TILE_HEIGHT
					&& mouseY >= y && mouseY < y + height;
				canvas.fill(tileX, tileY, tileX + tileWidth, tileY + TILE_HEIGHT, on ? TILE_ACTIVE : hovered ? TILE_HOVER : TILE);
				if (on) {
					canvas.fill(tileX, tileY, tileX + 2, tileY + TILE_HEIGHT, NovaDraw.FROST);
				}
				// Kästchen
				int boxX = tileX + 7;
				int boxY = tileY + 6;
				canvas.fill(boxX, boxY, boxX + 8, boxY + 8, on ? NovaDraw.FROST : NovaDraw.SOFT);
				canvas.fill(boxX + 1, boxY + 1, boxX + 7, boxY + 7, on ? NovaDraw.FROST_STRONG : TILE);
				if (on) {
					canvas.fill(boxX + 2, boxY + 2, boxX + 6, boxY + 6, NovaDraw.WHITE);
				}
				canvas.text(NovaDraw.trim(canvas, entry.name, tileWidth - 26), tileX + 21, tileY + 6, NovaDraw.WHITE, false);
				if (tileY + TILE_HEIGHT > y && tileY < y + height) {
					this.hits.add(new Hit(tileX, Math.max(tileY, y), tileX + tileWidth, Math.min(tileY + TILE_HEIGHT, y + height), new Runnable() {
						@Override
						public void run() {
							NovaSocial.sendLoadout(Cosmetics.toggle(entry.id));
						}
					}));
				}
				index++;
			}
			cursor += (count + columns - 1) / columns * (TILE_HEIGHT + 4) + 4;
		}
		canvas.disableScissor();
		if (catalog.isEmpty()) {
			canvas.text("Keine Cosmetics gefunden.", x, y, NovaDraw.MUTED, false);
		}
	}

	private static int countOfType(List<Cosmetics.Entry> catalog, String type) {
		int count = 0;
		for (Cosmetics.Entry entry : catalog) {
			if (entry.type.equals(type)) {
				count++;
			}
		}
		return count;
	}

	private static int countEquipped(List<Cosmetics.Entry> catalog, List<String> equipped, String type) {
		int count = 0;
		for (Cosmetics.Entry entry : catalog) {
			if (entry.type.equals(type) && equipped.contains(entry.id)) {
				count++;
			}
		}
		return count;
	}

	private void renderFriends(NovaCanvas canvas, int x, int y, int width, int height, int mouseX, int mouseY, List<Integer> targets) {
		if (!NovaSocial.available()) {
			String line1 = NovaSocial.bridged() ? "Verbinde mit dem Nova-Server …" : "Freunde und Chat gibt es, wenn du";
			String line2 = NovaSocial.bridged() ? "" : "Minecraft über den Nova Launcher startest.";
			canvas.text(line1, x + (width - canvas.textWidth(line1)) / 2, y + height / 2 - 10, NovaDraw.SOFT, false);
			canvas.text(line2, x + (width - canvas.textWidth(line2)) / 2, y + height / 2 + 2, NovaDraw.SOFT, false);
			return;
		}

		int listWidth = Math.min(170, width * 2 / 5);
		this.input(canvas, x, y, listWidth, this.friendName, "Freund hinzufügen …", Focus.ADD_FRIEND);
		this.renderFriendList(canvas, x, y + INPUT_HEIGHT + 6, listWidth, height - INPUT_HEIGHT - 6, mouseX, mouseY, targets);

		int chatX = x + listWidth + 8;
		int chatWidth = width - listWidth - 8;
		canvas.fill(chatX, y, chatX + chatWidth, y + height, 0xFF17171D);
		NovaSocial.Friend friend = null;
		for (NovaSocial.Friend candidate : NovaSocial.friends()) {
			if (candidate.id.equals(this.selectedFriend)) {
				friend = candidate;
			}
		}
		if (friend == null) {
			String hint = "Wähle links einen Freund aus.";
			canvas.text(hint, chatX + (chatWidth - canvas.textWidth(hint)) / 2, y + height / 2 - 4, NovaDraw.MUTED, false);
			return;
		}
		this.renderChat(canvas, friend, chatX, y, chatWidth, height, targets);
	}

	private void renderFriendList(NovaCanvas canvas, int x, int y, int width, int height, int mouseX, int mouseY, List<Integer> targets) {
		List<NovaSocial.Request> incoming = NovaSocial.incoming();
		List<NovaSocial.Friend> friends = new ArrayList<NovaSocial.Friend>(NovaSocial.friends());
		// Spielende zuerst, dann Launcher, dann offline
		java.util.Collections.sort(friends, new java.util.Comparator<NovaSocial.Friend>() {
			@Override
			public int compare(NovaSocial.Friend a, NovaSocial.Friend b) {
				int rank = rank(a) - rank(b);
				return rank != 0 ? rank : a.name.compareToIgnoreCase(b.name);
			}
		});

		int total = (incoming.isEmpty() ? 0 : 12 + incoming.size() * REQUEST_HEIGHT + 4) + friends.size() * FRIEND_HEIGHT;
		this.friendScroll = Math.max(0, Math.min(this.friendScroll, total - height));
		this.scrollAreas.add(new Hit(x, y, x + width, y + height, null));
		targets.add(1);

		canvas.enableScissor(x, y, x + width, y + height);
		int cursor = y - this.friendScroll;
		if (!incoming.isEmpty()) {
			NovaDraw.smallText(canvas, "ANFRAGEN", x, cursor + 2, NovaDraw.MUTED, 1);
			cursor += 12;
			for (final NovaSocial.Request request : incoming) {
				canvas.fill(x, cursor, x + width, cursor + REQUEST_HEIGHT - 2, TILE);
				canvas.text(NovaDraw.trim(canvas, request.name, width - 40), x + 4, cursor + 3, NovaDraw.WHITE, false);
				this.iconButton(canvas, x + width - 30, cursor + 1, "✔", NovaDraw.ONLINE, y, height, new Runnable() {
					@Override
					public void run() {
						NovaSocial.acceptFriend(request.id);
					}
				});
				this.iconButton(canvas, x + width - 15, cursor + 1, "✕", 0xFFD05050, y, height, new Runnable() {
					@Override
					public void run() {
						NovaSocial.declineFriend(request.id);
					}
				});
				cursor += REQUEST_HEIGHT;
			}
			cursor += 4;
		}

		for (final NovaSocial.Friend friend : friends) {
			boolean selected = friend.id.equals(this.selectedFriend);
			boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= cursor && mouseY < cursor + FRIEND_HEIGHT - 2
				&& mouseY >= y && mouseY < y + height;
			canvas.fill(x, cursor, x + width, cursor + FRIEND_HEIGHT - 2, selected ? TILE_ACTIVE : hovered ? TILE_HOVER : TILE);
			int dot = friend.playing() ? NovaDraw.ONLINE : friend.online ? NovaDraw.FROST : OFFLINE;
			canvas.fill(x + 5, cursor + 8, x + 9, cursor + 12, dot);
			int unread = NovaSocial.unread(friend.id);
			int nameRight = x + width - (unread > 0 ? canvas.textWidth(String.valueOf(unread)) + 12 : 4);
			canvas.text(NovaDraw.trim(canvas, friend.name, nameRight - x - 14), x + 14, cursor + 3, friend.online ? NovaDraw.WHITE : NovaDraw.MUTED, false);
			NovaDraw.smallText(canvas, NovaDraw.trim(canvas, friend.status(), (int) ((width - 18) / 0.75F)), x + 14, cursor + 12.5F, NovaDraw.MUTED, 0);
			if (unread > 0) {
				String count = String.valueOf(unread);
				int badgeX = x + width - canvas.textWidth(count) - 8;
				canvas.fill(badgeX - 2, cursor + 2, x + width - 3, cursor + 12, NovaDraw.FROST_STRONG);
				canvas.text(count, badgeX + 1, cursor + 3, NovaDraw.WHITE, false);
			}
			if (cursor + FRIEND_HEIGHT > y && cursor < y + height) {
				this.hits.add(new Hit(x, Math.max(cursor, y), x + width, Math.min(cursor + FRIEND_HEIGHT - 2, y + height), new Runnable() {
					@Override
					public void run() {
						NovaModMenu.this.selectedFriend = friend.id;
						NovaModMenu.this.chatScroll = 0;
						NovaModMenu.this.focus = Focus.CHAT;
						NovaSocial.openChat(friend.id);
					}
				}));
			}
			cursor += FRIEND_HEIGHT;
		}
		canvas.disableScissor();
		if (friends.isEmpty() && incoming.isEmpty()) {
			canvas.text("Noch keine Freunde.", x + 2, y + 4, NovaDraw.MUTED, false);
			NovaDraw.smallText(canvas, "Name oben eingeben + Enter.", x + 2, y + 16, NovaDraw.MUTED, 0);
		}
	}

	private static int rank(NovaSocial.Friend friend) {
		return friend.playing() ? 0 : friend.online ? 1 : 2;
	}

	private void iconButton(NovaCanvas canvas, int x, int y, String icon, int color, int clipTop, int clipHeight, Runnable action) {
		canvas.fill(x, y, x + 12, y + 12, 0xFF17171D);
		canvas.text(icon, x + (12 - canvas.textWidth(icon)) / 2 + 1, y + 2, color, false);
		if (y + 12 > clipTop && y < clipTop + clipHeight) {
			this.hits.add(new Hit(x, y, x + 12, y + 12, action));
		}
	}

	private void renderChat(NovaCanvas canvas, NovaSocial.Friend friend, int x, int y, int width, int height, List<Integer> targets) {
		canvas.text(NovaDraw.trim(canvas, friend.name, width - 12), x + 6, y + 5, NovaDraw.WHITE, false);
		NovaDraw.smallText(canvas, NovaDraw.trim(canvas, friend.status(), (int) ((width - 12) / 0.75F)), x + 6, y + 15, NovaDraw.MUTED, 0);
		canvas.fill(x, y + 23, x + width, y + 24, BORDER);

		int areaTop = y + 26;
		int areaBottom = y + height - INPUT_HEIGHT - 6;
		int bubbleWidth = width * 3 / 4;
		List<NovaSocial.Message> messages = NovaSocial.conversation(friend.id);

		this.scrollAreas.add(new Hit(x, areaTop, x + width, areaBottom, null));
		targets.add(2);

		if (messages == null) {
			canvas.text("Lädt …", x + 6, areaTop + 4, NovaDraw.MUTED, false);
		} else if (messages.isEmpty()) {
			canvas.text("Schreib die erste Nachricht.", x + 6, areaTop + 4, NovaDraw.MUTED, false);
		} else {
			// Von unten nach oben stapeln, damit die neueste Nachricht am Eingabefeld steht
			List<List<String>> wrapped = new ArrayList<List<String>>();
			int total = 0;
			for (NovaSocial.Message message : messages) {
				List<String> lines = wrap(canvas, message.text, bubbleWidth - 8);
				wrapped.add(lines);
				total += lines.size() * 10 + 8;
			}
			this.chatScroll = Math.max(0, Math.min(this.chatScroll, total - (areaBottom - areaTop)));
			canvas.enableScissor(x, areaTop, x + width, areaBottom);
			int cursor = areaBottom + this.chatScroll;
			for (int i = messages.size() - 1; i >= 0 && cursor > areaTop; i--) {
				List<String> lines = wrapped.get(i);
				boolean mine = NovaSocial.mine(messages.get(i));
				int textWidth = 0;
				for (String line : lines) {
					textWidth = Math.max(textWidth, canvas.textWidth(line));
				}
				int boxHeight = lines.size() * 10 + 4;
				int boxTop = cursor - boxHeight - 4;
				int boxLeft = mine ? x + width - 6 - textWidth - 8 : x + 6;
				canvas.fill(boxLeft, boxTop, boxLeft + textWidth + 8, boxTop + boxHeight, mine ? TILE_ACTIVE : TILE);
				for (int line = 0; line < lines.size(); line++) {
					canvas.text(lines.get(line), boxLeft + 4, boxTop + 3 + line * 10, NovaDraw.WHITE, false);
				}
				cursor = boxTop;
			}
			canvas.disableScissor();
		}

		this.input(canvas, x + 4, y + height - INPUT_HEIGHT - 4, width - 8, this.draft, "Nachricht … (Enter)", Focus.CHAT);
	}

	private void input(NovaCanvas canvas, int x, int y, int width, StringBuilder text, String placeholder, final Focus target) {
		boolean focused = this.focus == target;
		canvas.fill(x, y, x + width, y + INPUT_HEIGHT, focused ? NovaDraw.FROST : BORDER);
		canvas.fill(x + 1, y + 1, x + width - 1, y + INPUT_HEIGHT - 1, 0xFF0C0C11);
		int textY = y + (INPUT_HEIGHT - canvas.fontHeight()) / 2 + 1;
		if (text.length() == 0 && !focused) {
			canvas.text(NovaDraw.trim(canvas, placeholder, width - 8), x + 4, textY, NovaDraw.MUTED, false);
		} else {
			// Ende des Texts zeigen, wenn er zu lang ist
			String value = text.toString();
			while (value.length() > 0 && canvas.textWidth(value) > width - 14) {
				value = value.substring(1);
			}
			boolean caret = focused && System.currentTimeMillis() / 500 % 2 == 0;
			canvas.text(value + (caret ? "_" : ""), x + 4, textY, NovaDraw.WHITE, false);
		}
		this.hits.add(new Hit(x, y, x + width, y + INPUT_HEIGHT, new Runnable() {
			@Override
			public void run() {
				NovaModMenu.this.focus = target;
			}
		}));
	}

	/** Bricht Text an Leerzeichen um; zu lange Wörter werden hart geteilt. */
	private static List<String> wrap(NovaCanvas canvas, String text, int maxWidth) {
		List<String> lines = new ArrayList<String>();
		StringBuilder line = new StringBuilder();
		for (String word : text.split(" ")) {
			String candidate = line.length() == 0 ? word : line + " " + word;
			if (canvas.textWidth(candidate) <= maxWidth) {
				line.setLength(0);
				line.append(candidate);
				continue;
			}
			if (line.length() > 0) {
				lines.add(line.toString());
				line.setLength(0);
			}
			while (canvas.textWidth(word) > maxWidth) {
				int cut = word.length();
				while (cut > 1 && canvas.textWidth(word.substring(0, cut)) > maxWidth) {
					cut--;
				}
				lines.add(word.substring(0, cut));
				word = word.substring(cut);
			}
			line.append(word);
		}
		if (line.length() > 0 || lines.isEmpty()) {
			lines.add(line.toString());
		}
		return lines;
	}
}
