use serde::{Deserialize, Serialize};

use crate::download::sha1_hex;
use crate::error::{Error, Result};
use crate::state::{Paths, read_json, write_json};

/// Ein Build der Nova-Mod und die Minecraft-Versionen, für die er gebaut und geprüft ist.
/// Erzeugt von `mod/build-all.sh`; Gruppen entsprechen den API-Ständen von Minecraft.
struct Build {
    name: &'static str,
    game_versions: &'static [&'static str],
    bytes: &'static [u8],
}

macro_rules! build {
    ($name:literal, [$($version:literal),+ $(,)?]) => {
        Build {
            name: $name,
            game_versions: &[$($version),+],
            bytes: include_bytes!(concat!("../resources/nova-mods/nova-client-", $name, ".jar")),
        }
    };
}

const BUILDS: &[Build] = &[
    build!("1.8.9", ["1.8.9"]),
    build!("1.20.1", ["1.20", "1.20.1"]),
    build!("1.20.4", ["1.20.2", "1.20.3", "1.20.4"]),
    build!("1.20.6", ["1.20.5", "1.20.6"]),
    build!("1.21.1", ["1.21", "1.21.1"]),
    build!("1.21.3", ["1.21.2", "1.21.3"]),
    build!("1.21.4", ["1.21.4"]),
    build!("1.21.5", ["1.21.5"]),
    build!("1.21.8", ["1.21.6", "1.21.7", "1.21.8"]),
    build!("1.21.10", ["1.21.9", "1.21.10"]),
    build!("1.21.11", ["1.21.11"]),
    build!("26.1", ["26.1", "26.1.1", "26.1.2"]),
    build!("26.2", ["26.2"]),
    build!("26.3", ["26.3"]),
];

const MOD_MARKER_FILE: &str = ".nova-mod.json";

#[derive(Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct ManagedMod {
    game_version: String,
    sha1: String,
}

fn bundled_build(game_version: &str) -> Option<&'static Build> {
    BUILDS
        .iter()
        .find(|build| build.game_versions.contains(&game_version))
}

fn bundled_mod(game_version: &str) -> Option<&'static [u8]> {
    bundled_build(game_version).map(|build| build.bytes)
}

fn mod_file_name(game_version: &str) -> Option<String> {
    bundled_build(game_version).map(|build| format!("nova-client-{}.jar", build.name))
}

pub fn supported_versions() -> Vec<String> {
    BUILDS
        .iter()
        .flat_map(|build| build.game_versions.iter().map(|version| (*version).to_owned()))
        .collect()
}

#[tauri::command]
pub fn nova_mod_versions() -> Vec<String> {
    supported_versions()
}

pub async fn sync_profile_mod(
    paths: &Paths,
    profile_id: &str,
    game_version: &str,
    is_fabric: bool,
) -> Result<()> {
    let instance = paths.instance(profile_id);
    let mods_dir = instance.join("mods");
    let marker_path = instance.join(MOD_MARKER_FILE);
    let desired_bytes = is_fabric.then(|| bundled_mod(game_version)).flatten();
    let previous = if marker_path.exists() {
        read_json::<Option<ManagedMod>>(&marker_path).await?
    } else {
        None
    };

    if let Some(previous) = previous {
        let previous_bytes = bundled_mod(&previous.game_version)
            .ok_or_else(|| Error::msg("Die Nova-Mod-Markierung im Profilordner ist ungültig."))?;
        let previous_name = mod_file_name(&previous.game_version)
            .ok_or_else(|| Error::msg("Die Nova-Mod-Markierung im Profilordner ist ungültig."))?;
        let previous_path = mods_dir.join(previous_name);

        match tokio::fs::read(&previous_path).await {
            Ok(contents) if sha1_hex(&contents) == previous.sha1 => {
                // Gleicher Build (z. B. 1.21.6 → 1.21.7) muss nicht neu kopiert werden
                let keep_existing = is_fabric
                    && mod_file_name(&previous.game_version) == mod_file_name(game_version);
                if keep_existing && sha1_hex(previous_bytes) == previous.sha1 {
                    return Ok(());
                }
                tokio::fs::remove_file(&previous_path).await?;
            }
            Ok(_) => {
                return Err(Error::msg(format!(
                    "Die verwaltete Nova-Mod-Datei für Minecraft {} wurde verändert. \
                     Sie wurde nicht überschrieben; bitte prüfe den Mods-Ordner des Profils.",
                    previous.game_version
                )));
            }
            Err(error) if error.kind() == std::io::ErrorKind::NotFound => {}
            Err(error) => return Err(error.into()),
        }

        tokio::fs::remove_file(&marker_path).await?;
    }

    let (Some(bytes), Some(file_name)) = (desired_bytes, mod_file_name(game_version)) else {
        return Ok(());
    };

    tokio::fs::create_dir_all(&mods_dir).await?;
    let mod_path = mods_dir.join(&file_name);
    let expected_hash = sha1_hex(bytes);

    match tokio::fs::read(&mod_path).await {
        Ok(contents) if sha1_hex(&contents) == expected_hash => {}
        Ok(_) => {
            return Err(Error::msg(format!(
                "Im Mods-Ordner liegt bereits eine andere Datei namens {file_name}. \
                 Sie wurde nicht überschrieben."
            )));
        }
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => {
            let temp_path = mods_dir.join(format!("{file_name}.tmp"));
            tokio::fs::write(&temp_path, bytes).await?;
            tokio::fs::rename(&temp_path, &mod_path).await?;
        }
        Err(error) => return Err(error.into()),
    }

    write_json(
        &marker_path,
        &ManagedMod {
            game_version: game_version.to_owned(),
            sha1: expected_hash,
        },
        false,
    )
    .await
}

