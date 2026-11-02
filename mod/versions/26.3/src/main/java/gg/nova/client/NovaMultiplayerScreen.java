package gg.nova.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
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
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DirectJoinServerScreen;
import net.minecraft.client.gui.screens.FaviconTexture;
import net.minecraft.client.gui.screens.ManageServerScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.server.LanServer;
import net.minecraft.client.server.LanServerDetection;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

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
	private final LanServerDetection.LanServerList lanEntries = new LanServerDetection.LanServerList();
	private LanServerDetection.LanServerDetector lanDetector;
	private List<LanServer> lanServers = new ArrayList<>();

	/** Hochgeladenes Server-Icon; neu geladen, wenn sich die Bytes ändern. */
	private static final class Favicon {
		byte[] bytes;
		FaviconTexture icon;
	}

	public NovaMultiplayerScreen(Screen parent) {
		super(Component.translatable("multiplayer.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		if (this.serverList == null) {
			this.serverList = new ServerList(this.minecraft);
			this.serverList.load();
		}
		this.restartPings();
		if (this.lanDetector == null) {
			try {
				this.lanDetector = new LanServerDetection.LanServerDetector(this.lanEntries);
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
		List<ServerData> servers = new ArrayList<>();
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
		List<LanServer> updated = this.lanEntries.takeDirtyServers();
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
	public void onClose() {
		this.show(this.parent);
	}

	private void show(Screen screen) {
		this.minecraft.gui.setScreen(screen);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	/* ========================================
	   ZEICHNEN UND EINGABEN
	======================================== */

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		this.menu.render(new GuiCanvas(graphics, this.font), mouseX, mouseY, this);
		if (this.menu.showPlayerCard(this.width)) {
			PlayerFaceExtractor.extractRenderState(graphics,
				this.minecraft.getSkinManager().createLookup(this.minecraft.getGameProfile(), false).get(),
				this.menu.headX(this.width), this.menu.headY(), NovaMenu.HEAD_SIZE, -1);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		return this.menu.click(event.x(), event.y()) || super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		return this.menu.scroll(mouseX, mouseY, vertical);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int keyCode = event.key();
		NovaModMenu.Key key = keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER
			? NovaModMenu.Key.ENTER : NovaModMenu.Key.OTHER;
		if (this.menu.key(key, keyCode == InputConstants.KEY_UP, keyCode == InputConstants.KEY_DOWN, this)) {
			return true;
		}
		if (keyCode == InputConstants.KEY_F5) {
			this.refresh();
			return true;
		}
		return super.keyPressed(event);
	}

	/* ========================================
	   SERVER FÜR DAS MENÜ
	======================================== */

	@Override
	public List<NovaMultiplayer.Server> saved() {
		List<NovaMultiplayer.Server> result = new ArrayList<>();
		List<NovaServerList.PartnerStatus> statuses = this.savedPings.statuses();
		for (int index = 0; index < this.serverList.size(); index++) {
			ServerData data = this.serverList.get(index);
			NovaServerList.PartnerStatus status = index < statuses.size() ? statuses.get(index) : NovaServerList.PartnerStatus.PINGING;
			result.add(new NovaMultiplayer.Server(data, data.name, data.motd == null ? "" : data.motd.getString(),
				data.status == null ? "" : data.status.getString(), status(status), status.ping(),
				null, null, null, this.favicon(data), false));
		}
		return result;
	}

	@Override
	public List<NovaMultiplayer.Server> partners() {
		List<NovaMultiplayer.Server> result = new ArrayList<>();
		List<NovaServerList.PartnerStatus> statuses = this.partnerPings.statuses();
		for (int index = 0; index < NovaContent.PARTNERS.size(); index++) {
			NovaContent.Partner partner = NovaContent.PARTNERS.get(index);
			ServerData data = this.partnerPings.server(index);
			NovaServerList.PartnerStatus status = statuses.get(index);
			String motd = data.motd != null && !data.motd.getString().isBlank() && status.state() == NovaServerList.PartnerState.ONLINE
				? data.motd.getString() : partner.description();
			result.add(new NovaMultiplayer.Server(data, partner.name(), motd,
				data.status == null ? "" : data.status.getString(), status(status), status.ping(),
				partner.badge(), partner.banner(), partner.icon(), this.favicon(data), false));
		}
		return result;
	}

	@Override
	public List<NovaMultiplayer.Server> lan() {
		List<NovaMultiplayer.Server> result = new ArrayList<>();
		for (LanServer server : this.lanServers) {
			result.add(new NovaMultiplayer.Server(server, I18n.get("lanServer.title"), server.getMotd() + " · " + server.getAddress(),
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
	private Object favicon(ServerData data) {
		byte[] bytes = data.getIconBytes();
		if (bytes == null) {
			return null;
		}
		Favicon favicon = this.favicons.computeIfAbsent(data.ip, address -> new Favicon());
		if (!Arrays.equals(bytes, favicon.bytes)) {
			favicon.bytes = bytes;
			try {
				if (favicon.icon == null) {
					favicon.icon = FaviconTexture.forServer(this.minecraft.getTextureManager(), data.ip);
				}
				favicon.icon.upload(NativeImage.read(bytes));
			} catch (IOException | RuntimeException exception) {
				NovaClient.LOGGER.debug("Server-Icon von {} ungültig", data.ip, exception);
				favicon.bytes = null;
				return null;
			}
		}
		return favicon.icon == null ? null : favicon.icon.textureLocation();
	}

	/* ========================================
	   AKTIONEN
	======================================== */

	@Override
	public void join(NovaMultiplayer.Server server) {
		ServerData data = server.lan()
			? new ServerData(((LanServer) server.handle()).getMotd(), ((LanServer) server.handle()).getAddress(), ServerData.Type.LAN)
			: (ServerData) server.handle();
		this.connect(data);
	}

	private void connect(ServerData data) {
		ConnectScreen.startConnecting(this, this.minecraft, ServerAddress.parseString(data.ip), data, false, null);
	}

	@Override
	public void directConnect() {
		ServerData direct = new ServerData(I18n.get("selectServer.defaultName"), "", ServerData.Type.OTHER);
		this.show(new DirectJoinServerScreen(this, accepted -> {
			if (accepted) {
				this.minecraft.options.lastMpIp = direct.ip;
				this.minecraft.options.save();
				this.connect(direct);
			} else {
				this.show(this);
			}
		}, direct));
	}

	@Override
	public void add() {
		ServerData created = new ServerData(I18n.get("selectServer.defaultName"), "", ServerData.Type.OTHER);
		this.show(new ManageServerScreen(this, Component.translatable("manageServer.add.title"), accepted -> {
			if (accepted) {
				this.serverList.add(created, false);
				this.serverList.save();
				this.menu.select(created);
				this.restartPings();
			}
			this.show(this);
		}, created));
	}

	@Override
	public void edit(NovaMultiplayer.Server server) {
		ServerData data = (ServerData) server.handle();
		ServerData copy = new ServerData(data.name, data.ip, ServerData.Type.OTHER);
		copy.copyFrom(data);
		this.show(new ManageServerScreen(this, Component.translatable("manageServer.edit.title"), accepted -> {
			if (accepted) {
				data.copyNameIconFrom(copy);
				data.ip = copy.ip;
				data.setResourcePackStatus(copy.getResourcePackStatus());
				this.serverList.save();
				this.restartPings();
			}
			this.show(this);
		}, copy));
	}

	@Override
	public void delete(NovaMultiplayer.Server server) {
		ServerData data = (ServerData) server.handle();
		this.show(new ConfirmScreen(accepted -> {
			if (accepted) {
				this.serverList.remove(data);
				this.serverList.save();
				this.menu.select(null);
				this.restartPings();
			}
			this.show(this);
		}, Component.translatable("selectServer.deleteQuestion"), Component.translatable("selectServer.deleteWarning", data.name),
			Component.translatable("selectServer.deleteButton"), CommonComponents.GUI_CANCEL));
	}

	@Override
	public void refresh() {
		this.restartPings();
	}

	@Override
	public void back() {
		this.onClose();
	}

	@Override
	public void tool(MenuButton.Action action) {
		switch (action) {
			case MODS_FOLDER -> NovaTitleScreen.openFolder(FabricLoader.getInstance().getGameDir().resolve("mods"));
			case SCREENSHOTS_FOLDER -> NovaTitleScreen.openFolder(new File(this.minecraft.gameDirectory, "screenshots").toPath());
			case COSMETICS -> this.show(new NovaMenuScreen(this, NovaModMenu.Tab.COSMETICS));
			default -> {
			}
		}
	}

	@Override
	public String playerName() {
		return this.minecraft.getUser().getName();
	}

	@Override
	public boolean microsoftAccount() {
		return this.minecraft.getUser().getXuid().isPresent();
	}

	@Override
	public String minecraftVersion() {
		return SharedConstants.getCurrentVersion().name();
	}

	@Override
	public String novaVersion() {
		return NovaClient.version();
	}

	@Override
	public String translate(String key) {
		return I18n.get(key);
	}
}
