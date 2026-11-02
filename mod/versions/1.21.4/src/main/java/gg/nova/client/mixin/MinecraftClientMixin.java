package gg.nova.client.mixin;

import gg.nova.client.NovaMultiplayerScreen;
import gg.nova.client.NovaTitleScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Ersetzt jeden Aufruf des Vanilla-Hauptmenüs und -Mehrspielermenüs durch die Nova-Menüs. */
@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
	@ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
	private Screen nova$replaceTitleScreen(Screen screen) {
		// Nur die exakte Vanilla-Klasse ersetzen, damit andere Mods eigene Menüs behalten können
		if (screen != null && screen.getClass() == TitleScreen.class) {
			return new NovaTitleScreen();
		}
		// Ebenso das Mehrspielermenü (auch nach Verbindungsabbruch), immer mit dem Hauptmenü als Ziel von "Zurück"
		if (screen != null && screen.getClass() == MultiplayerScreen.class) {
			return new NovaMultiplayerScreen(new NovaTitleScreen());
		}
		return screen;
	}

	/** Ohne Welt macht {@code setScreen(null)} intern ein Vanilla-Hauptmenü – auch das ersetzen. */
	@ModifyVariable(method = "setScreen", at = @At("STORE"), argsOnly = true)
	private Screen nova$replaceDefaultTitleScreen(Screen screen) {
		return this.nova$replaceTitleScreen(screen);
	}
}
