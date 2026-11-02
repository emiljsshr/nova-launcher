package gg.nova.core.menu;

import gg.nova.core.NovaCanvas;
import gg.nova.core.NovaDraw;
import gg.nova.core.cosmetic.Cosmetics;
import gg.nova.core.social.NovaSocial;
import java.util.ArrayList;
import java.util.List;

/**
 * Nova-Menü im Spiel (Rechts-Shift): Cosmetics ohne Neustart wechseln, Freunde und Chat.
 * Zeichnen und Eingaben laufen komplett hier; die Versions-Schicht leitet nur Maus und Tastatur weiter.
 * Der Zustand (Tab, offener Chat, Eingaben) bleibt zwischen zwei Öffnungen erhalten.
 */
public final class NovaModMenu {
	public enum Tab {
		COSMETICS,
		FRIENDS
	}

	/** Tasten, die das Menü selbst behandelt; alles andere geht als Zeichen über {@link #typed}. */
	public enum Key {
		ESCAPE,
		ENTER,
		BACKSPACE,
		OTHER
	}

	private enum Focus {
		NONE,
		ADD_FRIEND,
		CHAT
	}

	private static final NovaModMenu INSTANCE = new NovaModMenu();

	private static final int BACKGROUND = 0xF0131319;
	private static final int HEADER = 0xFF1D1D23;
	private static final int TILE = 0xFF25252B;
	private static final int TILE_HOVER = 0xFF36363D;
	private static final int TILE_ACTIVE = 0xFF4A4A55;
	private static final int BORDER = 0xFF494950;
	private static final int OFFLINE = 0xFF63636B;
	private static final int HEADER_HEIGHT = 22;
	private static final int TILE_HEIGHT = 20;
	private static final int FRIEND_HEIGHT = 22;
	private static final int REQUEST_HEIGHT = 16;
	private static final int INPUT_HEIGHT = 16;
	private static final int NAME_LIMIT = 16;
	private static final int MESSAGE_LIMIT = 256;

	private static final String[] TYPES = {"CAPE", "HEAD", "WINGS", "PET", "TRAIL"};
	private static final String[] TYPE_LABELS = {"CAPE", "KOPF", "FLÜGEL", "PET", "SPUREN"};

	/** Klickbare Fläche aus dem letzten Bild. */
	private static final class Hit {
		final int x1;
		final int y1;
		final int x2;
		final int y2;
		final Runnable action;

		Hit(int x1, int y1, int x2, int y2, Runnable action) {
			this.x1 = x1;
			this.y1 = y1;
			this.x2 = x2;
			this.y2 = y2;
			this.action = action;
		}

		boolean contains(double x, double y) {
			return x >= this.x1 && x < this.x2 && y >= this.y1 && y < this.y2;
		}
	}

	private Tab tab = Tab.COSMETICS;
	private Focus focus = Focus.NONE;
	private String selectedFriend;
	private final StringBuilder friendName = new StringBuilder();
	private final StringBuilder draft = new StringBuilder();
	private int cosmeticScroll;
	private int friendScroll;
	/** Abstand vom unteren Ende des Verlaufs (0 = neueste Nachricht sichtbar). */
	private int chatScroll;
	private final List<Hit> hits = new ArrayList<Hit>();
	private final List<Hit> scrollAreas = new ArrayList<Hit>();
	private int[] scrollTargets = new int[0];

	private NovaModMenu() {
	}

	public static NovaModMenu get() {
		return INSTANCE;
	}

	/** Beim Öffnen; ein offener Chat lädt seinen Verlauf neu. */
	public void opened(Tab startTab) {
		if (startTab != null) {
			this.tab = startTab;
		}
		this.focus = this.tab == Tab.FRIENDS && this.selectedFriend != null ? Focus.CHAT : Focus.NONE;
		NovaSocial.openChat(this.tab == Tab.FRIENDS ? this.selectedFriend : null);
	}

