package gg.nova.client;

import gg.nova.core.cosmetic.CosmeticModel;
import gg.nova.core.cosmetic.Cosmetics;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Zeichnet die angelegten 3D-Cosmetics am eigenen Spieler (F5, Inventar). Modell und Animation: {@link Cosmetics}.
 * Erst die Textur mit Licht, dann die Leucht-Ebene in voller Helligkeit darüber.
 */
public final class NovaCosmeticsFeature
		extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
	public NovaCosmeticsFeature(
			FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> context) {
		super(context);
	}

	@Override
	public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
			AbstractClientPlayerEntity player, float limbAngle, float limbDistance, float tickDelta,
			float animationProgress, float headYaw, float headPitch) {
		if (player.isInvisible() || player != MinecraftClient.getInstance().player) {
			return;
		}

		float seconds = Cosmetics.animationSeconds();
		Vec3d position = player.getLerpedPos(tickDelta);
		Cosmetics.trackPlayer(position.x, position.y, position.z,
			MathHelper.lerpAngleDegrees(tickDelta, player.prevBodyYaw, player.bodyYaw), player.isInSneakingPose(), player.isFallFlying());
		// Wie beim Vanilla-Cape: unter einer Elytra wird das Cape ausgeblendet
		boolean elytra = player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA);
		// Über einem Helm sitzen Kronen und Hüte etwas größer, damit sie außen auf ihm liegen
		boolean helmet = !player.getEquippedStack(EquipmentSlot.HEAD).isEmpty();
		for (Cosmetics.Cosmetic cosmetic : Cosmetics.equipped()) {
			if (elytra && cosmetic.hiddenByElytra) {
				continue;
			}
			matrices.push();
			if (!cosmetic.onRoot) {
				(cosmetic.onHead ? this.getContextModel().head : this.getContextModel().body).rotate(matrices);
			}
			if (helmet && cosmetic.onHead) {
				matrices.scale(Cosmetics.HELMET_SCALE, Cosmetics.HELMET_SCALE, Cosmetics.HELMET_SCALE);
			}
			MatrixStack.Entry entry = matrices.peek();

			Identifier texture = Identifier.of(Cosmetics.NAMESPACE, cosmetic.texturePath);
			VertexConsumer base = vertexConsumers.getBuffer(RenderLayer.getEntityCutout(texture));
			draw(base, entry, cosmetic, seconds, CosmeticModel.Pass.BASE, -1, light);

			if (cosmetic.glowTexturePath != null) {
				Identifier glow = Identifier.of(Cosmetics.NAMESPACE, cosmetic.glowTexturePath);
				// "eyes" mischt hier additiv ohne Alpha; die Leucht-Textur braucht transluzente Mischung
				VertexConsumer glowing = vertexConsumers.getBuffer(RenderLayer.getEntityTranslucentEmissive(glow));
				draw(glowing, entry, cosmetic, seconds, CosmeticModel.Pass.GLOW,
					Cosmetics.glowColor(cosmetic, seconds), Cosmetics.FULL_BRIGHT);
			}
			matrices.pop();
		}
	}

	private static void draw(VertexConsumer consumer, MatrixStack.Entry entry, Cosmetics.Cosmetic cosmetic,
			float seconds, CosmeticModel.Pass pass, int color, int light) {
		cosmetic.render((x, y, z, u, v, nx, ny, nz) -> {
				consumer.vertex(entry, x, y, z)
					.color(color)
					.texture(u, v)
					.overlay(OverlayTexture.DEFAULT_UV)
					.light(light)
					.normal(entry, nx, ny, nz);
				consumer.next();
			}, seconds, pass);
	}
}
