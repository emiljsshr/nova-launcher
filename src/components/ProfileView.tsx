
import { useState } from "react";
import { open } from "@tauri-apps/plugin-dialog";
import MinecraftSkinViewer from "./MinecraftSkinViewer";
import { ServerIcon } from "./PixelIcon";
import SkinHead from "./SkinHead";
import { Card, PageHeader } from "./ui";
import { profileSubtitle } from "./LaunchBar";
import {
  errorMessage,
  formatPlayTime,
  formatRelative,
  launcher,
  type Account,
  type GameProfile,
} from "../lib/launcher";
import "./ProfileView.css";

type ProfileViewProps = {
  accounts: Account[];
  activeAccount: Account | null;
  skin: string | null;
  profiles: GameProfile[];
  onAccountsChange: (accounts: Account[]) => void;
  onLogin: () => void;
  onNotify: (message: string) => void;
  onOpenCosmetics: () => void;
};

const NAME_PATTERN = /^[A-Za-z0-9_]{3,16}$/;

function formatUuid(id: string) {
  return id.length === 32
    ? `${id.slice(0, 8)}-${id.slice(8, 12)}-${id.slice(12, 16)}-${id.slice(16, 20)}-${id.slice(20)}`
    : id;
}

export default function ProfileView({
  accounts,
  activeAccount,
  skin,
  profiles,
  onAccountsChange,
  onLogin,
  onNotify,
  onOpenCosmetics,
}: ProfileViewProps) {
  const [skinModel, setSkinModel] = useState<"classic" | "slim">(
    activeAccount?.skinVariant ?? "classic",
  );
  const [newName, setNewName] = useState("");
  const [busy, setBusy] = useState<"skin" | "name" | null>(null);
  const [nameHint, setNameHint] = useState("");

  // Skin und Name gehören zum Microsoft-Konto – Offline-Testkonten haben beides nicht
  const canEditProfile = Boolean(activeAccount && !activeAccount.offline);

  const totalSeconds = profiles.reduce((sum, profile) => sum + profile.playSeconds, 0);
  const totalSessions = profiles.reduce((sum, profile) => sum + profile.sessions, 0);
  const recentProfiles = [...profiles]
    .filter((profile) => profile.lastPlayed)
    .sort((a, b) => (b.lastPlayed ?? 0) - (a.lastPlayed ?? 0))
    .slice(0, 4);
  const lastPlayed = recentProfiles[0]?.lastPlayed ?? null;

  async function run(kind: "skin" | "name", action: () => Promise<Account[]>, success: string) {
    setBusy(kind);
    try {
      onAccountsChange(await action());
      onNotify(success);
      return true;
    } catch (error) {
      onNotify(errorMessage(error));
      return false;
    } finally {
      setBusy(null);
    }
  }

  async function uploadSkin() {
    const path = await open({
      multiple: false,
      directory: false,
      filters: [{ name: "Minecraft-Skin", extensions: ["png"] }],
    }).catch(() => null);
    if (typeof path !== "string") return;

    await run("skin", () => launcher.uploadSkin(path, skinModel), "Skin hochgeladen.");
  }

  async function changeName() {
    const name = newName.trim();
    if (!NAME_PATTERN.test(name)) {
      setNameHint("3–16 Zeichen: Buchstaben, Zahlen und _");
      return;
    }

    setBusy("name");
    try {
      const status = await launcher.nameAvailable(name);
      if (status !== "AVAILABLE") {
        setNameHint(status === "DUPLICATE" ? "Dieser Name ist bereits vergeben." : "Dieser Name ist nicht erlaubt.");
        setBusy(null);
        return;
      }
    } catch (error) {
      setNameHint(errorMessage(error));
      setBusy(null);
      return;
    }

    if (await run("name", () => launcher.changeName(name), `Du heißt jetzt ${name}.`)) {
      setNewName("");
      setNameHint("");
    }
  }

  return (
    <div className="nova-page profile-page">
      <PageHeader
        eyebrow="DEIN ACCOUNT"
        title="PROFIL"
        description="Microsoft-Konto, Skin, Minecraft-Name und deine Spielzeit."
      />

      <div className="profile-layout">
        <Card dark className="profile-hero">
          <div className="profile-stage">
            {skin || !activeAccount?.skinUrl ? (
              <MinecraftSkinViewer
                key={skin ?? "default"}
                skinUrl={skin ?? undefined}
                width={230}
                height={290}
              />
            ) : (
              <div className="profile-stage-loading">Skin wird geladen…</div>
            )}
          </div>

          {activeAccount ? (
            <>
              <div className="profile-identity">
                <span className="status-square is-online" />
                <h2>{activeAccount.name}</h2>
              </div>
              <span className="profile-uuid">
                {activeAccount.offline ? "OFFLINE-TESTKONTO · " : ""}
                {formatUuid(activeAccount.id)}
              </span>

              <div className="profile-skin-model" role="radiogroup" aria-label="Skin-Modell">
                {(["classic", "slim"] as const).map((model) => (
                  <button
                    key={model}
                    type="button"
                    role="radio"
                    aria-checked={skinModel === model}
                    className={skinModel === model ? "is-active" : ""}
                    onClick={() => setSkinModel(model)}
                  >
                    {model === "classic" ? "CLASSIC" : "SLIM"}
                  </button>
                ))}
              </div>

              <div className="profile-hero-actions">
                <button
                  type="button"
                  className="nova-button nova-button-light"
                  disabled={busy !== null || !canEditProfile}
                  onClick={uploadSkin}
                >
                  {busy === "skin" ? "LÄDT…" : "SKIN HOCHLADEN"}
                </button>
                <button
                  type="button"
                  className="nova-button nova-button-outline"
                  disabled={busy !== null || !canEditProfile}
                  onClick={() => run("skin", launcher.resetSkin, "Skin zurückgesetzt.")}
                >
                  ZURÜCKSETZEN
                </button>
              </div>
              <button type="button" className="profile-cosmetics-link" onClick={onOpenCosmetics}>
                NOVA COSMETICS →
              </button>
            </>
          ) : (
            <div className="profile-connect-hero">
              <h2>Nicht angemeldet</h2>
              <p>Melde dich mit dem Microsoft-Konto an, das Minecraft: Java Edition besitzt.</p>
              <button type="button" className="nova-button nova-button-frost" onClick={onLogin}>
                MIT MICROSOFT ANMELDEN
              </button>
            </div>
          )}
        </Card>

        <div className="profile-center">
          <div className="profile-stats">
            <div className="profile-stat pixel-corners">
              <span>SPIELZEIT</span>
              <strong>{formatPlayTime(totalSeconds)}</strong>
            </div>
            <div className="profile-stat pixel-corners">
              <span>SESSIONS</span>
              <strong>{totalSessions}</strong>
            </div>
            <div className="profile-stat pixel-corners">
              <span>SPIELPROFILE</span>
              <strong>{profiles.length}</strong>
            </div>
            <div className="profile-stat pixel-corners">
              <span>ZULETZT GESPIELT</span>
              <strong>{lastPlayed ? formatRelative(lastPlayed) : "—"}</strong>
            </div>
          </div>

          <Card title="ZULETZT GESPIELT">
            {recentProfiles.length === 0 ? (
              <p className="profile-empty">Sobald du spielst, erscheinen deine Profile hier.</p>
            ) : (
              <ul className="profile-activity">
                {recentProfiles.map((profile) => (
                  <li key={profile.id}>
                    <ServerIcon variant={profile.icon} size={36} />
                    <div>
                      <strong>{profile.name}</strong>
                      <span>
                        {profileSubtitle(profile)} · {formatRelative(profile.lastPlayed)} ·{" "}
                        {formatPlayTime(profile.playSeconds)}
                      </span>
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </Card>
        </div>

        <div className="profile-side">
          <Card title="MINECRAFT-NAME">
            <div className="profile-name-row">
              <input
                className="nova-input"
                value={newName}
                maxLength={16}
                disabled={!canEditProfile}
                placeholder={activeAccount?.name ?? "Erst anmelden"}
                onChange={(event) => {
                  setNewName(event.target.value);
                  setNameHint("");
                }}
                onKeyDown={(event) => {
                  if (event.key === "Enter") changeName();
                }}
              />
              <button
                type="button"
                className="nova-button nova-button-dark"
                disabled={!canEditProfile || !newName.trim() || busy !== null}
                onClick={changeName}
              >
                {busy === "name" ? "…" : "ÄNDERN"}
              </button>
            </div>
            <p className={`profile-hint ${nameHint ? "is-error" : ""}`}>
              {nameHint ||
                (activeAccount?.offline
                  ? "Nur mit einem Microsoft-Konto möglich."
                  : "Der Name lässt sich nur alle 30 Tage ändern.")}
            </p>
          </Card>

          <Card title="KONTEN" meta={String(accounts.length)}>
            <ul className="profile-accounts">
              {accounts.map((account) => (
                <li key={account.id} className={account.active ? "is-active" : ""}>
                  <SkinHead skin={account.active ? skin : null} size={32} />
                  <div>
                    <strong>{account.name}</strong>
                    <span>
                      {account.offline ? "Offline-Test" : "Microsoft"}
                      {account.active ? " · Aktiv" : ""}
                    </span>
                  </div>
                  {!account.active && (
                    <button
                      type="button"
                      className="nova-button nova-button-muted"
                      onClick={() =>
                        launcher
                          .selectAccount(account.id)
                          .then(onAccountsChange)
                          .catch((error) => onNotify(errorMessage(error)))
                      }
                    >
                      WECHSELN
                    </button>
                  )}
                  <button
                    type="button"
                    className="nova-button nova-button-muted profile-account-remove"
                    aria-label={`${account.name} abmelden`}
                    onClick={() =>
                      launcher
                        .removeAccount(account.id)
                        .then((next) => {
                          onAccountsChange(next);
                          onNotify(`${account.name} abgemeldet.`);
                        })
                        .catch((error) => onNotify(errorMessage(error)))
                    }
                  >
                    ✕
                  </button>
                </li>
              ))}
            </ul>

            <button
              type="button"
              className="nova-button nova-button-frost profile-connect"
              onClick={onLogin}
            >
              {accounts.length === 0 ? "MIT MICROSOFT ANMELDEN" : "+ KONTO HINZUFÜGEN"}
            </button>
          </Card>
        </div>
      </div>
    </div>
  );
}
