
import { useMemo, useState } from "react";
import { CharacterPreview, CosmeticThumbnail } from "./cosmetics/PixelArt";
import { cosmeticVisuals, type CharacterLoadout } from "./cosmetics/sprites";
import { seededRandom } from "./PixelIcon";
import { CoinIcon, Tabs, formatNumber } from "./ui";
import {
  coinBalance,
  cosmeticSectionLabels,
  cosmeticTypeLabels,
  cosmeticTypes,
  cosmetics,
  rarityLabels,
  type Cosmetic,
  type CosmeticType,
} from "../data/launcherData";
import "./CosmeticsView.css";

// Pro Typ eine Liste angelegter Items; am Kopf passen mehrere zusammen (z. B. Krone, Halo und Hörner)
export type Loadout = Record<CosmeticType, string[]>;

export const slotLimits: Record<CosmeticType, number> = {
  CAPE: 1,
  HEAD: 3,
  WINGS: 1,
  AURA: 1,
  PET: 1,
  TRAIL: 1,
};

export const defaultLoadout: Loadout = {
  CAPE: ["frost-cape"],
  HEAD: ["ice-crown"],
  WINGS: ["crystal-wings"],
  AURA: [],
  PET: [],
  TRAIL: [],
};

// Ältere Speicherstände hatten ein Item (oder null) pro Typ
export function normalizeLoadout(saved: Record<string, unknown>): Loadout {
  const loadout = { ...defaultLoadout };
  for (const type of cosmeticTypes) {
    const value = saved[type];
    const ids = Array.isArray(value) ? value : value ? [value] : [];
    loadout[type] = ids.filter((id): id is string => typeof id === "string").slice(-slotLimits[type]);
  }
  return loadout;
}

// Loadout aus einer flachen ID-Liste (z. B. aus dem Spiel), Typen aus den Shop-Daten
export function loadoutFromIds(ids: string[]): Loadout {
  const byType: Record<string, string[]> = {};
  for (const id of ids) {
    const type = cosmetics.find((item) => item.id === id)?.type;
    if (type) (byType[type] ??= []).push(id);
  }
  return normalizeLoadout(byType);
}

export function equippedIds(loadout: Loadout) {
  return cosmeticTypes.flatMap((type) => loadout[type]);
}

// Anlegen oder ablegen; ist der Typ voll, fliegt das am längsten angelegte Item raus
function toggle(loadout: Loadout, item: Cosmetic): Loadout {
  const current = loadout[item.type];
  const next = current.includes(item.id)
    ? current.filter((id) => id !== item.id)
    : [...current, item.id].slice(-slotLimits[item.type]);
  return { ...loadout, [item.type]: next };
}

// Die Pixel-Figur zeigt pro Typ ein Item: das neueste, das sie zeichnen kann
function characterLoadout(loadout: Loadout): CharacterLoadout {
  const pick = (ids: string[]) => [...ids].reverse().find((id) => cosmeticVisuals[id]) ?? ids[ids.length - 1] ?? null;
  return { CAPE: pick(loadout.CAPE), HEAD: pick(loadout.HEAD), WINGS: pick(loadout.WINGS), AURA: pick(loadout.AURA), PET: pick(loadout.PET) };
}

type TypeFilter = "ALL" | CosmeticType;

const stars = (() => {
  const random = seededRandom(1312);
  return Array.from({ length: 70 }, () => ({
    x: Math.round(random() * 100),
    y: Math.round(random() * 100),
    big: random() > 0.88,
    opacity: 0.35 + random() * 0.65,
  }));
})();

function StarField() {
  return (
    <svg className="wardrobe-stars" viewBox="0 0 100 100" preserveAspectRatio="none" aria-hidden="true">
      {stars.map((star, index) =>
        star.big ? (
          <g key={index} fill="#fff" opacity={star.opacity}>
            <rect x={star.x - 0.3} y={star.y - 0.9} width={0.6} height={1.8} />
            <rect x={star.x - 0.9} y={star.y - 0.3} width={1.8} height={0.6} />
          </g>
        ) : (
          <rect key={index} x={star.x} y={star.y} width={0.45} height={0.3} fill="#fff" opacity={star.opacity} />
        ),
      )}
    </svg>
  );
}

type CosmeticsViewProps = {
  loadout: Loadout;
  onLoadoutChange: (loadout: Loadout) => void;
  onNotify: (message: string) => void;
};

