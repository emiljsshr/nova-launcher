package gg.nova.core.social;

import gg.nova.core.cosmetic.Json;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Verbindung zum Nova Launcher (src-tauri/src/bridge.rs): lokaler TCP-Socket, eine JSON-Nachricht pro Zeile.
 * Der Launcher übergibt Adresse und Token mit {@code -Dnova.bridge=127.0.0.1:<port>} und
 * {@code -Dnova.bridge.token=…}; ohne diese Angaben (z. B. in Lunar) bleibt die Verbindung aus.
 * Eingehende Nachrichten sammelt ein Hintergrund-Thread, verarbeitet werden sie im Spieltakt ({@link #poll}).
 */
public final class NovaBridge {
	private static final Charset UTF8 = Charset.forName("UTF-8");
	private static final long RETRY_MILLIS = 5000L;
	private static final ConcurrentLinkedQueue<Map<String, Object>> INBOX = new ConcurrentLinkedQueue<Map<String, Object>>();
	private static volatile Writer out;
	private static boolean started;

	private NovaBridge() {
	}

	public static synchronized void start() {
		final String address = System.getProperty("nova.bridge");
		final String token = System.getProperty("nova.bridge.token", "");
		if (started || address == null || !address.contains(":")) {
			return;
		}
		started = true;
		Thread thread = new Thread(new Runnable() {
			@Override
			public void run() {
				String host = address.substring(0, address.lastIndexOf(':'));
				int port = Integer.parseInt(address.substring(address.lastIndexOf(':') + 1));
				while (true) {
					try {
						connect(host, port, token);
					} catch (IOException | RuntimeException error) {
						// Launcher noch nicht bereit oder geschlossen – später erneut
					}
					out = null;
					try {
						Thread.sleep(RETRY_MILLIS);
					} catch (InterruptedException error) {
						return;
					}
				}
			}
		}, "Nova launcher bridge");
		thread.setDaemon(true);
		thread.start();
	}

	private static void connect(String host, int port, String token) throws IOException {
		Socket socket = new Socket(host, port);
		try {
			Writer writer = new OutputStreamWriter(socket.getOutputStream(), UTF8);
			writer.write(token + "\n");
			writer.flush();
			out = writer;
			BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), UTF8));
			String line;
			while ((line = reader.readLine()) != null) {
				try {
					INBOX.add(Json.object(Json.parse(line)));
				} catch (RuntimeException error) {
					// Kaputte Zeile überspringen
				}
			}
		} finally {
			socket.close();
		}
	}

	public static boolean connected() {
		return out != null;
	}

	/** Schickt eine fertige JSON-Nachricht an den Launcher (ohne Verbindung passiert nichts). */
	public static void send(String json) {
		Writer writer = out;
		if (writer == null) {
			return;
		}
		try {
			synchronized (writer) {
				writer.write(json + "\n");
				writer.flush();
			}
		} catch (IOException error) {
			out = null;
		}
	}

	/** Nächste Nachricht vom Launcher oder null. */
	static Map<String, Object> poll() {
		return INBOX.poll();
	}
}
