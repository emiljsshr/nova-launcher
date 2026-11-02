package gg.nova.client;

import gg.nova.core.menu.MenuButton;
import java.util.function.Supplier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.client.input.AbstractInput;
import net.minecraft.text.Text;

/** Minecraft-Widget, das einen {@link MenuButton} aus dem Kern darstellt. */
public final class NovaButtonWidget extends PressableWidget {
	private final MenuButton spec;
	private final Runnable action;
	private final Supplier<String> badge;

	public NovaButtonWidget(MenuButton spec, Runnable action, Supplier<String> badge) {
		super(spec.x(), spec.y(), spec.width(), spec.height(), Text.translatable(spec.labelKey()));
		this.spec = spec;
		this.action = action;
		this.badge = badge;
		if (spec.tooltipKey() != null) {
			this.setTooltip(Tooltip.of(Text.translatable(spec.tooltipKey())));
		}
	}

	@Override
	public void onPress(AbstractInput input) {
		this.action.run();
	}

	@Override
	protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
		DrawContextCanvas canvas = new DrawContextCanvas(context, MinecraftClient.getInstance().textRenderer);
		this.spec.render(canvas, this.getMessage().getString(), this.badge.get(), this.isSelected(), this.active, this.alpha);
	}

	@Override
	protected void appendClickableNarrations(NarrationMessageBuilder builder) {
		this.appendDefaultNarrations(builder);
	}
}
