package gg.nova.client;

import gg.nova.core.NovaContent;
import gg.nova.core.menu.NovaServerList;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.network.MultiplayerServerListPinger;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

/**
 * Pingt die Partner-Server wie die Vanilla-Serverliste – DNS-Auflösung nicht im Render-Thread.
 * Bis 1.20.4 hat {@link ServerInfo} keinen Status: er wird aus Spielerzahl, Fehlertext und Zeit abgeleitet.
 */
public final class PartnerPings {
	private static final long TIMEOUT = 8000L;
	private static final String CANNOT_CONNECT = Text.translatable("multiplayer.status.cannot_connect").getString();

	private final MultiplayerServerListPinger pinger = new MultiplayerServerListPinger();
	private final List<ServerInfo> servers = new ArrayList<>();
	private final Map<ServerInfo, Boolean> failed = new HashMap<>();
	private long startedAt;
	private boolean started;

	public PartnerPings() {
		for (NovaContent.Partner partner : NovaContent.PARTNERS) {
			this.servers.add(new ServerInfo(partner.name(), partner.address(), ServerInfo.ServerType.OTHER));
		}
	}

	public ServerInfo server(int index) {
		return this.servers.get(index);
	}

	public void start() {
		if (this.started) {
			return;
		}
		this.started = true;
		this.startedAt = Util.getMeasuringTimeMs();
		for (ServerInfo info : this.servers) {
			info.players = null;
			this.failed.put(info, false);
			Util.getIoWorkerExecutor().execute(() -> {
				try {
					this.pinger.add(info, () -> {
					});
				} catch (UnknownHostException exception) {
					this.failed.put(info, true);
				} catch (Exception exception) {
					NovaClient.LOGGER.debug("Ping an {} fehlgeschlagen", info.address, exception);
					this.failed.put(info, true);
				}
			});
		}
	}

	public void tick() {
		this.pinger.tick();
	}

	/** Beim Verlassen des Screens; beim Zurückkehren wird neu gepingt. */
	public void cancel() {
		this.pinger.cancel();
		this.started = false;
	}

	public List<NovaServerList.PartnerStatus> statuses() {
		return this.servers.stream().map(this::status).toList();
	}

	private NovaServerList.PartnerStatus status(ServerInfo info) {
		if (info.players != null && info.ping >= 0L) {
			return new NovaServerList.PartnerStatus(NovaServerList.PartnerState.ONLINE, info.players.online(), info.ping);
		}
		boolean error = Boolean.TRUE.equals(this.failed.get(info))
			|| info.label != null && CANNOT_CONNECT.equals(info.label.getString())
			|| Util.getMeasuringTimeMs() - this.startedAt > TIMEOUT;
		return error ? NovaServerList.PartnerStatus.OFFLINE : NovaServerList.PartnerStatus.PINGING;
	}
}
