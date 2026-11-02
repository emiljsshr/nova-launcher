package gg.nova.core.menu;

import gg.nova.core.NovaCanvas;
import gg.nova.core.NovaContent;
import gg.nova.core.NovaDraw;
import gg.nova.core.NovaSprites;
import gg.nova.core.Sprite;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Nova-Mehrspielermenü: Tabs (gespeicherte Server, Nova-Server, LAN), Partner-Karten mit Banner und Badge,
 * gespeicherte Server darunter, Aktionen in einer Leiste unten. Zeichnen und Eingaben laufen hier;
 * die Versions-Schicht liefert die Server ({@link Source}) und führt die Aktionen aus.
 */
public final class NovaMultiplayer {
	public enum Tab {
		SAVED("nova.mp.tab.saved"),
		NOVA("nova.menu.servers"),
		LAN("nova.mp.tab.lan");

		final String key;

		Tab(String key) {
			this.key = key;
		}
	}

	public enum Status {
		PINGING,
		ONLINE,
		OFFLINE
	}

	/** Ein Eintrag der Liste; {@code handle} ist das Versions-Objekt (ServerInfo, LAN-Server). */
	public static final class Server {
		final Object handle;
		final String name;
		final String motd;
		final String players;
		final Status status;
		final long ping;
		final NovaContent.Badge badge;
		final NovaContent.Banner banner;
		final NovaContent.IconStyle partnerIcon;
		/** Textur des Server-Icons für {@link NovaCanvas#texture}, sonst null (dann ein Ersatz-Icon). */
		final Object icon;
		final boolean lan;
		/** Bild-Banner (Textur-ID der Version) statt Pixel-Landschaft, sonst null. */
		Object bannerImage;
		int bannerWidth;
		int bannerHeight;

		public Server(Object handle, String name, String motd, String players, Status status, long ping,
				NovaContent.Badge badge, NovaContent.Banner banner, NovaContent.IconStyle partnerIcon, Object icon, boolean lan) {
			this.handle = handle;
			this.name = name;
			// Zweizeilige MOTDs in eine Zeile, Einrück-Leerzeichen (zum Zentrieren im Vanilla-Menü) weg
			this.motd = motd == null ? "" : motd.replace('\n', ' ').replaceAll(" {2,}", " ").trim();
			this.players = players == null ? "" : players;
			this.status = status;
			this.ping = ping;
			this.badge = badge;
			this.banner = banner;
			this.partnerIcon = partnerIcon;
			this.icon = icon;
			this.lan = lan;
		}

		public Object handle() {
			return this.handle;
		}

		/** Bild-Banner setzen ({@code texture} = Textur-ID der Version, Größe in Pixeln). */
		public Server withBanner(Object texture, int width, int height) {
			this.bannerImage = texture;
			this.bannerWidth = width;
			this.bannerHeight = height;
			return this;
		}

		public boolean lan() {
			return this.lan;
		}

		/** Gespeicherter Server (bearbeiten und löschen erlaubt). */
		public boolean editable() {
			return !this.lan && this.badge == null;
		}
	}

	/** Was die Versions-Schicht liefert und ausführt. */
	public interface Source {
		List<Server> saved();

		List<Server> partners();

		List<Server> lan();

		void join(Server server);

		void directConnect();

		void add();

		void edit(Server server);

		void delete(Server server);

		void refresh();

		void back();

		/** Mods, Cosmetics oder Screenshots (wie im Hauptmenü). */
		void tool(MenuButton.Action action);

		String playerName();

		boolean microsoftAccount();

		String minecraftVersion();

		String novaVersion();

		String translate(String key);
	}

	private enum Action {
		JOIN("selectServer.select"),
		DIRECT("selectServer.direct"),
		ADD("selectServer.add"),
		EDIT("selectServer.edit"),
		DELETE("selectServer.delete"),
		REFRESH("selectServer.refresh"),
		BACK("gui.back");

		final String key;

		Action(String key) {
			this.key = key;
		}
	}

