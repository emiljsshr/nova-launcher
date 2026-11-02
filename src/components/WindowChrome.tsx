
import { getCurrentWindow } from "@tauri-apps/api/window";

// Im Browser (npm run dev ohne Tauri) gibt es kein Fenster-Objekt
async function runWindowAction(action: "minimize" | "close") {
  try {
    const appWindow = getCurrentWindow();
    if (action === "minimize") {
      await appWindow.minimize();
    } else {
      await appWindow.close();
    }
  } catch {
    console.warn(`Window action "${action}" is only available in Tauri.`);
  }
}

export function WindowControls({ dark = false }: { dark?: boolean }) {
  return (
    <div className={`window-controls ${dark ? "is-dark" : ""}`}>
      <button
        type="button"
        className="window-button"
        aria-label="Minimieren"
        onClick={() => runWindowAction("minimize")}
      >
        <svg viewBox="0 0 12 12" shapeRendering="crispEdges">
          <rect x="1" y="9" width="10" height="2" fill="currentColor" />
        </svg>
      </button>

      <button
        type="button"
        className="window-button close"
        aria-label="Schließen"
        onClick={() => runWindowAction("close")}
      >
        <svg viewBox="0 0 12 12" shapeRendering="crispEdges">
          <path
            fill="currentColor"
            d="M1 1h2v1h1v1h1v1h2V3h1V2h1V1h2v2h-1v1h-1v1H8v2h1v1h1v1h1v2H9v-1H8v-1H7V8H5v1H4v1H3v1H1V9h1V8h1V7h1V5H3V4H2V3H1z"
          />
        </svg>
      </button>
    </div>
  );
}

const pixelBlocks = [
  [0, 0, 17, 9],
  [0, 9, 22, 6],
  [37, 0, 8, 12],
  [42, 10, 12, 8],
  [57, 0, 12, 8],
  [65, 8, 13, 8],
  [82, 0, 18, 9],
  [87, 9, 13, 9],

  [2, 25, 21, 7],
  [22, 19, 8, 8],
  [30, 25, 15, 8],
  [44, 18, 12, 10],
  [55, 25, 11, 8],
  [70, 18, 14, 8],
  [89, 25, 11, 8],

  [0, 42, 17, 8],
  [4, 35, 8, 8],
  [13, 35, 8, 8],
  [23, 42, 14, 9],
  [37, 35, 12, 8],
  [52, 42, 13, 8],
  [67, 35, 10, 9],
  [77, 42, 20, 8],

  [0, 59, 17, 8],
  [8, 67, 15, 8],
  [24, 59, 12, 8],
  [36, 67, 16, 8],
  [50, 59, 11, 8],
  [64, 67, 14, 8],
  [80, 59, 20, 8],

  [0, 83, 25, 9],
  [25, 75, 12, 8],
  [37, 83, 17, 9],
  [54, 75, 12, 8],
  [66, 83, 15, 9],
  [81, 75, 19, 8],
] as const;

// Hintergrund mit Pixel-Blöcken
export function PixelBackground({ className = "" }: { className?: string }) {
  return (
    <div className={`pixel-background ${className}`} aria-hidden="true">
      {pixelBlocks.map(([x, y, width, height], index) => (
        <span
          key={index}
          className={`pixel-block pixel-block-${index % 5}`}
          style={{
            left: `${x}%`,
            top: `${y}%`,
            width: `${width}%`,
            height: `${height}%`,
          }}
        />
      ))}
    </div>
  );
}
