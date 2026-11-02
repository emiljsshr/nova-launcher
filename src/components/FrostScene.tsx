
import { memo } from "react";
import { seededRandom } from "./PixelIcon";

// Pixel-Art-Nachtlandschaft für das Hero-Banner.
// Die Szene wird einmal auf einem 160x105-Raster berechnet.

const WIDTH = 160;
const HEIGHT = 105;

type Rect = {
  x: number;
  y: number;
  w: number;
  h: number;
  fill: string;
  opacity?: number;
};

type Mountain = {
  peakX: number;
  peakY: number;
  slope: number;
  baseY: number;
  light: string;
  dark: string;
  snowLight: string;
  snowDark: string;
  snowDepth: number;
};

const skyBands = [
  "#0a1129",
  "#0c142f",
  "#0e1834",
  "#111c3a",
  "#142140",
  "#172646",
  "#1a2a4c",
  "#1d2f52",
];

const backMountains: Mountain[] = [
  { peakX: 18, peakY: 52, slope: 0.9, baseY: 92, light: "#26355c", dark: "#1f2c4e", snowLight: "#55688f", snowDark: "#45577c", snowDepth: 4 },
  { peakX: 62, peakY: 54, slope: 0.8, baseY: 92, light: "#2a3b66", dark: "#223157", snowLight: "#6b80ad", snowDark: "#55688f", snowDepth: 5 },
  { peakX: 104, peakY: 50, slope: 0.85, baseY: 92, light: "#2a3b66", dark: "#223157", snowLight: "#6b80ad", snowDark: "#55688f", snowDepth: 5 },
  { peakX: 150, peakY: 56, slope: 0.8, baseY: 92, light: "#26355c", dark: "#1f2c4e", snowLight: "#55688f", snowDark: "#45577c", snowDepth: 4 },
];

const frontMountains: Mountain[] = [
  { peakX: 54, peakY: 62, slope: 1.1, baseY: 94, light: "#3a5187", dark: "#2c3f6c", snowLight: "#a9bde3", snowDark: "#8099c7", snowDepth: 6 },
  { peakX: 82, peakY: 56, slope: 1.0, baseY: 94, light: "#44609c", dark: "#33497c", snowLight: "#e3ecfb", snowDark: "#b4c7ea", snowDepth: 8 },
  { peakX: 116, peakY: 68, slope: 1.05, baseY: 94, light: "#3a5187", dark: "#2c3f6c", snowLight: "#c7d6f2", snowDark: "#9db2dc", snowDepth: 5 },
  { peakX: 140, peakY: 66, slope: 1.1, baseY: 94, light: "#3a5187", dark: "#2c3f6c", snowLight: "#c7d6f2", snowDark: "#9db2dc", snowDepth: 5 },
];

function drawMountain(mountain: Mountain, random: () => number): Rect[] {
  const rects: Rect[] = [];
  const reach = Math.ceil((mountain.baseY - mountain.peakY) / mountain.slope);

  for (let x = mountain.peakX - reach; x <= mountain.peakX + reach; x++) {
    if (x < 0 || x >= WIDTH) continue;

    const distance = Math.abs(x - mountain.peakX);
    // In 2er-Stufen quantisieren, damit die Hänge pixelig wirken
    const top =
      mountain.peakY + Math.floor((distance * mountain.slope) / 2) * 2;
    if (top >= mountain.baseY) continue;

    const isLeft = x <= mountain.peakX;
    rects.push({
      x,
      y: top,
      w: 1,
      h: mountain.baseY - top,
      fill: isLeft ? mountain.light : mountain.dark,
    });

    const snowLine =
      mountain.peakY + mountain.snowDepth + Math.floor(random() * 3);
    if (top < snowLine) {
      rects.push({
        x,
        y: top,
        w: 1,
        h: snowLine - top,
        fill: isLeft ? mountain.snowLight : mountain.snowDark,
      });
    }
  }

  return rects;
}

