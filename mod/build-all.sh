#!/bin/sh
# Baut die Nova-Mod für alle Minecraft-Versionen und legt die JARs im Launcher ab
# (src-tauri/resources/nova-mods/nova-client-<build>.jar). Kein Spielstart.
set -e
cd "$(dirname "$0")"

# Loom 1.19 braucht Java 25+ für Gradle; die Mod selbst wird je Version für Java 17/21/25 kompiliert
if [ -z "$JAVA_HOME" ] && [ -d /opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home ]; then
  export JAVA_HOME=/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home
fi

target="../src-tauri/resources/nova-mods"
mkdir -p "$target"

for dir in versions/*/; do
  build="$(basename "$dir")"
  echo "== Nova-Mod für Minecraft $build"
  ./gradlew -p "$dir" build --console=plain -q
  jar="$(ls "$dir"build/libs/nova-client-*.jar | grep -v -- '-sources' | head -1)"
  cp "$jar" "$target/nova-client-$build.jar"
done

echo "Fertig: $(ls "$target" | wc -l | tr -d ' ') JARs in $target"
