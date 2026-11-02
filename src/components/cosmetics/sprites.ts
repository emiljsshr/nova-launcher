
import { seededRandom } from "../PixelIcon";

// Pixel-Sprites für Cosmetics und die Charakter-Vorschau.
// Ein Sprite besteht aus Textzeilen; jedes Zeichen verweist auf eine Farbe, "." ist leer.

export type Sprite = {
  rows: string[];
  colors: Record<string, string>;
};

export class PixelCanvas {
  readonly pixels: (string | null)[];

  constructor(
    readonly width: number,
    readonly height: number,
  ) {
    this.pixels = new Array(width * height).fill(null);
  }

  set(x: number, y: number, color: string) {
    if (x < 0 || y < 0 || x >= this.width || y >= this.height) return;
    this.pixels[y * this.width + x] = color;
  }

  fill(x: number, y: number, w: number, h: number, color: string) {
    for (let row = y; row < y + h; row++) {
      for (let col = x; col < x + w; col++) this.set(col, row, color);
    }
  }

  stamp(sprite: Sprite, ox: number, oy: number, mirror = false) {
    sprite.rows.forEach((row, y) => {
      [...row].forEach((cell, index) => {
        if (cell === ".") return;
        const x = mirror ? row.length - 1 - index : index;
        this.set(ox + x, oy + y, sprite.colors[cell]);
      });
    });
  }
}

function buildSprite(
  width: number,
  height: number,
  pick: (x: number, y: number) => string,
  colors: Record<string, string>,
): Sprite {
  return {
    rows: Array.from({ length: height }, (_, y) =>
      Array.from({ length: width }, (_, x) => pick(x, y)).join(""),
    ),
    colors,
  };
}

/* ========================================
   CAPES (10x16)
======================================== */

const CAPE_W = 10;
const CAPE_H = 16;

function isCapeEdge(x: number, y: number) {
  return x === 0 || x === CAPE_W - 1 || y === CAPE_H - 1;
}

const frostCape = buildSprite(
  CAPE_W,
  CAPE_H,
  (x, y) => {
    if (isCapeEdge(x, y)) return "o";
    const dx = Math.abs(x - 4.5);
    const dy = Math.abs(y - 7);
    const isFlake =
      (dx < 1 && dy <= 3) ||
      (dy === 0 && dx <= 3.5) ||
      (Math.abs(dx - dy) < 0.6 && dy <= 2.5);
    if (isFlake) return "w";
    return y < 5 ? "a" : y < 11 ? "b" : "c";
  },
  { o: "#4a7fd6", a: "#cfe3fb", b: "#9cc2f7", c: "#6f9be0", w: "#ffffff" },
);

const starfieldCape = (() => {
  const random = seededRandom(77);
  return buildSprite(
    CAPE_W,
    CAPE_H,
    (x, y) => {
      if (isCapeEdge(x, y)) return "o";
      const dx = Math.abs(x - 4.5);
      if ((dx < 1 && y >= 5 && y <= 9) || (y === 7 && dx <= 2.5)) return "w";
      const roll = random();
      return roll > 0.9 ? "w" : roll > 0.82 ? "s" : "n";
    },
    { o: "#070b1c", n: "#0d1631", s: "#6f9be0", w: "#ffffff" },
  );
})();

const auroraCape = buildSprite(
  CAPE_W,
  CAPE_H,
  (x, y) => {
    if (isCapeEdge(x, y)) return "o";
    const distance = Math.abs(x - (4.5 + 3 * Math.sin(y * 0.75)));
    return distance < 1.3 ? "g" : distance < 2.4 ? "t" : "n";
  },
  { o: "#081620", n: "#0f2233", t: "#1f8f7a", g: "#4ad6a8" },
);

const founderCape = buildSprite(
  CAPE_W,
  CAPE_H,
  (x, y) => {
    if (x === 0 || x === CAPE_W - 1 || y === 0 || y === CAPE_H - 1) return "g";
    if (x === 1 || x === CAPE_W - 2 || y === 1 || y === CAPE_H - 2) return "d";
    const distance = Math.abs(x - 4.5) + Math.abs(y - 7);
    if (distance < 1.6) return "w";
    if (distance < 3.6) return "g";
    if (y === 12) return "g";
    return "k";
  },
  { g: "#e0a33a", d: "#7a5310", k: "#15151c", w: "#fff1b8" },
);

