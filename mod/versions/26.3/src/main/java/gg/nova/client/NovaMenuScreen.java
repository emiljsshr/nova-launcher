package gg.nova.client;

import com.mojang.blaze3d.platform.InputConstants;
import gg.nova.core.menu.NovaModMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Nova-Menü (Rechts-Shift, oder "Cosmetics" im Hauptmenü): Zeichnen und Eingaben kommen aus {@link NovaModMenu}. */
public class NovaMenuScreen extends Screen {
	private final Screen parent;

	public NovaMenuScreen(Screen parent, NovaModMenu.Tab tab) {
		super(Component.literal("Nova"));
		this.parent = parent;
		NovaModMenu.get().opened(tab);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		NovaModMenu.get().render(new GuiCanvas(graphics, this.font), mouseX, mouseY);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		return NovaModMenu.get().click(event.x(), event.y()) || super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		NovaModMenu.get().release();
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		return NovaModMenu.get().scroll(mouseX, mouseY, vertical);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (NovaModMenu.get().key(key(event.key()))) {
			return true;
		}
		// Rechts-Shift schließt das Menü wieder
		if (event.key() == InputConstants.KEY_RSHIFT) {
			this.onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		boolean taken = false;
		for (char character : Character.toChars(event.codepoint())) {
			taken |= NovaModMenu.get().typed(character);
		}
		return taken;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(this.parent);
	}

	@Override
	public void removed() {
		NovaModMenu.get().closed();
	}

	private static NovaModMenu.Key key(int code) {
		// Ab 26.3 ohne GLFW: Tastencodes aus InputConstants (keine Konstanten für switch)
		if (code == InputConstants.KEY_ESCAPE) {
			return NovaModMenu.Key.ESCAPE;
		}
		if (code == InputConstants.KEY_RETURN || code == InputConstants.KEY_NUMPADENTER) {
			return NovaModMenu.Key.ENTER;
		}
		return code == InputConstants.KEY_BACKSPACE ? NovaModMenu.Key.BACKSPACE : NovaModMenu.Key.OTHER;
	}
}
