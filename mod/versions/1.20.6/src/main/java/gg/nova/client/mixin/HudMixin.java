package gg.nova.client.mixin;

import gg.nova.client.DrawContextCanvas;
import gg.nova.core.mods.NovaMods;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Zeichnet die HUD-Module des Mod-Menüs nach dem Vanilla-HUD. */
@Mixin(InGameHud.class)
public abstract class HudMixin {
	@Inject(method = "render", at = @At("TAIL"))
	private void nova$renderHud(DrawContext context, float tickDelta, CallbackInfo info) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (!client.options.hudHidden && !client.getDebugHud().shouldShowDebugHud()) {
			NovaMods.renderHud(new DrawContextCanvas(context, client.textRenderer));
		}
	}
}
