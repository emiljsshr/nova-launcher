package gg.nova.client;

import gg.nova.core.NovaContent;
import gg.nova.core.NovaScenery;
import gg.nova.core.menu.MenuButton;
import gg.nova.core.menu.NovaMenu;
import gg.nova.core.menu.NovaModMenu;
import gg.nova.core.menu.NovaMultiplayer;
import gg.nova.core.menu.NovaServerList;
import com.mojang.blaze3d.platform.GlStateManager;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.screen.AddServerScreen;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.ConnectScreen;
import net.minecraft.client.gui.screen.DirectConnectScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.LanServerQueryManager;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.ServerList;
import net.minecraft.client.resource.language.I18n;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * Nova-Mehrspielermenü für 1.8.9 (ersetzt das Vanilla-Menü, siehe MinecraftClientMixin). Layout, Zeichnen und
 * Eingaben kommen aus {@link NovaMultiplayer}. Die Vanilla-Dialoge melden sich hier über {@link #confirmResult}.
 * Server-Icons gibt es in 1.8.9 nicht – das Menü zeigt Ersatz-Icons.
 */
public class NovaMultiplayerScreen extends Screen implements NovaMultiplayer.Source {
	/** Welcher Dialog gerade offen ist (1.8.9 meldet nur ja/nein zurück). */
	private enum Pending {
		NONE,
		ADD,
		EDIT,
		DIRECT,
		DELETE
	}

	private final Screen parent;
	private final NovaMultiplayer menu = new NovaMultiplayer();
	private final PartnerPings partnerPings = new PartnerPings();
	private ServerList serverList;
	private PartnerPings savedPings;
	private final LanServerQueryManager.LanServerEntryList lanEntries = new LanServerQueryManager.LanServerEntryList();
	private LanServerQueryManager.LanServerDetector lanDetector;
	private List<LanServerQueryManager.LanServerInfo> lanServers = new ArrayList<LanServerQueryManager.LanServerInfo>();
	private Pending pending = Pending.NONE;
	private ServerInfo editing;
	private ServerInfo original;

	public NovaMultiplayerScreen(Screen parent) {
		this.parent = parent;
	}

	@Override
	public void init() {
		Keyboard.enableRepeatEvents(true);
		if (this.serverList == null) {
			this.serverList = new ServerList(this.client);
			this.serverList.loadFile();
		}
		this.restartPings();
		if (this.lanDetector == null) {
			try {
				this.lanDetector = new LanServerQueryManager.LanServerDetector(this.lanEntries);
				this.lanDetector.start();
			} catch (IOException exception) {
				NovaClient.LOGGER.warn("LAN-Suche nicht möglich: {}", exception.getMessage());
			}
		}
	}

	private void restartPings() {
		if (this.savedPings != null) {
			this.savedPings.cancel();
		}
		List<ServerInfo> servers = new ArrayList<ServerInfo>();
		for (int index = 0; index < this.serverList.size(); index++) {
			servers.add(this.serverList.get(index));
		}
		this.savedPings = new PartnerPings(servers);
		this.savedPings.start();
		this.partnerPings.cancel();
		this.partnerPings.start();
	}

	@Override
	public void tick() {
		this.savedPings.tick();
		this.partnerPings.tick();
		if (this.lanEntries.needsUpdate()) {
			this.lanServers = new ArrayList<LanServerQueryManager.LanServerInfo>(this.lanEntries.getServers());
			this.lanEntries.markClean();
		}
	}

	@Override
	public void removed() {
		Keyboard.enableRepeatEvents(false);
		this.savedPings.cancel();
		this.partnerPings.cancel();
		if (this.lanDetector != null) {
			this.lanDetector.interrupt();
			this.lanDetector = null;
		}
	}

	@Override
	public boolean shouldPauseGame() {
		return false;
	}

	/* ========================================
	   ZEICHNEN UND EINGABEN
	======================================== */

	@Override
	public void render(int mouseX, int mouseY, float delta) {
		LegacyCanvas canvas = new LegacyCanvas(this.client);
		// Hintergrund wie im Nova-Hauptmenü: Winterlandschaft
		NovaScenery.sky(canvas, 0, 0, this.width, this.height, 1.0F);
		NovaScenery.stars(canvas, 0, 0, this.width, (int) (this.height * 0.75F), 1312, 0.9F, 1.0F);
		int landHeight = (int) (this.height * 0.3F);
		NovaScenery.landscape(canvas, 0, this.height - landHeight, this.width, landHeight, 1.0F);
		this.menu.render(canvas, mouseX, mouseY, this);
		if (this.menu.showPlayerCard(this.width)) {
			int size = NovaMenu.HEAD_SIZE;
			int x = this.menu.headX(this.width);
			int y = this.menu.headY();
			GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
			this.client.getTextureManager().bindTexture(NovaTitleScreen.playerSkin(this.client));
			DrawableHelper.drawTexture(x, y, 8.0F, 8.0F, 8, 8, size, size, 64.0F, 64.0F);
			GlStateManager.enableBlend();
			DrawableHelper.drawTexture(x, y, 40.0F, 8.0F, 8, 8, size, size, 64.0F, 64.0F);
		}
	}

	@Override
	protected void mouseClicked(int mouseX, int mouseY, int button) {
		this.menu.click(mouseX, mouseY);
	}

	@Override
	public void handleMouse() {
		super.handleMouse();
		int wheel = Mouse.getEventDWheel();
		if (wheel != 0) {
			int mouseX = Mouse.getEventX() * this.width / this.client.width;
			int mouseY = this.height - Mouse.getEventY() * this.height / this.client.height - 1;
			this.menu.scroll(mouseX, mouseY, Integer.signum(wheel));
		}
	}

	@Override
	protected void keyPressed(char character, int keyCode) {
		NovaModMenu.Key key = keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER ? NovaModMenu.Key.ENTER : NovaModMenu.Key.OTHER;
		if (this.menu.key(key, keyCode == Keyboard.KEY_UP, keyCode == Keyboard.KEY_DOWN, this)) {
			return;
		}
		if (keyCode == Keyboard.KEY_F5) {
			this.refresh();
		} else if (keyCode == Keyboard.KEY_ESCAPE) {
			this.back();
		}
	}

	/* ========================================
	   SERVER FÜR DAS MENÜ
	======================================== */

	@Override
	public List<NovaMultiplayer.Server> saved() {
		List<NovaMultiplayer.Server> result = new ArrayList<NovaMultiplayer.Server>();
		List<NovaServerList.PartnerStatus> statuses = this.savedPings.statuses();
		for (int index = 0; index < this.serverList.size(); index++) {
			ServerInfo info = this.serverList.get(index);
			NovaServerList.PartnerStatus status = index < statuses.size() ? statuses.get(index) : NovaServerList.PartnerStatus.PINGING;
			result.add(new NovaMultiplayer.Server(info, info.name, info.label, info.playerCountLabel, status(status), status.ping(),
				null, null, null, null, false));
		}
		return result;
	}

	@Override
	public List<NovaMultiplayer.Server> partners() {
		List<NovaMultiplayer.Server> result = new ArrayList<NovaMultiplayer.Server>();
		List<NovaServerList.PartnerStatus> statuses = this.partnerPings.statuses();
		for (int index = 0; index < NovaContent.PARTNERS.size(); index++) {
			NovaContent.Partner partner = NovaContent.PARTNERS.get(index);
			ServerInfo info = this.partnerPings.server(index);
			NovaServerList.PartnerStatus status = statuses.get(index);
			String motd = info.label != null && !info.label.trim().isEmpty() && status.state() == NovaServerList.PartnerState.ONLINE
				? info.label : partner.description();
			NovaMultiplayer.Server server = new NovaMultiplayer.Server(info, partner.name(), motd, info.playerCountLabel, status(status), status.ping(),
				partner.badge(), partner.banner(), partner.icon(), null, false);
			if (partner.bannerTexture() != null) {
				server.withBanner(NovaClient.texture(partner.bannerTexture()), partner.bannerWidth(), partner.bannerHeight());
			}
			result.add(server);
		}
		return result;
	}

	@Override
	public List<NovaMultiplayer.Server> lan() {
		List<NovaMultiplayer.Server> result = new ArrayList<NovaMultiplayer.Server>();
		for (LanServerQueryManager.LanServerInfo server : this.lanServers) {
			result.add(new NovaMultiplayer.Server(server, I18n.translate("lanServer.title"), server.getMotd() + " · " + server.getAddressPort(),
				"", NovaMultiplayer.Status.ONLINE, 0L, null, null, null, null, true));
		}
		return result;
	}

	private static NovaMultiplayer.Status status(NovaServerList.PartnerStatus status) {
		switch (status.state()) {
			case ONLINE:
				return NovaMultiplayer.Status.ONLINE;
			case PINGING:
				return NovaMultiplayer.Status.PINGING;
			default:
				return NovaMultiplayer.Status.OFFLINE;
		}
	}

	/* ========================================
	   AKTIONEN
	======================================== */

	@Override
	public void join(NovaMultiplayer.Server server) {
		ServerInfo info;
		if (server.lan()) {
			LanServerQueryManager.LanServerInfo lan = (LanServerQueryManager.LanServerInfo) server.handle();
			info = new ServerInfo(lan.getMotd(), lan.getAddressPort(), true);
		} else {
			info = (ServerInfo) server.handle();
		}
		this.client.setScreen(new ConnectScreen(this, this.client, info));
	}

	@Override
	public void directConnect() {
		this.pending = Pending.DIRECT;
		this.editing = new ServerInfo(I18n.translate("selectServer.defaultName"), "", false);
		this.client.setScreen(new DirectConnectScreen(this, this.editing));
	}

	@Override
	public void add() {
		this.pending = Pending.ADD;
		this.editing = new ServerInfo(I18n.translate("selectServer.defaultName"), "", false);
		this.client.setScreen(new AddServerScreen(this, this.editing));
	}

	@Override
	public void edit(NovaMultiplayer.Server server) {
		this.pending = Pending.EDIT;
		this.original = (ServerInfo) server.handle();
		this.editing = new ServerInfo(this.original.name, this.original.address, false);
		this.editing.copyFrom(this.original);
		this.client.setScreen(new AddServerScreen(this, this.editing));
	}

	@Override
	public void delete(NovaMultiplayer.Server server) {
		this.pending = Pending.DELETE;
		this.original = (ServerInfo) server.handle();
		this.client.setScreen(new ConfirmScreen(this, I18n.translate("selectServer.deleteQuestion"),
			"'" + this.original.name + "' " + I18n.translate("selectServer.deleteWarning"),
			I18n.translate("selectServer.deleteButton"), I18n.translate("gui.cancel"), 0));
	}

	/** Antwort der Vanilla-Dialoge (Hinzufügen, Bearbeiten, Direkt verbinden, Löschen). */
	@Override
	public void confirmResult(boolean confirmed, int id) {
		Pending action = this.pending;
		this.pending = Pending.NONE;
		if (confirmed) {
			switch (action) {
				case DIRECT:
					this.client.options.lastServer = this.editing.address;
					this.client.options.save();
					this.client.setScreen(new ConnectScreen(this, this.client, this.editing));
					return;
				case ADD:
					this.serverList.add(this.editing);
					this.serverList.saveFile();
					this.menu.select(this.editing);
					break;
				case EDIT:
					this.original.name = this.editing.name;
					this.original.address = this.editing.address;
					this.original.setResourcePackState(this.editing.getResourcePack());
					this.serverList.saveFile();
					break;
				case DELETE:
					for (int index = 0; index < this.serverList.size(); index++) {
						if (this.serverList.get(index) == this.original) {
							this.serverList.remove(index);
							break;
						}
					}
					this.serverList.saveFile();
					this.menu.select(null);
					break;
				default:
					break;
			}
		}
		this.client.setScreen(this);
	}

	@Override
	public void refresh() {
		this.restartPings();
	}

	@Override
	public void back() {
		this.client.setScreen(this.parent);
	}

	@Override
	public void tool(MenuButton.Action action) {
		switch (action) {
			case MODS_FOLDER:
				NovaTitleScreen.openFolder(FabricLoader.getInstance().getGameDir().resolve("mods").toFile());
				break;
			case SCREENSHOTS_FOLDER:
				NovaTitleScreen.openFolder(new File(this.client.runDirectory, "screenshots"));
				break;
			case COSMETICS:
				this.client.setScreen(new NovaMenuScreen(this, NovaModMenu.Tab.COSMETICS));
				break;
			default:
				break;
		}
	}

	@Override
	public String playerName() {
		return this.client.getSession().getUsername();
	}

	@Override
	public boolean microsoftAccount() {
		return NovaTitleScreen.microsoftAccount(this.client);
	}

	@Override
	public String minecraftVersion() {
		return "1.8.9";
	}

	@Override
	public String novaVersion() {
		return NovaClient.version();
	}

	@Override
	public String translate(String key) {
		return I18n.translate(key);
	}
}
