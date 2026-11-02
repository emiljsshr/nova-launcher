package gg.nova.client;

import gg.nova.core.NovaContent;
import gg.nova.core.menu.NovaServerList;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import net.minecraft.server.network.EventLoopGroupHolder;
import net.minecraft.util.Util;

/** Pingt die Partner-Server wie die Vanilla-Serverliste – DNS-Auflösung nicht im Render-Thread. */
public final class PartnerPings {
	private final ServerStatusPinger pinger = new ServerStatusPinger();
	private final List<ServerData> servers = new ArrayList<>();
	private boolean started;

	public PartnerPings() {
		for (NovaContent.Partner partner : NovaContent.PARTNERS) {
			this.servers.add(new ServerData(partner.name(), partner.address(), ServerData.Type.OTHER));
		}
	}

	public ServerData server(int index) {
		return this.servers.get(index);
	}

	public void start() {
		if (this.started) {
			return;
		}
		this.started = true;
		for (ServerData data : this.servers) {
			data.setState(ServerData.State.PINGING);
			Util.ioPool().execute(() -> {
				try {
					this.pinger.pingServer(data, () -> {
					}, () -> data.setState(ServerData.State.SUCCESSFUL),
						EventLoopGroupHolder.remote(Minecraft.getInstance().options.useNativeTransport()));
				} catch (UnknownHostException exception) {
					data.setState(ServerData.State.UNREACHABLE);
				} catch (Exception exception) {
					NovaClient.LOGGER.debug("Ping an {} fehlgeschlagen", data.ip, exception);
					data.setState(ServerData.State.UNREACHABLE);
				}
			});
		}
	}

	public void tick() {
		this.pinger.tick();
	}

	/** Beim Verlassen des Screens; beim Zurückkehren wird neu gepingt. */
	public void cancel() {
		this.pinger.removeAll();
		this.started = false;
	}

	public List<NovaServerList.PartnerStatus> statuses() {
		return this.servers.stream().map(PartnerPings::status).toList();
	}

	private static NovaServerList.PartnerStatus status(ServerData data) {
		return switch (data.state()) {
			case SUCCESSFUL -> data.players != null
				? new NovaServerList.PartnerStatus(NovaServerList.PartnerState.ONLINE, data.players.online(), data.ping)
				: NovaServerList.PartnerStatus.OFFLINE;
			case INITIAL, PINGING -> NovaServerList.PartnerStatus.PINGING;
			default -> NovaServerList.PartnerStatus.OFFLINE;
		};
	}
}
