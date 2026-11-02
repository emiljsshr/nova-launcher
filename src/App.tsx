
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import "./App.css";
import novaLogo from "./assets/nova-logo.png";
import PartnerServerView from "./components/PartnerServerView";
import CosmeticsView, {
  defaultLoadout,
  equippedIds,
  loadoutFromIds,
  normalizeLoadout,
  type Loadout,
} from "./components/CosmeticsView";
import FriendsView from "./components/FriendsView";
import ScreenshotsView from "./components/ScreenshotsView";
import ProfileView from "./components/ProfileView";
import StoreView from "./components/StoreView";
import DiscordView from "./components/DiscordView";
import SettingsView from "./components/SettingsView";
import GameProfilesView from "./components/GameProfilesView";
import HomeView from "./components/HomeView";
import LaunchBar from "./components/LaunchBar";
import LoginDialog from "./components/LoginDialog";
import SkinHead from "./components/SkinHead";
import WelcomeScreen from "./components/WelcomeScreen";
import { PixelBackground, WindowControls } from "./components/WindowChrome";
import { usePersistentState } from "./hooks/usePersistentState";
import { useLauncher } from "./hooks/useLauncher";
import { useNews } from "./hooks/useNews";
import { useSocial, type ChatMessage, type Friend } from "./hooks/useSocial";
import { useUpdater } from "./hooks/useUpdater";
import { inTauri, launcher, type Account } from "./lib/launcher";
import {
  cosmetics,
  currentCollection,
  defaultSettings,
  type LauncherSettings,
} from "./data/launcherData";

const navigationItems = [
  "HOME",
  "FREUNDE",
  "PARTNER SERVER",
  "STORE",
  "SCREENSHOTS",
  "DISCORD",
  "PROFILE",
] as const;

// Diese Seiten sind über Buttons erreichbar, nicht über die Navigation
export type NavigationItem =
  | (typeof navigationItems)[number]
  | "COSMETICS"
  | "SETTINGS"
  | "GAME PROFILES";

const TOAST_DURATION = 4000;

