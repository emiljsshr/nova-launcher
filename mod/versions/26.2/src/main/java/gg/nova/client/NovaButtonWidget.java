package gg.nova.client;

import gg.nova.core.menu.MenuButton;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

/** Minecraft-Widget, das einen {@link MenuButton} aus dem Kern darstellt. */
public final class NovaButtonWidget extends AbstractButton {
	private final MenuButton spec;
	private final Runnable action;
	private final Supplier<String> badge;

	public NovaButtonWidget(MenuButton spec, Runnable action, Supplier<String> badge) {
		super(spec.x(), spec.y(), spec.width(), spec.height(), Component.translatable(spec.labelKey()));
		this.spec = spec;
		this.action = action;
		this.badge = badge;
		if (spec.tooltipKey() != null) {
			this.setTooltip(Tooltip.create(Component.translatable(spec.tooltipKey())));
		}
	}

	@Override
	public void onPress(InputWithModifiers input) {
		this.action.run();
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		GuiCanvas canvas = new GuiCanvas(graphics, Minecraft.getInstance().font);
		this.spec.render(canvas, this.getMessage().getString(), this.badge.get(), this.isHoveredOrFocused(), this.active, this.alpha);
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		this.defaultButtonNarrationText(output);
	}
}
