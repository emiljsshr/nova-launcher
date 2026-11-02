
import { useEffect, useMemo, useRef, useState } from "react";
import { ServerIcon, type ServerIconVariant } from "./PixelIcon";
import { Card, PageHeader, Toggle } from "./ui";
import { profileSubtitle } from "./LaunchBar";
import {
  errorMessage,
  formatPlayTime,
  formatRelative,
  launcher,
  type GameLog,
  type GameProfile,
  type GameVersion,
  type Loader,
  type ProfileInput,
} from "../lib/launcher";
import type { GameState } from "../hooks/useLauncher";
import "./GameProfilesView.css";

const iconVariants: ServerIconVariant[] = ["ice", "arena", "grass", "sand", "lava", "amethyst"];

type GameProfilesViewProps = {
  profiles: GameProfile[];
  onProfilesChange: (profiles: GameProfile[]) => void;
  selectedProfileId: string | null;
  onSelectProfile: (id: string) => void;
  game: GameState;
  logs: GameLog[];
  onLaunch: (profileId: string) => void;
  onStop: () => void;
  onNotify: (message: string) => void;
};

export default function GameProfilesView({
  profiles,
  onProfilesChange,
  selectedProfileId,
  onSelectProfile,
  game,
  logs,
  onLaunch,
  onStop,
  onNotify,
}: GameProfilesViewProps) {
  const [editing, setEditing] = useState<GameProfile | "new" | null>(null);
  const [deleting, setDeleting] = useState<GameProfile | null>(null);
  const consoleRef = useRef<HTMLPreElement>(null);

  // Konsole folgt neuen Zeilen automatisch
  useEffect(() => {
    const element = consoleRef.current;
    if (element) element.scrollTop = element.scrollHeight;
  }, [logs]);

  const busy = game.status !== "idle";

  async function openFolder(id: string) {
    try {
      await launcher.openProfileFolder(id);
    } catch (error) {
      onNotify(errorMessage(error));
    }
  }

  async function copyLogs() {
    try {
      await navigator.clipboard.writeText(logs.map((log) => log.line).join("\n"));
      onNotify("Konsole kopiert.");
    } catch {
      onNotify("Kopieren nicht möglich.");
    }
  }

  return (
    <div className="nova-page profiles-page">
      <PageHeader
        eyebrow="INSTALLATIONEN"
        title="SPIELPROFILE"
        description="Jedes Profil hat eigene Welten, Mods und Einstellungen."
        actions={
          <button
            type="button"
            className="nova-button nova-button-dark"
            onClick={() => setEditing("new")}
          >
            + NEUES PROFIL
          </button>
        }
      />

      <div className="profiles-layout">
        <div className="profiles-grid">
          {profiles.map((profile) => {
            const isSelected = profile.id === selectedProfileId;
            const isActive = game.status !== "idle" && game.profileId === profile.id;

            return (
              <article
                key={profile.id}
                className={`profile-card pixel-corners ${isSelected ? "is-selected" : ""}`}
              >
                <button
                  type="button"
                  className="profile-card-main"
                  onClick={() => onSelectProfile(profile.id)}
                >
                  <ServerIcon variant={profile.icon} size={56} />
                  <span className="profile-card-copy">
                    <span className="profile-card-title">
                      <strong>{profile.name}</strong>
                      {isActive ? (
                        <span className="nova-chip is-running">
                          {game.status === "running" ? "LÄUFT" : "STARTET"}
                        </span>
                      ) : isSelected ? (
                        <span className="nova-chip is-frost">AUSGEWÄHLT</span>
                      ) : null}
                    </span>
                    <span>{profileSubtitle(profile)}</span>
                  </span>
                </button>

                <dl className="profile-card-stats">
                  <div>
                    <dt>ZULETZT</dt>
                    <dd>{formatRelative(profile.lastPlayed)}</dd>
                  </div>
                  <div>
                    <dt>SPIELZEIT</dt>
                    <dd>{formatPlayTime(profile.playSeconds)}</dd>
                  </div>
                  <div>
                    <dt>SESSIONS</dt>
                    <dd>{profile.sessions}</dd>
                  </div>
                </dl>

                <div className="profile-card-actions">
                  {isActive && game.status === "running" ? (
                    <button type="button" className="nova-button nova-button-muted" onClick={onStop}>
                      STOPPEN
                    </button>
                  ) : (
                    <button
                      type="button"
                      className="nova-button nova-button-frost"
                      disabled={busy}
                      onClick={() => {
                        onSelectProfile(profile.id);
                        onLaunch(profile.id);
                      }}
                    >
                      ▶ SPIELEN
                    </button>
                  )}
                  <button type="button" className="nova-button nova-button-muted" onClick={() => openFolder(profile.id)}>
                    ORDNER
                  </button>
                  <button type="button" className="nova-button nova-button-muted" onClick={() => setEditing(profile)}>
                    BEARBEITEN
                  </button>
                  <button
                    type="button"
                    className="nova-button nova-button-muted profile-delete"
                    aria-label={`${profile.name} löschen`}
                    disabled={profiles.length <= 1 || isActive}
                    onClick={() => setDeleting(profile)}
                  >
                    ✕
                  </button>
                </div>
              </article>
            );
          })}
        </div>

        <Card dark className="game-console" title="SPIELKONSOLE" meta={
          <button type="button" className="game-console-copy" onClick={copyLogs} disabled={logs.length === 0}>
            KOPIEREN
          </button>
        }>
          <pre ref={consoleRef}>
            {logs.length === 0 ? (
              <span className="game-console-empty">
                Hier erscheint die Ausgabe von Minecraft, sobald ein Profil läuft.
              </span>
            ) : (
              logs.map((log, index) => (
                <span key={index} className={log.error ? "is-error" : ""}>
                  {log.line}
                  {"\n"}
                </span>
              ))
            )}
          </pre>
        </Card>
      </div>

      {editing && (
        <ProfileEditor
          profile={editing === "new" ? null : editing}
          onClose={() => setEditing(null)}
          onSaved={(next, message) => {
            onProfilesChange(next);
            onNotify(message);
            setEditing(null);
          }}
        />
      )}

      {deleting && (
        <DeleteDialog
          profile={deleting}
          onClose={() => setDeleting(null)}
          onDeleted={(next) => {
            onProfilesChange(next);
            onNotify(`${deleting.name} gelöscht.`);
            setDeleting(null);
          }}
          onError={onNotify}
        />
      )}
    </div>
  );
}