export default function CosmeticsView({
  loadout,
  onLoadoutChange,
  onNotify,
}: CosmeticsViewProps) {
  const [type, setType] = useState<TypeFilter>("ALL");
  const [showBack, setShowBack] = useState(true);
  const [tryOnId, setTryOnId] = useState<string | null>(null);

  const tryOn = cosmetics.find((item) => item.id === tryOnId);

  // Anprobierte Items werden nur in der Vorschau gezeigt, nicht gespeichert
  const previewLoadout = useMemo(
    () => (tryOn && !loadout[tryOn.type].includes(tryOn.id) ? toggle(loadout, tryOn) : loadout),
    [loadout, tryOn],
  );

  const sections = (type === "ALL" ? cosmeticTypes : [type]).map((sectionType) => ({
    type: sectionType,
    items: cosmetics.filter((item) => item.type === sectionType),
  }));

  const tabs = [
    { value: "ALL" as TypeFilter, label: "ALLE" },
    ...cosmeticTypes.map((value) => ({
      value: value as TypeFilter,
      label: cosmeticTypeLabels[value],
    })),
  ];

  const equippedSlots = cosmeticTypes
    .flatMap((slot) => previewLoadout[slot].map((id) => ({
      slot,
      item: cosmetics.find((entry) => entry.id === id),
    })))
    .filter((entry): entry is { slot: CosmeticType; item: Cosmetic } => Boolean(entry.item));

  function selectItem(item: Cosmetic) {
    if (!item.owned) {
      setTryOnId((current) => (current === item.id ? null : item.id));
      return;
    }

    if (tryOn?.type === item.type) setTryOnId(null);

    onLoadoutChange(toggle(loadout, item));
  }

  return (
    <div className="nova-page wardrobe">
      <aside className="wardrobe-preview pixel-corners">
        <StarField />

        <header className="wardrobe-preview-header">
          <span>VORSCHAU</span>
          <span className="wardrobe-set">FROST SET</span>
        </header>

        <div className="wardrobe-stage">
          <CharacterPreview loadout={characterLoadout(previewLoadout)} back={showBack} />
        </div>

        {tryOn && (
          <div className="wardrobe-tryon">
            <span>
              ANPROBE · <strong>{tryOn.name}</strong>
            </span>
            <button
              type="button"
              onClick={() =>
                onNotify(`${tryOn.name}: Der Checkout ist noch nicht verbunden.`)
              }
            >
              <CoinIcon size={12} /> {formatNumber(tryOn.price)}
            </button>
            <button type="button" aria-label="Anprobe beenden" onClick={() => setTryOnId(null)}>
              ✕
            </button>
          </div>
        )}

        <div className="wardrobe-view-toggle" role="group" aria-label="Ansicht">
          <button type="button" aria-label="Drehen" onClick={() => setShowBack((current) => !current)}>
            ◀
          </button>
          <button
            type="button"
            className={!showBack ? "is-active" : ""}
            aria-pressed={!showBack}
            onClick={() => setShowBack(false)}
          >
            VORNE
          </button>
          <button
            type="button"
            className={showBack ? "is-active" : ""}
            aria-pressed={showBack}
            onClick={() => setShowBack(true)}
          >
            HINTEN
          </button>
          <button type="button" aria-label="Drehen" onClick={() => setShowBack((current) => !current)}>
            ▶
          </button>
        </div>

        <div className="wardrobe-slots">
          {equippedSlots.length === 0 && (
            <span className="wardrobe-slots-empty">Nichts ausgerüstet</span>
          )}
          {equippedSlots.map(({ slot, item }) => (
            <div key={item.id} className="wardrobe-slot">
              <span>{cosmeticTypeLabels[slot].replace(/S$/, "")}</span>
              <strong>{item.name}</strong>
            </div>
          ))}
        </div>
      </aside>

      <section className="wardrobe-items">
        <div className="wardrobe-toolbar">
          <Tabs items={tabs} value={type} onChange={setType} label="Kategorien" />
          <span className="wardrobe-coins">
            <CoinIcon size={16} />
            {formatNumber(coinBalance)} NOVA COINS
          </span>
        </div>

        <div className="wardrobe-scroll">
          {sections.map((section) => (
            <section key={section.type} className="wardrobe-section">
              <h2>
                {cosmeticSectionLabels[section.type]}
                <span>
                  {slotLimits[section.type] > 1 &&
                    `${loadout[section.type].length}/${slotLimits[section.type]} AUSGERÜSTET · `}
                  {section.items.length} ITEMS
                </span>
              </h2>

              <div className="wardrobe-grid">
                {section.items.map((item) => {
                  const equipped = loadout[item.type].includes(item.id);
                  const tryingOn = tryOnId === item.id;

                  return (
                    <button
                      key={item.id}
                      type="button"
                      aria-pressed={equipped || tryingOn}
                      className={[
                        "wardrobe-card",
                        `rarity-${item.rarity.toLowerCase()}`,
                        equipped ? "is-equipped" : "",
                        tryingOn ? "is-trying" : "",
                      ]
                        .filter(Boolean)
                        .join(" ")}
                      onClick={() => selectItem(item)}
                    >
                      <span className="wardrobe-card-art">
                        <CosmeticThumbnail id={item.id} className={`is-${item.type.toLowerCase()}`} />
                        {item.badge && (
                          <span className={`wardrobe-badge ${item.badge === "EXKLUSIV" ? "is-dark" : ""}`}>
                            {item.badge}
                          </span>
                        )}
                      </span>

                      <span className="wardrobe-card-info">
                        <strong>{item.name}</strong>
                        <span className="wardrobe-card-meta">
                          <span className="rarity-label">{rarityLabels[item.rarity]}</span>
                          {equipped ? (
                            <span className="wardrobe-status is-equipped">
                              <span className="status-square is-online" /> AUSGERÜSTET
                            </span>
                          ) : item.owned ? (
                            <span className="wardrobe-status is-owned">BESITZT</span>
                          ) : tryingOn ? (
                            <span className="wardrobe-status is-trying">ANPROBE</span>
                          ) : (
                            <span className="wardrobe-status">
                              <CoinIcon size={12} />
                              {formatNumber(item.price)}
                            </span>
                          )}
                        </span>
                      </span>
                    </button>
                  );
                })}
              </div>
            </section>
          ))}
        </div>
      </section>
    </div>
  );
}
