package gg.nova.client;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.blaze3d.platform.GlStateManager;
import gg.nova.core.NovaScenery;
import gg.nova.core.menu.MenuButton;
import gg.nova.core.menu.NovaModMenu;
import gg.nova.core.menu.NovaMenu;
import gg.nova.core.menu.NovaServerList;
import java.io.File;
import java.util.List;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.SettingsScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.options.LanguageOptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.util.Identifier;
import org.lwjgl.Sys;

/**
 * Nova-Hauptmenü (1.8.9): Layout und Zeichnen kommen aus {@link NovaMenu}, hier nur die Minecraft-Anbindung.
 * Hintergrund ist die Nova-Winterlandschaft – das 1.8.9-Panorama gehört fest zum Vanilla-Menü.
 */
public class NovaTitleScreen extends Screen {
	private static final String COPYRIGHT = "Copyright Mojang AB. Do not distribute!";

	/** Skin des Spielers – wird einmalig im Hintergrund aufgelöst, weil 1.8.9 sonst den Render-Thread blockiert. */
	private static volatile Identifier skin;
	private static boolean skinRequested;

	private final PartnerPings pings = new PartnerPings();
	private NovaMenu menu;
	private NovaMenu.State state;

	@Override
	public void init() {
		this.buttons.clear();
		this.menu = NovaMenu.layout(this.width, this.height, key -> I18n.translate(key), new LegacyCanvas(this.client));
		List<MenuButton> specs = this.menu.buttons();
		for (int index = 0; index < specs.size(); index++) {
			final MenuButton spec = specs.get(index);
			NovaButtonWidget widget = new NovaButtonWidget(index, spec,
				() -> spec.action() == MenuButton.Action.NOVA_SERVERS && this.state != null ? NovaMenu.serverBadge(this.state) : null);
			// 1.8.9 hat keine Barrierefreiheits-Einstellungen
			widget.active = !spec.action().comingSoon() && spec.action() != MenuButton.Action.ACCESSIBILITY;
			this.buttons.add(widget);
		}
		this.pings.start();
		this.requestSkin();
	}

	@Override
	protected void buttonClicked(ButtonWidget button) {
		switch (((NovaButtonWidget) button).spec().action()) {
			case SINGLEPLAYER:
				this.client.setScreen(new SelectWorldScreen(this));
				break;
			case MULTIPLAYER:
				this.client.setScreen(new MultiplayerScreen(this));
				break;
			case NOVA_SERVERS:
				this.client.setScreen(new NovaServerScreen(this));
				break;
			case COSMETICS:
				this.client.setScreen(new NovaMenuScreen(this, NovaModMenu.Tab.COSMETICS));
				break;
			case MODS_FOLDER:
				openFolder(FabricLoader.getInstance().getGameDir().resolve("mods").toFile());
				break;
			case SCREENSHOTS_FOLDER:
				openFolder(new File(this.client.runDirectory, "screenshots"));
				break;
			case OPTIONS:
				this.client.setScreen(new SettingsScreen(this, this.client.options));
				break;
			case LANGUAGE:
				this.client.setScreen(new LanguageOptionsScreen(this, this.client.options, this.client.getLanguageManager()));
				break;
			case QUIT:
				this.client.scheduleStop();
				break;
			default:
				break;
		}
	}

	private static void openFolder(File folder) {
		folder.mkdirs();
		Sys.openURL("file://" + folder.getAbsolutePath());
	}

	/** Das Hauptmenü lässt sich nicht mit ESC schließen. */
	@Override
	protected void keyPressed(char character, int code) {
	}

	@Override
	public void tick() {
		this.pings.tick();
	}

	@Override
	public void removed() {
		this.pings.cancel();
	}

	private void requestSkin() {
		if (skinRequested) {
			return;
		}
		skinRequested = true;
		final GameProfile profile = this.client.getSession().getProfile();
		Thread thread = new Thread(() -> {
			try {
				final Map<MinecraftProfileTexture.Type, MinecraftProfileTexture> textures = this.client.getSkinProvider().getTextures(profile);
				if (textures.containsKey(MinecraftProfileTexture.Type.SKIN)) {
					this.client.submit(() -> skin = this.client.getSkinProvider()
						.loadSkin(textures.get(MinecraftProfileTexture.Type.SKIN), MinecraftProfileTexture.Type.SKIN));
				}
			} catch (Exception exception) {
				NovaClient.LOGGER.debug("Skin konnte nicht geladen werden", exception);
			}
		}, "Nova Skin");
		thread.setDaemon(true);
		thread.start();
	}

	private Identifier currentSkin() {
		if (skin != null) {
			return skin;
		}
		GameProfile profile = this.client.getSession().getProfile();
		return profile.getId() != null ? DefaultSkinHelper.getTexture(profile.getId()) : DefaultSkinHelper.getTexture();
	}

	/** Microsoft-Konten haben ein echtes Zugriffstoken; Offline-Konten "0" oder einen Platzhalter. */
	private boolean microsoftAccount() {
		String token = this.client.getSession().getAccessToken();
		return token != null && token.length() > 32;
	}

	@Override
	public void render(int mouseX, int mouseY, float delta) {
		LegacyCanvas canvas = new LegacyCanvas(this.client);
		this.state = new NovaMenu.State(
			1.0F,
			this.client.getSession().getUsername(),
			this.microsoftAccount(),
			NovaServerList.totalPlayers(this.pings.statuses()),
			"1.8.9",
			NovaClient.version(),
			false,
			this.textRenderer.getStringWidth(COPYRIGHT),
			key -> I18n.translate(key)
		);

		// Hintergrund: Nova-Winterlandschaft statt Panorama
		NovaScenery.sky(canvas, 0, 0, this.width, this.height, 1.0F);
		NovaScenery.stars(canvas, 0, 0, this.width, (int) (this.height * 0.75F), 1312, 0.9F, 1.0F);
		int landHeight = (int) (this.height * 0.3F);
		NovaScenery.landscape(canvas, 0, this.height - landHeight, this.width, landHeight, 1.0F);
		this.menu.renderBackground(canvas, 1.0F);

		super.render(mouseX, mouseY, delta);

		if (this.menu.showPlayerCard()) {
			int size = NovaMenu.HEAD_SIZE;
			GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
			this.client.getTextureManager().bindTexture(this.currentSkin());
			DrawableHelper.drawTexture(this.menu.headX(), this.menu.headY(), 8.0F, 8.0F, 8, 8, size, size, 64.0F, 64.0F);
			GlStateManager.enableBlend();
			DrawableHelper.drawTexture(this.menu.headX(), this.menu.headY(), 40.0F, 8.0F, 8, 8, size, size, 64.0F, 64.0F);
		}
		this.menu.renderOverlay(canvas, this.state);
		canvas.text(COPYRIGHT, this.width - this.textRenderer.getStringWidth(COPYRIGHT) - this.menu.margin(), this.height - 10, 0xFFFFFFFF, true);

		for (ButtonWidget button : this.buttons) {
			MenuButton spec = ((NovaButtonWidget) button).spec();
			if (button.isHovered() && button.active && spec.tooltipKey() != null) {
				this.renderTooltip(I18n.translate(spec.tooltipKey()), mouseX, mouseY);
			}
		}
	}
}
