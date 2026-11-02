
import { MemoryBlocks } from "./LaunchBar";
import { Card, PageHeader, SettingRow, Toggle } from "./ui";
import { defaultSettings, type LauncherSettings } from "../data/launcherData";
import { errorMessage, launcher } from "../lib/launcher";
import packageInfo from "../../package.json";
import "./SettingsView.css";

type SettingsViewProps = {
  settings: LauncherSettings;
  onSettingsChange: (changes: Partial<LauncherSettings>) => void;
  onResetSettings: () => void;
  onShowWelcome: () => void;
  onOpenProfiles: () => void;
  onNotify: (message: string) => void;
};

function clampResolution(value: string, fallback: number) {
  const parsed = Number.parseInt(value, 10);
  if (Number.isNaN(parsed)) return fallback;
  return Math.min(Math.max(parsed, 320), 7680);
}

export default function SettingsView({
  settings,
  onSettingsChange,
  onResetSettings,
  onShowWelcome,
  onOpenProfiles,
  onNotify,
}: SettingsViewProps) {
  return (
    <div className="nova-page settings-page">
      <PageHeader
        eyebrow="LAUNCHER"
        title="EINSTELLUNGEN"
        description="Änderungen werden automatisch gespeichert und beim nächsten Spielstart angewendet."
      />

      <div className="settings-grid">
        <Card title="SPIEL">
          <SettingRow title="Spielprofile" description="Versionen, Fabric und Mod-Ordner.">
            <button
              type="button"
              className="nova-button nova-button-muted settings-small-button"
              onClick={onOpenProfiles}
            >
              VERWALTEN
            </button>
          </SettingRow>

          <div className="settings-field">
            <div className="settings-field-heading">
              <span className="nova-field-label">ARBEITSSPEICHER</span>
              <strong>{settings.memory} GB</strong>
            </div>
            <MemoryBlocks
              className="is-light"
              value={settings.memory}
              onChange={(memory) => onSettingsChange({ memory })}
            />
            <p className="settings-hint">Empfohlen: 4–8 GB. Mehr hilft nur bei großen Modpacks.</p>
          </div>

          <div className="settings-field">
            <span className="nova-field-label">AUFLÖSUNG</span>
            <div className="settings-resolution">
              <input
                className="nova-input"
                type="number"
                min={320}
                aria-label="Breite"
                value={settings.resolutionWidth}
                disabled={settings.fullscreen}
                onChange={(event) =>
                  onSettingsChange({
                    resolutionWidth: clampResolution(
                      event.target.value,
                      settings.resolutionWidth,
                    ),
                  })
                }
              />
              <span>×</span>
              <input
                className="nova-input"
                type="number"
                min={320}
                aria-label="Höhe"
                value={settings.resolutionHeight}
                disabled={settings.fullscreen}
                onChange={(event) =>
                  onSettingsChange({
                    resolutionHeight: clampResolution(
                      event.target.value,
                      settings.resolutionHeight,
                    ),
                  })
                }
              />
            </div>
          </div>

          <SettingRow title="Vollbild" description="Startet Minecraft direkt im Vollbildmodus.">
            <Toggle
              checked={settings.fullscreen}
              onChange={(fullscreen) => onSettingsChange({ fullscreen })}
              label="Vollbild"
            />
          </SettingRow>

          <SettingRow
            title="Performance-Mods"
            description="Installiert Sodium, Lithium, FerriteCore, ImmediatelyFast und Entity Culling in Fabric-Profile (ab 1.20)."
          >
            <Toggle
              checked={settings.performanceMods}
              onChange={(performanceMods) => onSettingsChange({ performanceMods })}
              label="Performance-Mods"
            />
          </SettingRow>
        </Card>

        <div className="settings-column">
          <Card title="JAVA">
            <div className="settings-field">
              <label className="nova-field-label" htmlFor="settings-java">
                JAVA-PFAD
              </label>
              <input
                id="settings-java"
                className="nova-input"
                value={settings.javaPath}
                placeholder="Automatisch (empfohlen)"
                onChange={(event) => onSettingsChange({ javaPath: event.target.value })}
              />
            </div>

            <div className="settings-field">
              <label className="nova-field-label" htmlFor="settings-jvm">
                JVM-ARGUMENTE
              </label>
              <textarea
                id="settings-jvm"
                className="nova-input"
                value={settings.jvmArgs}
                spellCheck={false}
                onChange={(event) => onSettingsChange({ jvmArgs: event.target.value })}
              />
              <button
                type="button"
                className="settings-link"
                onClick={() => onSettingsChange({ jvmArgs: defaultSettings.jvmArgs })}
              >
                STANDARD WIEDERHERSTELLEN
              </button>
            </div>
          </Card>

          <Card title="LAUNCHER">
            <div className="settings-field">
              <label className="nova-field-label" htmlFor="settings-after-launch">
                NACH DEM SPIELSTART
              </label>
              <select
                id="settings-after-launch"
                className="nova-select"
                value={settings.afterLaunch}
                onChange={(event) =>
                  onSettingsChange({
                    afterLaunch: event.target.value as LauncherSettings["afterLaunch"],
                  })
                }
              >
                <option value="keep">Launcher geöffnet lassen</option>
                <option value="minimize">Launcher minimieren</option>
                <option value="hide">Launcher ausblenden, bis das Spiel endet</option>
              </select>
            </div>

            <SettingRow
              title="Discord Rich Presence"
              description="Zeigt auf Discord, was du gerade spielst."
            >
              <Toggle
                checked={settings.discordPresence}
                onChange={(discordPresence) => onSettingsChange({ discordPresence })}
                label="Discord Rich Presence"
              />
            </SettingRow>
          </Card>
        </div>

        <div className="settings-column">
          <Card title="DATEN">
            <SettingRow title="Launcher-Daten" description="Versionen, Java, Bibliotheken und Profile.">
              <button
                type="button"
                className="nova-button nova-button-muted settings-small-button"
                onClick={() =>
                  launcher.openDataFolder().catch((error) => onNotify(errorMessage(error)))
                }
              >
                ÖFFNEN
              </button>
            </SettingRow>
            <SettingRow title="Willkommensbildschirm" description="Zeigt den Start-Screen erneut an.">
              <button
                type="button"
                className="nova-button nova-button-muted settings-small-button"
                onClick={onShowWelcome}
              >
                ANZEIGEN
              </button>
            </SettingRow>
            <SettingRow title="Zurücksetzen" description="Alle Einstellungen auf Standard.">
              <button
                type="button"
                className="nova-button nova-button-dark settings-small-button"
                onClick={() => {
                  onResetSettings();
                  onNotify("Einstellungen zurückgesetzt.");
                }}
              >
                RESET
              </button>
            </SettingRow>
          </Card>

          <Card dark className="settings-about">
            <span className="page-eyebrow">ÜBER</span>
            <strong>NOVA LAUNCHER</strong>
            <span>Version {packageInfo.version}</span>
          </Card>
        </div>
      </div>
    </div>
  );
}
