import { listen } from "@tauri-apps/api/event";
import { useCallback, useEffect, useRef, useState } from "react";
import { errorMessage, inTauri, launcher, type Account } from "../lib/launcher";

// Verbindung zum Nova-Server (server/): Freunde, Online-Status und Chat.
// Läuft das Spiel, leitet der Hook außerdem zwischen Server und Nova-Mod weiter (src-tauri/src/bridge.rs).

export type PresenceState = "launcher" | "menu" | "singleplayer" | "multiplayer";

export type Presence = {
  state: PresenceState;
  version?: string;
  server?: string;
};

export type Friend = {
  id: string;
  name: string;
  online: boolean;
  presence: Presence | null;
};

export type FriendRequest = {
  id: string;
  name: string;
  at: number;
};

export type ChatMessage = {
  id: string;
  from: string;
  to: string;
  text: string;
  at: number;
};

export type SocialStatus = "offline" | "connecting" | "online" | "unavailable";

type ServerMessage =
  | { type: "ready"; self: { id: string; name: string } }
  | { type: "social"; friends: Friend[]; incoming: FriendRequest[]; outgoing: FriendRequest[]; unread: Record<string, number> }
  | { type: "presence"; id: string; online: boolean; presence: Presence | null }
  | { type: "chat.message"; message: ChatMessage }
  | { type: "chat.history"; with: string; messages: ChatMessage[] }
  | { type: "chat.read"; with: string }
  | { type: "notice"; text: string };

// Nachrichten der Mod, die unverändert an den Server gehen
const RELAYED_FROM_GAME = new Set([
  "friend.request",
  "friend.accept",
  "friend.decline",
  "friend.cancel",
  "friend.remove",
  "chat.send",
  "chat.history",
  "chat.read",
]);

const RECONNECT_MIN = 3000;
const RECONNECT_MAX = 30000;

export function presenceLabel(friend: Pick<Friend, "online" | "presence">) {
  if (!friend.online) return "Offline";
  const presence = friend.presence;
  const version = presence?.version ? ` · ${presence.version}` : "";
  switch (presence?.state) {
    case "multiplayer":
      return `Spielt auf ${presence.server ?? "einem Server"}${version}`;
    case "singleplayer":
      return `Spielt Einzelspieler${version}`;
    case "menu":
      return `Im Hauptmenü${version}`;
    default:
      return "Im Launcher";
  }
}

