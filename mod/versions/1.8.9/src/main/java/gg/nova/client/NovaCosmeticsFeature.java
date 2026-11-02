package gg.nova.client;

import com.mojang.blaze3d.platform.GLX;
import com.mojang.blaze3d.platform.GlStateManager;
import gg.nova.core.cosmetic.CosmeticModel;
import gg.nova.core.cosmetic.Cosmetics;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.util.Identifier;

/**
 * Zeichnet die angelegten 3D-Cosmetics am eigenen Spieler (F5, Inventar). Modell und Animation: {@link Cosmetics}.
 * 1.8.9: OpenGL-Sofortmodus. Erst die Textur mit Licht, dann die Leucht-Ebene additiv (mit Alpha) in voller
 * Helligkeit – ähnlich den Spinnenaugen.
 */
public final class NovaCosmeticsFeature implements FeatureRenderer<AbstractClientPlayerEntity> {
	private final PlayerEntityRenderer renderer;

	public NovaCosmeticsFeature(PlayerEntityRenderer renderer) {
		this.renderer = renderer;
	}

	@Override
	public void render(AbstractClientPlayerEntity player, float handSwing, float handSwingAmount, float tickDelta,
			float age, float headYaw, float headPitch, float scale) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (player.isInvisible() || player != client.player) {
			return;
		}

		float seconds = Cosmetics.animationSeconds();
		Cosmetics.trackPlayer(player.prevX + (player.x - player.prevX) * tickDelta,
			player.prevY + (player.y - player.prevY) * tickDelta, player.prevZ + (player.z - player.prevZ) * tickDelta,
			player.prevBodyYaw + (player.bodyYaw - player.prevBodyYaw) * tickDelta, player.isSneaking(), false);
		// Über einem Helm sitzen Kronen und Hüte etwas größer, damit sie außen auf ihm liegen
		boolean helmet = player.getArmorSlot(3) != null;
		for (Cosmetics.Cosmetic cosmetic : Cosmetics.equipped()) {
			GlStateManager.pushMatrix();
			// Wie beim Vanilla-Cape: geduckt sitzt der Oberkörper tiefer
			if (player.isSneaking()) {
				GlStateManager.translate(0.0F, 0.2F, 0.0F);
			}
			if (!cosmetic.onRoot) {
				(cosmetic.onHead ? this.renderer.getModel().head : this.renderer.getModel().body).preRender(scale);
			}
			if (helmet && cosmetic.onHead) {
				GlStateManager.scale(Cosmetics.HELMET_SCALE, Cosmetics.HELMET_SCALE, Cosmetics.HELMET_SCALE);
			}

			client.getTextureManager().bindTexture(new Identifier(Cosmetics.NAMESPACE, cosmetic.texturePath));
			GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
			draw(cosmetic, seconds, CosmeticModel.Pass.BASE);

			if (cosmetic.glowTexturePath != null) {
				client.getTextureManager().bindTexture(new Identifier(Cosmetics.NAMESPACE, cosmetic.glowTexturePath));
				float level = cosmetic.model.glowIntensity(seconds);
				GlStateManager.enableBlend();
				GlStateManager.blendFunc(770, 1);
				GlStateManager.depthMask(false);
				GlStateManager.disableLighting();
				GlStateManager.disableAlphaTest();
				GLX.gl13MultiTexCoord2f(GLX.lightmapTextureUnit, 240.0F, 240.0F);
				GlStateManager.color(1.0F, 1.0F, 1.0F, level);
				draw(cosmetic, seconds, CosmeticModel.Pass.GLOW);

				int brightness = player.getLightmapCoordinates(tickDelta);
				GLX.gl13MultiTexCoord2f(GLX.lightmapTextureUnit, brightness % 65536, brightness / 65536);
				GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
				GlStateManager.enableAlphaTest();
				GlStateManager.enableLighting();
				GlStateManager.depthMask(true);
				GlStateManager.disableBlend();
			}
			GlStateManager.popMatrix();
		}
	}

	private static void draw(Cosmetics.Cosmetic cosmetic, float seconds, CosmeticModel.Pass pass) {
		Tessellator tessellator = Tessellator.getInstance();
		final BufferBuilder buffer = tessellator.getBuffer();
		buffer.begin(7, VertexFormats.POSITION_TEXTURE_NORMAL);
		cosmetic.render((x, y, z, u, v, nx, ny, nz) -> {
			buffer.vertex(x, y, z).texture(u, v).normal(nx, ny, nz);
			buffer.next();
		}, seconds, pass);
		tessellator.draw();
	}

	@Override
	public boolean combineTextures() {
		return false;
	}
}
