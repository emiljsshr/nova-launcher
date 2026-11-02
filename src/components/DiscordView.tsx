
import { openUrl } from "@tauri-apps/plugin-opener";
import { Card, PageHeader, SettingRow, Toggle } from "./ui";
import {
  DISCORD_INVITE_URL,
  discordAnnouncements,
  discordChannels,
  discordStats,
  type LauncherSettings,
} from "../data/launcherData";
import "./DiscordView.css";

type DiscordViewProps = {
  settings: LauncherSettings;
  onSettingsChange: (changes: Partial<LauncherSettings>) => void;
  onNotify: (message: string) => void;
};

function ChatIcon() {
  return (
    <svg
      className="discord-chat-icon"
      viewBox="0 0 16 16"
      shapeRendering="crispEdges"
      aria-hidden="true"
    >
      <path fill="#9cc2f7" d="M2 2h12v1h1v8h-1v1H8v1H7v1H6v1H5v-3H2v-1H1V3h1z" />
      <path fill="#e3eefd" d="M2 3h12v1H2z" />
      <path fill="#121a36" d="M4 6h2v2H4zm3 0h2v2H7zm3 0h2v2h-2z" />
    </svg>
  );
}

export default function DiscordView({
  settings,
  onSettingsChange,
  onNotify,
}: DiscordViewProps) {
  async function joinDiscord() {
    if (!DISCORD_INVITE_URL) {
      onNotify("Der Discord-Einladungslink ist noch nicht hinterlegt.");
      return;
    }

    try {
      await openUrl(DISCORD_INVITE_URL);
    } catch {
      window.open(DISCORD_INVITE_URL, "_blank", "noopener");
    }
  }

  return (
    <div className="nova-page discord-page">
      <PageHeader
        eyebrow="COMMUNITY"
        title="DISCORD"
        description="Neuigkeiten, Events und Support – direkt aus der Nova-Community."
      />

      <div className="discord-layout">
        <div className="discord-main">
          <Card dark className="discord-hero">
            <div className="discord-hero-copy">
              <ChatIcon />
              <div>
                <h2>NOVA COMMUNITY</h2>
                <p>
                  Finde Mitspieler, melde dich für Turniere an und erfahre als
                  Erstes von neuen Updates und Cosmetics.
                </p>
              </div>
            </div>

            <div className="discord-hero-footer">
              <dl className="discord-stats">
                {discordStats.map((stat) => (
                  <div key={stat.label}>
                    <dt>{stat.label}</dt>
                    <dd>{stat.value}</dd>
                  </div>
                ))}
              </dl>

              <button
                type="button"
                className="nova-button nova-button-frost discord-join"
                onClick={joinDiscord}
              >
                DISCORD BEITRETEN ↗
              </button>
            </div>
          </Card>

          <Card title="ANKÜNDIGUNGEN" meta="#ANKÜNDIGUNGEN">
            <ul className="discord-announcements">
              {discordAnnouncements.map((post) => (
                <li key={post.id}>
                  <div className="discord-post-head">
                    <span className="nova-chip is-frost">{post.tag}</span>
                    <span>{post.date}</span>
                  </div>
                  <strong>{post.title}</strong>
                  <p>{post.body}</p>
                </li>
              ))}
            </ul>
          </Card>
        </div>

        <aside className="discord-side">
          <Card title="CHANNELS" meta={String(discordChannels.length)}>
            <ul className="discord-channels">
              {discordChannels.map((channel) => (
                <li key={channel.name}>
                  <span className="discord-hash">#</span>
                  <div>
                    <strong>{channel.name}</strong>
                    <span>{channel.description}</span>
                  </div>
                </li>
              ))}
            </ul>
          </Card>

          <Card title="INTEGRATION">
            <SettingRow
              title="Rich Presence"
              description="Zeigt auf Discord, was du gerade spielst."
            >
              <Toggle
                checked={settings.discordPresence}
                onChange={(discordPresence) => onSettingsChange({ discordPresence })}
                label="Discord Rich Presence"
              />
            </SettingRow>
            <SettingRow
              title="Server anzeigen"
              description="Teilt den Server, auf dem du spielst, mit Freunden."
            >
              <Toggle
                checked={settings.showFriendActivity}
                onChange={(showFriendActivity) =>
                  onSettingsChange({ showFriendActivity })
                }
                label="Server mit Freunden teilen"
              />
            </SettingRow>
          </Card>
        </aside>
      </div>
    </div>
  );
}