export function useSocial({
  account,
  gameRunning,
  gameVersion,
  shareServer,
  onNotify,
  onMessage,
  onGameLoadout,
}: {
  account: Account | null;
  gameRunning: boolean;
  gameVersion: string | null;
  // Aus: Freunde sehen "Mehrspieler", aber nicht welcher Server
  shareServer: boolean;
  onNotify: (message: string) => void;
  // Neue Nachricht eines Freundes (nicht die eigenen)
  onMessage: (message: ChatMessage, from: Friend | undefined) => void;
  // Im Spiel geänderte Cosmetics
  onGameLoadout: (ids: string[]) => void;
}) {
  const [status, setStatus] = useState<SocialStatus>("offline");
  const [self, setSelf] = useState<{ id: string; name: string } | null>(null);
  const [friends, setFriends] = useState<Friend[]>([]);
  const [incoming, setIncoming] = useState<FriendRequest[]>([]);
  const [outgoing, setOutgoing] = useState<FriendRequest[]>([]);
  const [unread, setUnread] = useState<Record<string, number>>({});
  const [conversations, setConversations] = useState<Record<string, ChatMessage[]>>({});
  const [gamePresence, setGamePresence] = useState<Presence | null>(null);
  const [gameConnected, setGameConnected] = useState(false);

  const socketRef = useRef<WebSocket | null>(null);
  const latest = useRef({ onNotify, onMessage, onGameLoadout, friends, self, gameConnected });
  latest.current = { onNotify, onMessage, onGameLoadout, friends, self, gameConnected };
  // Letzter Stand für die Mod, falls sie sich später verbindet
  const snapshotRef = useRef<{ ready?: ServerMessage; social?: ServerMessage }>({});

  const send = useCallback((message: Record<string, unknown>) => {
    const socket = socketRef.current;
    if (socket?.readyState === WebSocket.OPEN) socket.send(JSON.stringify(message));
  }, []);

  const toGame = useCallback((message: unknown) => {
    if (latest.current.gameConnected) launcher.bridgeSend(message).catch(() => undefined);
  }, []);

  // Verbindung zum Nova-Server, solange ein Konto aktiv ist
  useEffect(() => {
    if (!inTauri || !account) {
      setStatus("offline");
      return;
    }

    let stopped = false;
    let retryTimer = 0;
    let delay = RECONNECT_MIN;

    const handle = (message: ServerMessage) => {
      switch (message.type) {
        case "ready":
          setSelf(message.self);
          snapshotRef.current.ready = message;
          break;
        case "social":
          setFriends(message.friends);
          setIncoming(message.incoming);
          setOutgoing(message.outgoing);
          setUnread(message.unread);
          snapshotRef.current.social = message;
          break;
        case "presence":
          setFriends((current) =>
            current.map((friend) =>
              friend.id === message.id ? { ...friend, online: message.online, presence: message.presence } : friend,
            ),
          );
          break;
        case "chat.message": {
          const { message: chat } = message;
          const selfId = latest.current.self?.id;
          const other = chat.from === selfId ? chat.to : chat.from;
          setConversations((current) =>
            current[other] ? { ...current, [other]: [...current[other], chat] } : current,
          );
          if (chat.from !== selfId) {
            setUnread((current) => ({ ...current, [other]: (current[other] ?? 0) + 1 }));
            latest.current.onMessage(chat, latest.current.friends.find((friend) => friend.id === other));
          }
          break;
        }
        case "chat.history":
          setConversations((current) => ({ ...current, [message.with]: message.messages }));
          break;
        case "chat.read":
          setUnread((current) => {
            const next = { ...current };
            delete next[message.with];
            return next;
          });
          break;
        case "notice":
          latest.current.onNotify(message.text);
          break;
      }
      toGame(message);
    };

    const connect = async () => {
      setStatus("connecting");
      try {
        const session = await launcher.socialLogin();
        if (stopped) return;
        const socket = new WebSocket(`${session.serverUrl.replace(/^http/, "ws")}/ws`);
        socketRef.current = socket;
        socket.onopen = () => {
          socket.send(JSON.stringify({ type: "auth", token: session.token }));
        };
        socket.onmessage = (event) => {
          delay = RECONNECT_MIN;
          setStatus("online");
          try {
            handle(JSON.parse(event.data));
          } catch {
            // Unbekanntes Format ignorieren
          }
        };
        socket.onclose = async (event) => {
          if (socketRef.current === socket) socketRef.current = null;
          if (stopped) return;
          // Token unbekannt (z. B. Server-Daten gelöscht): neu anmelden
          if (event.code === 4001) await launcher.socialLogout().catch(() => undefined);
          setStatus("unavailable");
          retryTimer = window.setTimeout(connect, delay);
          delay = Math.min(delay * 2, RECONNECT_MAX);
        };
      } catch (error) {
        if (stopped) return;
        setStatus("unavailable");
        console.warn("Nova-Server:", errorMessage(error));
        retryTimer = window.setTimeout(connect, delay);
        delay = Math.min(delay * 2, RECONNECT_MAX);
      }
    };

    connect();
    return () => {
      stopped = true;
      window.clearTimeout(retryTimer);
      socketRef.current?.close();
      socketRef.current = null;
      snapshotRef.current = {};
      setSelf(null);
      setFriends([]);
      setIncoming([]);
      setOutgoing([]);
      setUnread({});
      setConversations({});
      setStatus("offline");
    };
  }, [account?.id, toGame]);

  // Nachrichten der Nova-Mod
  useEffect(() => {
    if (!inTauri) return;
    const unlisten = listen<{ type: string; [key: string]: unknown }>("game-bridge", (event) => {
      const message = event.payload;
      if (message.type === "connected") {
        setGameConnected(true);
        latest.current.gameConnected = true;
        const { ready, social } = snapshotRef.current;
        if (ready) toGame(ready);
        if (social) toGame(social);
      } else if (message.type === "presence") {
        setGamePresence({
          state: message.state as PresenceState,
          server: typeof message.server === "string" ? message.server : undefined,
        });
      } else if (message.type === "loadout" && Array.isArray(message.ids)) {
        latest.current.onGameLoadout(message.ids.filter((id): id is string => typeof id === "string"));
      } else if (RELAYED_FROM_GAME.has(message.type)) {
        send(message);
      }
    });
    return () => {
      unlisten.then((stop) => stop());
    };
  }, [send, toGame]);

  useEffect(() => {
    if (!gameRunning) {
      setGameConnected(false);
      setGamePresence(null);
    }
  }, [gameRunning]);

  // Eigenen Status melden: Launcher, oder was die Mod aus dem Spiel meldet
  const presence: Presence = gameRunning
    ? { ...(gamePresence ?? { state: "menu" }), version: gameVersion ?? undefined }
    : { state: "launcher" };
  const shared = shareServer ? presence : { ...presence, server: undefined };
  const presenceKey = JSON.stringify(shared);
  useEffect(() => {
    if (status === "online") send({ type: "presence", ...JSON.parse(presenceKey) });
  }, [presenceKey, status, send]);

  const openChat = useCallback(
    (friendId: string) => {
      send({ type: "chat.history", with: friendId });
      send({ type: "chat.read", with: friendId });
    },
    [send],
  );

  return {
    status,
    self,
    friends,
    incoming,
    outgoing,
    unread,
    conversations,
    presence,
    gameConnected,
    openChat,
    markRead: (friendId: string) => send({ type: "chat.read", with: friendId }),
    sendMessage: (to: string, text: string) => send({ type: "chat.send", to, text }),
    requestFriend: (name: string) => send({ type: "friend.request", name }),
    acceptFriend: (id: string) => send({ type: "friend.accept", id }),
    declineFriend: (id: string) => send({ type: "friend.decline", id }),
    cancelRequest: (id: string) => send({ type: "friend.cancel", id }),
    removeFriend: (id: string) => send({ type: "friend.remove", id }),
  };
}

export type SocialApi = ReturnType<typeof useSocial>;