	private static final int TOP_HEIGHT = 80;
	private static final int TOOL_HEIGHT = 18;
	private static final int TAB_HEIGHT = 20;
	private static final int BANNER_HEIGHT = 37;
	private static final int CARD_INFO = 44;
	private static final int ROW_HEIGHT = 40;
	private static final int GAP = 8;
	private static final int BUTTON_HEIGHT = 20;
	private static final long DOUBLE_CLICK = 300L;

	private static final int BAND = 0xA0101014;
	private static final int ENTRY = 0xE6101014;
	private static final int ENTRY_HOVER = 0xF01C1C22;
	private static final int VERIFIED = 0xFF5FE1EA;
	private static final int PARTNER = 0xFFF5A623;

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

	private Tab tab = Tab.SAVED;
	private Object selected;
	private int scroll;
	private int maxScroll;
	private long lastClick;
	private Object lastClicked;
	private final List<Hit> hits = new ArrayList<Hit>();
	private int listX1;
	private int listY1;
	private int listX2;
	private int listY2;

	/** Zeilen und Karten des aktuellen Tabs, in Anzeige-Reihenfolge (für Tastatur-Navigation). */
	private List<Server> visible = new ArrayList<Server>();

	public Tab tab() {
		return this.tab;
	}

	/* ========================================
	   EINGABEN
	======================================== */

	public boolean click(double x, double y) {
		for (int i = this.hits.size() - 1; i >= 0; i--) {
			Hit hit = this.hits.get(i);
			if (hit.contains(x, y)) {
				hit.action.run();
				return true;
			}
		}
		return false;
	}

	public boolean scroll(double x, double y, double amount) {
		if (x < this.listX1 || x >= this.listX2 + 8 || y < this.listY1 || y >= this.listY2) {
			return false;
		}
		this.scroll = Math.max(0, Math.min(this.maxScroll, this.scroll - (int) Math.round(amount * 20)));
		return true;
	}

	/** Pfeiltasten wählen, Enter verbindet. */
	public boolean key(NovaModMenu.Key key, boolean up, boolean down, Source source) {
		Server current = this.find(this.selected);
		if (key == NovaModMenu.Key.ENTER && current != null) {
			source.join(current);
			return true;
		}
		if ((up || down) && !this.visible.isEmpty()) {
			int index = current == null ? -1 : this.visible.indexOf(current);
			index = Math.max(0, Math.min(this.visible.size() - 1, index + (down ? 1 : -1)));
			this.selected = this.visible.get(index).handle;
			return true;
		}
		return false;
	}

	/** Nach Hinzufügen/Löschen: Auswahl aufheben, wenn der Eintrag fehlt. */
	public void select(Object handle) {
		this.selected = handle;
	}

	private Server find(Object handle) {
		for (Server server : this.visible) {
			if (server.handle == handle) {
				return server;
			}
		}
		return null;
	}

	/* ========================================
	   ZEICHNEN
	======================================== */

