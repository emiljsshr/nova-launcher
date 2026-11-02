package gg.nova.client;

import gg.nova.core.NovaContent;
import gg.nova.core.menu.NovaServerList;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.network.MultiplayerServerListPinger;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.util.Util;

/** Pingt die Partner-Server wie die Vanilla-Serverliste – DNS-Auflösung nicht im Render-Thread. */
public final class PartnerPings {
	private final MultiplayerServerListPinger pinger = new MultiplayerServerListPinger();
	private final List<ServerInfo> servers = new ArrayList<>();
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
		for (ServerInfo info : this.servers) {
			info.setStatus(ServerInfo.Status.PINGING);
			Util.getIoWorkerExecutor().execute(() -> {
				try {
					this.pinger.add(info, () -> {
					}, () -> {
					});
				} catch (UnknownHostException exception) {
					info.setStatus(ServerInfo.Status.UNREACHABLE);
				} catch (Exception exception) {
					NovaClient.LOGGER.debug("Ping an {} fehlgeschlagen", info.address, exception);
					info.setStatus(ServerInfo.Status.UNREACHABLE);
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
		return this.servers.stream().map(PartnerPings::status).toList();
	}

	private static NovaServerList.PartnerStatus status(ServerInfo info) {
		return switch (info.getStatus()) {
			case SUCCESSFUL -> info.players != null
				? new NovaServerList.PartnerStatus(NovaServerList.PartnerState.ONLINE, info.players.online(), info.ping)
				: NovaServerList.PartnerStatus.OFFLINE;
			case INITIAL, PINGING -> NovaServerList.PartnerStatus.PINGING;
			default -> NovaServerList.PartnerStatus.OFFLINE;
		};
	}
}
