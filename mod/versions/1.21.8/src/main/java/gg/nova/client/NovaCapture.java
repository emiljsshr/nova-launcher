package gg.nova.client;

import gg.nova.core.NovaDebug;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.ScreenshotRecorder;

/**
 * Screenshots für {@link NovaDebug} (nur mit -Dnova.capture=true aktiv).
 * Ab 1.21.6 wird die GUI erst am Frame-Ende gezeichnet: gespeichert wird daher in
 * {@link #flush()}, das {@code MinecraftClientMixin} direkt vor dem Bildtausch aufruft.
 */
public final class NovaCapture {
	private static boolean loadingCaptured;
	private static String pending;
	private static boolean stopAfter;

	private NovaCapture() {
	}

	public static void loading(DrawContext context, float progress) {
		if (!NovaDebug.captureEnabled() || loadingCaptured || progress < NovaDebug.LOADING_CAPTURE_PROGRESS) {
			return;
		}
		loadingCaptured = true;
		pending = "nova-loading.png";
	}

	public static void title(DrawContext context, long shownMillis) {
		if (!NovaDebug.captureEnabled() || stopAfter || shownMillis < NovaDebug.TITLE_CAPTURE_DELAY) {
			return;
		}
		pending = "nova-title.png";
		stopAfter = true;
	}

	public static void flush() {
		if (pending == null) {
			return;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		ScreenshotRecorder.saveScreenshot(client.runDirectory, pending, client.getFramebuffer(), 1, text -> {
		});
		pending = null;
		if (stopAfter) {
			client.scheduleStop();
		}
	}
}
