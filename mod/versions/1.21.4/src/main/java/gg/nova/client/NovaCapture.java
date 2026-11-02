package gg.nova.client;

import gg.nova.core.NovaDebug;
import gg.nova.core.menu.NovaModMenu;
import gg.nova.core.social.NovaSocial;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.option.Perspective;
import net.minecraft.block.Blocks;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;

/** Screenshots für {@link NovaDebug} (nur mit -Dnova.capture=true oder =world aktiv). */
public final class NovaCapture {
	private static boolean loadingCaptured;
	private static boolean worldStarted;

	private NovaCapture() {
	}

	public static void loading(DrawContext context, float progress) {
		if (!NovaDebug.captureEnabled() || loadingCaptured || progress < NovaDebug.LOADING_CAPTURE_PROGRESS) {
			return;
		}
		loadingCaptured = true;
		save(context, "nova-loading.png");
	}

	public static void title(DrawContext context, long shownMillis) {
		if (!NovaDebug.captureEnabled() || worldStarted || shownMillis < NovaDebug.TITLE_CAPTURE_DELAY) {
			return;
		}
		save(context, "nova-title.png");
		if (NovaDebug.captureWorld()) {
			worldStarted = true;
			startWorldCapture();
			return;
		}
		MinecraftClient.getInstance().scheduleStop();
	}

