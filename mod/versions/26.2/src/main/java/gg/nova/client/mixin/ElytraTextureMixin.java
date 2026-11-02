package gg.nova.client.mixin;

import gg.nova.core.cosmetic.Cosmetics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.layers.WingsLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Elytra des eigenen Spielers in der Textur des angelegten Capes (wie bei Minecraft-Capes). */
@Mixin(WingsLayer.class)
public abstract class ElytraTextureMixin {
	@Inject(method = "getPlayerElytraTexture", at = @At("HEAD"), cancellable = true)
	private static void nova$capeTexture(HumanoidRenderState state, CallbackInfoReturnable<Identifier> info) {
		Minecraft minecraft = Minecraft.getInstance();
		String path = Cosmetics.elytraTexturePath();
		if (path != null && minecraft.player != null && state instanceof AvatarRenderState avatar
				&& avatar.id == minecraft.player.getId()) {
			info.setReturnValue(Identifier.fromNamespaceAndPath(Cosmetics.NAMESPACE, path));
		}
	}
}
