package gg.nova.client;

import com.mojang.blaze3d.systems.RenderSystem;
import gg.nova.core.menu.MenuButton;
import gg.nova.core.menu.NovaModMenu;
import gg.nova.core.menu.NovaMenu;
import gg.nova.core.menu.NovaServerList;
import java.io.File;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerWarningScreen;
import net.minecraft.client.gui.screen.option.AccessibilityOptionsScreen;
import net.minecraft.client.gui.screen.option.CreditsAndAttributionScreen;
import net.minecraft.client.gui.screen.option.LanguageOptionsScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.PressableTextWidget;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.session.Session;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

/** Nova-Hauptmenü (1.20.5 – 1.20.6): Layout und Zeichnen kommen aus {@link NovaMenu}, hier nur die Minecraft-Anbindung. */
public class NovaTitleScreen extends Screen {
	private static final Text COPYRIGHT = Text.translatable("title.credits");
	private static final long FADE_DURATION = 900L;

	/** Nur beim allerersten Öffnen nach dem Spielstart einblenden. */
	private static boolean shownBefore;

	private final PartnerPings pings = new PartnerPings();
	private final boolean fadeIn;
	private long openedAt;
	private NovaMenu menu;
	private NovaMenu.State state;

	public NovaTitleScreen() {
		super(Text.translatable("nova.menu.title"));
		this.fadeIn = !shownBefore;
		shownBefore = true;
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	protected void init() {
		if (this.openedAt == 0L) {
			this.openedAt = Util.getMeasuringTimeMs();
		}

		this.menu = NovaMenu.layout(this.width, this.height, I18n::translate, new DrawContextCanvas(null, this.textRenderer));
		boolean multiplayerEnabled = this.client.isMultiplayerEnabled();
		for (MenuButton spec : this.menu.buttons()) {
			NovaButtonWidget widget = this.addDrawableChild(new NovaButtonWidget(spec, () -> this.perform(spec),
				() -> spec.action() == MenuButton.Action.NOVA_SERVERS && this.state != null ? NovaMenu.serverBadge(this.state) : null));
			widget.active = !spec.action().comingSoon() && (multiplayerEnabled || !spec.action().needsMultiplayer());
		}

		int copyrightWidth = this.textRenderer.getWidth(COPYRIGHT);
		this.addDrawableChild(new PressableTextWidget(
			this.width - copyrightWidth - this.menu.margin(), this.height - 10, copyrightWidth, 10, COPYRIGHT,
			button -> this.client.setScreen(new CreditsAndAttributionScreen(this)), this.textRenderer
		));

		this.pings.start();
	}

	private void perform(MenuButton spec) {
		switch (spec.action()) {
			case SINGLEPLAYER -> this.client.setScreen(new SelectWorldScreen(this));
			case MULTIPLAYER -> this.client.setScreen(this.client.options.skipMultiplayerWarning
				? new MultiplayerScreen(this)
				: new MultiplayerWarningScreen(this));
			case NOVA_SERVERS -> this.client.setScreen(new NovaServerScreen(this));
			case COSMETICS -> this.client.setScreen(new NovaMenuScreen(this, NovaModMenu.Tab.COSMETICS));
			case MODS_FOLDER -> openFolder(FabricLoader.getInstance().getGameDir().resolve("mods"));
			case SCREENSHOTS_FOLDER -> openFolder(new File(this.client.runDirectory, "screenshots").toPath());
			case OPTIONS -> this.client.setScreen(new OptionsScreen(this, this.client.options));
			case LANGUAGE -> this.client.setScreen(new LanguageOptionsScreen(this, this.client.options, this.client.getLanguageManager()));
			case ACCESSIBILITY -> this.client.setScreen(new AccessibilityOptionsScreen(this, this.client.options));
			case QUIT -> this.client.scheduleStop();
			default -> {
			}
		}
	}

	static void openFolder(Path folder) {
		folder.toFile().mkdirs();
		Util.getOperatingSystem().open(folder.toFile());
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
		return Math.min(1.0F, (Util.getMeasuringTimeMs() - this.openedAt) / (float) FADE_DURATION);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		float alpha = this.contentAlpha();
		Session session = this.client.getSession();
		this.state = new NovaMenu.State(
			alpha,
			session.getUsername(),
			session.getAccountType() == Session.AccountType.MSA,
			NovaServerList.totalPlayers(this.pings.statuses()),
			SharedConstants.getGameVersion().getName(),
			NovaClient.version(),
			this.client.options.getHideSplashTexts().getValue(),
			this.textRenderer.getWidth(COPYRIGHT),
			I18n::translate
		);

		for (var child : this.children()) {
			if (child instanceof ClickableWidget widget) {
				widget.setAlpha(alpha);
			}
		}

		super.render(context, mouseX, mouseY, delta);

		if (this.menu.showPlayerCard() && alpha > 0.02F) {
			RenderSystem.enableBlend();
			RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
			PlayerSkinDrawer.draw(context, this.client.getSkinProvider().getSkinTextures(this.client.getGameProfile()),
				this.menu.headX(), this.menu.headY(), NovaMenu.HEAD_SIZE);
			RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
		}
		this.menu.renderOverlay(new DrawContextCanvas(context, this.textRenderer), this.state);

		NovaCapture.title(context, Util.getMeasuringTimeMs() - this.openedAt);
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
		// Vanilla-Panorama ohne Weichzeichner, darüber eine leichte Abdunklung
		this.renderPanoramaBackground(context, delta);
		this.menu.renderBackground(new DrawContextCanvas(context, this.textRenderer), this.contentAlpha());
	}
}
