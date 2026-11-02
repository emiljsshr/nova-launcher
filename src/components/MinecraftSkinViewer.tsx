
import { useEffect, useRef, useState } from "react";
import { PlayerHead } from "./PixelIcon";
import {
  SkinViewer,
  IdleAnimation,
} from "skinview3d";

type MinecraftSkinViewerProps = {
  skinUrl?: string;
  width?: number;
  height?: number;
};

export default function MinecraftSkinViewer({
  skinUrl = "https://minotar.net/skin/kevin",
  width = 180,
  height = 240,
}: MinecraftSkinViewerProps) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;

    // Ohne WebGL würde skinview3d werfen und die ganze App leeren
    let viewer: SkinViewer;
    try {
      viewer = new SkinViewer({
        canvas,
        width,
        height,
        skin: skinUrl,
      });
    } catch {
      setFailed(true);
      return;
    }

    viewer.controls.enableRotate = true;
    viewer.controls.enableZoom = false;
    viewer.controls.enablePan = false;

    // Rotation begrenzen: nicht über Kopf drehen
    viewer.controls.minPolarAngle = Math.PI / 3;
    viewer.controls.maxPolarAngle = Math.PI / 2;

    viewer.animation = new IdleAnimation();

    return () => {
      viewer.dispose();
    };
  }, [skinUrl, width, height]);

  if (failed) {
    return (
      <div className="minecraft-skin-viewer" style={{ width, height }}>
        <PlayerHead size={Math.round(width * 0.45)} />
      </div>
    );
  }

  return (
    <div className="minecraft-skin-viewer" style={{ width, height }}>
      <canvas
        ref={canvasRef}
        style={{ width, height }}
        aria-label="Interaktives 3D-Minecraft-Spielermodell"
      />
    </div>
  );
}