	/** Wechselt zu den Freunden mit offenem Chat. */
	public void openChatWith(String friendId) {
		this.tab = Tab.FRIENDS;
		this.selectedFriend = friendId;
		this.chatScroll = 0;
		this.focus = Focus.CHAT;
		NovaSocial.openChat(friendId);
	}

	public void closed() {
		NovaSocial.openChat(null);
		this.focus = Focus.NONE;
	}

	// Eingaben

	public boolean click(double x, double y) {
		for (int i = this.hits.size() - 1; i >= 0; i--) {
			Hit hit = this.hits.get(i);
			if (hit.contains(x, y)) {
				hit.action.run();
				return true;
			}
		}
		this.focus = Focus.NONE;
		return false;
	}

	/** @param amount positiv = nach oben scrollen */
	public boolean scroll(double x, double y, double amount) {
		int step = (int) Math.round(amount * 12);
		for (int i = 0; i < this.scrollAreas.size(); i++) {
			if (this.scrollAreas.get(i).contains(x, y)) {
				int target = this.scrollTargets[i];
				if (target == 0) {
					this.cosmeticScroll -= step;
				} else if (target == 1) {
					this.friendScroll -= step;
				} else {
					this.chatScroll += step;
				}
				return true;
			}
		}
		return false;
	}

	/** @return true, wenn das Menü die Taste verbraucht hat (Escape ohne Eingabefeld schließt das Menü) */
	public boolean key(Key key) {
		if (key == Key.ESCAPE) {
			if (this.focus != Focus.NONE) {
				this.focus = Focus.NONE;
				return true;
			}
			return false;
		}
		StringBuilder field = this.field();
		if (field == null) {
			return false;
		}
		if (key == Key.BACKSPACE && field.length() > 0) {
			field.setLength(field.offsetByCodePoints(field.length(), -1));
		} else if (key == Key.ENTER) {
			this.submit();
		}
		return true;
	}

	/** Zeicheneingabe; true, wenn ein Eingabefeld sie aufgenommen hat. */
	public boolean typed(char character) {
		StringBuilder field = this.field();
		if (field == null || character < ' ' || character == '§' || character == 127) {
			return false;
		}
		if (this.focus == Focus.ADD_FRIEND) {
			boolean allowed = Character.isLetterOrDigit(character) && character < 128 || character == '_';
			if (allowed && field.length() < NAME_LIMIT) {
				field.append(character);
			}
		} else if (field.length() < MESSAGE_LIMIT) {
			field.append(character);
		}
		return true;
	}

	private StringBuilder field() {
		return this.focus == Focus.ADD_FRIEND ? this.friendName : this.focus == Focus.CHAT ? this.draft : null;
	}

	private void submit() {
		if (this.focus == Focus.ADD_FRIEND && this.friendName.length() > 0) {
			NovaSocial.requestFriend(this.friendName.toString());
			this.friendName.setLength(0);
		} else if (this.focus == Focus.CHAT && this.selectedFriend != null) {
			String text = this.draft.toString().trim();
			if (!text.isEmpty()) {
				NovaSocial.sendMessage(this.selectedFriend, text);
				this.draft.setLength(0);
				this.chatScroll = 0;
			}
		}
	}

	// Zeichnen

