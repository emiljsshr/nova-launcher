package gg.nova.client;

import gg.nova.core.mods.NovaMods;
import gg.nova.core.social.NovaSocial;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NovaClient implements ClientModInitializer {
	public static final String MOD_ID = "nova";
	public static final Logger LOGGER = LoggerFactory.getLogger("Nova");
	public static final Identifier LOGO = Identifier.of(MOD_ID, "textures/gui/logo.png");

	/** Textur der Mod (assets/nova/<path>), z. B. ein Partner-Banner. */
	public static Identifier texture(String path) {
		return Identifier.of(MOD_ID, path);
	}

	@Override
	public void onInitializeClient() {
		LOGGER.info("Nova Client {} geladen", version());
		NovaMods.init(new NovaGameAdapter());
		NovaSocial.init(text -> MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(Text.literal(text)));
	}

	private static boolean menuKeyDown;

	/** Einmal pro Spieltakt (ClientTickMixin): Freunde und Chat, eigener Status, Rechts-Shift öffnet das Nova-Menü. */
	public static void tick(MinecraftClient client) {
		ServerInfo server = client.getCurrentServerEntry();
		String state = client.world == null ? "menu" : client.isInSingleplayer() ? "singleplayer" : "multiplayer";
		NovaMods.tick();
		NovaSocial.tick(state, "multiplayer".equals(state) && server != null ? server.address : null);
		boolean down = InputUtil.isKeyPressed(client.getWindow(), GLFW.GLFW_KEY_RIGHT_SHIFT);
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
			if (mod.getContainingMod().isEmpty() && !"builtin".equals(type) && !"fabricloader".equals(id)) {
				count++;
			}
		}
		return count;
	}
}
