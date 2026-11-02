import { convertFileSrc } from "@tauri-apps/api/core";
import { useCallback, useEffect, useMemo, useState } from "react";
import { Card, PageHeader, Tabs } from "./ui";
import { errorMessage, launcher, type GameProfile, type Screenshot } from "../lib/launcher";
import "./ScreenshotsView.css";

type ScreenshotsViewProps = {
  profiles: GameProfile[];
  onNotify: (message: string) => void;
};

const dateFormat = new Intl.DateTimeFormat("de-DE", {
  day: "2-digit",
  month: "2-digit",
  year: "numeric",
  hour: "2-digit",
  minute: "2-digit",
});

export default function ScreenshotsView({ profiles, onNotify }: ScreenshotsViewProps) {
  const [shots, setShots] = useState<Screenshot[] | null>(null);
  const [profileFilter, setProfileFilter] = useState("ALL");
  const [openIndex, setOpenIndex] = useState<number | null>(null);

  const refresh = useCallback(() => {
    launcher
      .listScreenshots()
      .then(setShots)
      .catch((error) => {
        setShots([]);
        onNotify(errorMessage(error));
      });
  }, [onNotify]);

  useEffect(refresh, [refresh]);

  const visible = useMemo(
    () => (shots ?? []).filter((shot) => profileFilter === "ALL" || shot.profileId === profileFilter),
    [shots, profileFilter],
  );
  const open = openIndex === null ? null : visible[openIndex] ?? null;

  const tabs = [
    { value: "ALL", label: "ALLE", count: shots?.length ?? 0 },
    ...profiles
      .map((profile) => ({
        value: profile.id,
        label: profile.name.toUpperCase(),
        count: (shots ?? []).filter((shot) => shot.profileId === profile.id).length,
      }))
      .filter((tab) => tab.count > 0),
  ];

  // Pfeiltasten blättern, Escape schließt
  useEffect(() => {
    if (openIndex === null) return;
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") setOpenIndex(null);
      if (event.key === "ArrowRight") setOpenIndex((index) => (index === null ? null : Math.min(index + 1, visible.length - 1)));
      if (event.key === "ArrowLeft") setOpenIndex((index) => (index === null ? null : Math.max(index - 1, 0)));
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [openIndex, visible.length]);

  async function remove(shot: Screenshot) {
    if (!window.confirm(`${shot.file} wirklich löschen?`)) return;
    try {
      await launcher.deleteScreenshot(shot.profileId, shot.file);
      setShots((current) => (current ?? []).filter((entry) => entry.path !== shot.path));
      setOpenIndex((index) => (index === null || visible.length <= 1 ? null : Math.min(index, visible.length - 2)));
    } catch (error) {
      onNotify(errorMessage(error));
    }
  }

  function openExternal(shot: Screenshot | null, profileId: string) {
    launcher.openScreenshot(profileId, shot?.file ?? null).catch((error) => onNotify(errorMessage(error)));
  }

  const folderProfile = profileFilter !== "ALL" ? profileFilter : profiles[0]?.id;

  return (
    <div className="nova-page screenshots-page">
      <PageHeader
        eyebrow="GALERIE"
        title="SCREENSHOTS"
        description="Alle Screenshots aus deinen Spielprofilen. Im Spiel machst du einen mit F2."
        actions={
          <>
            <button type="button" className="nova-button nova-button-muted" onClick={refresh}>
              AKTUALISIEREN
            </button>
            {folderProfile && (
              <button type="button" className="nova-button nova-button-dark" onClick={() => openExternal(null, folderProfile)}>
                ORDNER ÖFFNEN
              </button>
            )}
          </>
        }
      />

      {tabs.length > 2 && <Tabs items={tabs} value={profileFilter} onChange={setProfileFilter} label="Profile" />}

      {shots === null ? (
        <p className="screenshots-hint">Screenshots werden geladen …</p>
      ) : visible.length === 0 ? (
        <Card>
          <p className="screenshots-hint">Noch keine Screenshots. Drück im Spiel F2 – sie erscheinen dann hier.</p>
        </Card>
      ) : (
        <ul className="screenshots-grid">
          {visible.map((shot, index) => (
            <li key={shot.path}>
              <button type="button" className="screenshot-tile" onClick={() => setOpenIndex(index)}>
                <img src={convertFileSrc(shot.path)} alt={shot.file} loading="lazy" draggable={false} />
                <span>
                  <strong>{dateFormat.format(new Date(shot.takenAt * 1000))}</strong>
                  <span>{shot.profileName}</span>
                </span>
              </button>
            </li>
          ))}
        </ul>
      )}

      {open && openIndex !== null && (
        <div className="screenshot-lightbox" role="dialog" aria-label={open.file} onClick={() => setOpenIndex(null)}>
          <div className="screenshot-lightbox-body" onClick={(event) => event.stopPropagation()}>
            <img src={convertFileSrc(open.path)} alt={open.file} draggable={false} />
            <footer>
              <div>
                <strong>{open.file}</strong>
                <span>
                  {open.profileName} · {dateFormat.format(new Date(open.takenAt * 1000))} · {(open.size / 1024 / 1024).toFixed(1)} MB
                </span>
              </div>
              <button type="button" aria-label="Vorheriger" disabled={openIndex === 0} onClick={() => setOpenIndex(openIndex - 1)}>
                ◀
              </button>
              <button
                type="button"
                aria-label="Nächster"
                disabled={openIndex === visible.length - 1}
                onClick={() => setOpenIndex(openIndex + 1)}
              >
                ▶
              </button>
              <button type="button" onClick={() => openExternal(open, open.profileId)}>
                ÖFFNEN
              </button>
              <button type="button" className="is-danger" onClick={() => remove(open)}>
                LÖSCHEN
              </button>
              <button type="button" aria-label="Schließen" onClick={() => setOpenIndex(null)}>
                ✕
              </button>
            </footer>
          </div>
        </div>
      )}
    </div>
  );
}
