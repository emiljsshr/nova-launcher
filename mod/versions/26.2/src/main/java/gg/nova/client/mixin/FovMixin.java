package gg.nova.client.mixin;

import gg.nova.core.mods.NovaMods;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Zoom (Mod-Menü): verkleinert das Sichtfeld der Welt; das der Hand (calculateHudFov) bleibt. */
@Mixin(Camera.class)
public abstract class FovMixin {
	@Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
	private void nova$zoom(float partialTick, CallbackInfoReturnable<Float> info) {
		info.setReturnValue((float) NovaMods.fov(info.getReturnValue()));
	}
}
