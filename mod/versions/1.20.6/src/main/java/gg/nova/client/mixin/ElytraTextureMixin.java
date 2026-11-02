package gg.nova.client.mixin;

import gg.nova.core.cosmetic.Cosmetics;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.ElytraFeatureRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Elytra des eigenen Spielers in der Textur des angelegten Capes (wie bei Minecraft-Capes). */
@Mixin(ElytraFeatureRenderer.class)
public abstract class ElytraTextureMixin {
	@Unique
	private static boolean nova$localPlayer;

	@Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/entity/LivingEntity;FFFFFF)V",
		at = @At("HEAD"))
	private void nova$rememberPlayer(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
			LivingEntity entity, float limbAngle, float limbDistance, float tickDelta, float animationProgress,
			float headYaw, float headPitch, CallbackInfo info) {
		nova$localPlayer = entity == MinecraftClient.getInstance().player;
	}

	@ModifyArg(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/entity/LivingEntity;FFFFFF)V",
		at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/render/RenderLayer;getArmorCutoutNoCull(Lnet/minecraft/util/Identifier;)Lnet/minecraft/client/render/RenderLayer;"))
	private Identifier nova$capeTexture(Identifier texture) {
		String path = Cosmetics.elytraTexturePath();
		return nova$localPlayer && path != null ? Identifier.of(Cosmetics.NAMESPACE, path) : texture;
	}
}
