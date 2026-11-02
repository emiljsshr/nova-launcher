package gg.nova.core.social;

import gg.nova.core.cosmetic.Json;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Freunde und Chat im Spiel. Der Launcher ist mit dem Nova-Server verbunden und reicht dessen Nachrichten
 * über {@link NovaBridge} durch (gleiches Format wie server/src/app.js); was das Spiel sendet, leitet er weiter.
 * Die Versions-Schicht ruft einmal pro Spieltakt {@link #tick} auf und zeigt Chatzeilen über {@link ChatOutput}.
 */
public final class NovaSocial {
	/** Gibt eine Zeile im Minecraft-Chat aus (Farbcodes mit §). */
	public interface ChatOutput {
		void line(String text);
	}

	public static final class Friend {
		public final String id;
		public final String name;
		public final boolean online;
		/** launcher, menu, singleplayer oder multiplayer (null wenn offline). */
		public final String state;
		public final String server;
		public final String version;

		Friend(String id, String name, boolean online, Map<String, Object> presence) {
			this.id = id;
			this.name = name;
			this.online = online;
			this.state = presence == null ? null : Json.string(presence.get("state"), null);
			this.server = presence == null ? null : Json.string(presence.get("server"), null);
			this.version = presence == null ? null : Json.string(presence.get("version"), null);
		}

		/** Status wie im Launcher, z. B. "Spielt auf play.example.net · 1.21.4". */
		public String status() {
			if (!this.online) {
				return "Offline";
			}
			String version = this.version != null ? " · " + this.version : "";
			if ("multiplayer".equals(this.state)) {
				return "Spielt auf " + (this.server != null ? this.server : "einem Server") + version;
			}
			if ("singleplayer".equals(this.state)) {
				return "Spielt Einzelspieler" + version;
			}
			if ("menu".equals(this.state)) {
				return "Im Hauptmenü" + version;
			}
			return "Im Launcher";
		}

		/** Spielt gerade (nicht nur im Launcher)? */
		public boolean playing() {
			return this.online && this.state != null && !"launcher".equals(this.state);
		}
	}

	public static final class Request {
		public final String id;
		public final String name;

		Request(String id, String name) {
			this.id = id;
			this.name = name;
		}
	}

	public static final class Message {
		public final String id;
		public final String from;
		public final String to;
		public final String text;
		public final long at;

		Message(Map<String, Object> source) {
			this.id = Json.string(source.get("id"), "");
			this.from = Json.string(source.get("from"), "");
			this.to = Json.string(source.get("to"), "");
			this.text = plain(Json.string(source.get("text"), ""));
			Object at = source.get("at");
			this.at = at instanceof Number ? ((Number) at).longValue() : 0L;
		}
	}

	private static ChatOutput chat = text -> {
	};
	private static String selfId;
	private static List<Friend> friends = Collections.emptyList();
	private static List<Request> incoming = Collections.emptyList();
	private static List<Request> outgoing = Collections.emptyList();
	private static final Map<String, Integer> UNREAD = new HashMap<String, Integer>();
	private static final Map<String, List<Message>> CONVERSATIONS = new HashMap<String, List<Message>>();
	/** Freund, dessen Chat gerade im Nova-Menü offen ist (dann keine Chatzeile im Spiel). */
	private static String openChat;
	private static String lastPresence;
	private static String notice;
	private static long noticeUntil;

	private NovaSocial() {
	}

	public static void init(ChatOutput output) {
		chat = output;
		NovaBridge.start();
	}

	/**
	 * Einmal pro Spieltakt: Nachrichten vom Launcher verarbeiten und den eigenen Status melden.
	 *
	 * @param state  menu, singleplayer oder multiplayer
	 * @param server Adresse im Mehrspieler, sonst null
	 */
	public static void tick(String state, String server) {
		Map<String, Object> message;
		while ((message = NovaBridge.poll()) != null) {
			handle(message);
		}
		String presence = "{\"type\":\"presence\",\"state\":" + Json.quote(state)
			+ (server != null ? ",\"server\":" + Json.quote(server) : "") + "}";
		if (NovaBridge.connected() && !presence.equals(lastPresence)) {
			lastPresence = presence;
			NovaBridge.send(presence);
		} else if (!NovaBridge.connected()) {
			lastPresence = null;
		}
	}

	private static void handle(Map<String, Object> message) {
		String type = Json.string(message.get("type"), "");
		if ("ready".equals(type)) {
			selfId = Json.string(Json.object(message.get("self")).get("id"), null);
		} else if ("social".equals(type)) {
			List<Friend> next = new ArrayList<Friend>();
			for (Object item : Json.list(message.get("friends"))) {
				Map<String, Object> friend = Json.object(item);
				next.add(new Friend(Json.string(friend.get("id"), ""), plain(Json.string(friend.get("name"), "?")),
					Boolean.TRUE.equals(friend.get("online")), presenceOf(friend)));
			}
			friends = Collections.unmodifiableList(next);
			incoming = requests(message.get("incoming"));
			outgoing = requests(message.get("outgoing"));
			UNREAD.clear();
			for (Map.Entry<String, Object> entry : Json.object(message.get("unread")).entrySet()) {
				UNREAD.put(entry.getKey(), (int) Json.number(entry.getValue(), 0.0F));
			}
		} else if ("presence".equals(type)) {
			String id = Json.string(message.get("id"), "");
			boolean online = Boolean.TRUE.equals(message.get("online"));
			List<Friend> next = new ArrayList<Friend>();
			for (Friend friend : friends) {
				if (friend.id.equals(id)) {
					if (online && !friend.online) {
						chat.line("§9[Nova] §b" + friend.name + " §7ist jetzt online.");
					}
					friend = new Friend(friend.id, friend.name, online, presenceOf(message));
				}
				next.add(friend);
			}
			friends = Collections.unmodifiableList(next);
		} else if ("chat.message".equals(type)) {
			Message chatMessage = new Message(Json.object(message.get("message")));
			String other = chatMessage.from.equals(selfId) ? chatMessage.to : chatMessage.from;
			List<Message> history = CONVERSATIONS.get(other);
			if (history != null) {
				history.add(chatMessage);
			}
			if (!chatMessage.from.equals(selfId)) {
				if (other.equals(openChat)) {
					markRead(other);
				} else {
					UNREAD.put(other, unread(other) + 1);
					chat.line("§9[Nova] §b" + nameOf(other) + "§7: §f" + chatMessage.text + " §8(Rechts-Shift)");
				}
			}
		} else if ("chat.history".equals(type)) {
			List<Message> history = new ArrayList<Message>();
			for (Object item : Json.list(message.get("messages"))) {
				history.add(new Message(Json.object(item)));
			}
			CONVERSATIONS.put(Json.string(message.get("with"), ""), history);
		} else if ("chat.read".equals(type)) {
			UNREAD.remove(Json.string(message.get("with"), ""));
		} else if ("notice".equals(type)) {
			notice = Json.string(message.get("text"), null);
			noticeUntil = System.currentTimeMillis() + 5000L;
		}
	}

	/** Ohne §-Farbcodes, damit Namen und Nachrichten die Chat-Formatierung nicht verstellen. */
	private static String plain(String text) {
		return text.replace("§", "");
	}

	private static Map<String, Object> presenceOf(Map<String, Object> source) {
		Object presence = source.get("presence");
		return presence instanceof Map ? Json.object(presence) : null;
	}

	private static List<Request> requests(Object value) {
		List<Request> result = new ArrayList<Request>();
		for (Object item : Json.list(value)) {
			Map<String, Object> request = Json.object(item);
			result.add(new Request(Json.string(request.get("id"), ""), plain(Json.string(request.get("name"), "?"))));
		}
		return Collections.unmodifiableList(result);
	}

	// Abfragen für das Nova-Menü

	/** Läuft das Spiel mit Nova Launcher, der beim Nova-Server angemeldet ist? */
	public static boolean available() {
		return NovaBridge.connected() && selfId != null;
	}

	/** Gestartet über den Nova Launcher (Verbindung vorhanden), auch wenn der Server noch fehlt? */
	public static boolean bridged() {
		return NovaBridge.connected();
	}

	public static List<Friend> friends() {
		return friends;
	}

	public static List<Request> incoming() {
		return incoming;
	}

	public static List<Request> outgoing() {
		return outgoing;
	}

	public static int unread(String friendId) {
		Integer count = UNREAD.get(friendId);
		return count == null ? 0 : count;
	}

	public static int unreadTotal() {
		int total = 0;
		for (int count : UNREAD.values()) {
			total += count;
		}
		return total;
	}

	/** Verlauf mit einem Freund, oder null solange er lädt. */
	public static List<Message> conversation(String friendId) {
		return CONVERSATIONS.get(friendId);
	}

	public static boolean mine(Message message) {
		return message.from.equals(selfId);
	}

	/** Hinweis des Servers (z. B. "Anfrage gesendet"), 5 Sekunden lang, sonst null. */
	public static String notice() {
		return System.currentTimeMillis() < noticeUntil ? notice : null;
	}

	public static String nameOf(String id) {
		for (Friend friend : friends) {
			if (friend.id.equals(id)) {
				return friend.name;
			}
		}
		return "?";
	}

	// Aktionen

	/** Öffnet den Chat im Menü: Verlauf laden, als gelesen markieren. Null schließt ihn. */
	public static void openChat(String friendId) {
		openChat = friendId;
		if (friendId != null) {
			NovaBridge.send("{\"type\":\"chat.history\",\"with\":" + Json.quote(friendId) + "}");
			markRead(friendId);
		}
	}

	private static void markRead(String friendId) {
		UNREAD.remove(friendId);
		NovaBridge.send("{\"type\":\"chat.read\",\"with\":" + Json.quote(friendId) + "}");
	}

	public static void sendMessage(String friendId, String text) {
		NovaBridge.send("{\"type\":\"chat.send\",\"to\":" + Json.quote(friendId) + ",\"text\":" + Json.quote(text) + "}");
	}

	public static void requestFriend(String name) {
		NovaBridge.send("{\"type\":\"friend.request\",\"name\":" + Json.quote(name) + "}");
	}

	public static void acceptFriend(String id) {
		NovaBridge.send("{\"type\":\"friend.accept\",\"id\":" + Json.quote(id) + "}");
	}

	public static void declineFriend(String id) {
		NovaBridge.send("{\"type\":\"friend.decline\",\"id\":" + Json.quote(id) + "}");
	}

	/** Meldet im Spiel geänderte Cosmetics an den Launcher, damit er sie fürs nächste Mal speichert. */
	public static void sendLoadout(List<String> ids) {
		StringBuilder list = new StringBuilder();
		for (String id : ids) {
			list.append(list.length() > 0 ? "," : "").append(Json.quote(id));
		}
		NovaBridge.send("{\"type\":\"loadout\",\"ids\":[" + list + "]}");
	}
}