	// Testwelt anlegen, in die Rückansicht wechseln, fotografieren, beenden – Schritte laufen im Render-Thread
	private static void startWorldCapture() {
		MinecraftClient client = MinecraftClient.getInstance();
		// Ohne Fokus nicht pausieren – das Testfenster liegt oft im Hintergrund
		client.options.pauseOnLostFocus = false;
		CreateWorldScreen.show(client, client.currentScreen);
		Thread thread = new Thread(() -> {
			String create = Text.translatable("selectWorld.create").getString();
			long joinedAt = 0;
			boolean pressed = false;
			boolean firstTaken = false;
			// -Dnova.capture.walk=true: auf einem Steinweg geradeaus laufen, dann ducken (Spuren, Aura, Emotes)
			boolean walk = Boolean.getBoolean("nova.capture.walk");
			boolean sneaked = false;
			// -Dnova.capture.glide=true: mit Elytra 60 Blöcke über dem Boden starten und nach kurzem Fall gleiten
			boolean glide = Boolean.getBoolean("nova.capture.glide");
			boolean gliding = false;
			// -Dnova.capture.menu=cosmetics|friends: Nova-Menü öffnen (bei friends mit dem ersten Freund im Chat)
			String menu = System.getProperty("nova.capture.menu");
			boolean menuOpened = false;
			while (true) {
				try {
					Thread.sleep(500L);
				} catch (InterruptedException error) {
					return;
				}
				if (!pressed && client.currentScreen instanceof CreateWorldScreen screen) {
					for (Element element : screen.children()) {
						if (element instanceof ButtonWidget button && button.getMessage().getString().equals(create)) {
							pressed = true;
							client.execute(button::onPress);
						}
					}
				}
				if (client.player != null && client.world != null && (client.currentScreen == null || client.currentScreen instanceof NovaMenuScreen)) {
					// Rückansicht immer wieder setzen – Tastendrücke im Testfenster sollen das Bild nicht verderben
					client.execute(() -> client.options.setPerspective(Perspective.THIRD_PERSON_BACK));
					if (joinedAt == 0) {
						joinedAt = System.currentTimeMillis();
						client.execute(() -> client.options.setPerspective(Perspective.THIRD_PERSON_BACK));
						// -Dnova.capture.elytra=true: Testfigur trägt eine Elytra (zum Prüfen von Elytra-Texturen),
						// mit -Dnova.capture.helmet=true zusätzlich einen Eisenhelm (Kopf-Cosmetics über Helmen)
						IntegratedServer server = client.getServer();
						if (walk && server != null) {
							java.util.UUID id = client.player.getUuid();
							server.execute(() -> {
								var player = server.getPlayerManager().getPlayer(id);
								if (player != null) {
									// Schwebender Steinweg 3 breit, 14 lang in Richtung +Z, 8 Blöcke über dem Boden
									var world = player.getServerWorld();
									BlockPos start = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING, player.getBlockPos()).up(8);
									for (int dz = -1; dz < 14; dz++) {
										for (int dx = -1; dx <= 1; dx++) {
											world.setBlockState(start.add(dx, -1, dz), Blocks.STONE.getDefaultState());
										}
									}
									player.requestTeleport(start.getX() + 0.5, start.getY(), start.getZ() + 0.5);
								}
							});
						}
						if (Boolean.getBoolean("nova.capture.elytra") && server != null) {
							java.util.UUID id = client.player.getUuid();
							server.execute(() -> {
								var player = server.getPlayerManager().getPlayer(id);
								if (player != null) {
									// Auf einen Steinblock 8 Blöcke über dem Boden stellen: nicht im Wasser, und die Kamera hat freie Sicht
									var world = player.getServerWorld();
									BlockPos top = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING, player.getBlockPos()).up(glide ? 60 : 8);
									if (!glide) {
										world.setBlockState(top.down(), Blocks.STONE.getDefaultState());
									}
									player.requestTeleport(top.getX() + 0.5, top.getY(), top.getZ() + 0.5);
									player.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.ELYTRA));
									if (Boolean.getBoolean("nova.capture.helmet")) {
										player.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
									}
								}
							});
						}
					} else if (walk && !firstTaken) {
						// 2,5–5,5 s laufen, dann ducken und kurz danach fotografieren
						long time = System.currentTimeMillis() - joinedAt;
						if (time > 2500 && time < 5500) {
							client.execute(() -> {
								client.player.setYaw(0);
								client.player.setPitch(15);
								client.options.forwardKey.setPressed(true);
							});
						} else if (time >= 5500 && !sneaked) {
							sneaked = true;
							client.execute(() -> {
								client.options.forwardKey.setPressed(false);
								client.options.sneakKey.setPressed(true);
							});
						} else if (sneaked) {
							firstTaken = true;
							client.execute(() -> {
								ScreenshotRecorder.saveScreenshot(client.runDirectory, "nova-world.png", client.getFramebuffer(), text -> {
								});
								client.options.sneakKey.setPressed(false);
							});
						}
					} else if (glide && !gliding && System.currentTimeMillis() - joinedAt > 1500) {
						gliding = true;
						IntegratedServer server = client.getServer();
						java.util.UUID id = client.player.getUuid();
						client.execute(() -> client.player.setPitch(10));
						if (server != null) {
							server.execute(() -> {
								var player = server.getPlayerManager().getPlayer(id);
								if (player != null) {
									player.startGliding();
								}
							});
						}
					} else if (menu != null && !menuOpened && System.currentTimeMillis() - joinedAt > 3000) {
						menuOpened = true;
						client.execute(() -> {
							boolean friends = "friends".equals(menu);
							client.setScreen(new NovaMenuScreen(null, friends ? NovaModMenu.Tab.FRIENDS : NovaModMenu.Tab.COSMETICS));
							if (friends && !NovaSocial.friends().isEmpty()) {
								NovaModMenu.get().openChatWith(NovaSocial.friends().get(0).id);
							}
						});
					} else if (!firstTaken && System.currentTimeMillis() - joinedAt > NovaDebug.WORLD_CAPTURE_DELAY) {
						firstTaken = true;
						client.execute(() -> ScreenshotRecorder.saveScreenshot(
							client.runDirectory, "nova-world.png", client.getFramebuffer(), text -> {
							}));
					} else if (System.currentTimeMillis() - joinedAt > NovaDebug.WORLD_CAPTURE_STILL) {
						client.execute(() -> {
							ScreenshotRecorder.saveScreenshot(client.runDirectory, "nova-world-still.png", client.getFramebuffer(), text -> {
							});
							client.scheduleStop();
						});
						return;
					}
				}
			}
		}, "Nova world capture");
		thread.setDaemon(true);
		thread.start();
	}

	private static void save(DrawContext context, String name) {
		MinecraftClient client = MinecraftClient.getInstance();
		context.draw();
		ScreenshotRecorder.saveScreenshot(client.runDirectory, name, client.getFramebuffer(), text -> {
		});
	}
}
