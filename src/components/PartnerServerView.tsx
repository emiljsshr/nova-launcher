
import { useMemo, useState } from "react";
import { ServerBanner, ServerIcon } from "./PixelIcon";
import { Card, PageHeader, SearchField, Tabs, formatNumber } from "./ui";
import {
  partnerServers,
  type PartnerServer,
  type ServerCategory,
} from "../data/launcherData";
import "./PartnerServerView.css";

type CategoryFilter = "ALL" | ServerCategory;

const categoryLabels: Record<CategoryFilter, string> = {
  ALL: "ALLE",
  PVP: "PVP",
  SURVIVAL: "SURVIVAL",
  SKYBLOCK: "SKYBLOCK",
  MINIGAMES: "MINIGAMES",
  CREATIVE: "CREATIVE",
};

const totalPlayers = partnerServers.reduce(
  (sum, server) => sum + server.players,
  0,
);

type PartnerServerViewProps = {
  onJoin: (address: string) => void;
  onNotify: (message: string) => void;
};

async function copyToClipboard(text: string) {
  try {
    await navigator.clipboard.writeText(text);
    return true;
  } catch {
    return false;
  }
}

export default function PartnerServerView({ onJoin, onNotify }: PartnerServerViewProps) {
  const [category, setCategory] = useState<CategoryFilter>("ALL");
  const [search, setSearch] = useState("");
  const [selectedId, setSelectedId] = useState(partnerServers[0].id);

  const selected =
    partnerServers.find((server) => server.id === selectedId) ??
    partnerServers[0];

  const filteredServers = useMemo(() => {
    const query = search.trim().toLowerCase();

    return partnerServers.filter(
      (server) =>
        (category === "ALL" || server.categories.includes(category)) &&
        (server.name.toLowerCase().includes(query) ||
          server.modes.toLowerCase().includes(query)),
    );
  }, [category, search]);

  const tabs = (Object.keys(categoryLabels) as CategoryFilter[]).map(
    (value) => ({
      value,
      label: categoryLabels[value],
      count:
        value === "ALL"
          ? partnerServers.length
          : partnerServers.filter((server) => server.categories.includes(value))
              .length,
    }),
  );

  // Startet das ausgewählte Spielprofil und verbindet direkt mit dem Server
  function joinServer(server: PartnerServer) {
    onJoin(server.address);
  }

  return (
    <div className="nova-page partner-page">
      <PageHeader
        eyebrow="NOVA PARTNERPROGRAMM"
        title="PARTNER SERVER"
        description="Handverlesene Server mit Nova-Integration, Events und exklusiven Belohnungen."
        actions={
          <div className="partner-header-stat">
            <span className="status-square is-online" />
            <strong>{formatNumber(totalPlayers)}</strong>
            <span>SPIELER AUF PARTNERN</span>
          </div>
        }
      />

      <div className="partner-layout">
        <div className="partner-list-column">
          <div className="partner-toolbar">
            <Tabs
              items={tabs}
              value={category}
              onChange={setCategory}
              label="Kategorien"
            />
            <SearchField
              value={search}
              onChange={setSearch}
              placeholder="Server suchen..."
            />
          </div>

          <Card className="partner-list-card">
            <div className="partner-list-head" aria-hidden="true">
              <span>SERVER</span>
              <span>VERSION</span>
              <span>SPIELER</span>
              <span />
            </div>

            <ul className="partner-list">
              {filteredServers.map((server) => {
                const load = server.players / server.maxPlayers;
                return (
                  <li key={server.id}>
                    <div
                      role="button"
                      tabIndex={0}
                      className={`partner-row ${
                        server.id === selected.id ? "is-selected" : ""
                      }`}
                      onClick={() => setSelectedId(server.id)}
                      onKeyDown={(event) => {
                        if (event.key === "Enter" || event.key === " ") {
                          event.preventDefault();
                          setSelectedId(server.id);
                        }
                      }}
                    >
                      <div className="partner-row-name">
                        <ServerIcon variant={server.icon} size={44} />
                        <div className="partner-row-copy">
                          <strong>
                            {server.name}
                            {server.badge && (
                              <span className={`nova-chip partner-badge is-${server.badge.toLowerCase()}`}>
                                {server.badge === "VERIFIED" ? "✔ VERIFIED" : "+ PARTNER"}
                              </span>
                            )}
                          </strong>
                          <span>{server.modes}</span>
                        </div>
                      </div>

                      <span className="partner-row-version">{server.version}</span>

                      <div className="partner-row-players">
                        <span>
                          <span className="status-square is-online" />
                          {formatNumber(server.players)}
                        </span>
                        <span className="partner-load" aria-hidden="true">
                          <span style={{ width: `${Math.round(load * 100)}%` }} />
                        </span>
                      </div>

                      <button
                        type="button"
                        className="nova-button nova-button-dark partner-row-join"
                        onClick={(event) => {
                          event.stopPropagation();
                          joinServer(server);
                        }}
                      >
                        BEITRETEN
                      </button>
                    </div>
                  </li>
                );
              })}
            </ul>

            {filteredServers.length === 0 && (
              <div className="nova-empty">
                <strong>KEINE SERVER GEFUNDEN</strong>
                <span>Versuche eine andere Kategorie oder Suche.</span>
              </div>
            )}
          </Card>
        </div>

        <aside className="partner-detail-column">
          <Card className="partner-detail-card" key={selected.id}>
            <div className={`partner-detail-banner ${selected.banner ? "is-image" : ""}`}>
              {selected.banner ? (
                <img src={selected.banner} alt={`Banner von ${selected.name}`} draggable={false} />
              ) : (
                <ServerBanner variant={selected.icon} />
              )}
              <div className="partner-detail-icon">
                <ServerIcon variant={selected.icon} size={72} />
              </div>
            </div>

            <div className="partner-detail-body">
              <div className="partner-detail-title">
                <h2>{selected.name}</h2>
                <span>{selected.modes}</span>
              </div>

              <p>{selected.description}</p>

              <dl className="partner-detail-stats">
                <div>
                  <dt>SPIELER</dt>
                  <dd>{formatNumber(selected.players)}</dd>
                </div>
                <div>
                  <dt>VERSION</dt>
                  <dd>{selected.version}</dd>
                </div>
                <div>
                  <dt>REGION</dt>
                  <dd>{selected.region}</dd>
                </div>
              </dl>

              <div className="partner-address">
                <span className="nova-field-label">SERVERADRESSE</span>
                <div>
                  <code>{selected.address}</code>
                  <button
                    type="button"
                    onClick={async () =>
                      onNotify(
                        (await copyToClipboard(selected.address))
                          ? "Adresse kopiert."
                          : "Kopieren nicht möglich.",
                      )
                    }
                  >
                    KOPIEREN
                  </button>
                </div>
              </div>

              <button
                type="button"
                className="nova-button nova-button-frost partner-detail-join"
                onClick={() => joinServer(selected)}
              >
                ▶ JETZT BEITRETEN
              </button>
            </div>
          </Card>

          <Card className="partner-apply-card">
            <strong>Du betreibst einen Server?</strong>
            <span>Werde Nova-Partner und erreiche tausende Spieler direkt im Launcher.</span>
            <button
              type="button"
              className="nova-button nova-button-muted"
              onClick={() => onNotify("Partner-Bewerbungen öffnen bald.")}
            >
              PARTNER WERDEN
            </button>
          </Card>
        </aside>
      </div>
    </div>
  );
}
