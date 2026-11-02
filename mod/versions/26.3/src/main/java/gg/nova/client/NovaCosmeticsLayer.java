package gg.nova.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import gg.nova.core.cosmetic.CosmeticModel;
import gg.nova.core.cosmetic.Cosmetics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;

/**
 * Zeichnet die angelegten 3D-Cosmetics am eigenen Spieler (F5, Inventar). Modell und Animation: {@link Cosmetics}.
 * Erst die Textur mit Licht, dann die Leucht-Ebene in voller Helligkeit darüber.
 */
public final class NovaCosmeticsLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	public NovaCosmeticsLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
		super(parent);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light,
			AvatarRenderState state, float yRot, float xRot) {
		Minecraft minecraft = Minecraft.getInstance();
		if (state.isInvisible || minecraft.player == null || state.id != minecraft.player.getId()) {
			return;
		}

		float seconds = Cosmetics.animationSeconds();
		Cosmetics.trackPlayer(state.x, state.y, state.z, state.bodyRot, state.isCrouching, state.isFallFlying);
		// Wie beim Vanilla-Cape: unter einer Elytra wird das Cape ausgeblendet
		boolean elytra = state.chestEquipment.is(Items.ELYTRA);
		// Über einem Helm sitzen Kronen und Hüte etwas größer, damit sie außen auf ihm liegen
		boolean helmet = !state.headEquipment.isEmpty();
		for (Cosmetics.Cosmetic cosmetic : Cosmetics.equipped()) {
			if (elytra && cosmetic.hiddenByElytra) {
				continue;
			}
			poseStack.pushPose();
			if (!cosmetic.onRoot) {
				(cosmetic.onHead ? this.getParentModel().head : this.getParentModel().body).translateAndRotate(poseStack);
			}
			if (helmet && cosmetic.onHead) {
				poseStack.scale(Cosmetics.HELMET_SCALE, Cosmetics.HELMET_SCALE, Cosmetics.HELMET_SCALE);
			}
			// Gezeichnet wird später gesammelt; die Ecken entstehen erst im Rückruf
			Identifier texture = Identifier.fromNamespaceAndPath(Cosmetics.NAMESPACE, cosmetic.texturePath);
			collector.submitCustomGeometry(poseStack, RenderTypes.entityCutoutCull(texture), (pose, consumer) ->
				draw(consumer, pose, cosmetic, seconds, CosmeticModel.Pass.BASE, -1, light));

			if (cosmetic.glowTexturePath != null) {
				Identifier glow = Identifier.fromNamespaceAndPath(Cosmetics.NAMESPACE, cosmetic.glowTexturePath);
				int color = Cosmetics.glowColor(cosmetic, seconds);
				collector.submitCustomGeometry(poseStack, RenderTypes.eyes(glow), (pose, consumer) ->
					draw(consumer, pose, cosmetic, seconds, CosmeticModel.Pass.GLOW, color, Cosmetics.FULL_BRIGHT));
			}
			poseStack.popPose();
		}
	}

	private static void draw(VertexConsumer consumer, PoseStack.Pose pose, Cosmetics.Cosmetic cosmetic,
			float seconds, CosmeticModel.Pass pass, int color, int light) {
		cosmetic.render((x, y, z, u, v, nx, ny, nz) -> consumer.addVertex(pose, x, y, z)
			.setColor(color)
			.setUv(u, v)
			.setOverlay(OverlayTexture.NO_OVERLAY)
			.setLight(light)
			.setNormal(pose, nx, ny, nz), seconds, pass);
	}
}
