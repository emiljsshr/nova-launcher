#!/bin/sh
# Rendert ein Cosmetic mit dem Mod-Kern (ohne Minecraft): thumbnail.png im Cosmetic-Ordner,
# Vorschauen von hinten und vorne nach $TMPDIR/nova-cosmetic/.
#   tools/cosmetics/render.sh public/cosmetics/frost-wings [sekunden]
set -e
root="$(cd "$(dirname "$0")/../.." && pwd)"
out="${TMPDIR:-/tmp}/nova-cosmetic"
mkdir -p "$out/classes"
javac -d "$out/classes" "$root"/mod/core/src/main/java/gg/nova/core/cosmetic/*.java "$root/tools/cosmetics/Render.java"
java -cp "$out/classes" gg.nova.core.cosmetic.Render "$1" "${2:-1.2}" "$out/preview.png" "$out/preview-front.png"
echo "$out/preview.png"
echo "$out/preview-front.png"
