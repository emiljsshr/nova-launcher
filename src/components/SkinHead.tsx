
import { useEffect, useRef } from "react";
import { PlayerHead } from "./PixelIcon";

// Zeichnet das Gesicht (inkl. Hut-Ebene) aus einer 64x64-Skin-Textur
export default function SkinHead({
  skin,
  size = 16,
}: {
  skin: string | null;
  size?: number;
}) {
  const canvasRef = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!skin || !canvas) return;

    const image = new Image();
    image.onload = () => {
      const context = canvas.getContext("2d");
      if (!context) return;
      context.imageSmoothingEnabled = false;
      context.clearRect(0, 0, 8, 8);
      context.drawImage(image, 8, 8, 8, 8, 0, 0, 8, 8);
      context.drawImage(image, 40, 8, 8, 8, 0, 0, 8, 8);
    };
    image.src = skin;
  }, [skin]);

  if (!skin) return <PlayerHead size={size} />;

  return (
    <canvas
      ref={canvasRef}
      className="player-head skin-head"
      width={8}
      height={8}
      style={{ width: size, height: size }}
      aria-hidden="true"
    />
  );
}
