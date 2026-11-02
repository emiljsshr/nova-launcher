package gg.nova.client;

import gg.nova.core.mods.NovaMods;
import gg.nova.core.social.NovaSocial;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.LiteralText;
import org.lwjgl.input.Keyboard;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.util.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class NovaClient implements ClientModInitializer {
	public static final String MOD_ID = "nova";
	public static final Logger LOGGER = LogManager.getLogger("Nova");
	public static final Identifier LOGO = new Identifier(MOD_ID, "textures/gui/logo.png");

	/** Textur der Mod (assets/nova/<path>), z. B. ein Partner-Banner. */
	public static Identifier texture(String path) {
		return new Identifier(MOD_ID, path);
	}

	@Override
	public void onInitializeClient() {
		LOGGER.info("Nova Client {} geladen", version());
		NovaMods.init(new NovaGameAdapter());
		NovaSocial.init(text -> MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(new LiteralText(text)));
	}

	private static boolean menuKeyDown;

	/** Einmal pro Spieltakt (ClientTickMixin): Freunde und Chat, eigener Status, Rechts-Shift öffnet das Nova-Menü. */
	public static void tick(MinecraftClient client) {
		ServerInfo server = client.getCurrentServerEntry();
		String state = client.world == null ? "menu" : client.isInSingleplayer() ? "singleplayer" : "multiplayer";
		NovaMods.tick();
		NovaSocial.tick(state, "multiplayer".equals(state) && server != null ? server.address : null);
		boolean down = Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
		if (down && !menuKeyDown && client.currentScreen == null) {
			client.setScreen(new NovaMenuScreen(null, null));
		}
		menuKeyDown = down;
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
			if (!mod.getContainingMod().isPresent() && !"builtin".equals(type) && !"fabricloader".equals(id)) {
				count++;
			}
		}
		return count;
	}
}
