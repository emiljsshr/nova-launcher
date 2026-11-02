package gg.nova.core.menu;

import gg.nova.core.NovaCanvas;
import gg.nova.core.NovaContent;
import gg.nova.core.NovaDraw;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.UnaryOperator;

/** "Nova Server": Liste der Partner-Server mit Status und Beitreten-Button. */
public final class NovaServerList {
	private static final int ROW_HEIGHT = 34;

	public enum PartnerState {
		PINGING,
		ONLINE,
		OFFLINE
	}

	public static final class PartnerStatus {
		private final PartnerState state;
		private final int players;
		private final long ping;

		public PartnerStatus(PartnerState state, int players, long ping) {
			this.state = state;
			this.players = players;
			this.ping = ping;
		}

		public PartnerState state() {
			return this.state;
		}

		public int players() {
			return this.players;
		}

		public long ping() {
			return this.ping;
		}
		public static final PartnerStatus PINGING = new PartnerStatus(PartnerState.PINGING, 0, 0L);
		public static final PartnerStatus OFFLINE = new PartnerStatus(PartnerState.OFFLINE, 0, 0L);
	}

	private final int width;
	private final int listX;
	private final int listY;
	private final int listWidth;
	private final List<MenuButton> buttons = new ArrayList<>();

	private NovaServerList(int width, int height) {
		this.width = width;
		this.listWidth = Math.min(340, width - 40);
		this.listX = (width - this.listWidth) / 2;
		int listHeight = NovaContent.PARTNERS.size() * ROW_HEIGHT;
		this.listY = Math.max(48, (height - listHeight) / 2 - 10);

		for (int index = 0; index < NovaContent.PARTNERS.size(); index++) {
			int rowY = this.listY + index * ROW_HEIGHT;
			this.buttons.add(new MenuButton(this.listX + this.listWidth - 8 - 40, rowY + 9, 40, 16,
				MenuButton.Action.JOIN_PARTNER, MenuButton.Style.ACCENT, null, "nova.menu.join", null, index));
		}
		this.buttons.add(new MenuButton((width - 200) / 2, Math.min(height - 28, this.listY + listHeight + 16), 200, 20,
			MenuButton.Action.BACK, MenuButton.Style.MAIN, null, "gui.back", null, -1));
	}

	public static NovaServerList layout(int width, int height) {
		return new NovaServerList(width, height);
	}

	public List<MenuButton> buttons() {
		return Collections.unmodifiableList(this.buttons);
	}

	public void render(NovaCanvas canvas, List<PartnerStatus> statuses, UnaryOperator<String> translate) {
		String title = translate.apply("nova.menu.servers").toUpperCase(Locale.ROOT);
		NovaDraw.spacedText(canvas, title, (this.width - NovaDraw.spacedWidth(canvas, title, 2)) / 2, this.listY - 30, NovaDraw.WHITE, 2, true);
		String subtitle = translate.apply("nova.menu.servers.subtitle");
		canvas.text(subtitle, (this.width - canvas.textWidth(subtitle)) / 2, this.listY - 17, NovaDraw.MUTED, false);

		for (int index = 0; index < NovaContent.PARTNERS.size(); index++) {
			NovaContent.Partner partner = NovaContent.PARTNERS.get(index);
			int rowY = this.listY + index * ROW_HEIGHT;
			canvas.fill(this.listX, rowY + 1, this.listX + this.listWidth, rowY + ROW_HEIGHT - 1, 0xD917171D);
			canvas.fill(this.listX, rowY + 1, this.listX + 2, rowY + ROW_HEIGHT - 1, 0xFF47474F);

			partnerIcon(canvas, partner.icon(), this.listX + 8, rowY + 5);
			int textX = this.listX + 40;
			int textRight = this.listX + this.listWidth - 8 - 40 - 8;
			canvas.text(NovaDraw.trim(canvas, partner.name(), textRight - textX), textX, rowY + 8, NovaDraw.WHITE, true);

			PartnerStatus status = index < statuses.size() ? statuses.get(index) : PartnerStatus.PINGING;
			int statusWidth = renderStatus(canvas, status, translate, textRight, rowY + 20);
			NovaDraw.smallText(canvas, NovaDraw.trim(canvas, partner.address(), (int) ((textRight - textX - statusWidth - 4) / 0.75F)),
				textX, rowY + 20.5F, NovaDraw.MUTED, 0);
		}
	}

	/** Summe der Spieler auf erreichbaren Servern, -1 wenn keiner erreichbar ist. */
	public static int totalPlayers(List<PartnerStatus> statuses) {
		int total = 0;
		boolean any = false;
		for (PartnerStatus status : statuses) {
			if (status.state() == PartnerState.ONLINE) {
				total += status.players();
				any = true;
			}
		}
		return any ? total : -1;
	}

	private static int renderStatus(NovaCanvas canvas, PartnerStatus status, UnaryOperator<String> translate, int right, int y) {
		String label;
		int bars;
		if (status.state() == PartnerState.ONLINE) {
			label = String.format(Locale.GERMANY, "%,d", status.players());
			long ping = status.ping();
			bars = ping < 150 ? 4 : ping < 300 ? 3 : ping < 600 ? 2 : 1;
		} else {
			label = translate.apply(status.state() == PartnerState.PINGING ? "nova.menu.pinging" : "nova.menu.offline");
			bars = 0;
		}

		int barsWidth = 12;
		for (int bar = 0; bar < 4; bar++) {
			int barX = right - barsWidth + bar * 3 + 1;
			canvas.fill(barX, y + 6 - (2 + bar), barX + 2, y + 6, bar < bars ? NovaDraw.ONLINE : 0x40FFFFFF);
		}
		int labelWidth = NovaDraw.smallWidth(canvas, label, 0);
		NovaDraw.smallText(canvas, label, right - barsWidth - 3 - labelWidth, y + 0.5F, bars > 0 ? NovaDraw.SOFT : NovaDraw.MUTED, 0);
		return barsWidth + 3 + labelWidth;
	}

	static void partnerIcon(NovaCanvas canvas, NovaContent.IconStyle style, int x, int y) {
		int seed = style.ordinal() * 97 + 13;
		for (int cell = 0; cell < 64; cell++) {
			int cellX = cell % 8;
			int cellY = cell / 8;
			float roll = NovaDraw.hash(seed + cell);
			int color;
			if (style == NovaContent.IconStyle.ICE) {
				color = new int[] {0xFFA9CDF7, 0xFF7FB1EF, 0xFFCFE3FB, 0xFF5F97E0}[(int) (roll * 4)];
			} else if (style == NovaContent.IconStyle.ARENA) {
				color = (cellX + cellY) % 3 == 0 && roll > 0.4F ? 0xFF9AB8EA : roll > 0.55F ? 0xFF1A2442 : roll > 0.25F ? 0xFF3D5FA8 : 0xFF9AB8EA;
			} else if (cellY < 2 || (cellY == 2 && roll > 0.5F)) {
				color = roll > 0.5F ? 0xFF5FA83A : 0xFF4C9230;
			} else {
				color = roll > 0.7F ? 0xFF5C3D24 : roll > 0.3F ? 0xFF79522F : 0xFF8B6139;
			}
			canvas.fill(x + cellX * 3, y + cellY * 3, x + cellX * 3 + 3, y + cellY * 3 + 3, color);
		}
	}
}
