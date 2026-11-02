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

	private static final int PANEL = 0xF2141418;
	private static final int SIDEBAR = 0xFF18181C;
	private static final int CARD = 0xFF1E1E23;
	private static final int CARD_BORDER = 0xFF34343B;
	private static final int ENABLED = 0xFF2E7D32;
	private static final int ENABLED_TEXT = 0xFFA6F5A6;
	private static final int DISABLED = 0xFF7A1F1F;
	private static final int DISABLED_TEXT = 0xFFFF9A9A;
	private static final int HIGHLIGHT = 0xFFFFFF55;
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

		int width = Math.min(752, canvas.width() - 24);
		int height = Math.min(430, canvas.height() - 44);
		int left = (canvas.width() - width) / 2;
		int top = (canvas.height() - height) / 2 + 8;

		canvas.fill(0, 0, canvas.width(), canvas.height(), 0x80000000);
		// Titel über dem Fenster wie im Mockup
		String title = this.tab == Tab.MODS ? t("nova.mods.title") : this.tab == Tab.COSMETICS ? t("nova.menu.cosmetics") : t("nova.mods.friends");
		canvas.push();
		canvas.translate(left, top - 17);
		canvas.scale(1.5F);
		canvas.text(title, 0, 0, NovaDraw.WHITE, true);
		canvas.pop();
		String hint = t("nova.mods.close");
		canvas.text(hint, left + width - canvas.textWidth(hint), top - 13, 0xFF9A9AA2, true);

		canvas.fill(left - 2, top - 2, left + width + 2, top + height + 2, 0xFF000000);
		canvas.fill(left, top, left + width, top + height, PANEL);
		int sidebarWidth = Math.max(110, Math.min(145, width * 19 / 100));
		canvas.fill(left, top, left + sidebarWidth, top + height, SIDEBAR);
		canvas.fill(left + sidebarWidth, top, left + sidebarWidth + 1, top + height, 0xFF2A2A30);
		this.renderSidebar(canvas, left, top, sidebarWidth, height, mouseX, mouseY);

		int contentX = left + sidebarWidth + 12;
		int contentY = top + 12;
		int contentWidth = width - sidebarWidth - 24;
		int contentHeight = height - 24;
		String notice = NovaSocial.notice();
		if (notice != null) {
			contentHeight -= 12;
			NovaDraw.smallText(canvas, NovaDraw.trim(canvas, notice, (int) (contentWidth / 0.75F)), contentX, top + height - 13, NovaDraw.FROST, 0);
		}

		if (this.tab == Tab.MODS) {
			if (this.settingsModule != null) {
				this.renderModuleSettings(canvas, this.settingsModule, contentX, contentY, contentWidth, mouseX, mouseY);
			} else if (this.globalSettings) {
				this.renderGlobalSettings(canvas, contentX, contentY, contentWidth, mouseX, mouseY);
			} else {
				this.renderMods(canvas, contentX, contentY, contentWidth, contentHeight, mouseX, mouseY, targets);
			}
		} else if (this.tab == Tab.COSMETICS) {
			this.renderCosmetics(canvas, contentX, contentY, contentWidth, contentHeight, mouseX, mouseY, targets);
		} else {
			this.renderFriends(canvas, contentX, contentY, contentWidth, contentHeight, mouseX, mouseY, targets);
		}

		this.scrollTargets = new int[targets.size()];
		for (int i = 0; i < targets.size(); i++) {
			this.scrollTargets[i] = targets.get(i);
		}
	}

	/* ========================================
	   SEITENLEISTE
	======================================== */

	private void renderSidebar(NovaCanvas canvas, int left, int top, int width, int height, int mouseX, int mouseY) {
		// Wenig Höhe (große GUI-Skalierung): kleinerer Kopf und Buttons
		boolean compact = height < 300;
		int buttonHeight = compact ? 16 : 20;
		// Wappen mit "Nova / Client"
		int emblemHeight = compact ? 16 : 22;
		NovaDraw.logo(canvas, left + 12, top + 12, NovaDraw.logoWidth(emblemHeight), 1.0F);
		int textX = left + 16 + NovaDraw.logoWidth(emblemHeight);
		canvas.push();
		canvas.translate(textX, top + 12);
		canvas.scale(1.25F);
		canvas.text("Nova", 0, 0, NovaDraw.WHITE, true);
		canvas.pop();
		if (!compact) {
			canvas.text("Client", textX, top + 25, 0xFF9A9AA2, true);
		}

		int x = left + 10;
		int itemWidth = width - 20;
		int y = top + (compact ? 32 : 46);
		// Unten: HUD-Editor und Einstellungen; die Einträge darüber rücken bei wenig Höhe zusammen
		int buttonY = top + height - (compact ? 8 : 12) - 2 * buttonHeight - (compact ? 4 : 6);
		int step = Math.max(13, Math.min(24, (buttonY - 6 - y) * 10 / 85));
		int itemHeight = Math.min(20, step - 2);
		this.navItem(canvas, x, y, itemWidth, itemHeight, t("nova.mods.category.all"), this.tab == Tab.MODS && this.category == null && !this.globalSettings, mouseX, mouseY, Tab.MODS, null);
		y += step;
		for (Module.Category candidate : Module.Category.values()) {
			this.navItem(canvas, x, y, itemWidth, itemHeight, t(candidate.key), this.tab == Tab.MODS && this.category == candidate && !this.globalSettings, mouseX, mouseY, Tab.MODS, candidate);
			y += step;
		}
		canvas.fill(x, y + step / 6, x + itemWidth, y + step / 6 + 1, 0xFF2A2A30);
		y += step / 3;
		this.navItem(canvas, x, y, itemWidth, itemHeight, t("nova.menu.cosmetics"), this.tab == Tab.COSMETICS, mouseX, mouseY, Tab.COSMETICS, null);
		y += step;
		int unread = NovaSocial.unreadTotal() + NovaSocial.incoming().size();
		this.navItem(canvas, x, y, itemWidth, itemHeight, t("nova.mods.friends") + (unread > 0 ? " (" + unread + ")" : ""), this.tab == Tab.FRIENDS, mouseX, mouseY, Tab.FRIENDS, null);

		this.button(canvas, x, buttonY, itemWidth, buttonHeight, t("nova.mods.editHud"), mouseX, mouseY, new Runnable() {
			@Override
			public void run() {
				NovaModMenu.this.hudEditor = true;
				NovaModMenu.this.focus = Focus.NONE;
			}
		});
		this.button(canvas, x, buttonY + buttonHeight + (compact ? 4 : 6), itemWidth, buttonHeight, t("nova.mods.settings"), mouseX, mouseY, new Runnable() {
			@Override
			public void run() {
				NovaModMenu.this.tab = Tab.MODS;
				NovaModMenu.this.settingsModule = null;
				NovaModMenu.this.globalSettings = true;
			}
		});
	}

	private void navItem(NovaCanvas canvas, int x, int y, int width, int height, String label, boolean active, int mouseX, int mouseY,
			final Tab target, final Module.Category targetCategory) {
		boolean hovered = inside(mouseX, mouseY, x, y, x + width, y + height);
		if (active) {
			canvas.fill(x, y, x + width, y + height, 0xFF2A2A30);
			frame(canvas, x, y, width, height, NovaDraw.WHITE);
		} else if (hovered) {
			canvas.fill(x, y, x + width, y + height, 0xFF222228);
		}
		canvas.text(NovaDraw.trim(canvas, label, width - 16), x + 9, y + (height - 8) / 2 + 1, active ? HIGHLIGHT : NovaDraw.WHITE, true);
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

	/** Grauer Button im Minecraft-Stil. */
	private void button(NovaCanvas canvas, int x, int y, int width, int height, String label, int mouseX, int mouseY, Runnable action) {
		boolean hovered = inside(mouseX, mouseY, x, y, x + width, y + height);
		canvas.fill(x, y, x + width, y + height, hovered ? NovaDraw.WHITE : 0xFF000000);
		canvas.fill(x + 1, y + 1, x + width - 1, y + height - 1, hovered ? 0xFF5C5C68 : 0xFF3C3C44);
		canvas.fill(x + 1, y + 1, x + width - 1, y + 2, 0x40FFFFFF);
		canvas.fill(x + 1, y + height - 3, x + width - 1, y + height - 1, 0x50000000);
		String text = NovaDraw.trim(canvas, label, width - 8);
		canvas.text(text, x + (width - canvas.textWidth(text)) / 2, y + (height - 8) / 2, hovered ? 0xFFFFFFA0 : NovaDraw.WHITE, true);
		this.hits.add(new Hit(x, y, x + width, y + height, action));
	}

	private static void frame(NovaCanvas canvas, int x, int y, int width, int height, int color) {
		canvas.fill(x, y, x + width, y + 1, color);
		canvas.fill(x, y + height - 1, x + width, y + height, color);
		canvas.fill(x, y, x + 1, y + height, color);
		canvas.fill(x + width - 1, y, x + width, y + height, color);
	}

	private static boolean inside(int mouseX, int mouseY, int x1, int y1, int x2, int y2) {
		return mouseX >= x1 && mouseX < x2 && mouseY >= y1 && mouseY < y2;
	}

	/* ========================================
	   MODS
	======================================== */

	private void renderMods(NovaCanvas canvas, int x, int y, int width, int height, int mouseX, int mouseY, List<Integer> targets) {
		// Suche und Zähler
		// Ohne Platzhalter übersetzen – Minecraft meldet sonst "Format error"
		String count = NovaMods.enabledCount() + " / " + NovaMods.modules().size() + " " + t("nova.mods.count");
		int countWidth = canvas.textWidth(count) + 16;
		int searchWidth = width - countWidth - 8;
		boolean searching = this.focus == Focus.SEARCH;
		canvas.fill(x, y, x + searchWidth, y + 22, searching ? NovaDraw.WHITE : 0xFF8A8A92);
		canvas.fill(x + 1, y + 1, x + searchWidth - 1, y + 21, 0xFF000000);
		// Lupe
		canvas.fill(x + 7, y + 7, x + 12, y + 8, 0xFF8A8A92);
		canvas.fill(x + 7, y + 12, x + 12, y + 13, 0xFF8A8A92);
		canvas.fill(x + 6, y + 8, x + 7, y + 12, 0xFF8A8A92);
		canvas.fill(x + 12, y + 8, x + 13, y + 12, 0xFF8A8A92);
		canvas.fill(x + 13, y + 13, x + 15, y + 15, 0xFF8A8A92);
		String query = this.search.toString();
		if (query.isEmpty() && !searching) {
			canvas.text(t("nova.mods.search"), x + 20, y + 7, 0xFF707078, false);
		} else {
			boolean caret = searching && System.currentTimeMillis() / 500 % 2 == 0;
			canvas.text(query + (caret ? "_" : ""), x + 20, y + 7, NovaDraw.WHITE, false);
		}
		this.hits.add(new Hit(x, y, x + searchWidth, y + 22, new Runnable() {
			@Override
			public void run() {
				NovaModMenu.this.focus = Focus.SEARCH;
			}
		}));
		canvas.fill(x + width - countWidth, y, x + width, y + 22, 0xFF1E1E23);
		canvas.text(count, x + width - countWidth + 8, y + 7, 0xFFC6C6CE, false);

		// Karten
		List<Module> shown = new ArrayList<Module>();
		String needle = query.toLowerCase(Locale.ROOT);
		for (Module module : NovaMods.modules()) {
			boolean inCategory = this.category == null || module.categories.contains(this.category);
			if (inCategory && (needle.isEmpty() || module.name.toLowerCase(Locale.ROOT).contains(needle))) {
				shown.add(module);
			}
		}
		int gridY = y + 32;
		int gridHeight = height - 32;
		int gap = 7;
		int columns = Math.max(2, Math.min(5, (width + gap) / (100 + gap)));
		int cardWidth = (width - (columns - 1) * gap) / columns;
		int cardHeight = 78;
		int rows = (shown.size() + columns - 1) / columns;
		int total = rows * (cardHeight + gap) - gap;
		this.modsScroll = Math.max(0, Math.min(this.modsScroll, total - gridHeight));
		this.scrollAreas.add(new Hit(x, gridY, x + width, gridY + gridHeight, null));
		targets.add(3);

		canvas.enableScissor(x - 1, gridY - 1, x + width + 1, gridY + gridHeight);
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
			canvas.text(empty, x + (width - canvas.textWidth(empty)) / 2, gridY + 20, 0xFF8A8A92, true);
		}
	}

	private void card(NovaCanvas canvas, final Module module, int x, int y, int width, int height, int clipTop, int clipBottom, int mouseX, int mouseY) {
		boolean visible = mouseY >= clipTop && mouseY < clipBottom;
		boolean hovered = visible && inside(mouseX, mouseY, x, y, x + width, y + height);
		int statusHeight = 14;
		canvas.fill(x, y, x + width, y + height, hovered ? NovaDraw.WHITE : CARD_BORDER);
		canvas.fill(x + 1, y + 1, x + width - 1, y + height - 1, CARD);

		// Icon in dunklem Kasten
		int box = 30;
		int boxX = x + (width - box) / 2;
		int boxY = y + 7;
		canvas.fill(boxX, boxY, boxX + box, boxY + box, 0xFF0E0E11);
		frame(canvas, boxX, boxY, box, box, 0xFF2E2E35);
		module.icon.draw(canvas, boxX + 3, boxY + 4, 2, 1.0F);

		String name = NovaDraw.trim(canvas, module.name, width - 8);
		canvas.text(name, x + (width - canvas.textWidth(name)) / 2, y + height - statusHeight - 15, NovaDraw.WHITE, true);

		// Status unten
		int statusY = y + height - statusHeight - 1;
		canvas.fill(x + 1, statusY, x + width - 1, y + height - 1, module.enabled() ? ENABLED : DISABLED);
		String status = t(module.enabled() ? "nova.mods.enabled" : "nova.mods.disabled");
		canvas.text(status, x + (width - canvas.textWidth(status)) / 2, statusY + 3, module.enabled() ? ENABLED_TEXT : DISABLED_TEXT, true);

		// Zahnrad oben rechts öffnet die Einstellungen
		int gearX = x + width - 12;
		int gearY = y + 4;
		boolean gearHovered = visible && inside(mouseX, mouseY, gearX - 2, gearY - 2, gearX + 9, gearY + 9);
		if (gearHovered) {
			canvas.fill(gearX - 2, gearY - 2, gearX + 9, gearY + 9, 0xFF34343B);
		}
		GEAR.draw(canvas, gearX, gearY, 1, 1.0F);

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
		if (gearY - 2 >= top && gearY + 9 <= bottom) {
			this.hits.add(new Hit(gearX - 2, gearY - 2, gearX + 9, gearY + 9, new Runnable() {
				@Override
				public void run() {
					NovaModMenu.this.settingsModule = module;
				}
			}));
		}
	}

	/** Einstellungen eines Moduls: an/aus und seine Optionen, jede Zeile ein Schalter oder eine Auswahl. */
	private void renderModuleSettings(NovaCanvas canvas, final Module module, int x, int y, int width, int mouseX, int mouseY) {
		this.button(canvas, x, y, 70, 20, "< " + t("nova.mods.back"), mouseX, mouseY, new Runnable() {
			@Override
			public void run() {
				NovaModMenu.this.settingsModule = null;
			}
		});
		module.icon.draw(canvas, x + 80, y + 2, 1, 1.0F);
		canvas.push();
		canvas.translate(x + 96, y + 3);
		canvas.scale(1.5F);
		canvas.text(module.name, 0, 0, NovaDraw.WHITE, true);
		canvas.pop();

		int rowY = y + 32;
		rowY = this.settingRow(canvas, x, rowY, width, t("nova.mods.setting.enabled"), t(module.enabled() ? "nova.mods.on" : "nova.mods.off"),
			module.enabled(), mouseX, mouseY, new Runnable() {
				@Override
				public void run() {
					module.toggle();
				}
			});
		for (final Module.Setting setting : module.settings()) {
			String value = setting.toggle() ? t(setting.on() ? "nova.mods.on" : "nova.mods.off") : valueLabel(setting);
			rowY = this.settingRow(canvas, x, rowY, width, t(setting.key), value, setting.toggle() && setting.on(), mouseX, mouseY, new Runnable() {
				@Override
				public void run() {
					setting.next();
				}
			});
		}
		if (module.hud) {
			NovaDraw.smallText(canvas, t("nova.mods.hudTip"), x, rowY + 6, 0xFF8A8A92, 0);
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

	private int settingRow(NovaCanvas canvas, int x, int y, int width, String label, String value, boolean on, int mouseX, int mouseY, Runnable action) {
		boolean hovered = inside(mouseX, mouseY, x, y, x + width, y + 24);
		canvas.fill(x, y, x + width, y + 24, hovered ? 0xFF26262C : CARD);
		canvas.text(label, x + 10, y + 8, NovaDraw.WHITE, true);
		int valueWidth = Math.max(70, canvas.textWidth(value) + 16);
		int valueX = x + width - valueWidth - 6;
		canvas.fill(valueX, y + 4, valueX + valueWidth, y + 20, on ? ENABLED : 0xFF3C3C44);
		canvas.text(value, valueX + (valueWidth - canvas.textWidth(value)) / 2, y + 8, on ? ENABLED_TEXT : NovaDraw.WHITE, true);
		this.hits.add(new Hit(x, y, x + width, y + 24, action));
		return y + 28;
	}

	private void renderGlobalSettings(NovaCanvas canvas, int x, int y, int width, int mouseX, int mouseY) {
		this.button(canvas, x, y, 70, 20, "< " + t("nova.mods.back"), mouseX, mouseY, new Runnable() {
			@Override
			public void run() {
				NovaModMenu.this.globalSettings = false;
			}
		});
		canvas.push();
		canvas.translate(x + 80, y + 3);
		canvas.scale(1.5F);
		canvas.text(t("nova.mods.settings"), 0, 0, NovaDraw.WHITE, true);
		canvas.pop();
		int rowY = y + 32;
		rowY = this.settingRow(canvas, x, rowY, width, t("nova.mods.friendAlerts"), t(NovaSocial.chatAlerts() ? "nova.mods.on" : "nova.mods.off"),
			NovaSocial.chatAlerts(), mouseX, mouseY, new Runnable() {
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
		canvas.fill(x, 8, x + width, 22, 0xC0000000);
		canvas.text(hint, x + 8, 11, 0xFFC6C6CE, true);
		this.button(canvas, (canvas.width() - 80) / 2, 26, 80, 20, t("nova.mods.done"), mouseX, mouseY, new Runnable() {
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
