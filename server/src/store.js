import { createHash, randomBytes, randomUUID } from "node:crypto";
import { mkdirSync, readFileSync, renameSync, writeFileSync } from "node:fs";
import { dirname } from "node:path";

const TOKEN_DAYS = 30;
const HISTORY_LIMIT = 200;
export const MESSAGE_MAX_LENGTH = 500;

const emptyData = () => ({ users: {}, tokens: {}, friends: {}, requests: [], messages: {}, unread: {} });

const hashToken = (token) => createHash("sha256").update(token).digest("hex");

// Gespräche zweier Spieler liegen unter einem gemeinsamen, sortierten Schlüssel
const pairKey = (a, b) => (a < b ? `${a}:${b}` : `${b}:${a}`);

/**
 * Dauerhafte Daten des Servers: Spieler, Anmelde-Tokens, Freundschaften, Anfragen und Nachrichten.
 * Liegt als JSON in einer Datei; Änderungen werden gesammelt und kurz danach am Stück geschrieben.
 * Ohne Dateipfad (Tests) bleibt alles im Speicher.
 */
export class Store {
  constructor(file = null) {
    this.file = file;
    this.data = emptyData();
    this.saveTimer = null;
    if (file) {
      try {
        this.data = { ...emptyData(), ...JSON.parse(readFileSync(file, "utf8")) };
      } catch (error) {
        if (error.code !== "ENOENT") throw error;
      }
    }
  }

  changed() {
    if (!this.file || this.saveTimer) return;
    this.saveTimer = setTimeout(() => this.flush(), 500);
  }

  flush() {
    clearTimeout(this.saveTimer);
    this.saveTimer = null;
    if (!this.file) return;
    // Erst in eine Nachbardatei schreiben und dann umbenennen, damit ein Absturz nichts halb überschreibt
    mkdirSync(dirname(this.file), { recursive: true });
    writeFileSync(`${this.file}.tmp`, JSON.stringify(this.data));
    renameSync(`${this.file}.tmp`, this.file);
  }

  // Spieler

  upsertUser(id, name) {
    const user = this.data.users[id] ?? { id, name, since: Date.now() };
    user.name = name;
    this.data.users[id] = user;
    this.changed();
    return user;
  }

  user(id) {
    return this.data.users[id] ?? null;
  }

  userByName(name) {
    const lower = name.toLowerCase();
    return Object.values(this.data.users).find((user) => user.name.toLowerCase() === lower) ?? null;
  }

  // Tokens: gespeichert wird nur der Hash, das Token selbst kennt nur der Launcher

  createToken(userId) {
    const token = randomBytes(32).toString("base64url");
    this.data.tokens[hashToken(token)] = { userId, expires: Date.now() + TOKEN_DAYS * 86_400_000 };
    this.changed();
    return token;
  }

  userForToken(token) {
    if (typeof token !== "string") return null;
    const entry = this.data.tokens[hashToken(token)];
    if (!entry || entry.expires < Date.now()) return null;
    return this.user(entry.userId);
  }

  dropExpiredTokens() {
    const now = Date.now();
    for (const [hash, entry] of Object.entries(this.data.tokens)) {
      if (entry.expires < now) delete this.data.tokens[hash];
    }
    this.changed();
  }

  // Freunde

  friendsOf(id) {
    return this.data.friends[id] ?? [];
  }

  areFriends(a, b) {
    return this.friendsOf(a).includes(b);
  }

  incoming(id) {
    return this.data.requests.filter((request) => request.to === id);
  }

  outgoing(id) {
    return this.data.requests.filter((request) => request.from === id);
  }

  /**
   * Freundschaftsanfrage von {@code from} an {@code to}. Hat {@code to} selbst schon angefragt,
   * werden beide sofort Freunde. Liefert "sent", "accepted" oder einen Fehlergrund.
   */
  request(from, to) {
    if (from === to) return "self";
    if (!this.user(to)) return "unknown";
    if (this.areFriends(from, to)) return "already";
    if (this.data.requests.some((request) => request.from === to && request.to === from)) {
      this.accept(from, to);
      return "accepted";
    }
    if (this.data.requests.some((request) => request.from === from && request.to === to)) return "pending";
    this.data.requests.push({ from, to, at: Date.now() });
    this.changed();
    return "sent";
  }

  /** {@code id} nimmt die Anfrage von {@code from} an. */
  accept(id, from) {
    const before = this.data.requests.length;
    this.data.requests = this.data.requests.filter((request) => !(request.from === from && request.to === id));
    if (this.data.requests.length === before) return false;
    for (const [a, b] of [[id, from], [from, id]]) {
      const list = this.friendsOf(a);
      if (!list.includes(b)) this.data.friends[a] = [...list, b];
    }
    this.changed();
    return true;
  }

  /** Entfernt eine Anfrage in beide Richtungen (ablehnen oder zurückziehen). */
  dropRequest(a, b) {
    const before = this.data.requests.length;
    this.data.requests = this.data.requests.filter(
      (request) => !((request.from === a && request.to === b) || (request.from === b && request.to === a)),
    );
    this.changed();
    return this.data.requests.length !== before;
  }

  removeFriend(a, b) {
    if (!this.areFriends(a, b)) return false;
    this.data.friends[a] = this.friendsOf(a).filter((id) => id !== b);
    this.data.friends[b] = this.friendsOf(b).filter((id) => id !== a);
    this.changed();
    return true;
  }

  // Nachrichten

  addMessage(from, to, text) {
    const message = { id: randomUUID(), from, to, text, at: Date.now() };
    const key = pairKey(from, to);
    const history = this.data.messages[key] ?? [];
    history.push(message);
    this.data.messages[key] = history.slice(-HISTORY_LIMIT);
    const unread = (this.data.unread[to] ??= {});
    unread[from] = (unread[from] ?? 0) + 1;
    this.changed();
    return message;
  }

  history(a, b) {
    return this.data.messages[pairKey(a, b)] ?? [];
  }

  unread(id) {
    return this.data.unread[id] ?? {};
  }

  markRead(id, from) {
    if (!this.data.unread[id]?.[from]) return;
    delete this.data.unread[id][from];
    this.changed();
  }
}
