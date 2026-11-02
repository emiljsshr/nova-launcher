package gg.nova.client;

import gg.nova.core.menu.MenuButton;
import gg.nova.core.menu.NovaServerList;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.text.Text;

/** "Nova Server": Partner-Server mit echtem Ping und Direktbeitritt. */
public class NovaServerScreen extends Screen {
	private final Screen parent;
	private final PartnerPings pings = new PartnerPings();
	private NovaServerList list;

	public NovaServerScreen(Screen parent) {
		super(Text.translatable("nova.menu.servers"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		this.list = NovaServerList.layout(this.width, this.height);
		boolean multiplayerEnabled = this.client.isMultiplayerEnabled();
		for (MenuButton spec : this.list.buttons()) {
			NovaButtonWidget widget = this.addDrawableChild(new NovaButtonWidget(spec, () -> this.perform(spec), () -> null));
			widget.active = multiplayerEnabled || !spec.action().needsMultiplayer();
		}
		this.pings.start();
	}

	private void perform(MenuButton spec) {
		if (spec.action() == MenuButton.Action.JOIN_PARTNER) {
			ServerInfo info = this.pings.server(spec.partnerIndex());
			ConnectScreen.connect(this, this.client, ServerAddress.parse(info.address), info, false);
		} else {
			this.close();
		}
	}

	@Override
	public void close() {
		this.client.setScreen(this.parent);
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
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		this.renderBackground(context);
		super.render(context, mouseX, mouseY, delta);
		this.list.render(new DrawContextCanvas(context, this.textRenderer), this.pings.statuses(), I18n::translate);
	}
}
