package gg.nova.client.mixin;

import gg.nova.client.LegacyCanvas;
import gg.nova.core.mods.NovaMods;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Zeichnet die HUD-Module des Mod-Menüs nach dem Vanilla-HUD. */
@Mixin(InGameHud.class)
public abstract class HudMixin {
	@Inject(method = "render", at = @At("TAIL"))
	private void nova$renderHud(float tickDelta, CallbackInfo info) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (!client.options.hudHidden && !client.options.debugEnabled) {
			NovaMods.renderHud(new LegacyCanvas(client));
		}
	}
}
