package gg.nova.core;

/**
 * Zeichenfläche, die jede Minecraft-Version selbst umsetzt.
 * Das gesamte Nova-Design zeichnet nur über diese Schnittstelle und ist dadurch versionsunabhängig.
 * Koordinaten sind skalierte GUI-Pixel, Farben ARGB.
 */
public interface NovaCanvas {
	int width();

	int height();

	void fill(int x1, int y1, int x2, int y2, int color);

	/** Vertikaler Verlauf von {@code top} nach {@code bottom}. */
	void gradient(int x1, int y1, int x2, int y2, int top, int bottom);

	/** Nova-Logo (590 × 181) in das Rechteck zeichnen; {@code color} trägt die Deckkraft. */
	void logo(int x, int y, int width, int height, int color);

	/** Text in Minecrafts Schrift – nur verwenden, wenn die Schriften geladen sind. */
	void text(String text, int x, int y, int color, boolean shadow);

	int textWidth(String text);

	int fontHeight();

	void push();

	void pop();

	void translate(float x, float y);

	void scale(float scale);

	void rotate(float degrees);

	void enableScissor(int x1, int y1, int x2, int y2);

	void disableScissor();
}