/* ========================================
   KOPF (relativ zur Kopf-Ecke oben links)
======================================== */

type HeadSprite = Sprite & { dx: number; dy: number };

const iceCrown: HeadSprite = {
  dx: -1,
  dy: -3,
  rows: [
    "b..b..b..b",
    "bc.bccb.cb",
    "bbbbbbbbbb",
    "bcbbwwbbcb",
    "aaaaaaaaaa",
  ],
  colors: { a: "#4a7fd6", b: "#cfe3fb", c: "#9cc2f7", w: "#ffffff" },
};

const halo: HeadSprite = {
  dx: -1,
  dy: -5,
  rows: [".aaaaaaaa.", "ab......ba", ".aaaaaaaa."],
  colors: { a: "#f2d36b", b: "#fff1b8" },
};

const frostHorns: HeadSprite = {
  dx: -2,
  dy: -5,
  rows: [
    "a..........a",
    "ab........ba",
    ".ab......ba.",
    "..bb....bb..",
    "...bb..bb...",
  ],
  colors: { a: "#ffffff", b: "#9cc2f7" },
};

const novaBeanie: HeadSprite = {
  dx: -1,
  dy: -3,
  rows: [
    "....ww....",
    "..bbbbbb..",
    "bcbcbcbcbc",
    "bbbbbbbbbb",
    "wwwwwwwwww",
  ],
  colors: { b: "#24457f", c: "#6f9be0", w: "#e3eefd" },
};

/* ========================================
   FLÜGEL (linker Flügel, rechts wird gespiegelt)
======================================== */

const featherShape = [
  "..........aa",
  "........aabb",
  "......aabbcb",
  "....aabbcbbb",
  "..aabbcbbbbb",
  "aabbcbbbbbb.",
  "abcbbbbbbb..",
  ".abbbbbbb...",
  "..abb.abb...",
  "...a...ab...",
  "........a...",
];

const batShape = [
  "aa..........",
  "abaa........",
  "abbbaa......",
  "abcbbbaa....",
  "abbcbbbbaa..",
  "abbbcbbbbbaa",
  "abbbbcbbbbba",
  ".abb.abbb.ba",
  "..a...ab...a",
];

/* ========================================
   AURA
======================================== */

export type AuraKind = "snow" | "aurora" | "void";

/* ========================================
   PETS (8x8)
======================================== */

const snowFox: Sprite = {
  rows: [
    "a....a..",
    "aa..aa..",
    "abbbba..",
    "bkbbkb..",
    "bbbnbbbc",
    ".bbbbbbc",
    ".bbbbbbc",
    ".b.b.b..",
  ],
  colors: { a: "#9cc2f7", b: "#f4f6f9", c: "#cfe3fb", k: "#1a1a1f", n: "#24457f" },
};

const pixelSlime: Sprite = {
  rows: [
    "........",
    ".aaaaaa.",
    "abbbbbba",
    "abkbbkba",
    "abbbbbba",
    "abbccbba",
    "abbbbbba",
    ".aaaaaa.",
  ],
  colors: { a: "#3f8f3a", b: "#7fd66a", c: "#2a5f27", k: "#1a2a1a" },
};

const snowOwl: Sprite = {
  rows: [
    "a......a",
    "aaaaaaaa",
    "awwaawwa",
    "awkaakwa",
    "aaaooaaa",
    "abbaabba",
    ".abbbba.",
    "..o..o..",
  ],
  colors: { a: "#d6dde8", b: "#9aa1ad", w: "#ffffff", k: "#1a1a1f", o: "#e0a33a" },
};

/* ========================================
   KATALOG
======================================== */

export type CosmeticVisual =
  | { kind: "cape"; sprite: Sprite }
  | { kind: "head"; sprite: HeadSprite }
  | { kind: "wings"; sprite: Sprite }
  | { kind: "aura"; aura: AuraKind }
  | { kind: "pet"; sprite: Sprite };

