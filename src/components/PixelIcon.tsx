
// Kleine Pixel-Grafiken im Minecraft-Stil (Spielerköpfe, Server-Icons).

type HeadPalette = {
  hair: string;
  skin: string;
  shade: string;
  eyes: string;
  mouth: string;
};

export const headPalettes = {
  steve: {
    hair: "#4a3020",
    skin: "#c08a5e",
    shade: "#9c6a45",
    eyes: "#4b4fb0",
    mouth: "#6b3f28",
  },
  frost: {
    hair: "#9fc4f2",
    skin: "#f0c7a5",
    shade: "#d6a684",
    eyes: "#3e6fc4",
    mouth: "#c06a5a",
  },
  blonde: {
    hair: "#f2d36b",
    skin: "#f3c8a2",
    shade: "#d9a77f",
    eyes: "#4a9d5b",
    mouth: "#c0605a",
  },
  dark: {
    hair: "#24170f",
    skin: "#7a4a2d",
    shade: "#5e3820",
    eyes: "#e9e9e9",
    mouth: "#3a2114",
  },
} satisfies Record<string, HeadPalette>;

export type HeadVariant = keyof typeof headPalettes;

// h = Haare, s = Haut, d = Schatten, w = Augenweiß, e = Pupille, m = Mund
const headPixels = [
  "hhhhhhhh",
  "hhhhhhhh",
  "hssssssh",
  "swessews",
  "ssssssss",
  "ssddddss",
  "ssdmmdss",
  "ssssssss",
];

export function PlayerHead({
  size = 14,
  variant = "steve",
}: {
  size?: number;
  variant?: HeadVariant;
}) {
  const palette = headPalettes[variant];
  const colors: Record<string, string> = {
    h: palette.hair,
    s: palette.skin,
    d: palette.shade,
    w: "#ffffff",
    e: palette.eyes,
    m: palette.mouth,
  };

  return (
    <svg
      className="player-head"
      width={size}
      height={size}
      viewBox="0 0 8 8"
      shapeRendering="crispEdges"
      aria-hidden="true"
    >
      {headPixels.flatMap((row, y) =>
        [...row].map((cell, x) => (
          <rect
            key={`${x}-${y}`}
            x={x}
            y={y}
            width={1}
            height={1}
            fill={colors[cell]}
          />
        )),
      )}
    </svg>
  );
}

// Deterministischer Zufall, damit die Icons bei jedem Render gleich aussehen
export function seededRandom(seed: number) {
  let value = seed;
  return () => {
    value = (value * 9301 + 49297) % 233280;
    return value / 233280;
  };
}

export type ServerIconVariant =
  | "ice"
  | "arena"
  | "grass"
  | "sand"
  | "lava"
  | "amethyst";

function serverIconColor(
  variant: ServerIconVariant,
  x: number,
  y: number,
  random: () => number,
) {
  const roll = random();

  if (variant === "ice") {
    return ["#a9cdf7", "#7fb1ef", "#cfe3fb", "#5f97e0"][Math.floor(roll * 4)];
  }

  if (variant === "arena") {
    if ((x + y) % 3 === 0 && roll > 0.4) return "#9ab8ea";
    return roll > 0.55 ? "#1a2442" : roll > 0.25 ? "#3d5fa8" : "#9ab8ea";
  }

  if (variant === "sand") {
    if (y === 0 || (y === 1 && roll > 0.5)) return "#5aa9e6";
    return roll > 0.6 ? "#e8d593" : roll > 0.25 ? "#d9c27a" : "#c7ac62";
  }

  if (variant === "lava") {
    if ((x + y * 2) % 5 === 0) return roll > 0.5 ? "#ffd166" : "#ff9f1c";
    return roll > 0.6 ? "#e2531f" : roll > 0.25 ? "#a8321a" : "#2a1a17";
  }

  if (variant === "amethyst") {
    return roll > 0.7 ? "#d7b5f5" : roll > 0.35 ? "#9a5bd6" : "#5b2f8f";
  }

  // Grasblock: oben grün, unten Erde
  if (y < 2 || (y === 2 && roll > 0.5)) {
    return roll > 0.5 ? "#5fa83a" : "#4c9230";
  }
  return roll > 0.7 ? "#5c3d24" : roll > 0.3 ? "#79522f" : "#8b6139";
}

export function ServerIcon({
  variant,
  size = 36,
}: {
  variant: ServerIconVariant;
  size?: number;
}) {
  const random = seededRandom(variant.length * 97 + 13);

  return (
    <svg
      className="server-icon"
      width={size}
      height={size}
      viewBox="0 0 8 8"
      shapeRendering="crispEdges"
      aria-hidden="true"
    >
      {Array.from({ length: 64 }, (_, index) => {
        const x = index % 8;
        const y = Math.floor(index / 8);
        return (
          <rect
            key={index}
            x={x}
            y={y}
            width={1}
            height={1}
            fill={serverIconColor(variant, x, y, random)}
          />
        );
      })}
    </svg>
  );
}

// Breites Mosaik in den Farben eines Servers, z. B. als Kopfbild
export function ServerBanner({
  variant,
  columns = 32,
  rows = 10,
}: {
  variant: ServerIconVariant;
  columns?: number;
  rows?: number;
}) {
  const random = seededRandom(variant.length * 31 + 7);

  return (
    <svg
      className="server-banner"
      viewBox={`0 0 ${columns} ${rows}`}
      preserveAspectRatio="xMidYMid slice"
      shapeRendering="crispEdges"
      aria-hidden="true"
    >
      {Array.from({ length: columns * rows }, (_, index) => {
        const x = index % columns;
        const y = Math.floor(index / columns);
        return (
          <rect
            key={index}
            x={x}
            y={y}
            width={1}
            height={1}
            fill={serverIconColor(variant, x, y % 8, random)}
          />
        );
      })}
    </svg>
  );
}