	/**
	 * Zeichnet das Menü. Den Spielerkopf zeichnet die Versions-Schicht an {@link #headX}/{@link #headY}.
	 */
	public void render(final NovaCanvas canvas, int mouseX, int mouseY, final Source source) {
		this.hits.clear();
		int width = canvas.width();
		int height = canvas.height();
		boolean compact = height < 330;
		int top = compact ? 58 : TOP_HEIGHT;
		int bottomHeight = compact ? 2 * BUTTON_HEIGHT + 16 : 2 * BUTTON_HEIGHT + 30;
		int bottom = height - bottomHeight;

		// Leisten oben und unten
		canvas.fill(0, 0, width, top, BAND);
		canvas.fill(0, top, width, top + 1, 0xFF000000);
		canvas.fill(0, top + 1, width, top + 2, 0x30FFFFFF);
		canvas.fill(0, bottom, width, height, BAND);
		canvas.fill(0, bottom - 2, width, bottom - 1, 0xFF000000);
		canvas.fill(0, bottom - 1, width, bottom, 0x30FFFFFF);

		this.renderTools(canvas, mouseX, mouseY, source, width);
		this.renderPlayerCard(canvas, source, width);

		// Titel mit Wappen
		String title = source.translate("multiplayer.title");
		float titleScale = compact ? 1.0F : 1.5F;
		int titleWidth = Math.round(canvas.textWidth(title) * titleScale);
		int emblemHeight = Math.round(16 * titleScale);
		int emblemWidth = NovaDraw.logoWidth(emblemHeight);
		int titleX = (width - emblemWidth - 6 - titleWidth) / 2;
		int titleY = compact ? 32 : 47;
		NovaDraw.logo(canvas, titleX, titleY - emblemHeight / 2, emblemWidth, 1.0F);
		canvas.push();
		canvas.translate(titleX + emblemWidth + 6, titleY - 4 * titleScale);
		canvas.scale(titleScale);
		canvas.text(title, 0, 0, NovaDraw.WHITE, true);
		canvas.pop();

		// Tabs
		int tabWidth = Math.min(120, (width - 40) / 3 - 4);
		int tabsY = top + 10;
		int tabsX = (width - 3 * tabWidth - 2 * 5) / 2;
		for (final Tab candidate : Tab.values()) {
			int x = tabsX + candidate.ordinal() * (tabWidth + 5);
			boolean hovered = inside(mouseX, mouseY, x, tabsY, x + tabWidth, tabsY + TAB_HEIGHT);
			this.button(canvas, x, tabsY, tabWidth, TAB_HEIGHT, source.translate(candidate.key), hovered, true, this.tab == candidate);
			this.hits.add(new Hit(x, tabsY, x + tabWidth, tabsY + TAB_HEIGHT, new Runnable() {
				@Override
				public void run() {
					if (NovaMultiplayer.this.tab != candidate) {
						NovaMultiplayer.this.tab = candidate;
						NovaMultiplayer.this.scroll = 0;
						NovaMultiplayer.this.selected = null;
					}
				}
			}));
		}

		// Liste
		int listWidth = Math.min(528, width - 40);
		this.listX1 = (width - listWidth) / 2;
		this.listX2 = this.listX1 + listWidth;
		this.listY1 = tabsY + TAB_HEIGHT + 8;
		this.listY2 = bottom - 6;
		this.renderList(canvas, mouseX, mouseY, source);

		int buttonsLeft = this.renderButtons(canvas, mouseX, mouseY, source, width, bottom, bottomHeight);

		// Versionen unten links, nur wenn sie neben die Buttons passen
		String minecraft = "Minecraft " + source.minecraftVersion();
		String nova = "Nova Client " + source.novaVersion();
		if (Math.max(canvas.textWidth(minecraft), canvas.textWidth(nova)) + 8 < buttonsLeft) {
			canvas.text(minecraft, 3, height - 19, NovaDraw.WHITE, true);
			canvas.text(nova, 3, height - 9, NovaDraw.WHITE, true);
		}
	}

	private void renderTools(NovaCanvas canvas, int mouseX, int mouseY, final Source source, int width) {
		MenuButton.Action[] tools = {MenuButton.Action.MODS_FOLDER, MenuButton.Action.COSMETICS, MenuButton.Action.SCREENSHOTS_FOLDER};
		String[] keys = {"nova.menu.mods", "nova.menu.cosmetics", "nova.menu.screenshots"};
		List<Sprite> icons = Arrays.asList(NovaSprites.MODS, NovaSprites.CAPE, NovaSprites.CAM);
		boolean iconsOnly = width < 430;
		int x = 8;
		for (int index = 0; index < tools.length; index++) {
			String label = source.translate(keys[index]);
			int toolWidth = iconsOnly ? TOOL_HEIGHT + 4 : 6 + Math.round(icons.get(index).width() * 0.75F) + 6 + canvas.textWidth(label) + 8;
			MenuButton spec = new MenuButton(x, 8, toolWidth, TOOL_HEIGHT, tools[index],
				iconsOnly ? MenuButton.Style.ICON : MenuButton.Style.TOOL, icons.get(index), keys[index], null, -1);
			spec.render(canvas, label, null, inside(mouseX, mouseY, x, 8, x + toolWidth, 8 + TOOL_HEIGHT), true, 1.0F);
			final MenuButton.Action action = tools[index];
			this.hits.add(new Hit(x, 8, x + toolWidth, 8 + TOOL_HEIGHT, new Runnable() {
				@Override
				public void run() {
					source.tool(action);
				}
			}));
			x += toolWidth + 4;
		}
	}

