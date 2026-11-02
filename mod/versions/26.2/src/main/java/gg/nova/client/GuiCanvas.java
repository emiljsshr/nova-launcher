package gg.nova.client;

import gg.nova.core.NovaCanvas;
import gg.nova.core.NovaDraw;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.RenderPipelines;

/**
 * {@link NovaCanvas} für Minecraft 26.x ({@link GuiGraphicsExtractor}, Mojang-Namen).
 * Ohne Extractor (null) nur zum Messen von Text.
 */
public final class GuiCanvas implements NovaCanvas {
	private final GuiGraphicsExtractor graphics;
	private final Font font;

	public GuiCanvas(GuiGraphicsExtractor graphics, Font font) {
		this.graphics = graphics;
		this.font = font;
	}

	@Override
	public int width() {
		return this.graphics.guiWidth();
	}

	@Override
	public int height() {
		return this.graphics.guiHeight();
	}

	@Override
	public void fill(int x1, int y1, int x2, int y2, int color) {
		this.graphics.fill(x1, y1, x2, y2, color);
	}

	@Override
	public void gradient(int x1, int y1, int x2, int y2, int top, int bottom) {
		this.graphics.fillGradient(x1, y1, x2, y2, top, bottom);
	}

	@Override
	public void logo(int x, int y, int width, int height, int color) {
		this.graphics.blit(
			RenderPipelines.GUI_TEXTURED, NovaClient.LOGO, x, y, 0.0F, 0.0F, width, height,
			NovaDraw.LOGO_WIDTH, NovaDraw.LOGO_HEIGHT, NovaDraw.LOGO_WIDTH, NovaDraw.LOGO_HEIGHT, color
		);
	}

	/** Ganze Textur (Server-Icon, Partner-Banner) ins Rechteck. */
	@Override
	public void texture(Object texture, int x, int y, int width, int height, int textureWidth, int textureHeight) {
		this.graphics.blit(
			RenderPipelines.GUI_TEXTURED, (Identifier) texture, x, y, 0.0F, 0.0F, width, height,
			textureWidth, textureHeight, textureWidth, textureHeight, -1
		);
	}

	@Override
	public void text(String text, int x, int y, int color, boolean shadow) {
		this.graphics.text(this.font, text, x, y, color, shadow);
	}

	@Override
	public int textWidth(String text) {
		return this.font.width(text);
	}

	@Override
	public int fontHeight() {
		return this.font.lineHeight;
	}

	@Override
	public void push() {
		this.graphics.pose().pushMatrix();
	}

	@Override
	public void pop() {
		this.graphics.pose().popMatrix();
	}

	@Override
	public void translate(float x, float y) {
		this.graphics.pose().translate(x, y);
	}

	@Override
	public void scale(float scale) {
		this.graphics.pose().scale(scale, scale);
	}

	@Override
	public void rotate(float degrees) {
		this.graphics.pose().rotate((float) Math.toRadians(degrees));
	}

	@Override
	public void enableScissor(int x1, int y1, int x2, int y2) {
		this.graphics.enableScissor(x1, y1, x2, y2);
	}

	@Override
	public void disableScissor() {
		this.graphics.disableScissor();
	}
}
