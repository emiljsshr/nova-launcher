// Stellt Theme-Farben von Frost-Blau/Navy auf das Graphit-Silber des Nova-Wappens um.
// Blau-, Türkis- und Violetttöne sowie Graus werden auf den kühlen Grauton des Logos gesetzt (OKLCH-Farbton 285°,
// kaum Sättigung), die Helligkeit bleibt – Kontraste ändern sich also nicht. Kräftige Blau-Akzente werden dunkler,
// damit weiße Schrift darauf lesbar bleibt. Status-, Seltenheits- und Markenfarben bleiben unverändert.
//   node tools/branding/recolor.mjs
import { readFileSync, writeFileSync } from "node:fs";

const files = [
  "src/App.css",
  ...["ui", "LaunchBar", "CosmeticsView", "StoreView", "GameProfilesView", "FriendsView", "ProfileView", "HomeView",
    "WelcomeScreen", "ScreenshotsView", "PartnerServerView", "PatchNotesDialog", "PixelButton", "DiscordView",
    "LoginDialog", "SettingsView"].map((name) => `src/components/${name}.css`),
  "src/components/FrostScene.tsx",
  "src/components/ui.tsx",
  "src/components/DiscordView.tsx",
  "mod/core/src/main/java/gg/nova/core/NovaScenery.java",
  "mod/core/src/main/java/gg/nova/core/NovaDraw.java",
  "mod/core/src/main/java/gg/nova/core/NovaSprites.java",
  "mod/core/src/main/java/gg/nova/core/menu/MenuButton.java",
  "mod/core/src/main/java/gg/nova/core/menu/NovaMenu.java",
  "mod/core/src/main/java/gg/nova/core/menu/NovaModMenu.java",
  "mod/core/src/main/java/gg/nova/core/menu/NovaServerList.java",
  "mod/core/src/main/java/gg/nova/core/loading/NovaLoadingRenderer.java",
];

// Zeilen mit Farben, die ihre Bedeutung behalten (Seltenheit, Microsoft-Logo) oder keine Farben sind (Hash-Konstanten)
const keep = /--rarity|launch-login-icon|0x9E3779B1|0x85EBCA77|0x1000000|& 0xFFFFFF/;
// Partner-Symbole in der Mod sind Inhalt, kein Theme
const stopAt = { "NovaServerList.java": "private static void partnerIcon" };

const HUE = 285;

const toLinear = (c) => (c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4);
const toGamma = (c) => (c <= 0.0031308 ? 12.92 * c : 1.055 * c ** (1 / 2.4) - 0.055);

function toOklch(r, g, b) {
  const [lr, lg, lb] = [r, g, b].map((v) => toLinear(v / 255));
  const l = Math.cbrt(0.4122214708 * lr + 0.5363325363 * lg + 0.0514459929 * lb);
  const m = Math.cbrt(0.2119034982 * lr + 0.6806995451 * lg + 0.1073969566 * lb);
  const s = Math.cbrt(0.0883024619 * lr + 0.2817188376 * lg + 0.6299787005 * lb);
  const L = 0.2104542553 * l + 0.793617785 * m - 0.0040720468 * s;
  const a = 1.9779984951 * l - 2.428592205 * m + 0.4505937099 * s;
  const B = 0.0259040371 * l + 0.7827717662 * m - 0.808675766 * s;
  return [L, Math.hypot(a, B), ((Math.atan2(B, a) * 180) / Math.PI + 360) % 360];
}

function fromOklch(L, C, h) {
  const a = C * Math.cos((h * Math.PI) / 180);
  const b = C * Math.sin((h * Math.PI) / 180);
  const l = (L + 0.3963377774 * a + 0.2158037573 * b) ** 3;
  const m = (L - 0.1055613458 * a - 0.0638541728 * b) ** 3;
  const s = (L - 0.0894841775 * a - 1.291485548 * b) ** 3;
  const rgb = [
    4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
    -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
    -0.0041960863 * l - 0.7034186147 * m + 1.707614701 * s,
  ];
  return rgb.map((v) => Math.round(Math.min(1, Math.max(0, toGamma(Math.min(1, Math.max(0, v))))) * 255));
}

function recolor(r, g, b) {
  let [L, C, h] = toOklch(r, g, b);
  const grey = C < 0.035;
  if (!grey && (h < 155 || h > 300)) return [r, g, b];
  // Kräftige Blau-Akzente (#4a7fd6 & Co.) werden zu dunklem Graphit
  if (C >= 0.1 && L > 0.4 && L < 0.78) L -= 0.2;
  const chroma = grey ? Math.min(C, 0.012) : 0.008 + Math.min(C, 0.15) * 0.06;
  return fromOklch(L, chroma, HUE);
}

const hex2 = (v) => v.toString(16).padStart(2, "0");
let changed = 0;

function convertCss(text) {
  return text
    .replace(/#([0-9a-fA-F]{6}|[0-9a-fA-F]{3})\b/g, (all, hex) => {
      const full = hex.length === 3 ? [...hex].map((c) => c + c).join("") : hex;
      const [r, g, b] = [0, 2, 4].map((i) => parseInt(full.slice(i, i + 2), 16));
      const out = "#" + recolor(r, g, b).map(hex2).join("");
      if (out.toLowerCase() === "#" + full.toLowerCase()) return all;
      changed++;
      return out;
    })
    .replace(/rgba?\((\s*\d+\s*),(\s*\d+\s*),(\s*\d+\s*)(,[^)]*)?\)/g, (all, r, g, b, alpha = "") => {
      const out = recolor(+r, +g, +b);
      if (out[0] === +r && out[1] === +g && out[2] === +b) return all;
      changed++;
      return `${alpha ? "rgba" : "rgb"}(${out.join(", ")}${alpha})`;
    });
}

function convertJava(text) {
  return text.replace(/0x([0-9A-Fa-f]{2})?([0-9A-Fa-f]{6})\b/g, (all, alpha = "", rgb) => {
    const [r, g, b] = [0, 2, 4].map((i) => parseInt(rgb.slice(i, i + 2), 16));
    const out = recolor(r, g, b).map(hex2).join("").toUpperCase();
    if (out === rgb.toUpperCase()) return all;
    changed++;
    return `0x${alpha.toUpperCase()}${out}`;
  });
}

for (const file of files) {
  const java = file.endsWith(".java");
  const lines = readFileSync(file, "utf8").split("\n");
  const stop = stopAt[file.split("/").pop()];
  let active = true;
  const before = changed;
  const out = lines.map((line) => {
    if (stop && line.includes(stop)) active = false;
    if (!active || keep.test(line)) return line;
    return java ? convertJava(line) : convertCss(line);
  });
  writeFileSync(file, out.join("\n"));
  console.log(`${String(changed - before).padStart(3)}  ${file}`);
}
console.log(`${changed} Farben umgestellt`);