	private int cardWidth(int width) {
		return width < 430 ? 0 : Math.min(150, Math.max(110, width / 4));
	}

	public int headX(int width) {
		return width - 8 - this.cardWidth(width) + 5;
	}

	public int headY() {
		return 12;
	}

	/** Spielerkarte oben rechts sichtbar (sonst keinen Kopf zeichnen). */
	public boolean showPlayerCard(int width) {
		return this.cardWidth(width) > 0;
	}

	private void renderPlayerCard(NovaCanvas canvas, Source source, int width) {
		int cardWidth = this.cardWidth(width);
		if (cardWidth == 0) {
			return;
		}
		int x = width - 8 - cardWidth;
		canvas.fill(x, 8, x + cardWidth, 34, 0xE617171D);
		int frame = 0xFF47474F;
		canvas.fill(x, 8, x + cardWidth, 9, frame);
		canvas.fill(x, 33, x + cardWidth, 34, frame);
		canvas.fill(x, 8, x + 1, 34, frame);
		canvas.fill(x + cardWidth - 1, 8, x + cardWidth, 34, frame);

		int textX = this.headX(width) + NovaMenu.HEAD_SIZE + 6;
		int maxWidth = width - 8 - 5 - textX;
		canvas.text(NovaDraw.trim(canvas, source.playerName(), maxWidth), textX, 12, NovaDraw.WHITE, true);
		boolean microsoft = source.microsoftAccount();
		canvas.fill(textX, 25, textX + 3, 28, microsoft ? NovaDraw.ONLINE : NovaDraw.MUTED);
		String status = source.translate(microsoft ? "nova.menu.account.online" : "nova.menu.account.offline");
		NovaDraw.smallText(canvas, NovaDraw.trim(canvas, status, (int) ((maxWidth - 6) / 0.75F)), textX + 6, 23.5F,
			microsoft ? 0xFF7FD68E : NovaDraw.MUTED, 0);
	}

	private void renderList(final NovaCanvas canvas, int mouseX, int mouseY, final Source source) {
		List<Server> partners = this.tab == Tab.LAN ? new ArrayList<Server>() : source.partners();
		List<Server> rows = this.tab == Tab.SAVED ? source.saved() : this.tab == Tab.LAN ? source.lan() : new ArrayList<Server>();
		List<Server> order = new ArrayList<Server>(partners);
		order.addAll(rows);
		this.visible = order;
		if (this.find(this.selected) == null) {
			this.selected = null;
		}

		int sectionHeight = this.tab == Tab.SAVED ? 18 : 0;
		int total = sectionHeight + rows.size() * (ROW_HEIGHT + 4);
		for (Server partner : partners) {
			total += this.bannerHeight(partner) + CARD_INFO + GAP;
		}
		int viewport = this.listY2 - this.listY1;
		this.maxScroll = Math.max(0, total - viewport);
		this.scroll = Math.min(this.scroll, this.maxScroll);

		canvas.enableScissor(this.listX1 - 1, this.listY1, this.listX2 + 1, this.listY2);
		int y = this.listY1 - this.scroll;
		for (Server partner : partners) {
			int bannerHeight = this.bannerHeight(partner);
			this.renderEntry(canvas, partner, y, bannerHeight, mouseX, mouseY, source);
			y += bannerHeight + CARD_INFO + GAP;
		}
		if (this.tab == Tab.SAVED) {
			String label = source.translate("nova.mp.tab.saved");
			canvas.text(label, this.listX1, y + 5, NovaDraw.SOFT, true);
			int lineX = this.listX1 + canvas.textWidth(label) + 8;
			canvas.fill(lineX, y + 9, this.listX2, y + 10, 0x50FFFFFF);
			y += sectionHeight;
		}
		for (Server row : rows) {
			this.renderEntry(canvas, row, y, 0, mouseX, mouseY, source);
			y += ROW_HEIGHT + 4;
		}
		canvas.disableScissor();

		if (order.isEmpty() || rows.isEmpty() && this.tab == Tab.LAN) {
			String hint = source.translate(this.tab == Tab.LAN ? "lanServer.scanning" : "nova.mp.empty");
			canvas.text(hint, (this.listX1 + this.listX2 - canvas.textWidth(hint)) / 2, this.listY1 + 20, NovaDraw.MUTED, true);
		}

		// Scrollleiste rechts neben der Liste
		if (this.maxScroll > 0) {
			int barX = this.listX2 + 6;
			canvas.fill(barX, this.listY1, barX + 4, this.listY2, 0x80000000);
			int thumb = Math.max(20, viewport * viewport / total);
			int thumbY = this.listY1 + (viewport - thumb) * this.scroll / this.maxScroll;
			canvas.fill(barX, thumbY, barX + 4, thumbY + thumb, 0xFF8A8A92);
			canvas.fill(barX, thumbY, barX + 3, thumbY + thumb - 1, 0xFFC6C6CE);
		}
	}

