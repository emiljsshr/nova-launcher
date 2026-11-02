import { relaunch } from "@tauri-apps/plugin-process";
import { check, type Update } from "@tauri-apps/plugin-updater";
import { useCallback, useEffect, useState } from "react";
import { errorMessage, inTauri } from "../lib/launcher";

export type UpdateState =
  | { status: "idle" }
  | { status: "available"; version: string; notes: string }
  | { status: "installing"; version: string; progress: number | null };

// Danach erneut nachsehen, falls der Launcher lange offen bleibt
const CHECK_INTERVAL = 6 * 60 * 60 * 1000;

/**
 * Sucht beim Start (und alle 6 Stunden) nach einer neuen Launcher-Version auf GitHub Releases.
 * Die Nova-Mod steckt im Launcher (src-tauri/resources/nova-mods) und kommt mit demselben Update.
 */
export function useUpdater(onNotify: (message: string) => void) {
  const [update, setUpdate] = useState<Update | null>(null);
  const [state, setState] = useState<UpdateState>({ status: "idle" });

  useEffect(() => {
    // Im Entwicklungsmodus gibt es keine signierten Releases zum Vergleichen
    if (!inTauri || import.meta.env.DEV) return;
    let cancelled = false;
    const run = () =>
      check()
        .then((found) => {
          if (cancelled || !found) return;
          setUpdate(found);
          setState({ status: "available", version: found.version, notes: found.body ?? "" });
        })
        .catch(() => undefined);
    run();
    const timer = window.setInterval(run, CHECK_INTERVAL);
    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, []);

  const install = useCallback(async () => {
    if (!update) return;
    let total = 0;
    let done = 0;
    setState({ status: "installing", version: update.version, progress: null });
    try {
      await update.downloadAndInstall((event) => {
        if (event.event === "Started") total = event.data.contentLength ?? 0;
        if (event.event === "Progress") {
          done += event.data.chunkLength;
          setState({ status: "installing", version: update.version, progress: total ? done / total : null });
        }
      });
      await relaunch();
    } catch (error) {
      setState({ status: "available", version: update.version, notes: update.body ?? "" });
      onNotify(`Update fehlgeschlagen: ${errorMessage(error)}`);
    }
  }, [update, onNotify]);

  return { state, install };
}
