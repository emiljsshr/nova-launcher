
import { useMemo, useState } from "react";
import { CosmeticThumbnail } from "./cosmetics/PixelArt";
import CosmeticPreview3D from "./cosmetics/CosmeticPreview3D";
import FrostScene from "./FrostScene";
import { CoinIcon, PageHeader, Tabs, formatNumber } from "./ui";
import {
  coinBalance,
  cosmeticTypeLabels,
  cosmeticTypes,
  cosmetics,
  rarityLabels,
  featuredBundle,
  type Cosmetic,
  type CosmeticType,
} from "../data/launcherData";
import "./StoreView.css";

type TypeFilter = "ALL" | CosmeticType;
type SortOrder = "featured" | "price-asc" | "price-desc" | "new";

const rarityRank = { COMMON: 0, RARE: 1, EPIC: 2, LEGENDARY: 3 };

const bundleItems = featuredBundle.itemIds
  .map((id) => cosmetics.find((item) => item.id === id))
  .filter((item): item is Cosmetic => Boolean(item));

// Das kostenlose Starter-Cape wird nicht im Store angeboten
const storeItems = cosmetics.filter((item) => item.price > 0);

type StoreViewProps = {
  skin: string | null;
  onNotify: (message: string) => void;
  onOpenCosmetics: () => void;
};

export default function StoreView({ skin, onNotify, onOpenCosmetics }: StoreViewProps) {
  const [type, setType] = useState<TypeFilter>("ALL");
  const [previewId, setPreviewId] = useState<string | null>(null);

  const preview = cosmetics.find((item) => item.id === previewId);
  const [sort, setSort] = useState<SortOrder>("featured");

  const visibleItems = useMemo(() => {
    const filtered = storeItems.filter(
      (item) => type === "ALL" || item.type === type,
    );

    return [...filtered].sort((a, b) => {
      if (sort === "price-asc") return a.price - b.price;
      if (sort === "price-desc") return b.price - a.price;
      if (sort === "new") return Number(b.badge === "NEU") - Number(a.badge === "NEU");
      return rarityRank[b.rarity] - rarityRank[a.rarity];
    });
  }, [type, sort]);

  const tabs = [
    { value: "ALL" as TypeFilter, label: "ALLE" },
    ...cosmeticTypes.map((value) => ({
      value: value as TypeFilter,
      label: cosmeticTypeLabels[value],
    })),
  ];

  function buy(name: string) {
    onNotify(`${name}: Der Checkout ist noch nicht verbunden.`);
  }

  return (
    <div className="nova-page store-page">
      <PageHeader
        eyebrow="NOVA STORE"
        title="STORE"
        description="Capes, Flügel und mehr – sichtbar für alle Nova-Spieler auf jedem Server."
        actions={
          <>
            <span className="coin-balance" aria-label="Guthaben">
              <CoinIcon size={18} />
              {formatNumber(coinBalance)}
            </span>
            <button
              type="button"
              className="nova-button nova-button-frost"
              onClick={() => onNotify("Coins aufladen ist bald verfügbar.")}
            >
              COINS AUFLADEN
            </button>
          </>
        }
      />

      <section className="store-bundle pixel-corners">
        <FrostScene />
        <div className="store-bundle-shade" aria-hidden="true" />

        <div className="store-bundle-copy">
          <span className="hero-badge">
            <span className="hero-badge-dot" />
            LIMITIERT · FROST SEASON
          </span>
          <h2>{featuredBundle.title}</h2>
          <p>{featuredBundle.description}</p>

          <div className="store-bundle-buy">
            <span className="store-price is-large">
              <CoinIcon size={20} />
              {formatNumber(featuredBundle.price)}
              <s>{formatNumber(featuredBundle.originalPrice)}</s>
            </span>
            <button
              type="button"
              className="nova-button nova-button-light"
              onClick={() => buy("Frost Bundle")}
            >
              BUNDLE KAUFEN
            </button>
          </div>
        </div>

        <div className="store-bundle-items">
          {bundleItems.map((item) => (
            <div key={item.id} className="store-bundle-item">
              <CosmeticThumbnail id={item.id} />
              <span>{item.name}</span>
            </div>
          ))}
        </div>
      </section>

      <div className="store-toolbar">
        <Tabs items={tabs} value={type} onChange={setType} label="Kategorien" />

        <label className="store-sort">
          <span className="nova-field-label">SORTIEREN</span>
          <select
            className="nova-select"
            value={sort}
            onChange={(event) => setSort(event.target.value as SortOrder)}
          >
            <option value="featured">Seltenheit</option>
            <option value="new">Neu zuerst</option>
            <option value="price-asc">Preis aufsteigend</option>
            <option value="price-desc">Preis absteigend</option>
          </select>
        </label>
      </div>

      <div className="store-grid">
        {visibleItems.map((item) => (
          <article
            key={item.id}
            className={`store-item pixel-corners rarity-${item.rarity.toLowerCase()}`}
          >
            <div className="store-item-stage">
              <CosmeticThumbnail id={item.id} />
              {item.model && (
                <button
                  type="button"
                  className="store-item-preview"
                  onClick={() => setPreviewId(item.id)}
                >
                  3D-VORSCHAU
                </button>
              )}
              {item.badge && (
                <span className={`store-item-new ${item.badge === "EXKLUSIV" ? "is-dark" : ""}`}>
                  {item.badge}
                </span>
              )}
            </div>

            <div className="store-item-body">
              <span className="rarity-label">{rarityLabels[item.rarity]}</span>
              <strong>{item.name}</strong>
              <span className="store-item-type">
                {cosmeticTypeLabels[item.type]}
              </span>
            </div>

            <div className="store-item-footer">
              {item.owned ? (
                <>
                  <span className="nova-chip">IM BESITZ</span>
                  <button
                    type="button"
                    className="nova-button nova-button-muted"
                    onClick={onOpenCosmetics}
                  >
                    ANLEGEN
                  </button>
                </>
              ) : (
                <>
                  <span className="store-price">
                    <CoinIcon size={14} />
                    {formatNumber(item.price)}
                  </span>
                  <button
                    type="button"
                    className="nova-button nova-button-dark"
                    onClick={() => buy(item.name)}
                  >
                    KAUFEN
                  </button>
                </>
              )}
            </div>
          </article>
        ))}
      </div>

      {preview && (
        <StorePreview
          item={preview}
          skin={skin}
          onClose={() => setPreviewId(null)}
          onBuy={() => buy(preview.name)}
          onEquip={() => {
            setPreviewId(null);
            onOpenCosmetics();
          }}
        />
      )}
    </div>
  );
}