	/** Karte mit Banner (Partner) oder einfache Zeile (gespeichert, LAN). */
	private void renderEntry(NovaCanvas canvas, final Server server, int y, int bannerHeight, int mouseX, int mouseY, final Source source) {
		int x1 = this.listX1;
		int x2 = this.listX2;
		int infoHeight = bannerHeight > 0 ? CARD_INFO : ROW_HEIGHT;
		int y2 = y + bannerHeight + infoHeight;
		if (y2 < this.listY1 || y > this.listY2) {
			return;
		}
		boolean hovered = inside(mouseX, mouseY, x1, Math.max(y, this.listY1), x2, Math.min(y2, this.listY2));
		boolean selectedEntry = server.handle == this.selected;

		if (bannerHeight > 0 && server.bannerImage != null) {
			canvas.texture(server.bannerImage, x1, y, x2 - x1, bannerHeight, server.bannerWidth, server.bannerHeight);
		} else if (bannerHeight > 0) {
			banner(canvas, server.banner, x1, y, x2 - x1, bannerHeight);
		}
		int infoY = y + bannerHeight;
		canvas.fill(x1, infoY, x2, y2, hovered ? ENTRY_HOVER : ENTRY);

		// Icon
		int iconSize = 32;
		int iconX = x1 + 6;
		int iconY = infoY + (infoHeight - iconSize) / 2;
		if (server.icon != null) {
			canvas.texture(server.icon, iconX, iconY, iconSize, iconSize);
		} else if (server.partnerIcon != null) {
			canvas.push();
			canvas.translate(iconX, iconY);
			canvas.scale(iconSize / 24.0F);
			NovaServerList.partnerIcon(canvas, server.partnerIcon, 0, 0);
			canvas.pop();
		} else {
			fallbackIcon(canvas, server, iconX, iconY, iconSize);
		}

		// Name, Badge, MOTD
		int textX = iconX + iconSize + 8;
		int right = x2 - 8;
		int statusWidth = this.renderStatus(canvas, server, right, infoY + 6);
		int nameY = infoY + (infoHeight - 20) / 2;
		String name = NovaDraw.trim(canvas, server.name, right - statusWidth - 8 - textX - 70);
		canvas.text(name, textX, nameY, NovaDraw.WHITE, true);
		if (server.badge != null) {
			badge(canvas, server.badge, textX + canvas.textWidth(name) + 6, nameY - 2);
		}
		String motd = NovaDraw.trim(canvas, server.motd, right - textX);
		canvas.text(motd, textX, nameY + 12, 0xFFA0A0A8, true);

		if (selectedEntry) {
			canvas.fill(x1 - 1, y - 1, x2 + 1, y, NovaDraw.WHITE);
			canvas.fill(x1 - 1, y2, x2 + 1, y2 + 1, NovaDraw.WHITE);
			canvas.fill(x1 - 1, y - 1, x1, y2 + 1, NovaDraw.WHITE);
			canvas.fill(x2, y - 1, x2 + 1, y2 + 1, NovaDraw.WHITE);
		}

		this.hits.add(new Hit(x1, Math.max(y, this.listY1), x2, Math.min(y2, this.listY2), new Runnable() {
			@Override
			public void run() {
				long now = System.currentTimeMillis();
				boolean doubleClick = NovaMultiplayer.this.lastClicked == server.handle && now - NovaMultiplayer.this.lastClick < DOUBLE_CLICK;
				NovaMultiplayer.this.selected = server.handle;
				NovaMultiplayer.this.lastClicked = server.handle;
				NovaMultiplayer.this.lastClick = now;
				if (doubleClick) {
					source.join(server);
				}
			}
		}));
	}

