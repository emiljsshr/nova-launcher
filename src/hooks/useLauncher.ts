
import { useCallback, useEffect, useRef, useState } from "react";
import { listen } from "@tauri-apps/api/event";
import { getCurrentWindow } from "@tauri-apps/api/window";
import {
  errorMessage,
  inTauri,
  launcher,
  type Account,
  type GameExit,
  type GameLog,
  type GameProfile,
  type LaunchProgress,
} from "../lib/launcher";
import type { LauncherSettings } from "../data/launcherData";
import { usePersistentState } from "./usePersistentState";

const MAX_LOG_LINES = 1500;

export type GameState =
  | { status: "idle" }
  | { status: "preparing"; profileId: string; progress: LaunchProgress | null }
  | { status: "running"; profileId: string };

export function useLauncher(
  settings: LauncherSettings,
  notify: (message: string) => void,
) {
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [profiles, setProfiles] = useState<GameProfile[]>([]);
  const [selectedProfileId, setSelectedProfileId] = usePersistentState(
    "nova.selectedProfile",
    "",
  );
  const [game, setGame] = useState<GameState>({ status: "idle" });
  const [logs, setLogs] = useState<GameLog[]>([]);
  const [skinDataUrl, setSkinDataUrl] = useState<string | null>(null);

  // Events lesen die aktuellen Werte über Refs, ohne die Listener neu zu registrieren
  const settingsRef = useRef(settings);
  const notifyRef = useRef(notify);
  settingsRef.current = settings;
  notifyRef.current = notify;

  const activeAccount = accounts.find((account) => account.active) ?? null;
  const selectedProfile =
    profiles.find((profile) => profile.id === selectedProfileId) ?? profiles[0] ?? null;

  const refreshProfiles = useCallback(async () => {
    try {
      setProfiles(await launcher.listProfiles());
    } catch (error) {
      notifyRef.current(errorMessage(error));
    }
  }, []);

  useEffect(() => {
    if (!inTauri) return;

    launcher.listAccounts().then(setAccounts).catch(() => undefined);
    refreshProfiles();
    launcher
      .status()
      .then((profileId) => {
        if (profileId) setGame({ status: "running", profileId });
      })
      .catch(() => undefined);

    const unlisteners = [
      listen<LaunchProgress>("launch-progress", (event) => {
        setGame((current) =>
          current.status === "preparing"
            ? { ...current, progress: event.payload }
            : current,
        );
      }),
      listen<string>("game-started", (event) => {
        setGame({ status: "running", profileId: event.payload });
        const appWindow = getCurrentWindow();
        if (settingsRef.current.afterLaunch === "minimize") appWindow.minimize();
        if (settingsRef.current.afterLaunch === "hide") appWindow.hide();
      }),
      listen<GameLog>("game-log", (event) => {
        setLogs((current) => {
          const next = [...current, event.payload];
          return next.length > MAX_LOG_LINES ? next.slice(-MAX_LOG_LINES) : next;
        });
      }),
      listen<GameExit>("game-exit", async (event) => {
        setGame({ status: "idle" });
        refreshProfiles();

        const appWindow = getCurrentWindow();
        await appWindow.show();
        await appWindow.unminimize();
        await appWindow.setFocus();

        const { code } = event.payload;
        if (code !== 0 && code !== null) {
          notifyRef.current(
            `Minecraft wurde mit Code ${code} beendet – Details in der Spielkonsole unter Profile.`,
          );
        }
      }),
    ];

    return () => {
      unlisteners.forEach((promise) => promise.then((unlisten) => unlisten()));
    };
  }, [refreshProfiles]);

  // Skin des aktiven Kontos als data-URL laden (für 3D-Ansicht und Kopf)
  useEffect(() => {
    const url = activeAccount?.skinUrl;
    if (!url) {
      setSkinDataUrl(null);
      return;
    }

    let cancelled = false;
    launcher
      .skinData(url)
      .then((data) => {
        if (!cancelled) setSkinDataUrl(data);
      })
      .catch(() => {
        if (!cancelled) setSkinDataUrl(null);
      });
    return () => {
      cancelled = true;
    };
  }, [activeAccount?.skinUrl]);

  const launch = useCallback(
    async (profileId: string, server: string | null = null, cosmetics: string[] = []) => {
      const current = settingsRef.current;
      setLogs([]);
      setGame({ status: "preparing", profileId, progress: null });

      try {
        await launcher.launch(profileId, {
          memoryMb: current.memory * 1024,
          jvmArgs: current.jvmArgs,
          javaPath: current.javaPath,
          width: current.resolutionWidth,
          height: current.resolutionHeight,
          fullscreen: current.fullscreen,
          server,
          cosmetics,
        });
      } catch (error) {
        setGame({ status: "idle" });
        notifyRef.current(errorMessage(error));
      }
    },
    [],
  );

  const stop = useCallback(async () => {
    try {
      await launcher.stop();
    } catch (error) {
      notifyRef.current(errorMessage(error));
    }
  }, []);

  return {
    accounts,
    setAccounts,
    activeAccount,
    skinDataUrl,
    profiles,
    setProfiles,
    refreshProfiles,
    selectedProfile,
    setSelectedProfileId,
    game,
    logs,
    launch,
    stop,
  };
}

export type LauncherApi = ReturnType<typeof useLauncher>;
