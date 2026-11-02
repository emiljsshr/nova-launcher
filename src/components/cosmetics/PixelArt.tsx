
import { memo, useMemo } from "react";
import {
  drawCharacter,
  drawThumbnail,
  type CharacterLoadout,
  type PixelCanvas,
} from "./sprites";
import { cosmetics } from "../../data/launcherData";

// Rendert eine PixelCanvas als SVG; gleiche Farben einer Zeile werden zusammengefasst
function PixelSvg({
  canvas,
  className = "",
}: {
  canvas: PixelCanvas;
  className?: string;
}) {
  const rects = useMemo(() => {
    const result: { x: number; y: number; w: number; fill: string }[] = [];

    for (let y = 0; y < canvas.height; y++) {
      let x = 0;
      while (x < canvas.width) {
        const fill = canvas.pixels[y * canvas.width + x];
        if (!fill) {
          x++;
          continue;
        }
        let w = 1;
        while (
          x + w < canvas.width &&
          canvas.pixels[y * canvas.width + x + w] === fill
        ) {
          w++;
        }
        result.push({ x, y, w, fill });
        x += w;
      }
    }

    return result;
  }, [canvas]);

  return (
    <svg
      className={className}
      viewBox={`0 0 ${canvas.width} ${canvas.height}`}
      shapeRendering="crispEdges"
      aria-hidden="true"
    >
      {rects.map((rect) => (
        <rect
          key={`${rect.x}-${rect.y}`}
          x={rect.x}
          y={rect.y}
          width={rect.w}
          height={1}
          fill={rect.fill}
        />
      ))}
    </svg>
  );
}

export const CosmeticThumbnail = memo(function CosmeticThumbnail({
  id,
  className = "",
}: {
  id: string;
  className?: string;
}) {
  const canvas = useMemo(() => drawThumbnail(id), [id]);
  // 3D-Cosmetics haben ein gerendertes Vorschaubild (tools/cosmetics/render.sh)
  if (cosmetics.some((item) => item.id === id && item.model)) {
    return (
      <img
        className={`cosmetic-thumb is-rendered ${className}`}
        src={`/cosmetics/${id}/thumbnail.png`}
        alt=""
        draggable={false}
      />
    );
  }
  return <PixelSvg canvas={canvas} className={`cosmetic-thumb ${className}`} />;
});

export function CharacterPreview({
  loadout,
  back,
  className = "",
}: {
  loadout: CharacterLoadout;
  back: boolean;
  className?: string;
}) {
  const canvas = useMemo(
    () => drawCharacter(loadout, back),
    [loadout, back],
  );
  return <PixelSvg canvas={canvas} className={`character-preview ${className}`} />;
}
