package gg.nova.client;

import gg.nova.core.menu.MenuButton;
import java.util.function.Supplier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.resource.language.I18n;

/** 1.8.9-Button, der einen {@link MenuButton} aus dem Kern darstellt. */
public final class NovaButtonWidget extends ButtonWidget {
	private final MenuButton spec;
	private final Supplier<String> badge;

	public NovaButtonWidget(int id, MenuButton spec, Supplier<String> badge) {
		super(id, spec.x(), spec.y(), spec.width(), spec.height(), I18n.translate(spec.labelKey()));
		this.spec = spec;
		this.badge = badge;
	}

	public MenuButton spec() {
		return this.spec;
	}

	@Override
	public void render(MinecraftClient client, int mouseX, int mouseY) {
		if (!this.visible) {
			return;
		}
		this.hovered = mouseX >= this.x && mouseY >= this.y && mouseX < this.x + this.width && mouseY < this.y + this.height;
		this.spec.render(new LegacyCanvas(client), this.message, this.badge.get(), this.hovered, this.active, 1.0F);
	}
}