/* ========================================
   PROFIL-EDITOR
======================================== */

function ProfileEditor({
  profile,
  onClose,
  onSaved,
}: {
  profile: GameProfile | null;
  onClose: () => void;
  onSaved: (profiles: GameProfile[], message: string) => void;
}) {
  const [name, setName] = useState(profile?.name ?? "Neues Profil");
  const [gameVersion, setGameVersion] = useState(profile?.gameVersion ?? "");
  const [loader, setLoader] = useState<Loader>(profile?.loader ?? "fabric");
  const [loaderVersion, setLoaderVersion] = useState(profile?.loaderVersion ?? "");
  const [icon, setIcon] = useState<ServerIconVariant>(profile?.icon ?? "ice");
  const [showSnapshots, setShowSnapshots] = useState(false);
  const [versions, setVersions] = useState<GameVersion[]>([]);
  const [fabricVersions, setFabricVersions] = useState<string[] | null>(null);
  const [novaModVersions, setNovaModVersions] = useState<string[]>([]);
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    let cancelled = false;
    launcher
      .novaModVersions()
      .then((list) => {
        if (!cancelled) setNovaModVersions(list);
      })
      .catch((loadError) => {
        if (!cancelled) setError(errorMessage(loadError));
      });
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    let cancelled = false;
    launcher
      .listVersions(showSnapshots)
      .then((list) => {
        if (cancelled) return;
        setVersions(list);
        setGameVersion((current) => current || list[0]?.id || "");
      })
      .catch((loadError) => setError(errorMessage(loadError)));
    return () => {
      cancelled = true;
    };
  }, [showSnapshots]);

  // Fabric-Unterstützung für die gewählte Version prüfen
  useEffect(() => {
    if (!gameVersion) return;
    let cancelled = false;
    setFabricVersions(null);
    launcher
      .fabricLoaders(gameVersion)
      .then((list) => {
        if (!cancelled) setFabricVersions(list);
      })
      .catch(() => {
        if (!cancelled) setFabricVersions([]);
      });
    return () => {
      cancelled = true;
    };
  }, [gameVersion]);

  const fabricSupported = fabricVersions === null || fabricVersions.length > 0;
  const effectiveLoader: Loader = loader === "fabric" && !fabricSupported ? "vanilla" : loader;

  const versionOptions = useMemo(() => {
    // Ältere, gespeicherte Version sichtbar halten, auch wenn sie gefiltert wäre
    if (gameVersion && !versions.some((version) => version.id === gameVersion)) {
      return [{ id: gameVersion, kind: "release", releaseTime: "" }, ...versions];
    }
    return versions;
  }, [versions, gameVersion]);

  async function save() {
    const input: ProfileInput = {
      name,
      gameVersion,
      loader: effectiveLoader,
      loaderVersion: effectiveLoader === "fabric" && loaderVersion ? loaderVersion : null,
      icon,
    };

    setSaving(true);
    setError("");
    try {
      const next = profile
        ? await launcher.updateProfile(profile.id, input)
        : await launcher.createProfile(input);
      onSaved(next, profile ? "Profil gespeichert." : `${name.trim()} erstellt.`);
    } catch (saveError) {
      setError(errorMessage(saveError));
      setSaving(false);
    }
  }

  return (
    <div className="nova-modal-backdrop" role="presentation" onMouseDown={onClose}>
      <div
        className="nova-modal profile-editor pixel-corners"
        role="dialog"
        aria-modal="true"
        aria-labelledby="profile-editor-title"
        onMouseDown={(event) => event.stopPropagation()}
      >
        <header className="nova-modal-header">
          <div>
            <span className="page-eyebrow">SPIELPROFIL</span>
            <h2 id="profile-editor-title">{profile ? "BEARBEITEN" : "NEUES PROFIL"}</h2>
          </div>
          <button type="button" className="nova-modal-close" aria-label="Schließen" onClick={onClose}>
            ✕
          </button>
        </header>

        <div className="profile-editor-field">
          <label className="nova-field-label" htmlFor="profile-name-input">NAME</label>
          <input
            id="profile-name-input"
            className="nova-input"
            value={name}
            maxLength={32}
            onChange={(event) => setName(event.target.value)}
          />
        </div>

        <div className="profile-editor-field">
          <span className="nova-field-label">SYMBOL</span>
          <div className="profile-icon-picker" role="radiogroup" aria-label="Symbol">
            {iconVariants.map((variant) => (
              <button
                key={variant}
                type="button"
                role="radio"
                aria-checked={icon === variant}
                className={icon === variant ? "is-active" : ""}
                onClick={() => setIcon(variant)}
              >
                <ServerIcon variant={variant} size={36} />
              </button>
            ))}
          </div>
        </div>

        <div className="profile-editor-field">
          <div className="profile-editor-row">
            <label className="nova-field-label" htmlFor="profile-version">MINECRAFT-VERSION</label>
            <label className="profile-editor-snapshots">
              <Toggle checked={showSnapshots} onChange={setShowSnapshots} label="Snapshots anzeigen" />
              SNAPSHOTS
            </label>
          </div>
          <select
            id="profile-version"
            className="nova-select"
            value={gameVersion}
            onChange={(event) => {
              setGameVersion(event.target.value);
              setLoaderVersion("");
            }}
          >
            {versionOptions.length === 0 && <option value="">Versionen werden geladen…</option>}
            {versionOptions.map((version) => (
              <option key={version.id} value={version.id}>
                {version.id}
                {version.kind !== "release" ? ` (${version.kind})` : ""}
              </option>
            ))}
          </select>
        </div>

        <div className="profile-editor-field">
          <span className="nova-field-label">MOD-LOADER</span>
          <div className="profile-loader-picker" role="radiogroup" aria-label="Mod-Loader">
            <button
              type="button"
              role="radio"
              aria-checked={effectiveLoader === "vanilla"}
              className={effectiveLoader === "vanilla" ? "is-active" : ""}
              onClick={() => setLoader("vanilla")}
            >
              <strong>Vanilla</strong>
              <span>Ohne Mods</span>
            </button>
            <button
              type="button"
              role="radio"
              aria-checked={effectiveLoader === "fabric"}
              className={effectiveLoader === "fabric" ? "is-active" : ""}
              disabled={!fabricSupported}
              onClick={() => setLoader("fabric")}
            >
              <strong>Fabric</strong>
              <span>{fabricSupported ? "Mods im Ordner „mods“" : "Für diese Version nicht verfügbar"}</span>
            </button>
          </div>
          {effectiveLoader === "fabric" &&
            novaModVersions.length > 0 &&
            !novaModVersions.includes(gameVersion) && (
              <p className="profile-editor-hint">
                Für Minecraft {gameVersion} gibt es noch keine passende Nova-Mod-Version.
                Das Fabric-Profil wird ohne Nova-Mod erstellt.
              </p>
            )}
        </div>

        {effectiveLoader === "fabric" && fabricVersions && fabricVersions.length > 0 && (
          <div className="profile-editor-field">
            <label className="nova-field-label" htmlFor="profile-loader-version">FABRIC-LOADER</label>
            <select
              id="profile-loader-version"
              className="nova-select"
              value={loaderVersion}
              onChange={(event) => setLoaderVersion(event.target.value)}
            >
              <option value="">Neueste stabile Version</option>
              {fabricVersions.map((version) => (
                <option key={version} value={version}>{version}</option>
              ))}
            </select>
          </div>
        )}

        {error && <p className="profile-editor-error">{error}</p>}

        <div className="nova-modal-actions">
          <button type="button" className="nova-button nova-button-muted" onClick={onClose}>
            ABBRECHEN
          </button>
          <button
            type="button"
            className="nova-button nova-button-dark"
            disabled={saving || !gameVersion || !name.trim()}
            onClick={save}
          >
            {profile ? "SPEICHERN" : "ERSTELLEN"}
          </button>
        </div>
      </div>
    </div>
  );
}

