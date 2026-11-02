package gg.nova.client;

import gg.nova.core.NovaDraw;
import gg.nova.core.menu.MenuButton;
import gg.nova.core.menu.NovaModMenu;
import gg.nova.core.menu.NovaMenu;
import gg.nova.core.menu.NovaServerList;
import java.io.File;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.User;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.PlainTextButton;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.screens.CreditsAndAttributionScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.SafetyScreen;
import net.minecraft.client.gui.screens.options.AccessibilityOptionsScreen;
import net.minecraft.client.gui.screens.options.LanguageSelectScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/** Nova-Hauptmenü (26.2): Layout und Zeichnen kommen aus {@link NovaMenu}, hier nur die Minecraft-Anbindung. */
public class NovaTitleScreen extends Screen {
	private static final Component COPYRIGHT = Component.translatable("title.credits");
	private static final long FADE_DURATION = 900L;

	/** Nur beim allerersten Öffnen nach dem Spielstart einblenden. */
	private static boolean shownBefore;

	private final PartnerPings pings = new PartnerPings();
	private final boolean fadeIn;
	private long openedAt;
	private NovaMenu menu;
	private NovaMenu.State state;

	public NovaTitleScreen() {
		super(Component.translatable("nova.menu.title"));
		this.fadeIn = !shownBefore;
		shownBefore = true;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	protected void init() {
		if (this.openedAt == 0L) {
			this.openedAt = Util.getMillis();
		}

		this.menu = NovaMenu.layout(this.width, this.height, key -> I18n.get(key), new GuiCanvas(null, this.font));
		boolean multiplayerEnabled = this.minecraft.allowsMultiplayer();
		for (MenuButton spec : this.menu.buttons()) {
			NovaButtonWidget widget = this.addRenderableWidget(new NovaButtonWidget(spec, () -> this.perform(spec),
				() -> spec.action() == MenuButton.Action.NOVA_SERVERS && this.state != null ? NovaMenu.serverBadge(this.state) : null));
			widget.active = !spec.action().comingSoon() && (multiplayerEnabled || !spec.action().needsMultiplayer());
		}

		int copyrightWidth = this.font.width(COPYRIGHT);
		this.addRenderableWidget(new PlainTextButton(
			this.width - copyrightWidth - this.menu.margin(), this.height - 10, copyrightWidth, 10, COPYRIGHT,
			button -> this.minecraft.gui.setScreen(new CreditsAndAttributionScreen(this)), this.font
		));

		this.pings.start();
	}

	private void perform(MenuButton spec) {
		switch (spec.action()) {
			case SINGLEPLAYER -> this.minecraft.gui.setScreen(new SelectWorldScreen(this));
			case MULTIPLAYER -> this.minecraft.gui.setScreen(this.minecraft.options.skipMultiplayerWarning
				? new JoinMultiplayerScreen(this)
				: new SafetyScreen(this));
			case NOVA_SERVERS -> this.minecraft.gui.setScreen(new NovaServerScreen(this));
			case COSMETICS -> this.minecraft.gui.setScreen(new NovaMenuScreen(this, NovaModMenu.Tab.COSMETICS));
			case MODS_FOLDER -> openFolder(FabricLoader.getInstance().getGameDir().resolve("mods"));
			case SCREENSHOTS_FOLDER -> openFolder(new File(this.minecraft.gameDirectory, "screenshots").toPath());
			case OPTIONS -> this.minecraft.gui.setScreen(new OptionsScreen(this, this.minecraft.options, false));
			case LANGUAGE -> this.minecraft.gui.setScreen(new LanguageSelectScreen(this, this.minecraft.options, this.minecraft.getLanguageManager()));
			case ACCESSIBILITY -> this.minecraft.gui.setScreen(new AccessibilityOptionsScreen(this, this.minecraft.options));
			case QUIT -> this.minecraft.stop();
			default -> {
			}
		}
	}

	static void openFolder(Path folder) {
		folder.toFile().mkdirs();
		Util.getPlatform().openPath(folder);
	}

	@Override
	public void tick() {
		this.pings.tick();
	}

	@Override
	public void removed() {
		this.pings.cancel();
	}

	private float contentAlpha() {
		if (!this.fadeIn) {
			return 1.0F;
		}
		return Math.min(1.0F, (Util.getMillis() - this.openedAt) / (float) FADE_DURATION);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		float alpha = this.contentAlpha();
		User user = this.minecraft.getUser();
		this.state = new NovaMenu.State(
			alpha,
			user.getName(),
			// Seit 1.21.9 kennt die Session keinen Kontotyp mehr – Microsoft-Konten haben eine Xbox-ID
			user.getXuid().isPresent(),
			NovaServerList.totalPlayers(this.pings.statuses()),
			SharedConstants.getCurrentVersion().name(),
			NovaClient.version(),
			this.minecraft.options.hideSplashTexts().get(),
			this.font.width(COPYRIGHT),
			key -> I18n.get(key)
		);

		for (var child : this.children()) {
			if (child instanceof AbstractWidget widget) {
				widget.setAlpha(alpha);
			}
		}

		super.extractRenderState(graphics, mouseX, mouseY, delta);

		if (this.menu.showPlayerCard() && alpha > 0.02F) {
			PlayerFaceExtractor.extractRenderState(graphics,
				this.minecraft.getSkinManager().createLookup(this.minecraft.getGameProfile(), false).get(),
				this.menu.headX(), this.menu.headY(), NovaMenu.HEAD_SIZE, NovaDraw.white(alpha));
		}
		this.menu.renderOverlay(new GuiCanvas(graphics, this.font), this.state);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		// Vanilla-Panorama ohne Weichzeichner, darüber eine leichte Abdunklung
		this.extractPanorama(graphics, delta);
		this.menu.renderBackground(new GuiCanvas(graphics, this.font), this.contentAlpha());
	}
}
