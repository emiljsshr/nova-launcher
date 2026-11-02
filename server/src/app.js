import { randomBytes } from "node:crypto";
import { createServer } from "node:http";
import { readFile } from "node:fs/promises";
import { WebSocketServer } from "ws";
import { MESSAGE_MAX_LENGTH } from "./store.js";

const CHALLENGE_SECONDS = 120;
const NAME_PATTERN = /^[A-Za-z0-9_]{1,16}$/;
// Höchstens so viele Nachrichten pro Spieler in 10 Sekunden
const MESSAGE_BURST = 8;

const PRESENCE_STATES = new Set(["launcher", "menu", "singleplayer", "multiplayer"]);

/**
 * HTTP- und WebSocket-Server.
 *  - GET  /api/news             News-Banner und Patchnotes (aus newsFile)
 *  - POST /api/auth/challenge   Zufällige serverId für die Mojang-Anmeldung
 *  - POST /api/auth/login       {name, serverId}: prüft bei Mojang, ob der Spieler beigetreten ist → Token
 *  - POST /api/auth/offline     {name}: nur mit allowOffline (lokale Tests mit Offline-Konten)
 *  - WS   /ws                   erste Nachricht {type:"auth", token}, danach Freunde, Status und Chat
 */
export function createApp({ store, newsFile, sessionUrl, allowOffline = false, log = console.log }) {
  const challenges = new Map();
  // Spieler-ID → offene Verbindungen und aktueller Status
  const sockets = new Map();
  const presence = new Map();

  // HTTP

  const server = createServer(async (request, response) => {
    response.setHeader("Access-Control-Allow-Origin", "*");
    response.setHeader("Access-Control-Allow-Headers", "Content-Type");
    response.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
    if (request.method === "OPTIONS") return send(response, 204);

    try {
      const url = new URL(request.url, "http://localhost");
      if (request.method === "GET" && url.pathname === "/api/health") {
        return send(response, 200, { ok: true });
      }
      if (request.method === "GET" && url.pathname === "/api/news") {
        return send(response, 200, JSON.parse(await readFile(newsFile, "utf8")));
      }
      if (request.method === "POST" && url.pathname === "/api/auth/challenge") {
        const serverId = randomBytes(20).toString("hex");
        challenges.set(serverId, Date.now() + CHALLENGE_SECONDS * 1000);
        return send(response, 200, { serverId });
      }
      if (request.method === "POST" && url.pathname === "/api/auth/login") {
        const { name, serverId } = await readJson(request);
        if (!NAME_PATTERN.test(name ?? "") || !takeChallenge(serverId)) {
          return send(response, 400, { error: "Anmeldung abgelaufen, bitte erneut versuchen." });
        }
        const profile = await hasJoined(name, serverId);
        if (!profile) return send(response, 401, { error: "Minecraft-Konto konnte nicht bestätigt werden." });
        return send(response, 200, login(formatUuid(profile.id), profile.name));
      }
      if (request.method === "POST" && url.pathname === "/api/auth/offline") {
        const { name } = await readJson(request);
        if (!allowOffline) return send(response, 403, { error: "Offline-Konten sind auf diesem Server nicht erlaubt." });
        if (!NAME_PATTERN.test(name ?? "")) return send(response, 400, { error: "Ungültiger Name." });
        return send(response, 200, login(`offline-${name.toLowerCase()}`, name));
      }
      send(response, 404, { error: "Nicht gefunden." });
    } catch (error) {
      log(`HTTP-Fehler: ${error.message}`);
      send(response, 500, { error: "Serverfehler." });
    }
  });

  function takeChallenge(serverId) {
    const now = Date.now();
    for (const [id, expires] of challenges) if (expires < now) challenges.delete(id);
    return typeof serverId === "string" && challenges.delete(serverId);
  }

  async function hasJoined(name, serverId) {
    const url = `${sessionUrl}/session/minecraft/hasJoined?username=${encodeURIComponent(name)}&serverId=${serverId}`;
    const reply = await fetch(url);
    if (reply.status !== 200) return null;
    return reply.json();
  }

  function login(id, name) {
    const user = store.upsertUser(id, name);
    log(`Angemeldet: ${user.name}`);
    return { token: store.createToken(id), user: { id: user.id, name: user.name } };
  }

  // WebSocket

  const wss = new WebSocketServer({ noServer: true, maxPayload: 16 * 1024 });
  server.on("upgrade", (request, socket, head) => {
    if (new URL(request.url, "http://localhost").pathname !== "/ws") return socket.destroy();
    wss.handleUpgrade(request, socket, head, (ws) => accept(ws));
  });

  function accept(ws) {
    let user = null;
    let burst = [];
    const authTimer = setTimeout(() => ws.close(4001, "auth"), 10_000);

    ws.on("message", (raw) => {
      let message;
      try {
        message = JSON.parse(raw.toString());
      } catch {
        return;
      }
      if (!user) {
        user = message.type === "auth" ? store.userForToken(message.token) : null;
        clearTimeout(authTimer);
        if (!user) return ws.close(4001, "auth");
        connect(user, ws);
        return;
      }
      if (message.type === "chat.send") {
        const now = Date.now();
        burst = burst.filter((at) => now - at < 10_000);
        if (burst.length >= MESSAGE_BURST) return emit(ws, { type: "notice", text: "Nicht so schnell." });
        burst.push(now);
      }
      handle(user, ws, message);
    });

    ws.on("close", () => {
      clearTimeout(authTimer);
      if (user) disconnect(user, ws);
    });
  }

  function connect(user, ws) {
    const set = sockets.get(user.id) ?? new Set();
    const wasOnline = set.size > 0;
    set.add(ws);
    sockets.set(user.id, set);
    if (!presence.has(user.id)) presence.set(user.id, { state: "launcher" });
    emit(ws, { type: "ready", self: { id: user.id, name: user.name } });
    emit(ws, socialSnapshot(user.id));
    if (!wasOnline) broadcastPresence(user.id);
  }

  function disconnect(user, ws) {
    const set = sockets.get(user.id);
    if (!set) return;
    set.delete(ws);
    if (set.size === 0) {
      sockets.delete(user.id);
      presence.delete(user.id);
      broadcastPresence(user.id);
    }
  }

  function handle(user, ws, message) {
    const me = user.id;
    switch (message.type) {
      case "presence": {
        if (!PRESENCE_STATES.has(message.state)) return;
        presence.set(me, {
          state: message.state,
          version: clip(message.version, 32),
          server: message.state === "multiplayer" ? clip(message.server, 80) : undefined,
        });
        broadcastPresence(me);
        return;
      }
      case "friend.request": {
        const target = typeof message.name === "string" ? store.userByName(message.name.trim()) : null;
        const result = target ? store.request(me, target.id) : "unknown";
        const notices = {
          self: "Du kannst dich nicht selbst hinzufügen.",
          unknown: `${message.name} hat Nova noch nicht benutzt.`,
          already: `${target?.name} ist schon dein Freund.`,
          pending: `Anfrage an ${target?.name} läuft bereits.`,
          sent: `Anfrage an ${target?.name} gesendet.`,
          accepted: `Du und ${target?.name} seid jetzt Freunde.`,
        };
        emit(ws, { type: "notice", text: notices[result] });
        if (result === "sent" || result === "accepted") refreshSocial(me, target.id);
        return;
      }
      case "friend.accept":
        if (store.accept(me, message.id)) refreshSocial(me, message.id);
        return;
      case "friend.decline":
      case "friend.cancel":
        if (store.dropRequest(me, message.id)) refreshSocial(me, message.id);
        return;
      case "friend.remove":
        if (store.removeFriend(me, message.id)) refreshSocial(me, message.id);
        return;
      case "chat.send": {
        const text = typeof message.text === "string" ? message.text.trim().slice(0, MESSAGE_MAX_LENGTH) : "";
        if (!text) return;
        if (!store.areFriends(me, message.to)) {
          return emit(ws, { type: "notice", text: "Nachrichten gehen nur an Freunde." });
        }
        const sent = store.addMessage(me, message.to, text);
        for (const id of [me, message.to]) toUser(id, { type: "chat.message", message: sent });
        return;
      }
      case "chat.history":
        if (store.areFriends(me, message.with)) {
          emit(ws, { type: "chat.history", with: message.with, messages: store.history(me, message.with) });
        }
        return;
      case "chat.read":
        store.markRead(me, message.with);
        toUser(me, { type: "chat.read", with: message.with });
        return;
      default:
    }
  }

  function socialSnapshot(id) {
    const person = (otherId) => {
      const other = store.user(otherId);
      return { id: otherId, name: other?.name ?? "?" };
    };
    return {
      type: "social",
      friends: store.friendsOf(id).map((friendId) => ({
        ...person(friendId),
        online: sockets.has(friendId),
        presence: presence.get(friendId) ?? null,
      })),
      incoming: store.incoming(id).map((request) => ({ ...person(request.from), at: request.at })),
      outgoing: store.outgoing(id).map((request) => ({ ...person(request.to), at: request.at })),
      unread: store.unread(id),
    };
  }

  function refreshSocial(...ids) {
    for (const id of ids) toUser(id, socialSnapshot(id));
  }

  function broadcastPresence(id) {
    const update = { type: "presence", id, online: sockets.has(id), presence: presence.get(id) ?? null };
    toUser(id, update);
    for (const friendId of store.friendsOf(id)) toUser(friendId, update);
  }

  function toUser(id, message) {
    for (const ws of sockets.get(id) ?? []) emit(ws, message);
  }

  return {
    server,
    close() {
      for (const ws of wss.clients) ws.terminate();
      wss.close();
      server.close();
      store.flush();
    },
  };
}

function emit(ws, message) {
  if (ws.readyState === ws.OPEN) ws.send(JSON.stringify(message));
}

function send(response, status, body) {
  response.writeHead(status, body === undefined ? {} : { "Content-Type": "application/json; charset=utf-8" });
  response.end(body === undefined ? undefined : JSON.stringify(body));
}

async function readJson(request) {
  let body = "";
  for await (const chunk of request) {
    body += chunk;
    if (body.length > 4096) throw new Error("Anfrage zu groß");
  }
  try {
    return JSON.parse(body || "{}");
  } catch {
    return {};
  }
}

function clip(value, length) {
  return typeof value === "string" ? value.slice(0, length) : undefined;
}

// Mojang liefert UUIDs ohne Bindestriche
function formatUuid(id) {
  return id.length === 32 ? `${id.slice(0, 8)}-${id.slice(8, 12)}-${id.slice(12, 16)}-${id.slice(16, 20)}-${id.slice(20)}` : id;
}