	/** Bild-Banner in voller Listenbreite mit ihrem Seitenverhältnis, Pixel-Landschaften fest 37 hoch. */
	private int bannerHeight(Server server) {
		if (server.bannerImage != null && server.bannerWidth > 0) {
			return Math.round((this.listX2 - this.listX1) * (float) server.bannerHeight / server.bannerWidth);
		}
		return BANNER_HEIGHT;
	}

	/** Spielerzahl und Ping-Balken oben rechts; gibt die belegte Breite zurück. */
	private int renderStatus(NovaCanvas canvas, Server server, int right, int y) {
		if (server.lan) {
			return 0;
		}
		int bars;
		if (server.status == Status.ONLINE) {
			long ping = server.ping;
			bars = ping < 150 ? 5 : ping < 300 ? 4 : ping < 600 ? 3 : ping < 1000 ? 2 : 1;
		} else {
			bars = 0;
		}
		// Ping-Balken wie Vanilla: fünf Stufen, beim Pingen wandert die Füllung
		int barsWidth = 14;
		int pinging = (int) (System.currentTimeMillis() / 150 % 8);
		for (int bar = 0; bar < 5; bar++) {
			int barX = right - barsWidth + bar * 3;
			int barTop = y + 8 - (2 + bar * 2);
			int color;
			if (server.status == Status.PINGING) {
				color = Math.abs(pinging - 4) == bar ? 0xFFC6C6CE : 0x40FFFFFF;
			} else if (server.status == Status.OFFLINE) {
				color = 0x40FFFFFF;
			} else {
				color = bar < bars ? NovaDraw.ONLINE : 0x40FFFFFF;
			}
			canvas.fill(barX, barTop, barX + 2, y + 8, color);
		}
		if (server.status == Status.OFFLINE) {
			canvas.text("✕", right - barsWidth + 4, y, 0xFFD05050, true);
		}
		String players = server.status == Status.ONLINE ? server.players : server.status == Status.PINGING ? "…" : "?/?";
		int playersWidth = canvas.textWidth(players);
		canvas.text(players, right - barsWidth - 6 - playersWidth, y, 0xFFC6C6CE, true);
		return barsWidth + 6 + playersWidth;
	}

	/** @return linker Rand der Button-Reihen */
	private int renderButtons(NovaCanvas canvas, int mouseX, int mouseY, final Source source, int width, int bottom, int bottomHeight) {
		final Server current = this.find(this.selected);
		boolean saved = current != null && current.editable();
		Action[][] rows = {{Action.JOIN, Action.DIRECT, Action.ADD}, {Action.EDIT, Action.DELETE, Action.REFRESH, Action.BACK}};
		int[] widths = {Math.min(155, (width - 40) / 3 - 4), Math.min(113, (width - 40) / 4 - 4)};
		int y = bottom + (bottomHeight - 2 * BUTTON_HEIGHT - 6) / 2 + 1;
		int left = width;
		for (int row = 0; row < rows.length; row++) {
			int buttonWidth = widths[row];
			int gap = row == 0 ? 7 : 6;
			int x = (width - rows[row].length * buttonWidth - (rows[row].length - 1) * gap) / 2;
			left = Math.min(left, x);
			for (final Action action : rows[row]) {
				boolean active = action == Action.JOIN ? current != null
					: action == Action.EDIT || action == Action.DELETE ? saved
					: true;
				boolean hovered = inside(mouseX, mouseY, x, y, x + buttonWidth, y + BUTTON_HEIGHT);
				this.button(canvas, x, y, buttonWidth, BUTTON_HEIGHT, source.translate(action.key), hovered, active, false);
				if (active) {
					this.hits.add(new Hit(x, y, x + buttonWidth, y + BUTTON_HEIGHT, new Runnable() {
						@Override
						public void run() {
							NovaMultiplayer.this.perform(action, current, source);
						}
					}));
				}
				x += buttonWidth + gap;
			}
			y += BUTTON_HEIGHT + 6;
		}
		return left;
	}

