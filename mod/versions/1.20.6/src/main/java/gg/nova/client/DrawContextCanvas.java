package gg.nova.client;

import gg.nova.core.NovaCanvas;
import gg.nova.core.NovaDraw;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.util.math.RotationAxis;

/** {@link NovaCanvas} für Minecraft 1.20.5 – 1.21.1 ({@link DrawContext}, Transparenz über die Shader-Farbe). */
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
		RenderSystem.enableBlend();
		RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, (color >>> 24) / 255.0F);
		this.context.drawTexture(
			NovaClient.LOGO, x, y, width, height, 0.0F, 0.0F,
			NovaDraw.LOGO_WIDTH, NovaDraw.LOGO_HEIGHT, NovaDraw.LOGO_WIDTH, NovaDraw.LOGO_HEIGHT
		);
		RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
	}

	/** Gegenstand mit Anzahl und Haltbarkeitsbalken. */
	@Override
	public void item(Object stack, int x, int y) {
		this.context.drawItem((ItemStack) stack, x, y);
		this.context.drawItemInSlot(this.textRenderer, (ItemStack) stack, x, y);
	}

	/** Ganze Textur (Server-Icon, Partner-Banner) ins Rechteck. */
	@Override
	public void texture(Object texture, int x, int y, int width, int height, int textureWidth, int textureHeight) {
		RenderSystem.enableBlend();
		RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
		this.context.drawTexture(
			(Identifier) texture, x, y, width, height, 0.0F, 0.0F,
			textureWidth, textureHeight, textureWidth, textureHeight
		);
		RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
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
