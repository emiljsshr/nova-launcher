#!/usr/bin/env python3
"""Legt versions/<mc>/ als Kopie einer bestehenden Version an (Startpunkt für die Anpassung)."""
import shutil, sys, pathlib

mc, yarn, fabric_api, dependency, java, run_java, base = sys.argv[1:8]
root = pathlib.Path(__file__).parent / "versions"
target = root / mc
if target.exists():
    sys.exit(f"{target} existiert bereits")

shutil.copytree(root / base / "src", target / "src")
for name in ["build.gradle"]:
    shutil.copy(root / base / name, target / name)
(target / "settings.gradle").write_text((root / base / "settings.gradle").read_text().replace(base, mc))
(target / "gradle.properties").write_text(f"""org.gradle.jvmargs=-Xmx2G

minecraft_version={mc}
yarn_mappings={yarn}
loader_version=0.20.1
loom_version=1.19.3
fabric_version={fabric_api}

# Für diese Minecraft-Versionen ist der Build gedacht (fabric.mod.json)
minecraft_dependency={dependency}
java_version={java}
run_java_version={run_java}
""")
print("angelegt:", target)
