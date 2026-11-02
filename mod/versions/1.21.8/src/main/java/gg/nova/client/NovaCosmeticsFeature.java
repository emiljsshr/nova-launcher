package gg.nova.client;

import gg.nova.core.cosmetic.CosmeticModel;
import gg.nova.core.cosmetic.Cosmetics;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;

/**
 * Zeichnet die angelegten 3D-Cosmetics am eigenen Spieler (F5, Inventar). Modell und Animation: {@link Cosmetics}.
 * Erst die Textur mit Licht, dann die Leucht-Ebene in voller Helligkeit darüber.
 */
public final class NovaCosmeticsFeature extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {
	public NovaCosmeticsFeature(FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> context) {
		super(context);
	}

	@Override
	public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
			PlayerEntityRenderState state, float limbAngle, float limbDistance) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (state.invisible || client.player == null || state.id != client.player.getId()) {
			return;
		}

		float seconds = Cosmetics.animationSeconds();
		Cosmetics.trackPlayer(state.x, state.y, state.z, state.bodyYaw, state.isInSneakingPose, state.isGliding);
		// Wie beim Vanilla-Cape: unter einer Elytra wird das Cape ausgeblendet
		boolean elytra = state.equippedChestStack.isOf(Items.ELYTRA);
		// Über einem Helm sitzen Kronen und Hüte etwas größer, damit sie außen auf ihm liegen
		boolean helmet = !state.equippedHeadStack.isEmpty();
		for (Cosmetics.Cosmetic cosmetic : Cosmetics.equipped()) {
			if (elytra && cosmetic.hiddenByElytra) {
				continue;
			}
			matrices.push();
			if (!cosmetic.onRoot) {
				(cosmetic.onHead ? this.getContextModel().head : this.getContextModel().body).applyTransform(matrices);
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
				VertexConsumer glowing = vertexConsumers.getBuffer(RenderLayer.getEyes(glow));
				draw(glowing, entry, cosmetic, seconds, CosmeticModel.Pass.GLOW,
					Cosmetics.glowColor(cosmetic, seconds), Cosmetics.FULL_BRIGHT);
			}
			matrices.pop();
		}
	}

	private static void draw(VertexConsumer consumer, MatrixStack.Entry entry, Cosmetics.Cosmetic cosmetic,
			float seconds, CosmeticModel.Pass pass, int color, int light) {
		cosmetic.render((x, y, z, u, v, nx, ny, nz) -> consumer.vertex(entry, x, y, z)
			.color(color)
			.texture(u, v)
			.overlay(OverlayTexture.DEFAULT_UV)
			.light(light)
			.normal(entry, nx, ny, nz), seconds, pass);
	}
}
