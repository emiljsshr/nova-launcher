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

	/** Nova-Wappen ({@link NovaDraw#LOGO_WIDTH} × {@link NovaDraw#LOGO_HEIGHT}) in das Rechteck zeichnen; {@code color} trägt die Deckkraft. */
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

	/**
	 * Ganze Textur (z. B. Server-Icon) in das Rechteck zeichnen; {@code texture} ist die Textur-ID der Version.
	 * Ohne Umsetzung passiert nichts – das Menü zeigt dann ein Ersatz-Icon.
	 */
	default void texture(Object texture, int x, int y, int width, int height) {
		this.texture(texture, x, y, width, height, 64, 64);
	}

	/** Ganze Textur mit der Größe {@code textureWidth} × {@code textureHeight} (z. B. ein Partner-Banner) ins Rechteck. */
	default void texture(Object texture, int x, int y, int width, int height, int textureWidth, int textureHeight) {
	}

	void disableScissor();
}
