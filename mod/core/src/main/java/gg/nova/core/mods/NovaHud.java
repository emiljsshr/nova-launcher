package gg.nova.core.mods;

import gg.nova.core.NovaCanvas;
import gg.nova.core.NovaDraw;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Zeichnet die HUD-Module. Im Editor ({@code edit}) mit Rahmen, Beispielwerten außerhalb einer Welt und
 * Verschieben per Maus ({@link #press}, {@link #release}).
 */
public final class NovaHud {
	private static final int BACKGROUND = 0x90000000;
	private static final int KEY_SIZE = 18;
	private static final String[] DIRECTIONS = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};

	/** Bildschirm-Rechtecke der Module im letzten Bild (für den Editor). */
	private static final Map<Module, int[]> BOUNDS = new LinkedHashMap<Module, int[]>();
	private static Module dragging;
	private static int grabX;
	private static int grabY;

	private NovaHud() {
	}

	static void render(NovaCanvas canvas, NovaGame game, boolean edit) {
		BOUNDS.clear();
		int[] cps = edit && !game.inWorld() ? new int[] {7, 3} : NovaMods.cps();
		for (Module module : NovaMods.modules()) {
			if (!module.hud || !module.enabled) {
				continue;
			}
			Element element = element(canvas, game, module, cps, edit);
			if (element == null) {
				continue;
			}
			float scale = module.scale();
			int width = Math.round(element.width * scale);
			int height = Math.round(element.height * scale);
			int x = Math.round(module.x * (canvas.width() - width));
			int y = Math.round(module.y * (canvas.height() - height));
			BOUNDS.put(module, new int[] {x, y, width, height});

			canvas.push();
			canvas.translate(x, y);
			canvas.scale(scale);
			element.draw(canvas, module.on("background"));
			canvas.pop();
		}
	}

	/** HUD-Editor: Module mit Rahmen zeichnen, gezogenes Modul der Maus folgen lassen. */
	public static void renderEditor(NovaCanvas canvas, int mouseX, int mouseY) {
		NovaGame game = NovaMods.game();
		if (game == null) {
			return;
		}
		if (dragging != null) {
			int[] bounds = BOUNDS.get(dragging);
			if (bounds != null) {
				int freeX = Math.max(1, canvas.width() - bounds[2]);
				int freeY = Math.max(1, canvas.height() - bounds[3]);
				dragging.x = NovaMods.clamp((mouseX - grabX) / (float) freeX);
				dragging.y = NovaMods.clamp((mouseY - grabY) / (float) freeY);
			}
		}
		canvas.fill(0, 0, canvas.width(), canvas.height(), 0x50000000);
		// Mittellinien als Orientierung
		canvas.fill(canvas.width() / 2, 0, canvas.width() / 2 + 1, canvas.height(), 0x30FFFFFF);
		canvas.fill(0, canvas.height() / 2, canvas.width(), canvas.height() / 2 + 1, 0x30FFFFFF);
		render(canvas, game, true);
		for (Map.Entry<Module, int[]> entry : BOUNDS.entrySet()) {
			int[] b = entry.getValue();
			boolean hovered = mouseX >= b[0] && mouseX < b[0] + b[2] && mouseY >= b[1] && mouseY < b[1] + b[3];
			int color = entry.getKey() == dragging ? NovaDraw.WHITE : hovered ? 0xFFC6C6CE : 0x90FFFFFF;
			frame(canvas, b[0] - 1, b[1] - 1, b[2] + 2, b[3] + 2, color);
		}
	}

	/** Maustaste gedrückt: Modul unter der Maus greifen. */
	public static boolean press(double mouseX, double mouseY) {
		List<Map.Entry<Module, int[]>> entries = new ArrayList<Map.Entry<Module, int[]>>(BOUNDS.entrySet());
		for (int index = entries.size() - 1; index >= 0; index--) {
			int[] b = entries.get(index).getValue();
			if (mouseX >= b[0] && mouseX < b[0] + b[2] && mouseY >= b[1] && mouseY < b[1] + b[3]) {
				dragging = entries.get(index).getKey();
				grabX = (int) mouseX - b[0];
				grabY = (int) mouseY - b[1];
				return true;
			}
		}
		return false;
	}

	public static void release() {
		if (dragging != null) {
			dragging = null;
			NovaMods.save();
		}
	}

	private static void frame(NovaCanvas canvas, int x, int y, int width, int height, int color) {
		canvas.fill(x, y, x + width, y + 1, color);
		canvas.fill(x, y + height - 1, x + width, y + height, color);
		canvas.fill(x, y, x + 1, y + height, color);
		canvas.fill(x + width - 1, y, x + width, y + height, color);
	}

	/* ========================================
	   ELEMENTE
	======================================== */

	private abstract static class Element {
		final int width;
		final int height;

		Element(int width, int height) {
			this.width = width;
			this.height = height;
		}

		abstract void draw(NovaCanvas canvas, boolean background);
	}

	/** Textkasten mit einer oder mehreren Zeilen. */
	private static Element text(final NovaCanvas canvas, final String... lines) {
		int width = 0;
		for (String line : lines) {
			width = Math.max(width, canvas.textWidth(line));
		}
		return new Element(width + 8, lines.length * 10 + 5) {
			@Override
			void draw(NovaCanvas canvas, boolean background) {
				if (background) {
					canvas.fill(0, 0, this.width, this.height, BACKGROUND);
				}
				for (int index = 0; index < lines.length; index++) {
					canvas.text(lines[index], 4, 4 + index * 10, NovaDraw.WHITE, true);
				}
			}
		};
	}

	private static Element element(NovaCanvas canvas, final NovaGame game, final Module module, final int[] cps, boolean edit) {
		boolean live = game.inWorld();
		if (module == NovaMods.FPS) {
			return text(canvas, (live ? game.fps() : 144) + " FPS");
		}
		if (module == NovaMods.CPS) {
			return text(canvas, module.on("right") ? cps[0] + " | " + cps[1] + " CPS" : cps[0] + " CPS");
		}
		if (module == NovaMods.PING) {
			return text(canvas, (live ? game.ping() : 24) + " ms");
		}
		if (module == NovaMods.CLOCK) {
			Calendar now = Calendar.getInstance();
			String time = module.on("seconds")
				? String.format(Locale.ROOT, "%02d:%02d:%02d", now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), now.get(Calendar.SECOND))
				: String.format(Locale.ROOT, "%02d:%02d", now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE));
			return text(canvas, time);
		}
		if (module == NovaMods.COORDS) {
			double x = live ? game.x() : 128.5;
			double y = live ? game.y() : 64;
			double z = live ? game.z() : -42.5;
			List<String> lines = new ArrayList<String>(Arrays.asList(
				"X: " + (int) Math.floor(x), "Y: " + (int) Math.floor(y), "Z: " + (int) Math.floor(z)));
			if (module.on("direction")) {
				float yaw = ((live ? game.yaw() : 180.0F) % 360.0F + 360.0F) % 360.0F;
				lines.add(DIRECTIONS[Math.round(yaw / 45.0F) % 8] + " (" + Math.round(yaw) + "°)");
			}
			return text(canvas, lines.toArray(new String[0]));
		}
		if (module == NovaMods.EFFECTS) {
			List<NovaGame.Effect> effects = live ? game.effects() : Arrays.asList(
				new NovaGame.Effect("Speed", 1, 1840), new NovaGame.Effect("Fire Resistance", 0, 4600));
			if (effects.isEmpty()) {
				return edit ? text(canvas, module.name) : null;
			}
			List<String> lines = new ArrayList<String>();
			for (NovaGame.Effect effect : effects) {
				String level = effect.amplifier > 0 ? " " + roman(effect.amplifier + 1) : "";
				String time = effect.ticks < 0 ? "∞" : String.format(Locale.ROOT, "%d:%02d", effect.ticks / 1200, effect.ticks / 20 % 60);
				lines.add(effect.name + level + " §7" + time);
			}
			return text(canvas, lines.toArray(new String[0]));
		}
		if (module == NovaMods.KEYSTROKES) {
			return keystrokes(game, module, cps, live);
		}
		if (module == NovaMods.ARMOR) {
			return armor(canvas, game, module, live, edit);
		}
		if (module == NovaMods.INVENTORY) {
			return inventory(game, live);
		}
		return null;
	}

	private static Element keystrokes(final NovaGame game, final Module module, final int[] cps, final boolean live) {
		final boolean mouse = module.on("mouse");
		final boolean space = module.on("space");
		int width = KEY_SIZE * 3 + 2;
		int height = KEY_SIZE * 2 + 1 + (mouse ? KEY_SIZE + 1 : 0) + (space ? 10 : 0);
		return new Element(width, height) {
			@Override
			void draw(NovaCanvas canvas, boolean background) {
				key(canvas, KEY_SIZE + 1, 0, KEY_SIZE, KEY_SIZE, "W", live && game.key(NovaGame.Key.FORWARD), null, background);
				key(canvas, 0, KEY_SIZE + 1, KEY_SIZE, KEY_SIZE, "A", live && game.key(NovaGame.Key.LEFT), null, background);
				key(canvas, KEY_SIZE + 1, KEY_SIZE + 1, KEY_SIZE, KEY_SIZE, "S", live && game.key(NovaGame.Key.BACK), null, background);
				key(canvas, 2 * (KEY_SIZE + 1), KEY_SIZE + 1, KEY_SIZE, KEY_SIZE, "D", live && game.key(NovaGame.Key.RIGHT), null, background);
				int y = 2 * (KEY_SIZE + 1);
				if (mouse) {
					int half = (this.width - 1) / 2;
					key(canvas, 0, y, half, KEY_SIZE, "LMB", live && game.key(NovaGame.Key.ATTACK), cps[0] + " CPS", background);
					key(canvas, half + 1, y, this.width - half - 1, KEY_SIZE, "RMB", live && game.key(NovaGame.Key.USE), cps[1] + " CPS", background);
					y += KEY_SIZE + 1;
				}
				if (space) {
					key(canvas, 0, y, this.width, 9, null, live && game.key(NovaGame.Key.JUMP), null, background);
					canvas.fill(this.width / 2 - 10, y + 4, this.width / 2 + 10, y + 5, live && game.key(NovaGame.Key.JUMP) ? 0xFF000000 : NovaDraw.WHITE);
				}
			}
		};
	}

	private static void key(NovaCanvas canvas, int x, int y, int width, int height, String label, boolean pressed, String sub, boolean background) {
		if (pressed || background) {
			canvas.fill(x, y, x + width, y + height, pressed ? 0xD8FFFFFF : BACKGROUND);
		}
		if (label == null) {
			return;
		}
		int color = pressed ? 0xFF000000 : NovaDraw.WHITE;
		if (sub == null) {
			canvas.text(label, x + (width - canvas.textWidth(label)) / 2 + 1, y + (height - 8) / 2 + 1, color, !pressed);
		} else {
			canvas.text(label, x + (width - canvas.textWidth(label)) / 2 + 1, y + 2, color, !pressed);
			int subWidth = NovaDraw.smallWidth(canvas, sub, 0);
			NovaDraw.smallText(canvas, sub, x + (width - subWidth) / 2.0F + 1, y + 11, pressed ? 0xFF303030 : 0xFFC6C6CE, 0);
		}
	}

	private static Element armor(NovaCanvas canvas, final NovaGame game, final Module module, final boolean live, boolean edit) {
		final List<Object> items = live ? game.armor() : new ArrayList<Object>(Arrays.asList(null, null, null, null));
		// Im Spiel nur getragene Teile, im Editor ohne Welt vier leere Plätze
		while (live && items.remove(null)) {
			// leere Plätze entfernen
		}
		boolean any = false;
		for (Object item : items) {
			any |= item != null;
		}
		if (!any && !edit) {
			return null;
		}
		final String mode = module.setting("durability").value();
		final List<String> labels = new ArrayList<String>();
		int textWidth = 0;
		for (Object item : items) {
			String label = "";
			int[] durability = item == null ? null : game.durability(item);
			if (durability != null && !"none".equals(mode)) {
				label = "percent".equals(mode) ? percent(durability[0], durability[1]) + "%" : String.valueOf(durability[0]);
			}
			if (item == null && !live) {
				label = "–";
			}
			labels.add(label);
			textWidth = Math.max(textWidth, canvas.textWidth(label));
		}
		final int width = 16 + 4 + textWidth + 4;
		return new Element(width, items.size() * 17 + 2) {
			@Override
			void draw(NovaCanvas canvas, boolean background) {
				if (background) {
					canvas.fill(0, 0, this.width, this.height, BACKGROUND);
				}
				for (int index = 0; index < items.size(); index++) {
					int y = 1 + index * 17;
					Object item = items.get(index);
					if (item != null) {
						canvas.item(item, 2, y);
					} else if (!live) {
						canvas.fill(3, y + 1, 17, y + 15, 0x40FFFFFF);
					}
					canvas.text(labels.get(index), 22, y + 5, NovaDraw.WHITE, true);
				}
			}
		};
	}

	private static Element inventory(final NovaGame game, final boolean live) {
		final List<Object> items = live ? game.inventory() : new ArrayList<Object>();
		return new Element(9 * 18 + 4, 3 * 18 + 4) {
			@Override
			void draw(NovaCanvas canvas, boolean background) {
				if (background) {
					canvas.fill(0, 0, this.width, this.height, BACKGROUND);
				}
				for (int slot = 0; slot < 27; slot++) {
					int x = 2 + slot % 9 * 18;
					int y = 2 + slot / 9 * 18;
					canvas.fill(x, y, x + 17, y + 17, 0x30FFFFFF);
					Object item = slot < items.size() ? items.get(slot) : null;
					if (item != null) {
						canvas.item(item, x, y);
					}
				}
			}
		};
	}

	private static int percent(int remaining, int max) {
		return max <= 0 ? -1 : Math.round(remaining * 100.0F / max);
	}

	private static String roman(int number) {
		String[] numerals = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
		return number >= 1 && number <= numerals.length ? numerals[number - 1] : String.valueOf(number);
	}
}
