package gg.nova.client;

import gg.nova.core.NovaContent;
import gg.nova.core.menu.MenuButton;
import gg.nova.core.menu.NovaMenu;
import gg.nova.core.menu.NovaModMenu;
import gg.nova.core.menu.NovaMultiplayer;
import gg.nova.core.menu.NovaServerList;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.client.gui.screen.multiplayer.AddServerScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.screen.multiplayer.DirectConnectScreen;
import net.minecraft.client.gui.screen.world.WorldIcon;
import net.minecraft.client.network.LanServerInfo;
import net.minecraft.client.network.LanServerQueryManager;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.ServerList;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.session.Session;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * Nova-Mehrspielermenü (ersetzt das Vanilla-Menü, siehe MinecraftClientMixin). Layout, Zeichnen und Eingaben
 * kommen aus {@link NovaMultiplayer}; hier Serverliste, Ping, LAN-Suche, Server-Icons und die Vanilla-Dialoge.
 */
public class NovaMultiplayerScreen extends Screen implements NovaMultiplayer.Source {
	private final Screen parent;
	private final NovaMultiplayer menu = new NovaMultiplayer();
	private final PartnerPings partnerPings = new PartnerPings();
	private final Map<String, Favicon> favicons = new HashMap<>();
	private ServerList serverList;
	private PartnerPings savedPings;
	private final LanServerQueryManager.LanServerEntryList lanEntries = new LanServerQueryManager.LanServerEntryList();
	private LanServerQueryManager.LanServerDetector lanDetector;
	private List<LanServerInfo> lanServers = new ArrayList<>();

	/** Hochgeladenes Server-Icon; neu geladen, wenn sich die Bytes ändern. */
	private static final class Favicon {
		byte[] bytes;
		WorldIcon icon;
	}

