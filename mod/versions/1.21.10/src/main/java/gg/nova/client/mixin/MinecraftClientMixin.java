package gg.nova.client.mixin;

import gg.nova.client.NovaCapture;
import gg.nova.client.NovaTitleScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Ersetzt jeden Aufruf des Vanilla-Hauptmenüs durch das Nova-Hauptmenü. */
@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
	@ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
	private Screen nova$replaceTitleScreen(Screen screen) {
		// Nur die exakte Vanilla-Klasse ersetzen, damit andere Mods eigene Menüs behalten können
		if (screen != null && screen.getClass() == TitleScreen.class) {
			return new NovaTitleScreen();
		}
		return screen;
	}

	/** Ohne Welt macht {@code setScreen(null)} intern ein Vanilla-Hauptmenü – auch das ersetzen. */
	@ModifyVariable(method = "setScreen", at = @At("STORE"), argsOnly = true)
	private Screen nova$replaceDefaultTitleScreen(Screen screen) {
		return this.nova$replaceTitleScreen(screen);
	}

	/** Test-Screenshots (nur mit -Dnova.capture=true): nach dem kompletten Frame inkl. GUI. */
	@Inject(method = "render", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/util/Window;swapBuffers(Lnet/minecraft/client/util/tracy/TracyFrameCapturer;)V"))
	private void nova$captureFrame(boolean tick, CallbackInfo ci) {
		NovaCapture.flush();
	}
}