function wings(colors: Record<string, string>, shape = featherShape): CosmeticVisual {
  return { kind: "wings", sprite: { rows: shape, colors } };
}

export const cosmeticVisuals: Record<string, CosmeticVisual> = {
  "frost-cape": { kind: "cape", sprite: frostCape },
  starfield: { kind: "cape", sprite: starfieldCape },
  "aurora-cape": { kind: "cape", sprite: auroraCape },
  founder: { kind: "cape", sprite: founderCape },

  "ice-crown": { kind: "head", sprite: iceCrown },
  halo: { kind: "head", sprite: halo },
  "frost-horns": { kind: "head", sprite: frostHorns },
  "nova-beanie": { kind: "head", sprite: novaBeanie },

  "frost-wings": wings({ a: "#6f9be0", b: "#cfe3fb", c: "#ffffff" }),
  "crystal-wings": wings({ a: "#4a7fd6", b: "#9cc2f7", c: "#e3eefd" }),
  nebula: wings({ a: "#2a1745", b: "#6b3fb0", c: "#c9a2ff" }, batShape),
  "ice-dragon": wings({ a: "#14203b", b: "#2c4a85", c: "#9cc2f7" }, batShape),

  snowfall: { kind: "aura", aura: "snow" },
  "polar-light": { kind: "aura", aura: "aurora" },
  "void-ring": { kind: "aura", aura: "void" },

  "snow-fox": { kind: "pet", sprite: snowFox },
  "pixel-slime": { kind: "pet", sprite: pixelSlime },
  "snow-owl": { kind: "pet", sprite: snowOwl },
};

/* ========================================
   CHARAKTER
======================================== */

const SKIN = "#c08a5e";
const SKIN_SHADE = "#9c6a45";
const HAIR = "#4a3020";
const SHIRT = "#2fa3a3";
const SHIRT_SHADE = "#248585";
const PANTS = "#3b3f9e";
const PANTS_SHADE = "#2e3180";
const SHOES = "#5a5a5a";

export function drawHead(canvas: PixelCanvas, x: number, y: number, back: boolean) {
  if (back) {
    canvas.fill(x, y, 8, 8, HAIR);
    canvas.fill(x, y + 7, 8, 1, "#3a2418");
    return;
  }

  canvas.fill(x, y, 8, 8, SKIN);
  canvas.fill(x, y, 8, 2, HAIR);
  canvas.set(x, y + 2, HAIR);
  canvas.set(x + 7, y + 2, HAIR);
  canvas.set(x + 1, y + 4, "#ffffff");
  canvas.set(x + 2, y + 4, "#4b4fb0");
  canvas.set(x + 5, y + 4, "#4b4fb0");
  canvas.set(x + 6, y + 4, "#ffffff");
  canvas.fill(x + 3, y + 5, 2, 1, SKIN_SHADE);
  canvas.fill(x + 2, y + 6, 4, 1, "#6b3f28");
}

function drawSnow(canvas: PixelCanvas) {
  const random = seededRandom(404);
  for (let index = 0; index < 34; index++) {
    const x = Math.floor(random() * canvas.width);
    const y = Math.floor(random() * (canvas.height - 6));
    canvas.set(x, y, random() > 0.5 ? "#ffffff" : "#cfe3fb");
  }
}

function drawAuroraBand(canvas: PixelCanvas) {
  for (let x = 2; x < canvas.width - 2; x++) {
    const y = Math.round(3 + 1.6 * Math.sin(x * 0.45));
    canvas.set(x, y, "#4ad6a8");
    canvas.set(x, y + 1, "#1f8f7a");
    if (x % 3 === 0) canvas.set(x, y - 1, "#c8ffe9");
  }
}

function drawVoidRing(canvas: PixelCanvas, cx: number, y: number) {
  canvas.fill(cx - 6, y, 12, 1, "#9a5bd6");
  canvas.set(cx - 8, y + 1, "#9a5bd6");
  canvas.set(cx - 7, y + 1, "#c9a2ff");
  canvas.set(cx + 6, y + 1, "#c9a2ff");
  canvas.set(cx + 7, y + 1, "#9a5bd6");
  canvas.fill(cx - 6, y + 2, 12, 1, "#6b3fb0");
}

