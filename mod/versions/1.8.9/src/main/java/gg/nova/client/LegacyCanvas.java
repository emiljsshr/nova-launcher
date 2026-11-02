package gg.nova.client;

import com.mojang.blaze3d.platform.GlStateManager;
import gg.nova.core.NovaCanvas;
import gg.nova.core.NovaDraw;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.util.Identifier;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.Window;
import org.lwjgl.opengl.GL11;

/** {@link NovaCanvas} für Minecraft 1.8.9 (OpenGL-Sofortmodus über GlStateManager und Tessellator). */
public final class LegacyCanvas implements NovaCanvas {
	private final MinecraftClient client;
	private final TextRenderer textRenderer;
	private final Window window;

	public LegacyCanvas(MinecraftClient client) {
		this.client = client;
		this.textRenderer = client.textRenderer;
		this.window = new Window(client);
	}

	@Override
	public int width() {
		return this.window.getWidth();
	}

	@Override
	public int height() {
		return this.window.getHeight();
	}

	@Override
	public void fill(int x1, int y1, int x2, int y2, int color) {
		if ((color >>> 24) == 0) {
			return;
		}
		DrawableHelper.fill(x1, y1, x2, y2, color);
	}

	/** Wie {@code DrawableHelper#fillGradient} (dort nur geschützt verfügbar). */
	@Override
	public void gradient(int x1, int y1, int x2, int y2, int top, int bottom) {
		GlStateManager.disableTexture();
		GlStateManager.enableBlend();
		GlStateManager.disableAlphaTest();
		GlStateManager.blendFuncSeparate(770, 771, 1, 0);
		GlStateManager.shadeModel(7425);
		Tessellator tessellator = Tessellator.getInstance();
		BufferBuilder buffer = tessellator.getBuffer();
		buffer.begin(7, VertexFormats.POSITION_COLOR);
		vertex(buffer, x2, y1, top);
		vertex(buffer, x1, y1, top);
		vertex(buffer, x1, y2, bottom);
		vertex(buffer, x2, y2, bottom);
		tessellator.draw();
		GlStateManager.shadeModel(7424);
		GlStateManager.disableBlend();
		GlStateManager.enableAlphaTest();
		GlStateManager.enableTexture();
	}

	private static void vertex(BufferBuilder buffer, int x, int y, int color) {
		buffer.vertex(x, y, 0.0)
			.color((color >> 16 & 0xFF) / 255.0F, (color >> 8 & 0xFF) / 255.0F, (color & 0xFF) / 255.0F, (color >>> 24) / 255.0F)
			.next();
	}

	@Override
	public void logo(int x, int y, int width, int height, int color) {
		GlStateManager.enableBlend();
		GlStateManager.blendFuncSeparate(770, 771, 1, 0);
		GlStateManager.color(1.0F, 1.0F, 1.0F, (color >>> 24) / 255.0F);
		this.client.getTextureManager().bindTexture(NovaClient.LOGO);
		DrawableHelper.drawTexture(x, y, 0.0F, 0.0F, NovaDraw.LOGO_WIDTH, NovaDraw.LOGO_HEIGHT, width, height,
			NovaDraw.LOGO_WIDTH, NovaDraw.LOGO_HEIGHT);
		GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
	}

	/** Ganze Textur (z. B. ein Partner-Banner) ins Rechteck. */
	@Override
	public void texture(Object texture, int x, int y, int width, int height, int textureWidth, int textureHeight) {
		GlStateManager.enableBlend();
		GlStateManager.blendFuncSeparate(770, 771, 1, 0);
		GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
		this.client.getTextureManager().bindTexture((Identifier) texture);
		DrawableHelper.drawTexture(x, y, 0.0F, 0.0F, textureWidth, textureHeight, width, height, textureWidth, textureHeight);
	}

	@Override
	public void text(String text, int x, int y, int color, boolean shadow) {
		// 1.8.9 zeichnet Alpha 0 als deckend – fast unsichtbaren Text daher gar nicht zeichnen
		if ((color >>> 24) < 4 || this.textRenderer == null) {
			return;
		}
		GlStateManager.enableBlend();
		this.textRenderer.draw(text, (float) x, (float) y, color, shadow);
	}

	@Override
	public int textWidth(String text) {
		return this.textRenderer == null ? text.length() * 6 : this.textRenderer.getStringWidth(text);
	}

	@Override
	public int fontHeight() {
		return this.textRenderer == null ? 9 : this.textRenderer.fontHeight;
	}

	@Override
	public void push() {
		GlStateManager.pushMatrix();
	}

	@Override
	public void pop() {
		GlStateManager.popMatrix();
	}

	@Override
	public void translate(float x, float y) {
		GlStateManager.translate(x, y, 0.0F);
	}

	@Override
	public void scale(float scale) {
		GlStateManager.scale(scale, scale, 1.0F);
	}

	@Override
	public void rotate(float degrees) {
		GlStateManager.rotate(degrees, 0.0F, 0.0F, 1.0F);
	}

	@Override
	public void enableScissor(int x1, int y1, int x2, int y2) {
		int scale = this.window.getScaleFactor();
		GL11.glEnable(GL11.GL_SCISSOR_TEST);
		GL11.glScissor(x1 * scale, this.client.height - y2 * scale, (x2 - x1) * scale, (y2 - y1) * scale);
	}

	@Override
	public void disableScissor() {
		GL11.glDisable(GL11.GL_SCISSOR_TEST);
	}
}
