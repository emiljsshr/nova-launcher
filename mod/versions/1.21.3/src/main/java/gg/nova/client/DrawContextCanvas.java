package gg.nova.client;

import gg.nova.core.NovaCanvas;
import gg.nova.core.NovaDraw;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.math.RotationAxis;

/** {@link NovaCanvas} für Minecraft 1.21.4 ({@link DrawContext} mit MatrixStack und RenderLayer). */
public final class DrawContextCanvas implements NovaCanvas {
	private final DrawContext context;
	private final TextRenderer textRenderer;

	public DrawContextCanvas(DrawContext context, TextRenderer textRenderer) {
		this.context = context;
		this.textRenderer = textRenderer;
	}

	@Override
	public int width() {
		return this.context.getScaledWindowWidth();
	}

	@Override
	public int height() {
		return this.context.getScaledWindowHeight();
	}

	@Override
	public void fill(int x1, int y1, int x2, int y2, int color) {
		this.context.fill(x1, y1, x2, y2, color);
	}

	@Override
	public void gradient(int x1, int y1, int x2, int y2, int top, int bottom) {
		this.context.fillGradient(x1, y1, x2, y2, top, bottom);
	}

	@Override
	public void logo(int x, int y, int width, int height, int color) {
		this.context.drawTexture(
			RenderLayer::getGuiTextured, NovaClient.LOGO, x, y, 0.0F, 0.0F, width, height,
			NovaDraw.LOGO_WIDTH, NovaDraw.LOGO_HEIGHT, NovaDraw.LOGO_WIDTH, NovaDraw.LOGO_HEIGHT, color
		);
	}

	/** Ganze 64×64-Textur, z. B. ein Server-Icon. */
	@Override
	public void texture(Object texture, int x, int y, int width, int height) {
		this.context.drawTexture(
			RenderLayer::getGuiTextured, (Identifier) texture, x, y, 0.0F, 0.0F, width, height,
			64, 64, 64, 64, -1
		);
	}

	@Override
	public void text(String text, int x, int y, int color, boolean shadow) {
		this.context.drawText(this.textRenderer, text, x, y, color, shadow);
	}

	@Override
	public int textWidth(String text) {
		return this.textRenderer.getWidth(text);
	}

	@Override
	public int fontHeight() {
		return this.textRenderer.fontHeight;
	}

	@Override
	public void push() {
		this.context.getMatrices().push();
	}

	@Override
	public void pop() {
		this.context.getMatrices().pop();
	}

	@Override
	public void translate(float x, float y) {
		this.context.getMatrices().translate(x, y, 0.0F);
	}

	@Override
	public void scale(float scale) {
		this.context.getMatrices().scale(scale, scale, 1.0F);
	}

	@Override
	public void rotate(float degrees) {
		this.context.getMatrices().multiply(RotationAxis.POSITIVE_Z.rotationDegrees(degrees));
	}

	@Override
	public void enableScissor(int x1, int y1, int x2, int y2) {
		this.context.enableScissor(x1, y1, x2, y2);
	}

	@Override
	public void disableScissor() {
		this.context.disableScissor();
	}
}
