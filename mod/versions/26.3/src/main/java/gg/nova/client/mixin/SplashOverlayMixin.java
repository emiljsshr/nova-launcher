package gg.nova.client.mixin;

import gg.nova.client.GuiCanvas;
import gg.nova.client.NovaClient;
import gg.nova.client.NovaLogoTexture;
import gg.nova.core.NovaDraw;
import gg.nova.core.loading.LoadingInfo;
import gg.nova.core.loading.NovaLoadingRenderer;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ersetzt das Zeichnen des Mojang-Ladebildschirms durch den Nova-Ladebildschirm.
 * Ab 26.x steckt die Abschluss-Logik in {@code LoadingOverlay#tick} und bleibt unverändert;
 * ersetzt wird nur {@code extractRenderState} (Ein-/Ausblenden wie Vanilla).
 */
@Mixin(LoadingOverlay.class)
public abstract class SplashOverlayMixin {
	@Shadow @Final private Minecraft minecraft;
	@Shadow @Final private ReloadInstance reload;
	@Shadow @Final private boolean fadeIn;
	@Shadow private float currentProgress;
	@Shadow private long fadeOutStart;
	@Shadow private long fadeInStart;

	@Inject(method = "registerTextures", at = @At("TAIL"))
	private static void nova$registerLogo(TextureManager textureManager, CallbackInfo ci) {
		textureManager.registerAndLoad(NovaClient.LOGO, new NovaLogoTexture());
	}

	@Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
	private void nova$render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		ci.cancel();

		long now = Util.getMillis();
		if (this.fadeIn && this.fadeInStart == -1L) {
			this.fadeInStart = now;
		}

		float fadeOut = this.fadeOutStart > -1L ? (now - this.fadeOutStart) / 1000.0F : -1.0F;
		float fadeInProgress = this.fadeInStart > -1L ? (now - this.fadeInStart) / 500.0F : -1.0F;
		float backgroundAlpha;
		float contentAlpha;

		if (fadeOut >= 1.0F) {
			// Laden fertig: darunterliegenden Screen zeigen und Nova ausblenden
			if (this.minecraft.gui.screen() != null) {
				this.minecraft.gui.screen().extractRenderStateWithTooltipAndSubtitles(graphics, 0, 0, delta);
			} else {
				this.minecraft.gui.hud.extractDeferredSubtitles();
			}
			graphics.nextStratum();
			backgroundAlpha = 1.0F - Mth.clamp(fadeOut - 1.0F, 0.0F, 1.0F);
			contentAlpha = backgroundAlpha;
		} else if (this.fadeIn) {
			// Neu laden (z. B. Ressourcenpaket gewechselt): über dem aktuellen Screen einblenden
			if (this.minecraft.gui.screen() != null && fadeInProgress < 1.0F) {
				this.minecraft.gui.screen().extractRenderStateWithTooltipAndSubtitles(graphics, mouseX, mouseY, delta);
			} else {
				this.minecraft.gui.hud.extractDeferredSubtitles();
			}
			graphics.nextStratum();
			backgroundAlpha = Mth.clamp(fadeInProgress, 0.15F, 1.0F);
			contentAlpha = Mth.clamp(fadeInProgress, 0.0F, 1.0F);
		} else {
			// Erster Start: Fenster in Nova-Navy löschen
			ARGB.setVector4fFromARGB32(this.minecraft.gameRenderer.gameRenderState().guiRenderState.clearColorOverride, NovaDraw.NAVY);
			backgroundAlpha = 1.0F;
			contentAlpha = 1.0F;
		}

		this.currentProgress = Mth.clamp(this.currentProgress * 0.95F + this.reload.getActualProgress() * 0.050000012F, 0.0F, 1.0F);
		float barAlpha = fadeOut < 0.0F ? contentAlpha : contentAlpha * (1.0F - Mth.clamp(fadeOut, 0.0F, 1.0F));

		NovaLoadingRenderer.render(new GuiCanvas(graphics, this.minecraft.font), new LoadingInfo(
			this.currentProgress,
			backgroundAlpha,
			contentAlpha,
			barAlpha,
			this.fadeOutStart > -1L,
			this.fadeIn,
			this.minecraft.options.languageCode.startsWith("de"),
			SharedConstants.getCurrentVersion().name(),
			NovaClient.version(),
			"FABRIC",
			NovaClient.loaderVersion(),
			NovaClient.modCount()
		));

		if (fadeOut >= 2.0F) {
			this.minecraft.gui.setOverlay(null);
		}
	}
}
