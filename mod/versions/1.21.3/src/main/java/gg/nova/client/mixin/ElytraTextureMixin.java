package gg.nova.client.mixin;

import gg.nova.core.cosmetic.Cosmetics;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.feature.ElytraFeatureRenderer;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Elytra des eigenen Spielers in der Textur des angelegten Capes (wie bei Minecraft-Capes). */
@Mixin(ElytraFeatureRenderer.class)
public abstract class ElytraTextureMixin {
	@Inject(method = "getTexture", at = @At("HEAD"), cancellable = true)
	private static void nova$capeTexture(BipedEntityRenderState state, CallbackInfoReturnable<Identifier> info) {
		MinecraftClient client = MinecraftClient.getInstance();
		String path = Cosmetics.elytraTexturePath();
		if (path != null && client.player != null && state instanceof PlayerEntityRenderState player
				&& player.id == client.player.getId()) {
			info.setReturnValue(Identifier.of(Cosmetics.NAMESPACE, path));
		}
	}
}