function App() {
  const [onboarded, setOnboarded] = usePersistentState("nova.onboarded", false);
  const [settings, setSettings] = usePersistentState<LauncherSettings>(
    "nova.settings",
    defaultSettings,
  );

  const [storedLoadout, setSavedLoadout] = usePersistentState<Loadout>(
    "nova.loadout.v2",
    defaultLoadout,
  );
  const savedLoadout = useMemo(() => normalizeLoadout(storedLoadout), [storedLoadout]);
  const [draftLoadout, setDraftLoadout] = useState<Loadout>(savedLoadout);

  const [activePage, setActivePage] = useState<NavigationItem>("HOME");
  const [toast, setToast] = useState("");
  const [loginOpen, setLoginOpen] = useState(false);
  // Chat, der beim Öffnen der Freunde-Seite gezeigt wird
  const [chatTarget, setChatTarget] = useState<string | null>(null);

  const launcherApi = useLauncher(settings, setToast);
  const {
    accounts,
    setAccounts,
    activeAccount,
    skinDataUrl,
    profiles,
    setProfiles,
    selectedProfile,
    setSelectedProfileId,
    game,
    logs,
    launch,
    stop,
  } = launcherApi;

  const news = useNews();
  const updater = useUpdater(setToast);

  const runningProfile =
    game.status === "idle" ? null : profiles.find((profile) => profile.id === game.profileId) ?? null;

  // Seite und Chat für Benachrichtigungen, ohne den Hook bei jedem Seitenwechsel neu aufzubauen
  const viewRef = useRef({ activePage, chatTarget });
  viewRef.current = { activePage, chatTarget };

  const handleMessage = useCallback((message: ChatMessage, from: Friend | undefined) => {
    const { activePage: page, chatTarget: chat } = viewRef.current;
    if (page === "FREUNDE" && chat === message.from) return;
    setToast(`${from?.name ?? "Neue Nachricht"}: ${message.text}`);
  }, []);

  const handleGameLoadout = useCallback(
    (ids: string[]) => {
      const next = loadoutFromIds(ids);
      setSavedLoadout(next);
      setDraftLoadout(next);
    },
    [setSavedLoadout],
  );

  const social = useSocial({
    account: activeAccount,
    gameRunning: game.status === "running",
    gameVersion: runningProfile?.gameVersion ?? null,
    shareServer: settings.showFriendActivity,
    onNotify: setToast,
    onMessage: handleMessage,
    onGameLoadout: handleGameLoadout,
  });
  const unreadTotal = Object.values(social.unread).reduce((sum, count) => sum + count, 0);

  // Discord Rich Presence: Launcher oder Spiel mit Version und Server
  const [playingSince, setPlayingSince] = useState<number | null>(null);
  useEffect(() => {
    setPlayingSince(game.status === "running" ? Math.floor(Date.now() / 1000) : null);
  }, [game.status]);
  const ownPresence = social.presence;
  const discordDetails =
    game.status === "running" ? `Spielt Minecraft ${runningProfile?.gameVersion ?? ""}`.trim() : "Im Launcher";
  const discordState =
    game.status !== "running"
      ? null
      : ownPresence.state === "multiplayer"
        ? settings.showFriendActivity && ownPresence.server
          ? `Auf ${ownPresence.server}`
          : "Mehrspieler"
        : ownPresence.state === "singleplayer"
          ? "Einzelspieler"
          : "Im Hauptmenü";
  useEffect(() => {
    if (!inTauri) return;
    launcher
      .setDiscordPresence(
        settings.discordPresence ? { details: discordDetails, state: discordState, startedAt: playingSince } : null,
      )
      .catch(() => undefined);
  }, [settings.discordPresence, discordDetails, discordState, playingSince]);

  useEffect(() => {
    if (!toast) return;
    const timer = window.setTimeout(() => setToast(""), TOAST_DURATION);
    return () => window.clearTimeout(timer);
  }, [toast]);

  const loadoutDirty =
    JSON.stringify(draftLoadout) !== JSON.stringify(savedLoadout);

  function openFriends(friendId?: string) {
    setChatTarget(friendId ?? null);
    goTo("FREUNDE");
  }

  function goTo(page: NavigationItem) {
    if (activePage === "COSMETICS" && page !== "COSMETICS" && loadoutDirty) {
      setToast("Ungespeicherte Cosmetic-Änderungen wurden verworfen.");
    }
    // Garderobe startet immer mit dem gespeicherten Loadout
    setDraftLoadout(savedLoadout);
    setActivePage(page);
  }

  function saveLoadout() {
    setSavedLoadout(draftLoadout);
    setToast("Loadout gespeichert.");
  }

  function finishOnboarding(page: NavigationItem) {
    goTo(page);
    setOnboarded(true);
  }

  function updateSettings(changes: Partial<LauncherSettings>) {
    setSettings((current) => ({ ...current, ...changes }));
  }

  // Ohne Konto kein Start – stattdessen direkt zur Anmeldung
  function startGame(profileId: string | undefined, server: string | null = null) {
    if (!activeAccount) {
      setLoginOpen(true);
      return;
    }
    if (!profileId) {
      setToast("Lege zuerst ein Spielprofil an.");
      return;
    }
    if (game.status !== "idle") {
      setToast("Minecraft läuft bereits.");
      return;
    }
    const cosmeticIds = equippedIds(savedLoadout);
    launch(profileId, server, cosmeticIds);
  }

  const handleAccounts = useCallback(
    (next: Account[]) => setAccounts(next),
    [setAccounts],
  );
  const closeLogin = useCallback(() => setLoginOpen(false), []);

  if (!onboarded) {
    return (
      <WelcomeScreen
        navigationItems={navigationItems}
        onFinish={finishOnboarding}
      />
    );
  }

  function renderPage() {
    switch (activePage) {
      case "HOME":
        return (
          <HomeView
            news={news}
            friends={social.friends}
            signedIn={Boolean(activeAccount)}
            onOpenPartners={() => goTo("PARTNER SERVER")}
            onOpenCosmetics={() => goTo("COSMETICS")}
            onOpenFriends={openFriends}
            onNotify={setToast}
          />
        );
      case "FREUNDE":
        return (
          <FriendsView
            social={social}
            signedIn={Boolean(activeAccount)}
            onLogin={() => setLoginOpen(true)}
            initialChat={chatTarget}
            onChatChange={setChatTarget}
          />
        );
      case "SCREENSHOTS":
        return <ScreenshotsView profiles={profiles} onNotify={setToast} />;
      case "PARTNER SERVER":
        return (
          <PartnerServerView
            onJoin={(address) => startGame(selectedProfile?.id, address)}
            onNotify={setToast}
          />
        );
      case "STORE":
        return (
          <StoreView
            skin={skinDataUrl}
            onNotify={setToast}
            onOpenCosmetics={() => goTo("COSMETICS")}
          />
        );
      case "DISCORD":
        return (
          <DiscordView
            settings={settings}
            onSettingsChange={updateSettings}
            onNotify={setToast}
          />
        );
      case "PROFILE":
        return (
          <ProfileView
            accounts={accounts}
            activeAccount={activeAccount}
            skin={skinDataUrl}
            profiles={profiles}
            onAccountsChange={setAccounts}
            onLogin={() => setLoginOpen(true)}
            onNotify={setToast}
            onOpenCosmetics={() => goTo("COSMETICS")}
          />
        );
      case "COSMETICS":
        return (
          <CosmeticsView
            loadout={draftLoadout}
            onLoadoutChange={setDraftLoadout}
            onNotify={setToast}
          />
        );
      case "GAME PROFILES":
        return (
          <GameProfilesView
            profiles={profiles}
            onProfilesChange={setProfiles}
            selectedProfileId={selectedProfile?.id ?? null}
            onSelectProfile={setSelectedProfileId}
            game={game}
            logs={logs}
            onLaunch={(profileId) => startGame(profileId)}
            onStop={stop}
            onNotify={setToast}
          />
        );
      case "SETTINGS":
        return (
          <SettingsView
            settings={settings}
            onSettingsChange={updateSettings}
            onResetSettings={() => setSettings(defaultSettings)}
            onShowWelcome={() => setOnboarded(false)}
            onOpenProfiles={() => goTo("GAME PROFILES")}
            onNotify={setToast}
          />
        );
    }
  }

  return (
    <main className="launcher">
      <PixelBackground />

      <header className="topbar" data-tauri-drag-region>
        <button
          type="button"
          className="topbar-brand"
          onClick={() => goTo("HOME")}
          aria-label="NOVA Home"
        >
          <img src={novaLogo} alt="NOVA" draggable={false} />
        </button>

        <nav className="navigation" aria-label="Hauptnavigation">
          {navigationItems.map((item) => (
            <button
              key={item}
              type="button"
              className={`nav-link ${
                activePage === item ||
                (item === "STORE" && activePage === "COSMETICS")
                  ? "active"
                  : ""
              }`}
              onClick={() => goTo(item)}
            >
              {item === "PROFILE" && <SkinHead skin={skinDataUrl} size={16} />}
              {item}
              {item === "FREUNDE" && unreadTotal > 0 && (
                <span className="nav-badge">{unreadTotal}</span>
              )}
            </button>
          ))}
        </nav>

        {updater.state.status !== "idle" && (
          <button
            type="button"
            className="update-button"
            disabled={updater.state.status === "installing"}
            onClick={updater.install}
            title={updater.state.status === "available" ? updater.state.notes : undefined}
          >
            {updater.state.status === "installing"
              ? `UPDATE ${
                  updater.state.progress === null ? "…" : `${Math.round(updater.state.progress * 100)} %`
                }`
              : `UPDATE ${updater.state.version} ↓`}
          </button>
        )}

        <WindowControls />
      </header>

      <section
        key={activePage}
        className={`main-content ${
          activePage === "HOME" ? "is-home" : "is-page"
        }`}
      >
        {renderPage()}
      </section>

      {toast && (
        <p className="launcher-toast" role="status">
          {toast}
        </p>
      )}

      {loginOpen && (
        <LoginDialog onAccounts={handleAccounts} onClose={closeLogin} />
      )}

      <LaunchBar
        account={activeAccount}
        skin={skinDataUrl}
        onLogin={() => setLoginOpen(true)}
        onOpenAccount={() => goTo("PROFILE")}
        profiles={profiles}
        selectedProfile={selectedProfile}
        onSelectProfile={setSelectedProfileId}
        onManageProfiles={() => goTo("GAME PROFILES")}
        settings={settings}
        onSettingsChange={updateSettings}
        onOpenSettings={() =>
          goTo(activePage === "SETTINGS" ? "HOME" : "SETTINGS")
        }
        settingsActive={activePage === "SETTINGS"}
        game={game}
        onPlay={() => startGame(selectedProfile?.id)}
        onStop={stop}
        context={
          activePage === "COSMETICS" ? (
            <>
              <div className="launch-info">
                <span className="launch-label">AUSGERÜSTET</span>
                <strong>
                  {equippedIds(draftLoadout).length} von{" "}
                  {cosmetics.length} Cosmetics
                </strong>
              </div>
              <div className="launch-info">
                <span className="launch-label">KOLLEKTION</span>
                <strong>{currentCollection}</strong>
              </div>
            </>
          ) : undefined
        }
        actions={
          activePage === "COSMETICS" ? (
            <button
              type="button"
              className={`launch-secondary ${loadoutDirty ? "is-dirty" : ""}`}
              disabled={!loadoutDirty}
              onClick={saveLoadout}
            >
              SPEICHERN
            </button>
          ) : undefined
        }
      />
    </main>
  );
}

export default App;