	public void render(NovaCanvas canvas, int mouseX, int mouseY) {
		this.hits.clear();
		this.scrollAreas.clear();
		List<Integer> targets = new ArrayList<Integer>();

		int width = Math.min(460, canvas.width() - 20);
		int height = Math.min(280, canvas.height() - 20);
		int left = (canvas.width() - width) / 2;
		int top = (canvas.height() - height) / 2;

		canvas.fill(0, 0, canvas.width(), canvas.height(), 0x80000000);
		canvas.fill(left, top, left + width, top + height, BACKGROUND);
		canvas.fill(left, top, left + width, top + HEADER_HEIGHT, HEADER);
		canvas.fill(left, top + HEADER_HEIGHT - 1, left + width, top + HEADER_HEIGHT, BORDER);
		NovaDraw.spacedText(canvas, "NOVA", left + 9, top + 8, NovaDraw.WHITE, 2, true);

		// Tabs rechts im Kopf
		int tabRight = left + width - 4;
		int unread = NovaSocial.unreadTotal() + NovaSocial.incoming().size();
		tabRight = this.tab(canvas, "FREUNDE" + (unread > 0 ? " (" + unread + ")" : ""), Tab.FRIENDS, tabRight, top, mouseX, mouseY);
		this.tab(canvas, "COSMETICS", Tab.COSMETICS, tabRight, top, mouseX, mouseY);

		int contentX = left + 8;
		int contentY = top + HEADER_HEIGHT + 8;
		int contentWidth = width - 16;
		int contentHeight = height - HEADER_HEIGHT - 16;
		String notice = NovaSocial.notice();
		if (notice != null) {
			contentHeight -= 12;
			NovaDraw.smallText(canvas, NovaDraw.trim(canvas, notice, (int) (contentWidth / 0.75F)), contentX, top + height - 13, NovaDraw.FROST, 0);
		}

		if (this.tab == Tab.COSMETICS) {
			this.renderCosmetics(canvas, contentX, contentY, contentWidth, contentHeight, mouseX, mouseY, targets);
		} else {
			this.renderFriends(canvas, contentX, contentY, contentWidth, contentHeight, mouseX, mouseY, targets);
		}

		this.scrollTargets = new int[targets.size()];
		for (int i = 0; i < targets.size(); i++) {
			this.scrollTargets[i] = targets.get(i);
		}
	}

	private int tab(NovaCanvas canvas, String label, final Tab target, int right, int top, int mouseX, int mouseY) {
		int tabWidth = NovaDraw.spacedWidth(canvas, label, 1) + 14;
		int x = right - tabWidth;
		boolean active = this.tab == target;
		boolean hovered = mouseX >= x && mouseX < right && mouseY >= top && mouseY < top + HEADER_HEIGHT;
		if (active) {
			canvas.fill(x, top + HEADER_HEIGHT - 2, right, top + HEADER_HEIGHT, NovaDraw.FROST);
		}
		NovaDraw.spacedText(canvas, label, x + 7, top + 8, active ? NovaDraw.WHITE : hovered ? NovaDraw.SOFT : NovaDraw.MUTED, 1, false);
		this.hits.add(new Hit(x, top, right, top + HEADER_HEIGHT, new Runnable() {
			@Override
			public void run() {
				NovaModMenu.this.opened(target);
			}
		}));
		return x - 2;
	}

