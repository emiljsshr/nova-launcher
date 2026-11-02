package gg.nova.client;

import gg.nova.core.menu.MenuButton;
import gg.nova.core.menu.NovaServerList;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

/** "Nova Server": Partner-Server mit echtem Ping und Direktbeitritt. */
public class NovaServerScreen extends Screen {
	private final Screen parent;
	private final PartnerPings pings = new PartnerPings();
	private NovaServerList list;

	public NovaServerScreen(Screen parent) {
		super(Component.translatable("nova.menu.servers"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		this.list = NovaServerList.layout(this.width, this.height);
		boolean multiplayerEnabled = this.minecraft.allowsMultiplayer();
		for (MenuButton spec : this.list.buttons()) {
			NovaButtonWidget widget = this.addRenderableWidget(new NovaButtonWidget(spec, () -> this.perform(spec), () -> null));
			widget.active = multiplayerEnabled || !spec.action().needsMultiplayer();
		}
		this.pings.start();
	}

	private void perform(MenuButton spec) {
		if (spec.action() == MenuButton.Action.JOIN_PARTNER) {
			ServerData data = this.pings.server(spec.partnerIndex());
			ConnectScreen.startConnecting(this, this.minecraft, ServerAddress.parseString(data.ip), data, false, null);
		} else {
			this.onClose();
		}
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(this.parent);
	}

	@Override
	public void tick() {
		this.pings.tick();
	}

	@Override
	public void removed() {
		this.pings.cancel();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		this.list.render(new GuiCanvas(graphics, this.font), this.pings.statuses(), key -> I18n.get(key));
	}
}