#[cfg(test)]
mod tests {
    use super::{BUILDS, bundled_mod, mod_file_name, supported_versions, sync_profile_mod};
    use crate::state::Paths;

    #[test]
    fn bundled_mods_match_the_advertised_versions() {
        for version in supported_versions() {
            assert!(bundled_mod(&version).is_some());
        }
    }

    #[test]
    fn every_game_version_belongs_to_exactly_one_build() {
        for version in supported_versions() {
            let count = BUILDS
                .iter()
                .filter(|build| build.game_versions.contains(&version.as_str()))
                .count();
            assert_eq!(count, 1, "{version} gehört zu {count} Builds");
        }
    }

    #[test]
    fn version_groups_share_a_build() {
        assert_eq!(mod_file_name("1.21.6").as_deref(), Some("nova-client-1.21.8.jar"));
        assert_eq!(mod_file_name("1.21.7"), mod_file_name("1.21.8"));
        assert_eq!(mod_file_name("1.20.2").as_deref(), Some("nova-client-1.20.4.jar"));
    }

    #[test]
    fn unsupported_versions_do_not_get_a_nova_mod() {
        assert!(bundled_mod("1.19.4").is_none());
        assert!(bundled_mod("1.12.2").is_none());
    }

    #[tokio::test]
    async fn sync_installs_replaces_and_removes_only_managed_mods() {
        let root = std::env::temp_dir().join(format!("nova-mod-test-{}", uuid::Uuid::new_v4()));
        let paths = Paths { root: root.clone() };
        let mods = paths.instance("profile").join("mods");
        tokio::fs::create_dir_all(&mods).await.unwrap();
        tokio::fs::write(mods.join("other-mod.jar"), b"keep")
            .await
            .unwrap();

        sync_profile_mod(&paths, "profile", "1.21.1", true)
            .await
            .unwrap();
        assert_eq!(
            tokio::fs::read(mods.join("nova-client-1.21.1.jar")).await.unwrap(),
            bundled_mod("1.21.1").unwrap()
        );

        // Wechsel auf eine Version aus einer Gruppe: Datei heißt nach dem Build
        sync_profile_mod(&paths, "profile", "1.21.7", true)
            .await
            .unwrap();
        assert!(!mods.join("nova-client-1.21.1.jar").exists());
        assert_eq!(
            tokio::fs::read(mods.join("nova-client-1.21.8.jar")).await.unwrap(),
            bundled_mod("1.21.8").unwrap()
        );

        sync_profile_mod(&paths, "profile", "1.21.7", false)
            .await
            .unwrap();
        assert!(!mods.join("nova-client-1.21.8.jar").exists());
        assert_eq!(
            tokio::fs::read(mods.join("other-mod.jar")).await.unwrap(),
            b"keep"
        );

        tokio::fs::remove_dir_all(root).await.unwrap();
    }
}
