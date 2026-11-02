package gg.nova.client;

import gg.nova.core.menu.NovaModMenu;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/** Nova-Menü (Rechts-Shift, oder "Cosmetics" im Hauptmenü): Zeichnen und Eingaben kommen aus {@link NovaModMenu}. */
public class NovaMenuScreen extends Screen {
	private final Screen parent;

	public NovaMenuScreen(Screen parent, NovaModMenu.Tab tab) {
		super(Text.literal("Nova"));
		this.parent = parent;
		NovaModMenu.get().opened(tab);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		NovaModMenu.get().render(new DrawContextCanvas(context, this.textRenderer), mouseX, mouseY);
		// Schließen-Knopf im Menü
		if (NovaModMenu.get().consumeClose()) {
			this.close();
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		return NovaModMenu.get().click(mouseX, mouseY) || super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		NovaModMenu.get().release();
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		return NovaModMenu.get().scroll(mouseX, mouseY, vertical);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (NovaModMenu.get().key(key(keyCode))) {
			return true;
		}
		// Rechts-Shift schließt das Menü wieder
		if (keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT) {
			this.close();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean charTyped(char character, int modifiers) {
		return NovaModMenu.get().typed(character);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public void close() {
		this.client.setScreen(this.parent);
	}

	@Override
	public void removed() {
		NovaModMenu.get().closed();
	}

	private static NovaModMenu.Key key(int code) {
		switch (code) {
			case GLFW.GLFW_KEY_ESCAPE:
				return NovaModMenu.Key.ESCAPE;
			case GLFW.GLFW_KEY_ENTER:
			case GLFW.GLFW_KEY_KP_ENTER:
				return NovaModMenu.Key.ENTER;
			case GLFW.GLFW_KEY_BACKSPACE:
				return NovaModMenu.Key.BACKSPACE;
			default:
				return NovaModMenu.Key.OTHER;
		}
	}
}
