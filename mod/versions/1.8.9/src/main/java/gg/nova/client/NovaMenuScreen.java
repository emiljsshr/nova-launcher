package gg.nova.client;

import gg.nova.core.menu.NovaModMenu;
import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/** Nova-Menü (Rechts-Shift, oder "Cosmetics" im Hauptmenü): Zeichnen und Eingaben kommen aus {@link NovaModMenu}. */
public class NovaMenuScreen extends Screen {
	private final Screen parent;

	public NovaMenuScreen(Screen parent, NovaModMenu.Tab tab) {
		this.parent = parent;
		NovaModMenu.get().opened(tab);
	}

	@Override
	public void init() {
		Keyboard.enableRepeatEvents(true);
	}

	@Override
	public void render(int mouseX, int mouseY, float delta) {
		NovaModMenu.get().render(new LegacyCanvas(this.client), mouseX, mouseY);
	}

	@Override
	protected void mouseClicked(int mouseX, int mouseY, int button) {
		NovaModMenu.get().click(mouseX, mouseY);
	}

	@Override
	protected void mouseReleased(int mouseX, int mouseY, int button) {
		NovaModMenu.get().release();
	}

	@Override
	public void handleMouse() {
		super.handleMouse();
		int wheel = Mouse.getEventDWheel();
		if (wheel != 0) {
			int mouseX = Mouse.getEventX() * this.width / this.client.width;
			int mouseY = this.height - Mouse.getEventY() * this.height / this.client.height - 1;
			NovaModMenu.get().scroll(mouseX, mouseY, Integer.signum(wheel));
		}
	}

	@Override
	protected void keyPressed(char character, int keyCode) {
		NovaModMenu.Key key = keyCode == Keyboard.KEY_ESCAPE ? NovaModMenu.Key.ESCAPE
			: keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER ? NovaModMenu.Key.ENTER
			: keyCode == Keyboard.KEY_BACK ? NovaModMenu.Key.BACKSPACE
			: NovaModMenu.Key.OTHER;
		if (NovaModMenu.get().key(key) || key == NovaModMenu.Key.OTHER && NovaModMenu.get().typed(character)) {
			return;
		}
		if (keyCode == Keyboard.KEY_ESCAPE || keyCode == Keyboard.KEY_RSHIFT) {
			this.client.setScreen(this.parent);
		}
	}

	@Override
	public boolean shouldPauseGame() {
		return false;
	}

	@Override
	public void removed() {
		Keyboard.enableRepeatEvents(false);
		NovaModMenu.get().closed();
	}
}
