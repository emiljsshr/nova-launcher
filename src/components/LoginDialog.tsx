
import { useEffect, useRef, useState } from "react";
import { openUrl } from "@tauri-apps/plugin-opener";
import {
  errorMessage,
  launcher,
  type Account,
  type DeviceCode,
  type LauncherInfo,
} from "../lib/launcher";
import "./LoginDialog.css";

type LoginDialogProps = {
  onAccounts: (accounts: Account[]) => void;
  onClose: () => void;
};

type Mode = "microsoft" | "offline";

// Microsoft-Anmeldung per Gerätecode; im Entwicklungsmodus zusätzlich Offline-Testkonten
export default function LoginDialog({ onAccounts, onClose }: LoginDialogProps) {
  const [info, setInfo] = useState<LauncherInfo | null>(null);
  const [mode, setMode] = useState<Mode>("microsoft");
  const [code, setCode] = useState<DeviceCode | null>(null);
  const [error, setError] = useState("");
  const [copied, setCopied] = useState(false);
  const [offlineName, setOfflineName] = useState("");
  // Jeder Anmeldeversuch bekommt eine Nummer; Antworten alter Versuche werden ignoriert
  const attempt = useRef(0);
  const running = useRef(false);

  useEffect(() => {
    launcher
      .info()
      .then((next) => {
        setInfo(next);
        if (!next.microsoftConfigured && next.devMode) setMode("offline");
      })
      .catch((infoError) => setError(errorMessage(infoError)));
  }, []);

  useEffect(() => {
    // StrictMode ruft Effekte doppelt auf – die Anmeldung darf nur einmal starten
    if (!info?.microsoftConfigured || mode !== "microsoft" || running.current) return;
    running.current = true;
    const id = ++attempt.current;

    (async () => {
      try {
        const deviceCode = await launcher.startLogin();
        if (attempt.current !== id) return;
        setCode(deviceCode);

        const accounts = await launcher.finishLogin(deviceCode);
        if (attempt.current !== id) return;
        onAccounts(accounts);
        onClose();
      } catch (loginError) {
        if (attempt.current === id) setError(errorMessage(loginError));
      }
    })();
  }, [info, mode, onAccounts, onClose]);

  function cancelMicrosoft() {
    attempt.current++;
    if (running.current) launcher.cancelLogin().catch(() => undefined);
    running.current = false;
  }

  function close() {
    cancelMicrosoft();
    onClose();
  }

  function switchToOffline() {
    cancelMicrosoft();
    setError("");
    setMode("offline");
  }

  async function openBrowser() {
    if (!code) return;
    try {
      await navigator.clipboard.writeText(code.userCode);
      setCopied(true);
    } catch {
      setCopied(false);
    }
    await openUrl(code.verificationUri).catch(() => undefined);
  }

  async function createOfflineAccount() {
    try {
      onAccounts(await launcher.offlineLogin(offlineName));
      onClose();
    } catch (offlineError) {
      setError(errorMessage(offlineError));
    }
  }

  function renderBody() {
    if (!info) {
      return error ? (
        <div className="login-state">
          <p className="login-error">{error}</p>
        </div>
      ) : (
        <div className="login-state">
          <span className="login-spinner" aria-hidden="true" />
        </div>
      );
    }

    if (mode === "offline") {
      return (
        <div className="login-offline">
          {!info.microsoftConfigured && (
            <p className="login-note">
              Die Microsoft-Anmeldung ist noch nicht eingerichtet (keine Azure Client-ID). Zum
              Testen kannst du ein Offline-Konto anlegen.
            </p>
          )}

          <label className="nova-field-label" htmlFor="offline-name">SPIELERNAME</label>
          <input
            id="offline-name"
            className="nova-input"
            value={offlineName}
            maxLength={16}
            placeholder="z. B. Steve_Nova"
            autoFocus
            onChange={(event) => {
              setOfflineName(event.target.value);
              setError("");
            }}
            onKeyDown={(event) => {
              if (event.key === "Enter") createOfflineAccount();
            }}
          />

          <p className="login-hint">
            Nur im Entwicklungsmodus verfügbar. Einzelspieler und Server im Offline-Modus
            funktionieren, Online-Server lehnen das Konto ab.
          </p>

          {error && <p className="login-error">{error}</p>}

          <div className="nova-modal-actions">
            {info.microsoftConfigured && (
              <button
                type="button"
                className="nova-button nova-button-muted"
                onClick={() => {
                  setError("");
                  setCode(null);
                  setMode("microsoft");
                }}
              >
                ZURÜCK
              </button>
            )}
            <button
              type="button"
              className="nova-button nova-button-dark"
              disabled={offlineName.trim().length < 3}
              onClick={createOfflineAccount}
            >
              TESTKONTO ANLEGEN
            </button>
          </div>
        </div>
      );
    }

    if (!info.microsoftConfigured) {
      return (
        <div className="login-state">
          <p className="login-note">
            Die Microsoft-Anmeldung ist in dieser Version nicht eingerichtet. Es fehlt die Azure
            Client-ID (siehe README).
          </p>
        </div>
      );
    }

    return (
      <>
        {error ? (
          <div className="login-state">
            <p className="login-error">{error}</p>
          </div>
        ) : !code ? (
          <div className="login-state">
            <span className="login-spinner" aria-hidden="true" />
            <p>Anmeldung wird vorbereitet…</p>
          </div>
        ) : (
          <div className="login-steps">
            <ol>
              <li>Öffne <strong>{code.verificationUri.replace(/^https?:\/\//, "")}</strong></li>
              <li>Gib diesen Code ein und melde dich mit dem Konto an, das Minecraft besitzt:</li>
            </ol>

            <div className="login-code" aria-label="Anmeldecode">
              {code.userCode}
            </div>

            <button
              type="button"
              className="nova-button nova-button-frost login-open"
              onClick={openBrowser}
            >
              {copied ? "CODE KOPIERT · BROWSER ÖFFNEN" : "CODE KOPIEREN & BROWSER ÖFFNEN"}
            </button>

            <p className="login-waiting">
              <span className="login-spinner is-small" aria-hidden="true" />
              Warte auf Bestätigung im Browser…
            </p>
          </div>
        )}

        {info.devMode && (
          <button type="button" className="login-offline-link" onClick={switchToOffline}>
            OFFLINE-TESTKONTO (ENTWICKLUNG) →
          </button>
        )}
      </>
    );
  }

  return (
    <div className="nova-modal-backdrop" role="presentation" onMouseDown={close}>
      <div
        className="nova-modal login-dialog pixel-corners"
        role="dialog"
        aria-modal="true"
        aria-labelledby="login-title"
        onMouseDown={(event) => event.stopPropagation()}
      >
        <header className="nova-modal-header">
          <div>
            <span className="page-eyebrow">
              {mode === "offline" ? "ENTWICKLUNGSMODUS" : "MICROSOFT-KONTO"}
            </span>
            <h2 id="login-title">{mode === "offline" ? "TESTKONTO" : "ANMELDEN"}</h2>
          </div>
          <button type="button" className="nova-modal-close" aria-label="Schließen" onClick={close}>
            ✕
          </button>
        </header>

        {renderBody()}
      </div>
    </div>
  );
}
