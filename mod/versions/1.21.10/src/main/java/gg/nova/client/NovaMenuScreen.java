package gg.nova.client;

import gg.nova.core.menu.NovaModMenu;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
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
	public boolean mouseClicked(Click click, boolean doubled) {
		return NovaModMenu.get().click(click.x(), click.y()) || super.mouseClicked(click, doubled);
	}

	@Override
	public boolean mouseReleased(Click click) {
		NovaModMenu.get().release();
		return super.mouseReleased(click);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		return NovaModMenu.get().scroll(mouseX, mouseY, vertical);
	}

	@Override
	public boolean keyPressed(KeyInput input) {
		if (NovaModMenu.get().key(key(input.key()))) {
			return true;
		}
		// Rechts-Shift schließt das Menü wieder
		if (input.key() == GLFW.GLFW_KEY_RIGHT_SHIFT) {
			this.close();
			return true;
		}
		return super.keyPressed(input);
	}

	@Override
	public boolean charTyped(CharInput input) {
		boolean taken = false;
		for (char character : Character.toChars(input.codepoint())) {
			taken |= NovaModMenu.get().typed(character);
		}
		return taken;
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
