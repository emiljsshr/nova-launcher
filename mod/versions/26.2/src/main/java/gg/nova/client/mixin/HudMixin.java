package gg.nova.client.mixin;

import gg.nova.client.GuiCanvas;
import gg.nova.core.mods.NovaMods;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Zeichnet die HUD-Module des Mod-Menüs nach dem Vanilla-HUD. */
@Mixin(Hud.class)
public abstract class HudMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V", at = @At("TAIL"))
	private void nova$renderHud(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo info) {
		Minecraft minecraft = Minecraft.getInstance();
		if (!minecraft.gui.hud.isHidden() && !minecraft.getDebugOverlay().showDebugScreen()) {
			NovaMods.renderHud(new GuiCanvas(graphics, minecraft.font));
		}
	}
}
