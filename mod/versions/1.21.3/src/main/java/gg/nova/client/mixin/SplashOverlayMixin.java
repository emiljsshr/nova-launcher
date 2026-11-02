package gg.nova.client.mixin;

import com.mojang.blaze3d.platform.GlStateManager;
import gg.nova.client.DrawContextCanvas;
import gg.nova.client.NovaCapture;
import gg.nova.client.NovaClient;
import gg.nova.client.NovaLogoTexture;
import gg.nova.core.NovaDraw;
import gg.nova.core.loading.LoadingInfo;
import gg.nova.core.loading.NovaLoadingRenderer;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.SharedConstants;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.SplashOverlay;
import net.minecraft.resource.ResourceReload;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ersetzt den Mojang-Ladebildschirm durch den Nova-Ladebildschirm.
 * Ablauf (Fortschritt, Ein-/Ausblenden, Fehlerbehandlung) entspricht 1:1 {@link SplashOverlay#render};
 * gezeichnet wird von {@link NovaLoadingRenderer} im Kern.
 */
@Mixin(SplashOverlay.class)
public abstract class SplashOverlayMixin {
	@Shadow @Final private MinecraftClient client;
	@Shadow @Final private ResourceReload reload;
	@Shadow @Final private Consumer<Optional<Throwable>> exceptionHandler;
	@Shadow @Final private boolean reloading;
	@Shadow private float progress;
	@Shadow private long reloadCompleteTime;
	@Shadow private long reloadStartTime;

	@Inject(method = "init", at = @At("TAIL"))
	private static void nova$registerLogo(MinecraftClient client, CallbackInfo ci) {
		client.getTextureManager().registerTexture(NovaClient.LOGO, new NovaLogoTexture());
	}

	@Inject(method = "render", at = @At("HEAD"), cancellable = true)
	private void nova$render(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		ci.cancel();

		int width = context.getScaledWindowWidth();
		int height = context.getScaledWindowHeight();
		long now = Util.getMeasuringTimeMs();
		if (this.reloading && this.reloadStartTime == -1L) {
			this.reloadStartTime = now;
		}

		float completeFade = this.reloadCompleteTime > -1L ? (now - this.reloadCompleteTime) / 1000.0F : -1.0F;
		float startFade = this.reloadStartTime > -1L ? (now - this.reloadStartTime) / 500.0F : -1.0F;
		float backgroundAlpha;
		float contentAlpha;

		if (completeFade >= 1.0F) {
			// Laden fertig: darunterliegenden Screen zeigen und Nova ausblenden
			if (this.client.currentScreen != null) {
				this.client.currentScreen.render(context, 0, 0, delta);
			}
			backgroundAlpha = 1.0F - MathHelper.clamp(completeFade - 1.0F, 0.0F, 1.0F);
			contentAlpha = backgroundAlpha;
		} else if (this.reloading) {
			// Neu laden (z. B. Ressourcenpaket gewechselt): über dem aktuellen Screen einblenden
			if (this.client.currentScreen != null && startFade < 1.0F) {
				this.client.currentScreen.render(context, mouseX, mouseY, delta);
			}
			backgroundAlpha = MathHelper.clamp(startFade, 0.15F, 1.0F);
			contentAlpha = MathHelper.clamp(startFade, 0.0F, 1.0F);
		} else {
			// Erster Start: Fenster komplett in Nova-Navy löschen
			GlStateManager._clearColor(0x0B / 255.0F, 0x12 / 255.0F, 0x29 / 255.0F, 1.0F);
			GlStateManager._clear(16384);
			backgroundAlpha = 1.0F;
			contentAlpha = 1.0F;
		}

		float target = this.reload.getProgress();
		this.progress = MathHelper.clamp(this.progress * 0.95F + target * 0.050000012F, 0.0F, 1.0F);
		float barAlpha = completeFade < 0.0F ? contentAlpha : contentAlpha * (1.0F - MathHelper.clamp(completeFade, 0.0F, 1.0F));

		NovaLoadingRenderer.render(new DrawContextCanvas(context, this.client.textRenderer), new LoadingInfo(
			this.progress,
			backgroundAlpha,
			contentAlpha,
			barAlpha,
			this.reloadCompleteTime > -1L,
			this.reloading,
			this.client.options.language.startsWith("de"),
			SharedConstants.getGameVersion().getName(),
			NovaClient.version(),
			"FABRIC",
			NovaClient.loaderVersion(),
			NovaClient.modCount()
		));
		NovaCapture.loading(context, this.progress);

		if (completeFade >= 2.0F) {
			this.client.setOverlay(null);
		}

		if (this.reloadCompleteTime == -1L && this.reload.isComplete() && (!this.reloading || startFade >= 2.0F)) {
			try {
				this.reload.throwException();
				this.exceptionHandler.accept(Optional.empty());
			} catch (Throwable throwable) {
				this.exceptionHandler.accept(Optional.of(throwable));
			}

			this.reloadCompleteTime = Util.getMeasuringTimeMs();
			if (this.client.currentScreen != null) {
				this.client.currentScreen.init(this.client, width, height);
			}
		}
	}
}
