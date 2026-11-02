package gg.nova.client.mixin;

import com.mojang.blaze3d.platform.GlStateManager;
import gg.nova.client.LegacyCanvas;
import gg.nova.client.NovaClient;
import gg.nova.core.loading.LoadingInfo;
import gg.nova.core.loading.NovaLoadingRenderer;
import java.io.InputStream;
import javax.imageio.ImageIO;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.client.util.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ersetzt den Mojang-Startbildschirm von 1.8.9 ({@code MinecraftClient#loadLogo}) durch den Nova-Ladebildschirm.
 * 1.8.9 zeichnet hier ein einziges Standbild und kennt keinen Ladefortschritt – der Balken zeigt daher "unbekannt".
 */
@Mixin(MinecraftClient.class)
public abstract class SplashOverlayMixin {
	@Shadow
	public abstract void updateDisplay();

	@Inject(method = "loadLogo", at = @At("HEAD"), cancellable = true)
	private void nova$drawLoadingScreen(TextureManager textureManager, CallbackInfo ci) {
		ci.cancel();
		MinecraftClient client = (MinecraftClient) (Object) this;

		// Logo direkt aus der Mod-JAR laden – die Ressourcenpakete sind noch nicht geladen
		try (InputStream stream = SplashOverlayMixin.class.getResourceAsStream("/assets/nova/textures/gui/logo.png")) {
			if (stream != null) {
				textureManager.loadTexture(NovaClient.LOGO, new NativeImageBackedTexture(ImageIO.read(stream)));
			}
		} catch (Exception exception) {
			NovaClient.LOGGER.warn("Nova-Logo konnte nicht geladen werden", exception);
		}

		// Gleiche Vorbereitung wie Vanilla: in einen Framebuffer zeichnen und anzeigen
		Window window = new Window(client);
		int scale = window.getScaleFactor();
		Framebuffer framebuffer = new Framebuffer(window.getWidth() * scale, window.getHeight() * scale, true);
		framebuffer.bind(false);
		GlStateManager.matrixMode(5889);
		GlStateManager.loadIdentity();
		GlStateManager.ortho(0.0, window.getWidth(), window.getHeight(), 0.0, 1000.0, 3000.0);
		GlStateManager.matrixMode(5888);
		GlStateManager.loadIdentity();
		GlStateManager.translate(0.0F, 0.0F, -2000.0F);
		GlStateManager.disableLighting();
		GlStateManager.disableFog();
		GlStateManager.disableDepthTest();
		GlStateManager.enableTexture();

		String language = client.options != null ? client.options.language : "en_US";
		NovaLoadingRenderer.render(new LegacyCanvas(client), new LoadingInfo(
			-1.0F, 1.0F, 1.0F, 1.0F, false, false, language.toLowerCase().startsWith("de"),
			"1.8.9", NovaClient.version(), "LEGACY FABRIC", NovaClient.loaderVersion(), NovaClient.modCount()
		));

		GlStateManager.disableLighting();
		GlStateManager.disableFog();
		framebuffer.unbind();
		framebuffer.draw(window.getWidth() * scale, window.getHeight() * scale);
		GlStateManager.enableAlphaTest();
		GlStateManager.alphaFunc(516, 0.1F);
		this.updateDisplay();
	}
}
