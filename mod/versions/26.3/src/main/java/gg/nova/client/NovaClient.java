package gg.nova.client;

import com.mojang.blaze3d.platform.InputConstants;
import gg.nova.core.social.NovaSocial;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NovaClient implements ClientModInitializer {
	public static final String MOD_ID = "nova";
	public static final Logger LOGGER = LoggerFactory.getLogger("Nova");
	public static final Identifier LOGO = Identifier.fromNamespaceAndPath(MOD_ID, "textures/gui/logo.png");

	@Override
	public void onInitializeClient() {
		LOGGER.info("Nova Client {} geladen", version());
		NovaSocial.init(text -> Minecraft.getInstance().gui.hud.getChat().addClientSystemMessage(Component.literal(text)));
	}

	private static boolean menuKeyDown;

	/** Einmal pro Spieltakt (ClientTickMixin): Freunde und Chat, eigener Status, Rechts-Shift öffnet das Nova-Menü. */
	public static void tick(Minecraft minecraft) {
		ServerData server = minecraft.getCurrentServer();
		String state = minecraft.level == null ? "menu" : minecraft.hasSingleplayerServer() ? "singleplayer" : "multiplayer";
		NovaSocial.tick(state, "multiplayer".equals(state) && server != null ? server.ip : null);
		boolean down = InputConstants.isKeyDown(InputConstants.KEY_RSHIFT);
		if (down && !menuKeyDown && minecraft.gui.screen() == null) {
			minecraft.gui.setScreen(new NovaMenuScreen(null, null));
		}
		menuKeyDown = down;
		debugOpen(minecraft);
	}

	private static int debugTicks = -1;

	/** -Dnova.open=multiplayer: Mehrspielermenü öffnen, nach 6 Sekunden Screenshot und beenden (Test ohne Maus). */
	private static void debugOpen(Minecraft minecraft) {
		if (!"multiplayer".equals(System.getProperty("nova.open"))) {
			return;
		}
		if (debugTicks < 0 && minecraft.gui.screen() instanceof NovaTitleScreen) {
			debugTicks = 0;
			minecraft.gui.setScreen(new JoinMultiplayerScreen(minecraft.gui.screen()));
		} else if (debugTicks >= 0 && ++debugTicks == 120) {
			Screenshot.grab(minecraft, false);
		} else if (debugTicks == 160) {
			minecraft.stop();
		}
	}

	public static String version() {
		return modVersion(MOD_ID).replaceAll("\\+.*", "");
	}

	public static String loaderVersion() {
		return modVersion("fabricloader");
	}

	private static String modVersion(String id) {
		return FabricLoader.getInstance()
			.getModContainer(id)
			.map(container -> container.getMetadata().getVersion().getFriendlyString())
			.orElse("dev");
	}

	/** Anzahl installierter Mods ohne Minecraft, Java, Loader und eingebettete Bibliotheken. */
	public static int modCount() {
		int count = 0;
		for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
			String type = mod.getMetadata().getType();
			String id = mod.getMetadata().getId();
			if (mod.getContainingMod().isEmpty() && !"builtin".equals(type) && !"fabricloader".equals(id)) {
				count++;
			}
		}
		return count;
	}
}