export type CharacterLoadout = Partial<Record<"CAPE" | "HEAD" | "WINGS" | "AURA" | "PET", string | null>>;

// Zeichnet die ganze Figur mit allen angelegten Cosmetics (40x44)
export function drawCharacter(loadout: CharacterLoadout, back: boolean) {
  const canvas = new PixelCanvas(40, 44);
  const visual = (slot: keyof CharacterLoadout) => {
    const id = loadout[slot];
    return id ? cosmeticVisuals[id] : undefined;
  };

  const aura = visual("AURA");
  const wingsVisual = visual("WINGS");
  const cape = visual("CAPE");
  const head = visual("HEAD");
  const pet = visual("PET");

  // Eisplattform
  canvas.fill(5, 38, 30, 2, "#9cc2f7");
  canvas.fill(7, 40, 26, 1, "#6f9be0");

  if (aura?.kind === "aura" && aura.aura === "snow") drawSnow(canvas);
  if (aura?.kind === "aura" && aura.aura === "aurora") drawAuroraBand(canvas);

  const drawWings = () => {
    if (wingsVisual?.kind !== "wings") return;
    canvas.stamp(wingsVisual.sprite, 3, 11);
    canvas.stamp(wingsVisual.sprite, 25, 11, true);
  };

  if (!back) drawWings();

  // Körper
  drawHead(canvas, 16, 6, back);
  canvas.fill(16, 14, 8, 12, SHIRT);
  canvas.fill(back ? 16 : 23, 14, 1, 12, SHIRT_SHADE);
  canvas.fill(12, 14, 4, 4, SHIRT);
  canvas.fill(24, 14, 4, 4, SHIRT);
  canvas.fill(12, 18, 4, 8, SKIN);
  canvas.fill(24, 18, 4, 8, SKIN);
  canvas.fill(12, 25, 4, 1, SKIN_SHADE);
  canvas.fill(24, 25, 4, 1, SKIN_SHADE);
  canvas.fill(16, 26, 4, 10, PANTS);
  canvas.fill(20, 26, 4, 10, PANTS_SHADE);
  canvas.fill(16, 36, 8, 2, SHOES);

  if (back && cape?.kind === "cape") canvas.stamp(cape.sprite, 15, 14);
  if (back) drawWings();

  if (head?.kind === "head") {
    canvas.stamp(head.sprite, 16 + head.sprite.dx, 6 + head.sprite.dy);
  }

  if (aura?.kind === "aura" && aura.aura === "void") drawVoidRing(canvas, 20, 35);
  if (pet?.kind === "pet") canvas.stamp(pet.sprite, 31, 30);

  return canvas;
}

// Vorschaubild für eine einzelne Item-Karte
export function drawThumbnail(id: string) {
  const visual = cosmeticVisuals[id];

  switch (visual?.kind) {
    case "cape": {
      const canvas = new PixelCanvas(14, 18);
      canvas.fill(3, 2, 10, 16, "rgba(16, 17, 22, 0.12)");
      canvas.stamp(visual.sprite, 2, 1);
      return canvas;
    }
    case "head": {
      const canvas = new PixelCanvas(14, 15);
      drawHead(canvas, 3, 7, false);
      canvas.stamp(visual.sprite, 3 + visual.sprite.dx, 7 + visual.sprite.dy);
      return canvas;
    }
    case "wings": {
      const canvas = new PixelCanvas(26, 14);
      canvas.stamp(visual.sprite, 1, 1);
      canvas.stamp(visual.sprite, 13, 1, true);
      canvas.fill(12, 0, 2, 2, SKIN);
      canvas.fill(11, 2, 4, 6, SHIRT);
      canvas.fill(11, 8, 4, 5, PANTS);
      return canvas;
    }
    case "pet": {
      const canvas = new PixelCanvas(10, 10);
      canvas.stamp(visual.sprite, 1, 1);
      return canvas;
    }
    case "aura": {
      const canvas = drawCharacter({ AURA: id }, false);
      return canvas;
    }
    default:
      return new PixelCanvas(1, 1);
  }
}
