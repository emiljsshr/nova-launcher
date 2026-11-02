import assert from "node:assert/strict";
import { createServer } from "node:http";
import { after, before, test } from "node:test";
import { fileURLToPath } from "node:url";
import WebSocket from "ws";
import { createApp } from "../src/app.js";
import { Store } from "../src/store.js";

// Falscher Mojang-Sitzungsserver: beigetreten ist nur, wessen serverId der Test vorher freigegeben hat
const joined = new Map();
const mojang = createServer((request, response) => {
  const url = new URL(request.url, "http://localhost");
  const name = url.searchParams.get("username");
  if (joined.get(url.searchParams.get("serverId")) !== name) {
    response.writeHead(204);
    return response.end();
  }
  response.writeHead(200, { "Content-Type": "application/json" });
  response.end(JSON.stringify({ id: Buffer.from(name.padEnd(16, "_")).toString("hex").slice(0, 32), name }));
});

let app;
let base;

before(async () => {
  await new Promise((resolve) => mojang.listen(0, resolve));
  app = createApp({
    store: new Store(),
    newsFile: fileURLToPath(new URL("../data/news.json", import.meta.url)),
    sessionUrl: `http://localhost:${mojang.address().port}`,
    allowOffline: true,
    log: () => {},
  });
  await new Promise((resolve) => app.server.listen(0, resolve));
  base = `http://localhost:${app.server.address().port}`;
});

after(() => {
  app.close();
  mojang.close();
});

const post = async (path, body) => {
  const response = await fetch(base + path, { method: "POST", body: JSON.stringify(body) });
  return { status: response.status, body: await response.json() };
};

// Verbindung mit Posteingang: next(type) wartet auf die nächste Nachricht dieses Typs
async function connect(token) {
  const ws = new WebSocket(`${base.replace("http", "ws")}/ws`);
  const inbox = [];
  const waiting = [];
  ws.on("message", (raw) => {
    const message = JSON.parse(raw.toString());
    const index = waiting.findIndex((entry) => entry.type === message.type);
    if (index >= 0) waiting.splice(index, 1)[0].resolve(message);
    else inbox.push(message);
  });
  await new Promise((resolve) => ws.on("open", resolve));
  ws.send(JSON.stringify({ type: "auth", token }));
  return {
    send: (message) => ws.send(JSON.stringify(message)),
    next: (type) => {
      const index = inbox.findIndex((message) => message.type === type);
      if (index >= 0) return Promise.resolve(inbox.splice(index, 1)[0]);
      return new Promise((resolve) => waiting.push({ type, resolve }));
    },
    close: () => ws.close(),
    // Status eines bestimmten Spielers (der eigene kommt auch an)
    async presenceOf(id) {
      for (;;) {
        const update = await this.next("presence");
        if (update.id === id) return update;
      }
    },
  };
}

test("News werden ausgeliefert", async () => {
  const response = await fetch(`${base}/api/news`);
  const news = await response.json();
  assert.ok(news.slides.length > 0);
  assert.ok(news.patchNotes[0].version);
});

test("Anmeldung prüft bei Mojang und gibt nur bestätigten Spielern ein Token", async () => {
  const { body: challenge } = await post("/api/auth/challenge", {});
  const denied = await post("/api/auth/login", { name: "Emil", serverId: challenge.serverId });
  assert.equal(denied.status, 401);

  const { body: second } = await post("/api/auth/challenge", {});
  joined.set(second.serverId, "Emil");
  const ok = await post("/api/auth/login", { name: "Emil", serverId: second.serverId });
  assert.equal(ok.status, 200);
  assert.equal(ok.body.user.name, "Emil");
  assert.match(ok.body.user.id, /^[0-9a-f]{8}-[0-9a-f]{4}-/);

  // Eine serverId gilt nur einmal
  const again = await post("/api/auth/login", { name: "Emil", serverId: second.serverId });
  assert.equal(again.status, 400);
});

test("Freunde, Status und Chat", async () => {
  const alice = (await post("/api/auth/offline", { name: "Alice" })).body;
  const bob = (await post("/api/auth/offline", { name: "Bob" })).body;

  const a = await connect(alice.token);
  const b = await connect(bob.token);
  assert.equal((await a.next("ready")).self.name, "Alice");
  await b.next("ready");
  await a.next("social");
  await b.next("social");

  // Unbekannte Spieler lassen sich nicht hinzufügen
  a.send({ type: "friend.request", name: "Niemand" });
  assert.match((await a.next("notice")).text, /noch nicht benutzt/);

  a.send({ type: "friend.request", name: "bob" });
  assert.match((await a.next("notice")).text, /gesendet/);
  const pending = await b.next("social");
  assert.equal(pending.incoming[0].name, "Alice");
  await a.next("social");

  b.send({ type: "friend.accept", id: alice.user.id });
  const friendsOfA = await a.next("social");
  assert.equal(friendsOfA.friends[0].name, "Bob");
  assert.equal(friendsOfA.friends[0].online, true);
  await b.next("social");

  b.send({ type: "presence", state: "multiplayer", version: "1.21.4", server: "play.example.net" });
  let status = await a.presenceOf(bob.user.id);
  while (!status.presence?.server) status = await a.presenceOf(bob.user.id);
  assert.equal(status.presence.server, "play.example.net");

  a.send({ type: "chat.send", to: bob.user.id, text: "  Hallo Bob!  " });
  const received = await b.next("chat.message");
  assert.equal(received.message.text, "Hallo Bob!");
  await a.next("chat.message");

  b.send({ type: "chat.history", with: alice.user.id });
  assert.equal((await b.next("chat.history")).messages.length, 1);

  // Geht Bob offline, erfährt Alice das
  b.close();
  let update = await a.presenceOf(bob.user.id);
  while (update.online) update = await a.presenceOf(bob.user.id);
  assert.equal(update.online, false);

  // Ungelesenes bleibt erhalten, bis Bob es liest
  a.send({ type: "chat.send", to: bob.user.id, text: "Bist du da?" });
  await a.next("chat.message");
  const b2 = await connect(bob.token);
  await b2.next("ready");
  assert.equal((await b2.next("social")).unread[alice.user.id], 2);
  b2.send({ type: "chat.read", with: alice.user.id });
  await b2.next("chat.read");

  // Nach dem Entfernen keine Nachrichten mehr
  a.send({ type: "friend.remove", id: bob.user.id });
  assert.equal((await a.next("social")).friends.length, 0);
  a.send({ type: "chat.send", to: bob.user.id, text: "Hallo?" });
  assert.match((await a.next("notice")).text, /nur an Freunde/);

  a.close();
  b2.close();
});

test("Ohne gültiges Token wird die Verbindung geschlossen", async () => {
  const ws = new WebSocket(`${base.replace("http", "ws")}/ws`);
  await new Promise((resolve) => ws.on("open", resolve));
  ws.send(JSON.stringify({ type: "auth", token: "falsch" }));
  const code = await new Promise((resolve) => ws.on("close", resolve));
  assert.equal(code, 4001);
});
