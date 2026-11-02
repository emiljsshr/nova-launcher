package gg.nova.client.mixin;

import gg.nova.client.NovaClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Ruft {@link NovaClient#tick} zu Beginn jedes Spieltakts auf (20-mal pro Sekunde, auch im Hauptmenü). */
@Mixin(Minecraft.class)
public abstract class ClientTickMixin {
	@Inject(method = "tick", at = @At("HEAD"))
	private void nova$tick(CallbackInfo info) {
		NovaClient.tick((Minecraft) (Object) this);
	}
}
