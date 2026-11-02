package gg.nova.client;

import gg.nova.core.NovaContent;
import gg.nova.core.menu.NovaServerList;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.client.network.MultiplayerServerListPinger;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.util.Formatting;

/**
 * Pingt die Partner-Server wie die Vanilla-Serverliste – DNS-Auflösung nicht im Render-Thread.
 * In 1.8.9 gibt es weder Status noch Spieler-Objekt: beides wird aus Ping und Spieler-Text abgeleitet.
 */
public final class PartnerPings {
	private static final long TIMEOUT = 8000L;
	private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(runnable -> {
		Thread thread = new Thread(runnable, "Nova Partner Ping");
		thread.setDaemon(true);
		return thread;
	});

	private final MultiplayerServerListPinger pinger = new MultiplayerServerListPinger();
	private final List<ServerInfo> servers = new ArrayList<ServerInfo>();
	private final Map<ServerInfo, Boolean> failed = new ConcurrentHashMap<ServerInfo, Boolean>();
	private long startedAt;
	private boolean started;

	public PartnerPings() {
		for (NovaContent.Partner partner : NovaContent.PARTNERS) {
			this.servers.add(new ServerInfo(partner.name(), partner.address(), false));
		}
	}

	/** Pingt beliebige Server (z. B. die gespeicherte Serverliste) statt der Partner. */
	public PartnerPings(List<ServerInfo> servers) {
		this.servers.addAll(servers);
	}

	public ServerInfo server(int index) {
		return this.servers.get(index);
	}

	public void start() {
		if (this.started) {
			return;
		}
		this.started = true;
		this.startedAt = System.currentTimeMillis();
		for (final ServerInfo info : this.servers) {
			info.playerCountLabel = null;
			this.failed.put(info, false);
			EXECUTOR.execute(() -> {
				try {
					this.pinger.add(info);
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
		List<NovaServerList.PartnerStatus> statuses = new ArrayList<NovaServerList.PartnerStatus>();
		for (ServerInfo info : this.servers) {
			statuses.add(this.status(info));
		}
		return statuses;
	}

	private NovaServerList.PartnerStatus status(ServerInfo info) {
		int players = onlinePlayers(info.playerCountLabel);
		if (players >= 0 && info.ping >= 0L) {
			return new NovaServerList.PartnerStatus(NovaServerList.PartnerState.ONLINE, players, info.ping);
		}
		boolean error = Boolean.TRUE.equals(this.failed.get(info)) || System.currentTimeMillis() - this.startedAt > TIMEOUT;
		return error ? NovaServerList.PartnerStatus.OFFLINE : NovaServerList.PartnerStatus.PINGING;
	}

	/** "§7123§8/§7500" → 123; -1 wenn (noch) unbekannt. */
	private static int onlinePlayers(String label) {
		if (label == null) {
			return -1;
		}
		String plain = Formatting.strip(label);
		int slash = plain.indexOf('/');
		if (slash <= 0) {
			return -1;
		}
		try {
			return Integer.parseInt(plain.substring(0, slash).trim());
		} catch (NumberFormatException exception) {
			return -1;
		}
	}
}
