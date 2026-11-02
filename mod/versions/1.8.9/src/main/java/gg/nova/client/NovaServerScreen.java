package gg.nova.client;

import gg.nova.core.menu.MenuButton;
import gg.nova.core.menu.NovaServerList;
import java.util.List;
import net.minecraft.client.gui.screen.ConnectScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.resource.language.I18n;

/** "Nova Server": Partner-Server mit echtem Ping und Direktbeitritt. */
public class NovaServerScreen extends Screen {
	private final Screen parent;
	private final PartnerPings pings = new PartnerPings();
	private NovaServerList list;

	public NovaServerScreen(Screen parent) {
		this.parent = parent;
	}

	@Override
	public void init() {
		this.buttons.clear();
		this.list = NovaServerList.layout(this.width, this.height);
		List<MenuButton> specs = this.list.buttons();
		for (int index = 0; index < specs.size(); index++) {
			this.buttons.add(new NovaButtonWidget(index, specs.get(index), () -> null));
		}
		this.pings.start();
	}

	@Override
	protected void buttonClicked(ButtonWidget button) {
		MenuButton spec = ((NovaButtonWidget) button).spec();
		if (spec.action() == MenuButton.Action.JOIN_PARTNER) {
			this.client.setScreen(new ConnectScreen(this, this.client, this.pings.server(spec.partnerIndex())));
		} else {
			this.client.setScreen(this.parent);
		}
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
	public void render(int mouseX, int mouseY, float delta) {
		this.renderBackground();
		super.render(mouseX, mouseY, delta);
		this.list.render(new LegacyCanvas(this.client), this.pings.statuses(), key -> I18n.translate(key));
	}
}
