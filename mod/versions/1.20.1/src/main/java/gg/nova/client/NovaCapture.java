package gg.nova.client;

import gg.nova.core.NovaDebug;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.ScreenshotRecorder;

/** Screenshots für {@link NovaDebug} (nur mit -Dnova.capture=true aktiv). */
public final class NovaCapture {
	private static boolean loadingCaptured;

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
		if (!NovaDebug.captureEnabled() || shownMillis < NovaDebug.TITLE_CAPTURE_DELAY) {
			return;
		}
		save(context, "nova-title.png");
		MinecraftClient.getInstance().scheduleStop();
	}

	private static void save(DrawContext context, String name) {
		MinecraftClient client = MinecraftClient.getInstance();
		context.draw();
		ScreenshotRecorder.saveScreenshot(client.runDirectory, name, client.getFramebuffer(), text -> {
		});
	}
}