	private void perform(Action action, Server current, Source source) {
		switch (action) {
			case JOIN:
				source.join(current);
				break;
			case DIRECT:
				source.directConnect();
				break;
			case ADD:
				source.add();
				break;
			case EDIT:
				source.edit(current);
				break;
			case DELETE:
				source.delete(current);
				break;
			case REFRESH:
				source.refresh();
				break;
			default:
				source.back();
				break;
		}
	}

	/** Grauer Button im Minecraft-Stil; {@code framed} = weißer Rahmen (aktiver Tab). */
	private void button(NovaCanvas canvas, int x, int y, int width, int height, String label, boolean hovered, boolean active, boolean framed) {
		int x2 = x + width;
		int y2 = y + height;
		canvas.fill(x, y, x2, y2, 0xFF000000);
		int face = !active ? 0xFF2A2A30 : hovered ? 0xFF5C5C68 : 0xFF3C3C44;
		canvas.fill(x + 1, y + 1, x2 - 1, y2 - 1, face);
		canvas.fill(x + 1, y + 1, x2 - 1, y + 2, active ? 0x40FFFFFF : 0x18FFFFFF);
		canvas.fill(x + 1, y2 - 3, x2 - 1, y2 - 1, 0x50000000);
		if (framed || active && hovered) {
			int frame = NovaDraw.WHITE;
			canvas.fill(x, y, x2, y + 1, frame);
			canvas.fill(x, y2 - 1, x2, y2, frame);
			canvas.fill(x, y, x + 1, y2, frame);
			canvas.fill(x2 - 1, y, x2, y2, frame);
		}
		String text = NovaDraw.trim(canvas, label, width - 8);
		int color = !active ? 0xFF707078 : hovered ? 0xFFFFFFA0 : NovaDraw.WHITE;
		canvas.text(text, x + (width - canvas.textWidth(text)) / 2, y + (height - 8) / 2, color, true);
	}

	private static void badge(NovaCanvas canvas, NovaContent.Badge badge, int x, int y) {
		boolean verified = badge == NovaContent.Badge.VERIFIED;
		String label = verified ? "✔ VERIFIED" : "+ PARTNER";
		int width = canvas.textWidth(label) + 6;
		int color = verified ? VERIFIED : PARTNER;
		canvas.fill(x, y, x + width, y + 12, color);
		canvas.fill(x, y + 11, x + width, y + 12, verified ? 0xFF2E9CA3 : 0xFFB8741A);
		canvas.text(label, x + 3, y + 2, verified ? 0xFF0D3A3D : 0xFF3A2206, false);
	}

	/** Ersatz-Icon für Server ohne eigenes Icon: Steinblock mit etwas Moos, je Server leicht anders. */
	private static void fallbackIcon(NovaCanvas canvas, Server server, int x, int y, int size) {
		int seed = server.name.hashCode();
		int cell = size / 8;
		for (int index = 0; index < 64; index++) {
			int cx = index % 8;
			int cy = index / 8;
			float roll = NovaDraw.hash(seed + index);
			int color = cy < 2 && roll > 0.35F ? (roll > 0.7F ? 0xFF5F9A3C : 0xFF4C7F30)
				: roll > 0.75F ? 0xFF8A8A92 : roll > 0.35F ? 0xFF6E6E76 : 0xFF5A5A62;
			if (server.lan) {
				color = roll > 0.5F ? 0xFF3A6EA8 : 0xFF2E5A8C;
			}
			canvas.fill(x + cx * cell, y + cy * cell, x + cx * cell + cell, y + cy * cell + cell, color);
		}
	}