	public NovaMultiplayerScreen(Screen parent) {
		super(Text.translatable("multiplayer.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
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
		List<ServerInfo> servers = new ArrayList<>();
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
		List<LanServerInfo> updated = this.lanEntries.getEntriesIfUpdated();
		if (updated != null) {
			this.lanServers = new ArrayList<>(updated);
		}
	}

	/** Beim Verlassen (auch zu Dialogen und beim Verbinden); init() startet Ping und LAN-Suche neu. */
	@Override
	public void removed() {
		this.savedPings.cancel();
		this.partnerPings.cancel();
		if (this.lanDetector != null) {
			this.lanDetector.interrupt();
			this.lanDetector = null;
		}
		for (Favicon favicon : this.favicons.values()) {
			if (favicon.icon != null) {
				favicon.icon.close();
			}
		}
		this.favicons.clear();
	}

	@Override
	public void close() {
		this.client.setScreen(this.parent);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	/* ========================================
	   ZEICHNEN UND EINGABEN
	======================================== */

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		this.menu.render(new DrawContextCanvas(context, this.textRenderer), mouseX, mouseY, this);
		if (this.menu.showPlayerCard(this.width)) {
			PlayerSkinDrawer.draw(context, this.client.getSkinProvider().getSkinTextures(this.client.getGameProfile()),
				this.menu.headX(this.width), this.menu.headY(), NovaMenu.HEAD_SIZE, -1);
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		return this.menu.click(mouseX, mouseY) || super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		return this.menu.scroll(mouseX, mouseY, vertical);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		NovaModMenu.Key key = keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER ? NovaModMenu.Key.ENTER : NovaModMenu.Key.OTHER;
		if (this.menu.key(key, keyCode == GLFW.GLFW_KEY_UP, keyCode == GLFW.GLFW_KEY_DOWN, this)) {
			return true;
		}
		if (keyCode == GLFW.GLFW_KEY_F5) {
			this.refresh();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	/* ========================================
	   SERVER FÜR DAS MENÜ
	======================================== */

	@Override
	public List<NovaMultiplayer.Server> saved() {
		List<NovaMultiplayer.Server> result = new ArrayList<>();
		List<NovaServerList.PartnerStatus> statuses = this.savedPings.statuses();
		for (int index = 0; index < this.serverList.size(); index++) {
			ServerInfo info = this.serverList.get(index);
			NovaServerList.PartnerStatus status = index < statuses.size() ? statuses.get(index) : NovaServerList.PartnerStatus.PINGING;
			result.add(new NovaMultiplayer.Server(info, info.name, info.label == null ? "" : info.label.getString(),
				info.playerCountLabel == null ? "" : info.playerCountLabel.getString(), status(status), status.ping(),
				null, null, null, this.favicon(info), false));
		}
		return result;
	}

	@Override
	public List<NovaMultiplayer.Server> partners() {
		List<NovaMultiplayer.Server> result = new ArrayList<>();
		List<NovaServerList.PartnerStatus> statuses = this.partnerPings.statuses();
		for (int index = 0; index < NovaContent.PARTNERS.size(); index++) {
			NovaContent.Partner partner = NovaContent.PARTNERS.get(index);
			ServerInfo info = this.partnerPings.server(index);
			NovaServerList.PartnerStatus status = statuses.get(index);
			String motd = info.label != null && !info.label.getString().isBlank() && status.state() == NovaServerList.PartnerState.ONLINE
				? info.label.getString() : partner.description();
			result.add(new NovaMultiplayer.Server(info, partner.name(), motd,
				info.playerCountLabel == null ? "" : info.playerCountLabel.getString(), status(status), status.ping(),
				partner.badge(), partner.banner(), partner.icon(), this.favicon(info), false));
		}
		return result;
	}

	@Override
	public List<NovaMultiplayer.Server> lan() {
		List<NovaMultiplayer.Server> result = new ArrayList<>();
		for (LanServerInfo server : this.lanServers) {
			result.add(new NovaMultiplayer.Server(server, I18n.translate("lanServer.title"), server.getMotd() + " · " + server.getAddressPort(),
				"", NovaMultiplayer.Status.ONLINE, 0L, null, null, null, null, true));
		}
		return result;
	}

	private static NovaMultiplayer.Status status(NovaServerList.PartnerStatus status) {
		return switch (status.state()) {
			case ONLINE -> NovaMultiplayer.Status.ONLINE;
			case PINGING -> NovaMultiplayer.Status.PINGING;
			default -> NovaMultiplayer.Status.OFFLINE;
		};
	}

	/** Server-Icon aus dem Ping (64×64 PNG) als Textur, oder null. */
	private Object favicon(ServerInfo info) {
		byte[] bytes = info.getFavicon();
		if (bytes == null) {
			return null;
		}
		Favicon favicon = this.favicons.computeIfAbsent(info.address, address -> new Favicon());
		if (!Arrays.equals(bytes, favicon.bytes)) {
			favicon.bytes = bytes;
			try {
				if (favicon.icon == null) {
					favicon.icon = WorldIcon.forServer(this.client.getTextureManager(), info.address);
				}
				favicon.icon.load(NativeImage.read(bytes));
			} catch (IOException | RuntimeException exception) {
				NovaClient.LOGGER.debug("Server-Icon von {} ungültig", info.address, exception);
				favicon.bytes = null;
				return null;
			}
		}
		return favicon.icon == null ? null : favicon.icon.getTextureId();
	}

	/* ========================================
	   AKTIONEN
	======================================== */

	@Override
	public void join(NovaMultiplayer.Server server) {
		ServerInfo info = server.lan()
			? new ServerInfo(((LanServerInfo) server.handle()).getMotd(), ((LanServerInfo) server.handle()).getAddressPort(), ServerInfo.ServerType.LAN)
			: (ServerInfo) server.handle();
		this.connect(info);
	}

	private void connect(ServerInfo info) {
		ConnectScreen.connect(this, this.client, ServerAddress.parse(info.address), info, false, null);
	}

	@Override
	public void directConnect() {
		ServerInfo direct = new ServerInfo(I18n.translate("selectServer.defaultName"), "", ServerInfo.ServerType.OTHER);
		this.client.setScreen(new DirectConnectScreen(this, accepted -> {
			if (accepted) {
				this.client.options.lastServer = direct.address;
				this.client.options.write();
				this.connect(direct);
			} else {
				this.client.setScreen(this);
			}
		}, direct));
	}

	@Override
	public void add() {
		ServerInfo created = new ServerInfo(I18n.translate("selectServer.defaultName"), "", ServerInfo.ServerType.OTHER);
		this.client.setScreen(new AddServerScreen(this, accepted -> {
			if (accepted) {
				this.serverList.add(created, false);
				this.serverList.saveFile();
				this.menu.select(created);
				this.restartPings();
			}
			this.client.setScreen(this);
		}, created));
	}

	@Override
	public void edit(NovaMultiplayer.Server server) {
		ServerInfo info = (ServerInfo) server.handle();
		ServerInfo copy = new ServerInfo(info.name, info.address, ServerInfo.ServerType.OTHER);
		copy.copyWithSettingsFrom(info);
		this.client.setScreen(new AddServerScreen(this, accepted -> {
			if (accepted) {
				info.copyWithSettingsFrom(copy);
				this.serverList.saveFile();
				this.restartPings();
			}
			this.client.setScreen(this);
		}, copy));
	}

	@Override
	public void delete(NovaMultiplayer.Server server) {
		ServerInfo info = (ServerInfo) server.handle();
		this.client.setScreen(new ConfirmScreen(accepted -> {
			if (accepted) {
				this.serverList.remove(info);
				this.serverList.saveFile();
				this.menu.select(null);
				this.restartPings();
			}
			this.client.setScreen(this);
		}, Text.translatable("selectServer.deleteQuestion"), Text.translatable("selectServer.deleteWarning", info.name),
			Text.translatable("selectServer.deleteButton"), ScreenTexts.CANCEL));
	}

	@Override
	public void refresh() {
		this.restartPings();
	}

	@Override
	public void back() {
		this.close();
	}

	@Override
	public void tool(MenuButton.Action action) {
		switch (action) {
			case MODS_FOLDER -> NovaTitleScreen.openFolder(net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("mods"));
			case SCREENSHOTS_FOLDER -> NovaTitleScreen.openFolder(new File(this.client.runDirectory, "screenshots").toPath());
			case COSMETICS -> this.client.setScreen(new NovaMenuScreen(this, NovaModMenu.Tab.COSMETICS));
			default -> {
			}
		}
	}

	@Override
	public String playerName() {
		return this.client.getSession().getUsername();
	}

	@Override
	public boolean microsoftAccount() {
		return this.client.getSession().getAccountType() == Session.AccountType.MSA;
	}

	@Override
	public String minecraftVersion() {
		return SharedConstants.getGameVersion().getName();
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
