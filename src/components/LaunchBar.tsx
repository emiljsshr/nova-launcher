
import { useEffect, useRef, useState, type ReactNode } from "react";
import { ServerIcon } from "./PixelIcon";
import SkinHead from "./SkinHead";
import { MAX_MEMORY_GB, type LauncherSettings } from "../data/launcherData";
import type { Account, GameProfile } from "../lib/launcher";
import type { GameState } from "../hooks/useLauncher";
import "./LaunchBar.css";

type LaunchBarProps = {
  account: Account | null;
  skin: string | null;
  onLogin: () => void;
  onOpenAccount: () => void;
  profiles: GameProfile[];
  selectedProfile: GameProfile | null;
  onSelectProfile: (id: string) => void;
  onManageProfiles: () => void;
  settings: LauncherSettings;
  onSettingsChange: (changes: Partial<LauncherSettings>) => void;
  onOpenSettings: () => void;
  settingsActive: boolean;
  game: GameState;
  onPlay: () => void;
  onStop: () => void;
  // Ersetzt Profil/RAM/Einstellungen, z. B. auf der Cosmetics-Seite
  context?: ReactNode;
  actions?: ReactNode;
};

export function profileSubtitle(profile: GameProfile) {
  return `${profile.gameVersion} · ${profile.loader === "fabric" ? "Fabric" : "Vanilla"}`;
}

export default function LaunchBar({
  account,
  skin,
  onLogin,
  onOpenAccount,
  profiles,
  selectedProfile,
  onSelectProfile,
  onManageProfiles,
  settings,
  onSettingsChange,
  onOpenSettings,
  settingsActive,
  game,
  onPlay,
  onStop,
  context,
  actions,
}: LaunchBarProps) {
  const [menuOpen, setMenuOpen] = useState(false);
  const menuRef = useRef<HTMLDivElement>(null);

  // Profilmenü bei Klick außerhalb schließen
  useEffect(() => {
    if (!menuOpen) return;

    function handleClick(event: MouseEvent) {
      if (!menuRef.current?.contains(event.target as Node)) setMenuOpen(false);
    }

    window.addEventListener("mousedown", handleClick);
    return () => window.removeEventListener("mousedown", handleClick);
  }, [menuOpen]);

  const progress =
    game.status === "preparing" && game.progress && game.progress.total > 0
      ? game.progress.done / game.progress.total
      : null;

  return (
    <footer className="launch-bar">
      {account ? (
        <button type="button" className="launch-player" onClick={onOpenAccount}>
          <SkinHead skin={skin} size={44} />
          <span className="launch-player-copy">
            <strong>{account.name}</strong>
            <span>
              <span className="status-square is-online" />{" "}
              {account.offline ? "Offline-Testkonto" : "Microsoft-Konto"}
            </span>
          </span>
        </button>
      ) : (
        <button type="button" className="launch-player is-login" onClick={onLogin}>
          <span className="launch-login-icon" aria-hidden="true">
            <span />
            <span />
            <span />
            <span />
          </span>
          <span className="launch-player-copy">
            <strong>Anmelden</strong>
            <span className="is-muted">Microsoft-Konto verbinden</span>
          </span>
        </button>
      )}

      {context ?? (
        <>
          <div className="launch-version" ref={menuRef}>
            <button
              type="button"
              className="launch-version-trigger"
              aria-haspopup="listbox"
              aria-expanded={menuOpen}
              onClick={() => setMenuOpen((current) => !current)}
            >
              <span className="launch-label">PROFIL</span>
              <strong>{selectedProfile ? selectedProfile.name : "Kein Profil"}</strong>
              <span className="launch-caret" aria-hidden="true" />
            </button>

            {menuOpen && (
              <div className="launch-version-menu">
                <ul role="listbox">
                  {profiles.map((profile) => (
                    <li key={profile.id}>
                      <button
                        type="button"
                        role="option"
                        aria-selected={profile.id === selectedProfile?.id}
                        className={profile.id === selectedProfile?.id ? "is-selected" : ""}
                        onClick={() => {
                          onSelectProfile(profile.id);
                          setMenuOpen(false);
                        }}
                      >
                        <ServerIcon variant={profile.icon} size={28} />
                        <span>
                          <strong>{profile.name}</strong>
                          <small>{profileSubtitle(profile)}</small>
                        </span>
                      </button>
                    </li>
                  ))}
                </ul>
                <button
                  type="button"
                  className="launch-version-manage"
                  onClick={() => {
                    setMenuOpen(false);
                    onManageProfiles();
                  }}
                >
                  PROFILE VERWALTEN →
                </button>
              </div>
            )}
          </div>

          <div className="launch-memory">
            <div className="launch-memory-heading">
              <span className="launch-label">ARBEITSSPEICHER</span>
              <span className="launch-label launch-memory-value">{settings.memory} GB</span>
            </div>

            <MemoryBlocks
              value={settings.memory}
              onChange={(memory) => onSettingsChange({ memory })}
            />
          </div>

          <button
            type="button"
            className={`launch-settings ${settingsActive ? "is-active" : ""}`}
            aria-label="Einstellungen"
            aria-pressed={settingsActive}
            onClick={onOpenSettings}
          >
            <svg viewBox="0 0 16 16" shapeRendering="crispEdges" aria-hidden="true">
              <path
                fill="currentColor"
                d="M7 1h2v2h2V2h2v2h-1v2h2v2h-2v2h1v2h-2v-1H9v2H7v-2H5v1H3v-2h1V8H2V6h2V4H3V2h2v1h2zm0 5v3h2V6z"
              />
            </svg>
          </button>
        </>
      )}

      <div className="launch-spacer" />
      {actions}

      {game.status === "running" ? (
        <button type="button" className="launch-play is-running" onClick={onStop}>
          <svg viewBox="0 0 10 10" aria-hidden="true">
            <rect width="10" height="10" fill="currentColor" />
          </svg>
          STOPPEN
        </button>
      ) : game.status === "preparing" ? (
        <button type="button" className="launch-play is-preparing" disabled>
          <span
            className="launch-play-progress"
            style={{ width: `${Math.round((progress ?? 0) * 100)}%` }}
            aria-hidden="true"
          />
          <span className="launch-play-status">
            <strong>{progress !== null ? `${Math.round(progress * 100)}%` : "LÄDT"}</strong>
            <small>{game.progress?.label ?? "Wird vorbereitet"}</small>
          </span>
        </button>
      ) : (
        <button
          type="button"
          className="launch-play"
          onClick={onPlay}
          disabled={!selectedProfile}
        >
          <svg viewBox="0 0 8 10" aria-hidden="true">
            <polygon fill="currentColor" points="0,0 8,5 0,10" />
          </svg>
          PLAY
        </button>
      )}
    </footer>
  );
}

export function MemoryBlocks({
  value,
  onChange,
  className = "",
}: {
  value: number;
  onChange: (value: number) => void;
  className?: string;
}) {
  return (
    <div
      className={`launch-memory-blocks ${className}`}
      role="group"
      aria-label="Arbeitsspeicher"
    >
      {Array.from({ length: MAX_MEMORY_GB }, (_, index) => {
        const blockValue = index + 1;
        return (
          <button
            key={blockValue}
            type="button"
            className={blockValue <= value ? "is-filled" : ""}
            aria-label={`${blockValue} GB`}
            aria-pressed={blockValue === value}
            onClick={() => onChange(blockValue)}
          />
        );
      })}
    </div>
  );
}
