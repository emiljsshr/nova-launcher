package gg.nova.client.mixin;

import gg.nova.core.mods.NovaMods;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Zoom (Mod-Menü): verkleinert das Sichtfeld der Welt, nicht das der Hand. */
@Mixin(GameRenderer.class)
public abstract class FovMixin {
	@Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
	private void nova$zoom(Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Float> info) {
		if (changingFov) {
			info.setReturnValue((float) NovaMods.fov(info.getReturnValue()));
		}
	}
}