	private void renderCosmetics(NovaCanvas canvas, int x, int y, int width, int height, int mouseX, int mouseY, List<Integer> targets) {
		List<Cosmetics.Entry> catalog = Cosmetics.catalog();
		List<String> equipped = Cosmetics.equippedIds();
		int columns = width >= 300 ? 2 : 1;
		int tileWidth = (width - 6 * (columns - 1)) / columns;

		// Gesamthöhe für die Scroll-Grenzen
		int total = 0;
		for (String type : TYPES) {
			int count = countOfType(catalog, type);
			if (count > 0) {
				total += 14 + (count + columns - 1) / columns * (TILE_HEIGHT + 4) + 4;
			}
		}
		this.cosmeticScroll = Math.max(0, Math.min(this.cosmeticScroll, total - height));
		this.scrollAreas.add(new Hit(x, y, x + width, y + height, null));
		targets.add(0);

		canvas.enableScissor(x, y, x + width, y + height);
		int cursor = y - this.cosmeticScroll;
		for (int t = 0; t < TYPES.length; t++) {
			String type = TYPES[t];
			int count = countOfType(catalog, type);
			if (count == 0) {
				continue;
			}
			int limit = Cosmetics.slotLimit(type);
			String header = TYPE_LABELS[t] + (limit > 1 ? " · " + countEquipped(catalog, equipped, type) + "/" + limit : "");
			NovaDraw.smallText(canvas, header, x, cursor + 3, NovaDraw.MUTED, 1);
			cursor += 14;
			int index = 0;
			for (final Cosmetics.Entry entry : catalog) {
				if (!entry.type.equals(type)) {
					continue;
				}
				int tileX = x + index % columns * (tileWidth + 6);
				int tileY = cursor + index / columns * (TILE_HEIGHT + 4);
				boolean on = equipped.contains(entry.id);
				boolean hovered = mouseX >= tileX && mouseX < tileX + tileWidth && mouseY >= tileY && mouseY < tileY + TILE_HEIGHT
					&& mouseY >= y && mouseY < y + height;
				canvas.fill(tileX, tileY, tileX + tileWidth, tileY + TILE_HEIGHT, on ? TILE_ACTIVE : hovered ? TILE_HOVER : TILE);
				if (on) {
					canvas.fill(tileX, tileY, tileX + 2, tileY + TILE_HEIGHT, NovaDraw.FROST);
				}
				// Kästchen
				int boxX = tileX + 7;
				int boxY = tileY + 6;
				canvas.fill(boxX, boxY, boxX + 8, boxY + 8, on ? NovaDraw.FROST : NovaDraw.SOFT);
				canvas.fill(boxX + 1, boxY + 1, boxX + 7, boxY + 7, on ? NovaDraw.FROST_STRONG : TILE);
				if (on) {
					canvas.fill(boxX + 2, boxY + 2, boxX + 6, boxY + 6, NovaDraw.WHITE);
				}
				canvas.text(NovaDraw.trim(canvas, entry.name, tileWidth - 26), tileX + 21, tileY + 6, NovaDraw.WHITE, false);
				if (tileY + TILE_HEIGHT > y && tileY < y + height) {
					this.hits.add(new Hit(tileX, Math.max(tileY, y), tileX + tileWidth, Math.min(tileY + TILE_HEIGHT, y + height), new Runnable() {
						@Override
						public void run() {
							NovaSocial.sendLoadout(Cosmetics.toggle(entry.id));
						}
					}));
				}
				index++;
			}
			cursor += (count + columns - 1) / columns * (TILE_HEIGHT + 4) + 4;
		}
		canvas.disableScissor();
		if (catalog.isEmpty()) {
			canvas.text("Keine Cosmetics gefunden.", x, y, NovaDraw.MUTED, false);
		}
	}

	private static int countOfType(List<Cosmetics.Entry> catalog, String type) {
		int count = 0;
		for (Cosmetics.Entry entry : catalog) {
			if (entry.type.equals(type)) {
				count++;
			}
		}
		return count;
	}

	private static int countEquipped(List<Cosmetics.Entry> catalog, List<String> equipped, String type) {
		int count = 0;
		for (Cosmetics.Entry entry : catalog) {
			if (entry.type.equals(type) && equipped.contains(entry.id)) {
				count++;
			}
		}
		return count;
	}