function StorePreview({
  item,
  skin,
  onClose,
  onBuy,
  onEquip,
}: {
  item: Cosmetic;
  skin: string | null;
  onClose: () => void;
  onBuy: () => void;
  onEquip: () => void;
}) {
  return (
    <div className="nova-modal-backdrop" role="presentation" onMouseDown={onClose}>
      <div
        className={`nova-modal store-preview pixel-corners rarity-${item.rarity.toLowerCase()}`}
        role="dialog"
        aria-modal="true"
        aria-labelledby="store-preview-title"
        onMouseDown={(event) => event.stopPropagation()}
      >
        <div className="store-preview-stage">
          <CosmeticPreview3D cosmeticId={item.id} skin={skin} />
          <span className="store-preview-hint">ZIEHEN ZUM DREHEN</span>
        </div>

        <div className="store-preview-info">
          <header className="nova-modal-header">
            <div>
              <span className="rarity-label">{rarityLabels[item.rarity]}</span>
              <h2 id="store-preview-title">{item.name.toUpperCase()}</h2>
            </div>
            <button type="button" className="nova-modal-close" aria-label="Schließen" onClick={onClose}>
              ✕
            </button>
          </header>

          <span className="store-item-type">{cosmeticTypeLabels[item.type]}</span>
          <p>{item.description}</p>

          <div className="nova-modal-actions">
            {item.owned ? (
              <>
                <span className="nova-chip">IM BESITZ</span>
                <button type="button" className="nova-button nova-button-dark" onClick={onEquip}>
                  ANLEGEN
                </button>
              </>
            ) : (
              <>
                <span className="store-price">
                  <CoinIcon size={14} />
                  {formatNumber(item.price)}
                </span>
                <button type="button" className="nova-button nova-button-dark" onClick={onBuy}>
                  KAUFEN
                </button>
              </>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
