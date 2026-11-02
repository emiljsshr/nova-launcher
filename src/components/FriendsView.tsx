import { useEffect, useMemo, useRef, useState, type FormEvent } from "react";
import { Card, PageHeader } from "./ui";
import SkinHead from "./SkinHead";
import { presenceLabel, type Friend, type SocialApi } from "../hooks/useSocial";
import "./FriendsView.css";

type FriendsViewProps = {
  social: SocialApi;
  signedIn: boolean;
  onLogin: () => void;
  // Freund, dessen Chat beim Öffnen der Seite gezeigt wird (z. B. aus einer Benachrichtigung)
  initialChat: string | null;
  onChatChange: (friendId: string | null) => void;
};

export function FriendHead({ name, size = 32 }: { name: string; size?: number }) {
  return <SkinHead skin={`https://minotar.net/skin/${encodeURIComponent(name)}`} size={size} />;
}

const timeFormat = new Intl.DateTimeFormat("de-DE", { hour: "2-digit", minute: "2-digit" });
const dayFormat = new Intl.DateTimeFormat("de-DE", { day: "2-digit", month: "2-digit" });

function formatTime(at: number) {
  const date = new Date(at);
  const today = new Date().toDateString() === date.toDateString();
  return today ? timeFormat.format(date) : `${dayFormat.format(date)} ${timeFormat.format(date)}`;
}