	private void renderFriends(NovaCanvas canvas, int x, int y, int width, int height, int mouseX, int mouseY, List<Integer> targets) {
		if (!NovaSocial.available()) {
			String line1 = NovaSocial.bridged() ? "Verbinde mit dem Nova-Server …" : "Freunde und Chat gibt es, wenn du";
			String line2 = NovaSocial.bridged() ? "" : "Minecraft über den Nova Launcher startest.";
			canvas.text(line1, x + (width - canvas.textWidth(line1)) / 2, y + height / 2 - 10, NovaDraw.SOFT, false);
			canvas.text(line2, x + (width - canvas.textWidth(line2)) / 2, y + height / 2 + 2, NovaDraw.SOFT, false);
			return;
		}

		int listWidth = Math.min(170, width * 2 / 5);
		this.input(canvas, x, y, listWidth, this.friendName, "Freund hinzufügen …", Focus.ADD_FRIEND);
		this.renderFriendList(canvas, x, y + INPUT_HEIGHT + 6, listWidth, height - INPUT_HEIGHT - 6, mouseX, mouseY, targets);

		int chatX = x + listWidth + 8;
		int chatWidth = width - listWidth - 8;
		canvas.fill(chatX, y, chatX + chatWidth, y + height, 0xFF17171D);
		NovaSocial.Friend friend = null;
		for (NovaSocial.Friend candidate : NovaSocial.friends()) {
			if (candidate.id.equals(this.selectedFriend)) {
				friend = candidate;
			}
		}
		if (friend == null) {
			String hint = "Wähle links einen Freund aus.";
			canvas.text(hint, chatX + (chatWidth - canvas.textWidth(hint)) / 2, y + height / 2 - 4, NovaDraw.MUTED, false);
			return;
		}
		this.renderChat(canvas, friend, chatX, y, chatWidth, height, targets);
	}

	private void renderFriendList(NovaCanvas canvas, int x, int y, int width, int height, int mouseX, int mouseY, List<Integer> targets) {
		List<NovaSocial.Request> incoming = NovaSocial.incoming();
		List<NovaSocial.Friend> friends = new ArrayList<NovaSocial.Friend>(NovaSocial.friends());
		// Spielende zuerst, dann Launcher, dann offline
		java.util.Collections.sort(friends, new java.util.Comparator<NovaSocial.Friend>() {
			@Override
			public int compare(NovaSocial.Friend a, NovaSocial.Friend b) {
				int rank = rank(a) - rank(b);
				return rank != 0 ? rank : a.name.compareToIgnoreCase(b.name);
			}
		});

		int total = (incoming.isEmpty() ? 0 : 12 + incoming.size() * REQUEST_HEIGHT + 4) + friends.size() * FRIEND_HEIGHT;
		this.friendScroll = Math.max(0, Math.min(this.friendScroll, total - height));
		this.scrollAreas.add(new Hit(x, y, x + width, y + height, null));
		targets.add(1);

		canvas.enableScissor(x, y, x + width, y + height);
		int cursor = y - this.friendScroll;
		if (!incoming.isEmpty()) {
			NovaDraw.smallText(canvas, "ANFRAGEN", x, cursor + 2, NovaDraw.MUTED, 1);
			cursor += 12;
			for (final NovaSocial.Request request : incoming) {
				canvas.fill(x, cursor, x + width, cursor + REQUEST_HEIGHT - 2, TILE);
				canvas.text(NovaDraw.trim(canvas, request.name, width - 40), x + 4, cursor + 3, NovaDraw.WHITE, false);
				this.iconButton(canvas, x + width - 30, cursor + 1, "✔", NovaDraw.ONLINE, y, height, new Runnable() {
					@Override
					public void run() {
						NovaSocial.acceptFriend(request.id);
					}
				});
				this.iconButton(canvas, x + width - 15, cursor + 1, "✕", 0xFFD05050, y, height, new Runnable() {
					@Override
					public void run() {
						NovaSocial.declineFriend(request.id);
					}
				});
				cursor += REQUEST_HEIGHT;
			}
			cursor += 4;
		}

		for (final NovaSocial.Friend friend : friends) {
			boolean selected = friend.id.equals(this.selectedFriend);
			boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= cursor && mouseY < cursor + FRIEND_HEIGHT - 2
				&& mouseY >= y && mouseY < y + height;
			canvas.fill(x, cursor, x + width, cursor + FRIEND_HEIGHT - 2, selected ? TILE_ACTIVE : hovered ? TILE_HOVER : TILE);
			int dot = friend.playing() ? NovaDraw.ONLINE : friend.online ? NovaDraw.FROST : OFFLINE;
			canvas.fill(x + 5, cursor + 8, x + 9, cursor + 12, dot);
			int unread = NovaSocial.unread(friend.id);
			int nameRight = x + width - (unread > 0 ? canvas.textWidth(String.valueOf(unread)) + 12 : 4);
			canvas.text(NovaDraw.trim(canvas, friend.name, nameRight - x - 14), x + 14, cursor + 3, friend.online ? NovaDraw.WHITE : NovaDraw.MUTED, false);
			NovaDraw.smallText(canvas, NovaDraw.trim(canvas, friend.status(), (int) ((width - 18) / 0.75F)), x + 14, cursor + 12.5F, NovaDraw.MUTED, 0);
			if (unread > 0) {
				String count = String.valueOf(unread);
				int badgeX = x + width - canvas.textWidth(count) - 8;
				canvas.fill(badgeX - 2, cursor + 2, x + width - 3, cursor + 12, NovaDraw.FROST_STRONG);
				canvas.text(count, badgeX + 1, cursor + 3, NovaDraw.WHITE, false);
			}
			if (cursor + FRIEND_HEIGHT > y && cursor < y + height) {
				this.hits.add(new Hit(x, Math.max(cursor, y), x + width, Math.min(cursor + FRIEND_HEIGHT - 2, y + height), new Runnable() {
					@Override
					public void run() {
						NovaModMenu.this.selectedFriend = friend.id;
						NovaModMenu.this.chatScroll = 0;
						NovaModMenu.this.focus = Focus.CHAT;
						NovaSocial.openChat(friend.id);
					}
				}));
			}
			cursor += FRIEND_HEIGHT;
		}
		canvas.disableScissor();
		if (friends.isEmpty() && incoming.isEmpty()) {
			canvas.text("Noch keine Freunde.", x + 2, y + 4, NovaDraw.MUTED, false);
			NovaDraw.smallText(canvas, "Name oben eingeben + Enter.", x + 2, y + 16, NovaDraw.MUTED, 0);
		}
	}