function drawTree(x: number, baseY: number, height: number): Rect[] {
  const rects: Rect[] = [];

  for (let row = 0; row < height; row++) {
    const half = Math.floor(row / 2);
    rects.push({
      x: x - half,
      y: baseY - height + row,
      w: half * 2 + 1,
      h: 1,
      fill: row % 3 === 0 && row > 0 ? "#1b2a4a" : "#14203b",
    });
  }

  // Schnee auf der Spitze und Stamm
  rects.push({ x, y: baseY - height, w: 1, h: 1, fill: "#d9e4f7" });
  rects.push({ x, y: baseY, w: 1, h: 2, fill: "#2a1d18" });
  return rects;
}

function buildScene(): Rect[] {
  const random = seededRandom(2024);
  const rects: Rect[] = [];

  // Himmel in Bändern
  const bandHeight = Math.ceil(HEIGHT / skyBands.length);
  skyBands.forEach((fill, index) => {
    rects.push({ x: 0, y: index * bandHeight, w: WIDTH, h: bandHeight, fill });
  });

  // Polarlicht als welliges Band
  for (let x = 0; x < WIDTH; x += 2) {
    const center = Math.round(
      34 + 7 * Math.sin(x / 15) + 3 * Math.sin(x / 6 + 1),
    );
    rects.push({ x, y: center - 9, w: 2, h: 18, fill: "#1f3263", opacity: 0.75 });
    rects.push({ x, y: center - 5, w: 2, h: 10, fill: "#2a4380", opacity: 0.55 });
    rects.push({ x, y: center - 2, w: 2, h: 4, fill: "#3b5ba3", opacity: 0.35 });
  }

  // Sterne
  for (let index = 0; index < 90; index++) {
    rects.push({
      x: Math.floor(random() * WIDTH),
      y: Math.floor(random() * 62),
      w: 1,
      h: 1,
      fill: "#ffffff",
      opacity: 0.35 + random() * 0.65,
    });
  }

  // Mond
  rects.push({ x: 118, y: 10, w: 10, h: 10, fill: "#eef3fd" });
  rects.push({ x: 120, y: 12, w: 2, h: 2, fill: "#c6d3ec" });
  rects.push({ x: 124, y: 15, w: 3, h: 2, fill: "#c6d3ec" });

  backMountains.forEach((mountain) => rects.push(...drawMountain(mountain, random)));
  frontMountains.forEach((mountain) => rects.push(...drawMountain(mountain, random)));

  // Nadelbäume
  [92, 98, 104, 112, 126, 132, 150, 155].forEach((x, index) => {
    rects.push(...drawTree(x, 92, 9 + (index % 3) * 2));
  });

  // Verschneiter, blockiger Boden
  let groundTop = 90;
  for (let x = 0; x < WIDTH; x += 4) {
    if (random() > 0.6) groundTop = 88 + Math.floor(random() * 4) * 2;
    rects.push({ x, y: groundTop, w: 4, h: HEIGHT - groundTop, fill: random() > 0.5 ? "#2b3349" : "#323b55" });
    if (random() > 0.35) {
      rects.push({ x, y: groundTop, w: 4, h: 1, fill: "#c9d6ee" });
    }
    if (random() > 0.6) {
      rects.push({ x, y: groundTop + 4 + Math.floor(random() * 6), w: 4, h: 4, fill: "#262d42" });
    }
  }

  return rects;
}

const sceneRects = buildScene();

function FrostScene() {
  return (
    <svg
      className="frost-scene"
      viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
      preserveAspectRatio="xMidYMid slice"
      shapeRendering="crispEdges"
      aria-hidden="true"
    >
      {sceneRects.map((rect, index) => (
        <rect
          key={index}
          x={rect.x}
          y={rect.y}
          width={rect.w}
          height={rect.h}
          fill={rect.fill}
          opacity={rect.opacity}
        />
      ))}
    </svg>
  );
}

export default memo(FrostScene);
