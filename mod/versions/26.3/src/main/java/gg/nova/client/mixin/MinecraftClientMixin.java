package gg.nova.client.mixin;

import gg.nova.client.NovaMultiplayerScreen;
import gg.nova.client.NovaTitleScreen;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Ersetzt jeden Aufruf des Vanilla-Hauptmenüs durch das Nova-Hauptmenü.
 * Ab 26.x wechseln Screens über {@link Gui#setScreen}; der Klassenname bleibt für die gemeinsame Mixin-Liste gleich.
 */
@Mixin(Gui.class)
public abstract class MinecraftClientMixin {
	@ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
	private Screen nova$replaceTitleScreen(Screen screen) {
		// Nur die exakte Vanilla-Klasse ersetzen, damit andere Mods eigene Menüs behalten können
		if (screen != null && screen.getClass() == TitleScreen.class) {
			return new NovaTitleScreen();
		}
		// Ebenso das Mehrspielermenü (auch nach Verbindungsabbruch), immer mit dem Hauptmenü als Ziel von "Zurück"
		if (screen != null && screen.getClass() == JoinMultiplayerScreen.class) {
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