	private static int rank(NovaSocial.Friend friend) {
		return friend.playing() ? 0 : friend.online ? 1 : 2;
	}

	private void iconButton(NovaCanvas canvas, int x, int y, String icon, int color, int clipTop, int clipHeight, Runnable action) {
		canvas.fill(x, y, x + 12, y + 12, 0xFF17171D);
		canvas.text(icon, x + (12 - canvas.textWidth(icon)) / 2 + 1, y + 2, color, false);
		if (y + 12 > clipTop && y < clipTop + clipHeight) {
			this.hits.add(new Hit(x, y, x + 12, y + 12, action));
		}
	}

	private void renderChat(NovaCanvas canvas, NovaSocial.Friend friend, int x, int y, int width, int height, List<Integer> targets) {
		canvas.text(NovaDraw.trim(canvas, friend.name, width - 12), x + 6, y + 5, NovaDraw.WHITE, false);
		NovaDraw.smallText(canvas, NovaDraw.trim(canvas, friend.status(), (int) ((width - 12) / 0.75F)), x + 6, y + 15, NovaDraw.MUTED, 0);
		canvas.fill(x, y + 23, x + width, y + 24, BORDER);

		int areaTop = y + 26;
		int areaBottom = y + height - INPUT_HEIGHT - 6;
		int bubbleWidth = width * 3 / 4;
		List<NovaSocial.Message> messages = NovaSocial.conversation(friend.id);

		this.scrollAreas.add(new Hit(x, areaTop, x + width, areaBottom, null));
		targets.add(2);

		if (messages == null) {
			canvas.text("Lädt …", x + 6, areaTop + 4, NovaDraw.MUTED, false);
		} else if (messages.isEmpty()) {
			canvas.text("Schreib die erste Nachricht.", x + 6, areaTop + 4, NovaDraw.MUTED, false);
		} else {
			// Von unten nach oben stapeln, damit die neueste Nachricht am Eingabefeld steht
			List<List<String>> wrapped = new ArrayList<List<String>>();
			int total = 0;
			for (NovaSocial.Message message : messages) {
				List<String> lines = wrap(canvas, message.text, bubbleWidth - 8);
				wrapped.add(lines);
				total += lines.size() * 10 + 8;
			}
			this.chatScroll = Math.max(0, Math.min(this.chatScroll, total - (areaBottom - areaTop)));
			canvas.enableScissor(x, areaTop, x + width, areaBottom);
			int cursor = areaBottom + this.chatScroll;
			for (int i = messages.size() - 1; i >= 0 && cursor > areaTop; i--) {
				List<String> lines = wrapped.get(i);
				boolean mine = NovaSocial.mine(messages.get(i));
				int textWidth = 0;
				for (String line : lines) {
					textWidth = Math.max(textWidth, canvas.textWidth(line));
				}
				int boxHeight = lines.size() * 10 + 4;
				int boxTop = cursor - boxHeight - 4;
				int boxLeft = mine ? x + width - 6 - textWidth - 8 : x + 6;
				canvas.fill(boxLeft, boxTop, boxLeft + textWidth + 8, boxTop + boxHeight, mine ? TILE_ACTIVE : TILE);
				for (int line = 0; line < lines.size(); line++) {
					canvas.text(lines.get(line), boxLeft + 4, boxTop + 3 + line * 10, NovaDraw.WHITE, false);
				}
				cursor = boxTop;
			}
			canvas.disableScissor();
		}

		this.input(canvas, x + 4, y + height - INPUT_HEIGHT - 4, width - 8, this.draft, "Nachricht … (Enter)", Focus.CHAT);
	}

