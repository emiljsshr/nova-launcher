
import { useEffect, useState } from "react";
import FrostScene from "./FrostScene";
import { ServerIcon } from "./PixelIcon";
import { FriendHead } from "./FriendsView";
import PatchNotesDialog from "./PatchNotesDialog";
import { partnerPreviews, upcomingEvent } from "../data/launcherData";
import type { News } from "../hooks/useNews";
import { presenceLabel, type Friend } from "../hooks/useSocial";
import "./HomeView.css";

type HomeViewProps = {
  news: News;
  friends: Friend[];
  signedIn: boolean;
  onOpenPartners: () => void;
  onOpenCosmetics: () => void;
  onOpenFriends: (friendId?: string) => void;
  onNotify: (message: string) => void;
};

const actionLabels = {
  COSMETICS: "COSMETICS ANSEHEN",
  FRIENDS: "FREUNDE ÖFFNEN",
  PARTNERS: "PARTNER SERVER",
} as const;

const FRIEND_PREVIEW = 5;

const SLIDE_INTERVAL = 8000;

export default function HomeView({
  news,
  friends,
  signedIn,
  onOpenPartners,
  onOpenCosmetics,
  onOpenFriends,
  onNotify,
}: HomeViewProps) {
  const [slideIndex, setSlideIndex] = useState(0);
  const [eventReminder, setEventReminder] = useState(false);
  const [patchNotesOpen, setPatchNotesOpen] = useState(false);

  const slides = news.slides;
  const slide = slides[slideIndex % slides.length];
  const action = slide.action ?? "COSMETICS";
  const onlineFriends = friends.filter((friend) => friend.online);
  // Online zuerst, Spielende vor Launcher-Nutzern
  const previewFriends = [...friends]
    .sort((a, b) => Number(b.online) - Number(a.online) || Number(b.presence?.state !== "launcher") - Number(a.presence?.state !== "launcher"))
    .slice(0, FRIEND_PREVIEW);

  function openAction() {
    if (action === "FRIENDS") onOpenFriends();
    else if (action === "PARTNERS") onOpenPartners();
    else onOpenCosmetics();
  }

  // Banner wechselt automatisch, ein Klick auf einen Punkt setzt den Timer zurück
  useEffect(() => {
    const timer = window.setTimeout(() => {
      setSlideIndex((current) => (current + 1) % slides.length);
    }, SLIDE_INTERVAL);

    return () => window.clearTimeout(timer);
  }, [slideIndex, slides.length]);

  return (
    <div className="home-view">
      <section className="hero-banner" aria-roledescription="carousel">
        <FrostScene />
        <div className="hero-shade" aria-hidden="true" />

        <div className="hero-content" key={slide.id}>
          <span className="hero-badge">
            <span className="hero-badge-dot" />
            {slide.badge}
          </span>

          <h1 className="hero-title">
            <span>{slide.titleTop}</span>
            <span className="hero-title-accent">{slide.titleBottom}</span>
          </h1>

          <p className="hero-description">{slide.description}</p>

          <div className="hero-actions">
            <button
              type="button"
              className="nova-button nova-button-light"
              onClick={() => setPatchNotesOpen(true)}
            >
              PATCH NOTES
            </button>
            <button
              type="button"
              className="nova-button nova-button-outline"
              onClick={openAction}
            >
              {actionLabels[action]}
            </button>
          </div>
        </div>

        <div className="hero-dots" role="tablist" aria-label="News">
          {slides.map((item, index) => (
            <button
              key={item.id}
              type="button"
              role="tab"
              aria-selected={index === slideIndex % slides.length}
              aria-label={`${item.titleTop} ${item.titleBottom}`}
              className={index === slideIndex % slides.length ? "is-active" : ""}
              onClick={() => setSlideIndex(index)}
            />
          ))}
        </div>
      </section>

      <aside className="home-side">
        <article className="side-card">
          <header className="side-card-header">
            <h2>PARTNER SERVER</h2>
            <button type="button" className="side-card-link" onClick={onOpenPartners}>
              ALLE →
            </button>
          </header>

          <ul className="partner-preview-list">
            {partnerPreviews.map((server) => (
              <li key={server.id}>
                <ServerIcon variant={server.icon} size={34} />
                <div className="side-entry-copy">
                  <strong>{server.name}</strong>
                  <span>{server.modes}</span>
                </div>
                <span className="player-count">
                  <span className="status-square is-online" />
                  {server.players.toLocaleString("de-DE")}
                </span>
              </li>
            ))}
          </ul>
        </article>

        <article className="side-card">
          <header className="side-card-header">
            <h2>FREUNDE</h2>
            <button type="button" className="side-card-link" onClick={() => onOpenFriends()}>
              {onlineFriends.length} ONLINE →
            </button>
          </header>

          {previewFriends.length === 0 ? (
            <p className="friend-list-empty">
              {signedIn ? "Noch keine Freunde – füge sie unter FREUNDE hinzu." : "Melde dich an, um deine Freunde zu sehen."}
            </p>
          ) : (
            <ul className="friend-list">
              {previewFriends.map((friend) => (
                <li key={friend.id}>
                  <button type="button" className="friend-list-entry" onClick={() => onOpenFriends(friend.id)}>
                    <FriendHead name={friend.name} size={34} />
                    <div className="side-entry-copy">
                      <strong>{friend.name}</strong>
                      <span>{presenceLabel(friend)}</span>
                    </div>
                    <span
                      className={`status-square ${
                        !friend.online ? "is-offline" : friend.presence?.state === "launcher" ? "is-idle" : "is-online"
                      }`}
                      aria-label={presenceLabel(friend)}
                    />
                  </button>
                </li>
              ))}
            </ul>
          )}
        </article>

        <article className="side-card event-card">
          <header className="side-card-header">
            <h2>EVENT</h2>
            <span className="side-card-meta">{upcomingEvent.label}</span>
          </header>

          <h3>{upcomingEvent.title}</h3>
          <p>{upcomingEvent.details}</p>

          <div className="event-actions">
            <button
              type="button"
              className="nova-button nova-button-dark"
              onClick={() => onNotify("Die Turnier-Anmeldung öffnet bald.")}
            >
              ANMELDEN
            </button>
            <button
              type="button"
              className="nova-button nova-button-muted"
              aria-pressed={eventReminder}
              onClick={() => {
                setEventReminder((current) => !current);
                onNotify(
                  eventReminder
                    ? "Erinnerung entfernt."
                    : "Wir erinnern dich vor dem Turnier.",
                );
              }}
            >
              {eventReminder ? "ERINNERT ✓" : "ERINNERN"}
            </button>
          </div>
        </article>
      </aside>

      {patchNotesOpen && <PatchNotesDialog notes={news.patchNotes} onClose={() => setPatchNotesOpen(false)} />}
    </div>
  );
}