export default function FriendsView({ social, signedIn, onLogin, initialChat, onChatChange }: FriendsViewProps) {
  const [name, setName] = useState("");
  const [activeId, setActive] = useState<string | null>(initialChat);
  const setActiveId = (id: string | null) => {
    setActive(id);
    onChatChange(id);
  };
  const [draft, setDraft] = useState("");
  const messagesRef = useRef<HTMLDivElement>(null);

  const { friends, incoming, outgoing, unread, conversations, status, openChat, markRead } = social;

  // Online zuerst, dann alphabetisch
  const sorted = useMemo(
    () =>
      [...friends].sort(
        (a, b) => Number(b.online) - Number(a.online) || a.name.localeCompare(b.name, "de"),
      ),
    [friends],
  );
  const online = sorted.filter((friend) => friend.online);
  const offline = sorted.filter((friend) => !friend.online);
  const active = friends.find((friend) => friend.id === activeId) ?? null;
  const messages = activeId ? conversations[activeId] : undefined;

  // Verlauf laden und als gelesen markieren, sobald ein Chat offen ist
  useEffect(() => {
    if (activeId && status === "online") openChat(activeId);
  }, [activeId, status, openChat]);

  // Neue Nachrichten im offenen Chat gelten sofort als gelesen
  const activeUnread = activeId ? unread[activeId] ?? 0 : 0;
  useEffect(() => {
    if (activeId && activeUnread > 0) markRead(activeId);
  }, [activeId, activeUnread, markRead]);

  useEffect(() => {
    const list = messagesRef.current;
    if (list) list.scrollTop = list.scrollHeight;
  }, [messages?.length, activeId]);

  function addFriend(event: FormEvent) {
    event.preventDefault();
    const trimmed = name.trim();
    if (!trimmed) return;
    social.requestFriend(trimmed);
    setName("");
  }

  function sendMessage(event: FormEvent) {
    event.preventDefault();
    const text = draft.trim();
    if (!active || !text) return;
    social.sendMessage(active.id, text);
    setDraft("");
  }

  const header = (
    <PageHeader
      eyebrow="SOCIAL"
      title="FREUNDE"
      description="Sieh, wer gerade spielt, und schreib deinen Freunden – im Launcher und im Spiel."
    />
  );

  if (!signedIn) {
    return (
      <div className="nova-page friends-page">
        {header}
        <Card className="friends-empty">
          <p>Melde dich an, um Freunde hinzuzufügen und zu chatten.</p>
          <button type="button" className="nova-button nova-button-dark" onClick={onLogin}>
            ANMELDEN
          </button>
        </Card>
      </div>
    );
  }

  const renderFriend = (friend: Friend) => (
    <li key={friend.id}>
      <button
        type="button"
        className={`friend-row ${friend.id === activeId ? "is-active" : ""}`}
        onClick={() => setActiveId(friend.id)}
      >
        <FriendHead name={friend.name} size={34} />
        <span className="friend-row-copy">
          <strong>{friend.name}</strong>
          <span>{presenceLabel(friend)}</span>
        </span>
        {unread[friend.id] ? <span className="friend-unread">{unread[friend.id]}</span> : null}
        <span
          className={`status-square ${friend.online ? (friend.presence?.state === "launcher" ? "is-idle" : "is-online") : ""}`}
          aria-hidden="true"
        />
      </button>
    </li>
  );

  return (
    <div className="nova-page friends-page">
      {header}

      {status !== "online" && (
        <p className="friends-status" role="status">
          {status === "connecting" ? "Verbinde mit dem Nova-Server …" : "Der Nova-Server ist gerade nicht erreichbar. Neuer Versuch läuft automatisch."}
        </p>
      )}

      <div className="friends-layout">
        <Card title="FREUNDESLISTE" meta={`${online.length} ONLINE`} className="friends-list-card">
          <form className="friends-add" onSubmit={addFriend}>
            <input
              className="nova-input"
              value={name}
              onChange={(event) => setName(event.target.value)}
              placeholder="Minecraft-Name hinzufügen"
              aria-label="Minecraft-Name hinzufügen"
              maxLength={16}
            />
            <button type="submit" className="nova-button nova-button-dark" disabled={status !== "online"}>
              +
            </button>
          </form>

          <div className="friends-scroll">
            {incoming.length > 0 && (
              <section>
                <h3>ANFRAGEN · {incoming.length}</h3>
                <ul className="friends-requests">
                  {incoming.map((request) => (
                    <li key={request.id}>
                      <FriendHead name={request.name} size={28} />
                      <strong>{request.name}</strong>
                      <button type="button" className="nova-button nova-button-dark" onClick={() => social.acceptFriend(request.id)}>
                        ANNEHMEN
                      </button>
                      <button
                        type="button"
                        className="friends-icon-button"
                        aria-label={`Anfrage von ${request.name} ablehnen`}
                        onClick={() => social.declineFriend(request.id)}
                      >
                        ✕
                      </button>
                    </li>
                  ))}
                </ul>
              </section>
            )}

            {online.length > 0 && (
              <section>
                <h3>ONLINE · {online.length}</h3>
                <ul className="friends-list">{online.map(renderFriend)}</ul>
              </section>
            )}

            {offline.length > 0 && (
              <section>
                <h3>OFFLINE · {offline.length}</h3>
                <ul className="friends-list">{offline.map(renderFriend)}</ul>
              </section>
            )}

            {outgoing.length > 0 && (
              <section>
                <h3>GESENDET · {outgoing.length}</h3>
                <ul className="friends-requests">
                  {outgoing.map((request) => (
                    <li key={request.id}>
                      <FriendHead name={request.name} size={28} />
                      <strong>{request.name}</strong>
                      <span className="friends-pending">AUSSTEHEND</span>
                      <button
                        type="button"
                        className="friends-icon-button"
                        aria-label={`Anfrage an ${request.name} zurückziehen`}
                        onClick={() => social.cancelRequest(request.id)}
                      >
                        ✕
                      </button>
                    </li>
                  ))}
                </ul>
              </section>
            )}

            {friends.length === 0 && incoming.length === 0 && outgoing.length === 0 && status === "online" && (
              <p className="friends-hint">
                Noch keine Freunde. Gib oben den Minecraft-Namen ein – dein Freund muss Nova dafür einmal gestartet haben.
              </p>
            )}
          </div>
        </Card>

        <Card dark className="friends-chat">
          {active ? (
            <>
              <header className="friends-chat-header">
                <FriendHead name={active.name} size={40} />
                <div>
                  <strong>{active.name}</strong>
                  <span>{presenceLabel(active)}</span>
                </div>
                <button
                  type="button"
                  className="friends-remove"
                  onClick={() => {
                    if (window.confirm(`${active.name} wirklich als Freund entfernen?`)) {
                      social.removeFriend(active.id);
                      setActiveId(null);
                    }
                  }}
                >
                  ENTFERNEN
                </button>
              </header>

              <div className="friends-messages" ref={messagesRef}>
                {messages === undefined && <p className="friends-hint">Verlauf wird geladen …</p>}
                {messages?.length === 0 && <p className="friends-hint">Schreib {active.name} die erste Nachricht.</p>}
                {messages?.map((message) => {
                  const mine = message.from !== active.id;
                  return (
                    <div key={message.id} className={`friends-message ${mine ? "is-mine" : ""}`}>
                      <p>{message.text}</p>
                      <time>{formatTime(message.at)}</time>
                    </div>
                  );
                })}
              </div>

              <form className="friends-compose" onSubmit={sendMessage}>
                <input
                  className="nova-input"
                  value={draft}
                  onChange={(event) => setDraft(event.target.value)}
                  placeholder={`Nachricht an ${active.name}`}
                  aria-label={`Nachricht an ${active.name}`}
                  maxLength={500}
                />
                <button type="submit" className="nova-button nova-button-frost" disabled={!draft.trim() || status !== "online"}>
                  SENDEN
                </button>
              </form>
            </>
          ) : (
            <div className="friends-chat-empty">
              <p>Wähle links einen Freund aus, um zu chatten.</p>
              <p>Im Spiel öffnest du den Chat mit Rechts-Shift.</p>
            </div>
          )}
        </Card>
      </div>
    </div>
  );
}
