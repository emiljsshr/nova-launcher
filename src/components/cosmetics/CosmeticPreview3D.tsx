import { useEffect, useRef, useState } from "react";
import { IdleAnimation, SkinViewer } from "skinview3d";
import { CosmeticThumbnail } from "./PixelArt";
import { loadCosmeticModel } from "./bedrockModel";

// Spieler mit eigenem Skin und angelegtem 3D-Cosmetic, drehbar mit der Maus
export default function CosmeticPreview3D({
  cosmeticId,
  skin,
  width = 300,
  height = 340,
}: {
  cosmeticId: string;
  skin: string | null;
  width?: number;
  height?: number;
}) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;

    let viewer: SkinViewer;
    try {
      viewer = new SkinViewer({
        canvas,
        width,
        height,
        skin: skin ?? "https://minotar.net/skin/kevin",
      });
    } catch {
      setFailed(true);
      return;
    }

    viewer.controls.enableZoom = false;
    viewer.controls.enablePan = false;
    viewer.controls.minPolarAngle = Math.PI / 3;
    viewer.controls.maxPolarAngle = Math.PI / 2;
    viewer.animation = new IdleAnimation();
    viewer.zoom = 0.75;
    // Von schräg hinten, damit Rücken-Cosmetics sofort sichtbar sind
    viewer.playerObject.rotation.y = Math.PI * 0.8;

    let disposed = false;
    let frame = 0;
    let dispose = () => {};

    loadCosmeticModel(cosmeticId)
      .then((model) => {
        if (disposed) {
          model.dispose();
          return;
        }
        (model.attach === "root" ? viewer.playerObject : viewer.playerObject.skin[model.attach]).add(model.object);
        // Kopf-Cosmetics schräg von vorne zeigen, alles andere von hinten
        if (model.attach === "head") viewer.playerObject.rotation.y = Math.PI * 0.15;
        const start = performance.now();
        const tick = () => {
          model.update((performance.now() - start) / 1000);
          frame = requestAnimationFrame(tick);
        };
        tick();
        dispose = model.dispose;
      })
      .catch(() => setFailed(true));

    return () => {
      disposed = true;
      cancelAnimationFrame(frame);
      dispose();
      viewer.dispose();
    };
  }, [cosmeticId, skin, width, height]);

  if (failed) {
    return (
      <div className="cosmetic-preview-3d is-fallback" style={{ width, height }}>
        <CosmeticThumbnail id={cosmeticId} />
      </div>
    );
  }

  return (
    <div className="cosmetic-preview-3d" style={{ width, height }}>
      <canvas ref={canvasRef} style={{ width, height }} aria-label="3D-Vorschau mit deinem Skin" />
    </div>
  );
}
