#!/bin/sh
# Verkleinerte Vorschau der Test-Screenshots einer Version: ./preview.sh 1.21.3
out="${TMPDIR:-/tmp}/nova-preview"
mkdir -p "$out"
for name in loading title; do
  sips -Z 1000 "versions/$1/run/screenshots/nova-$name.png" --out "$out/$1-$name.png" >/dev/null
  echo "$out/$1-$name.png"
done