function DeleteDialog({
  profile,
  onClose,
  onDeleted,
  onError,
}: {
  profile: GameProfile;
  onClose: () => void;
  onDeleted: (profiles: GameProfile[]) => void;
  onError: (message: string) => void;
}) {
  const [deleteFiles, setDeleteFiles] = useState(false);

  async function confirm() {
    try {
      onDeleted(await launcher.deleteProfile(profile.id, deleteFiles));
    } catch (error) {
      onError(errorMessage(error));
      onClose();
    }
  }

  return (
    <div className="nova-modal-backdrop" role="presentation" onMouseDown={onClose}>
      <div
        className="nova-modal delete-dialog pixel-corners"
        role="alertdialog"
        aria-modal="true"
        aria-labelledby="delete-title"
        onMouseDown={(event) => event.stopPropagation()}
      >
        <header className="nova-modal-header">
          <div>
            <span className="page-eyebrow">SPIELPROFIL</span>
            <h2 id="delete-title">LÖSCHEN?</h2>
          </div>
        </header>

        <p className="delete-text">
          <strong>{profile.name}</strong> wird aus der Liste entfernt.
        </p>

        <label className="delete-files">
          <Toggle checked={deleteFiles} onChange={setDeleteFiles} label="Welten und Mods löschen" />
          <span>
            Auch Welten, Mods und Screenshots löschen
            <small>Kann nicht rückgängig gemacht werden.</small>
          </span>
        </label>

        <div className="nova-modal-actions">
          <button type="button" className="nova-button nova-button-muted" onClick={onClose}>
            ABBRECHEN
          </button>
          <button type="button" className="nova-button nova-button-dark profile-delete-confirm" onClick={confirm}>
            LÖSCHEN
          </button>
        </div>
      </div>
    </div>
  );
}