	/** Pixel-Landschaften für Partner-Karten. */
	static void banner(NovaCanvas canvas, NovaContent.Banner banner, int x, int y, int width, int height) {
		int x2 = x + width;
		int y2 = y + height;
		NovaContent.Banner style = banner == null ? NovaContent.Banner.NIGHT : banner;
		if (style == NovaContent.Banner.NIGHT) {
			canvas.gradient(x, y, x2, y2, 0xFF141A3C, 0xFF24306A);
			for (int star = 0; star < width / 9; star++) {
				int sx = x + (int) (NovaDraw.hash(star * 3 + 1) * width);
				int sy = y + (int) (NovaDraw.hash(star * 3 + 2) * (height - 4));
				canvas.fill(sx, sy, sx + 1, sy + 1, NovaDraw.hash(star * 3 + 3) > 0.6F ? 0xFFFFFFFF : 0xFF9AA4D0);
			}
			// Schwebende Insel rechts
			int islandX = x + width * 3 / 4 - 30;
			int islandY = y + height / 2 - 2;
			for (int row = 0; row < 5; row++) {
				int inset = row * 5 + (row > 2 ? 4 : 0);
				canvas.fill(islandX + inset, islandY + row * 2, islandX + 60 - inset, islandY + row * 2 + 2, row == 0 ? 0xFFDCD49A : 0xFFC4BC84);
			}
			for (int pillar = 0; pillar < 3; pillar++) {
				int px = islandX + 14 + pillar * 14;
				canvas.fill(px, y, px + 2, islandY, 0xFF2A1F3A);
			}
		} else if (style == NovaContent.Banner.SUNSET) {
			canvas.gradient(x, y, x2, y + height * 2 / 3, 0xFFE08A7A, 0xFFF5B06A);
			canvas.fill(x, y + height * 2 / 3, x2, y2, 0xFFF0C070);
			int sunX = x + width * 6 / 7;
			canvas.fill(sunX - 6, y + height / 2 - 6, sunX + 6, y + height / 2 + 6, 0xFFFFE8A0);
			canvas.fill(sunX - 3, y + height / 2 - 3, sunX + 3, y + height / 2 + 3, 0xFFFFF8D8);
			// Hügel mit Gras
			for (int column = 0; column < width; column += 4) {
				int hill = (int) (3 + 3 * Math.sin((column + 13) * 0.05) + 2 * Math.sin(column * 0.13));
				canvas.fill(x + column, y2 - 6 - hill, x + column + 4, y2, 0xFF5E9A3A);
				canvas.fill(x + column, y2 - 3, x + column + 4, y2, 0xFF7A5230);
			}
			for (int tree = 0; tree < 3; tree++) {
				int tx = x + width * 11 / 20 + tree * 24;
				canvas.fill(tx + 3, y + 8, tx + 5, y2 - 6, 0xFF6A4524);
				canvas.fill(tx, y, tx + 8, y + 12, 0xFF3E7A2C);
				canvas.fill(tx + 1, y + 1, tx + 7, y + 4, 0xFF5E9A3A);
			}
		} else {
			canvas.gradient(x, y, x2, y + height / 2, 0xFF9CC8F0, 0xFFB8D8F4);
			canvas.fill(x, y + height / 2, x2, y + height * 3 / 4, 0xFF4F8AD0);
			canvas.fill(x, y + height * 3 / 4, x2, y2, 0xFF3A6EB8);
			// Sandstrand mit Treppenkante und Palme
			for (int step = 0; step < 4; step++) {
				int sx = x + width * 11 / 20 + step * 20;
				canvas.fill(sx, y2 - 4 - step * 2, x2, y2, step % 2 == 0 ? 0xFFE6D59A : 0xFFDCC98A);
			}
			int palmX = x + width * 17 / 20;
			canvas.fill(palmX, y + 8, palmX + 2, y2 - 6, 0xFF7A5A34);
			canvas.fill(palmX - 8, y + 6, palmX + 10, y + 8, 0xFF3E8A3A);
			canvas.fill(palmX - 4, y + 4, palmX + 6, y + 6, 0xFF4E9A44);
		}
	}

	private static boolean inside(int mouseX, int mouseY, int x1, int y1, int x2, int y2) {
		return mouseX >= x1 && mouseX < x2 && mouseY >= y1 && mouseY < y2;
	}
}