	private void input(NovaCanvas canvas, int x, int y, int width, StringBuilder text, String placeholder, final Focus target) {
		boolean focused = this.focus == target;
		canvas.fill(x, y, x + width, y + INPUT_HEIGHT, focused ? NovaDraw.FROST : BORDER);
		canvas.fill(x + 1, y + 1, x + width - 1, y + INPUT_HEIGHT - 1, 0xFF0C0C11);
		int textY = y + (INPUT_HEIGHT - canvas.fontHeight()) / 2 + 1;
		if (text.length() == 0 && !focused) {
			canvas.text(NovaDraw.trim(canvas, placeholder, width - 8), x + 4, textY, NovaDraw.MUTED, false);
		} else {
			// Ende des Texts zeigen, wenn er zu lang ist
			String value = text.toString();
			while (value.length() > 0 && canvas.textWidth(value) > width - 14) {
				value = value.substring(1);
			}
			boolean caret = focused && System.currentTimeMillis() / 500 % 2 == 0;
			canvas.text(value + (caret ? "_" : ""), x + 4, textY, NovaDraw.WHITE, false);
		}
		this.hits.add(new Hit(x, y, x + width, y + INPUT_HEIGHT, new Runnable() {
			@Override
			public void run() {
				NovaModMenu.this.focus = target;
			}
		}));
	}

	/** Bricht Text an Leerzeichen um; zu lange Wörter werden hart geteilt. */
	private static List<String> wrap(NovaCanvas canvas, String text, int maxWidth) {
		List<String> lines = new ArrayList<String>();
		StringBuilder line = new StringBuilder();
		for (String word : text.split(" ")) {
			String candidate = line.length() == 0 ? word : line + " " + word;
			if (canvas.textWidth(candidate) <= maxWidth) {
				line.setLength(0);
				line.append(candidate);
				continue;
			}
			if (line.length() > 0) {
				lines.add(line.toString());
				line.setLength(0);
			}
			while (canvas.textWidth(word) > maxWidth) {
				int cut = word.length();
				while (cut > 1 && canvas.textWidth(word.substring(0, cut)) > maxWidth) {
					cut--;
				}
				lines.add(word.substring(0, cut));
				word = word.substring(cut);
			}
			line.append(word);
		}
		if (line.length() > 0 || lines.isEmpty()) {
			lines.add(line.toString());
		}
		return lines;
	}
}
