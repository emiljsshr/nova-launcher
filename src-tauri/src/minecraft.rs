use std::collections::{HashMap, HashSet};
use std::path::{Path, PathBuf};

use serde::Serialize;
use serde_json::Value;
use tauri::State;

use crate::download::{download_all, fetch_json_cached, DownloadTask, Progress};
use crate::error::{Error, Result};
use crate::state::{LauncherState, Paths};

const VERSION_MANIFEST_URL: &str = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
const JAVA_MANIFEST_URL: &str =
    "https://launchermeta.mojang.com/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json";
const FABRIC_META_URL: &str = "https://meta.fabricmc.net/v2";
/// Legacy Fabric (z. B. 1.8.9): gleiches API-Format, eigener Server
const LEGACY_FABRIC_META_URL: &str = "https://meta.legacyfabric.net/v2";
const LIBRARIES_URL: &str = "https://libraries.minecraft.net/";
const RESOURCES_URL: &str = "https://resources.download.minecraft.net";

/* ========================================
   VERSIONEN
======================================== */

async fn version_manifest(http: &reqwest::Client, paths: &Paths, refresh: bool) -> Result<Value> {
    let cache = paths.versions().join("version_manifest_v2.json");

    if !refresh
        && let Ok(bytes) = tokio::fs::read(&cache).await
            && let Ok(value) = serde_json::from_slice(&bytes) {
                return Ok(value);
            }

    // Offline: auf den Cache zurückfallen
    match http.get(VERSION_MANIFEST_URL).send().await.and_then(|r| r.error_for_status()) {
        Ok(response) => {
            let bytes = response.bytes().await?;
            crate::download::write_file(&cache, &bytes, false).await?;
            Ok(serde_json::from_slice(&bytes)?)
        }
        Err(error) => match tokio::fs::read(&cache).await {
            Ok(bytes) => Ok(serde_json::from_slice(&bytes)?),
            Err(_) => Err(Error::msg(format!("Versionsliste nicht erreichbar: {error}"))),
        },
    }
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct GameVersion {
    id: String,
    kind: String,
    release_time: String,
}

#[tauri::command]
pub async fn versions_list(state: State<'_, LauncherState>, include_snapshots: bool) -> Result<Vec<GameVersion>> {
    let manifest = version_manifest(&state.http, &state.paths, true).await?;
    let versions = manifest["versions"].as_array().cloned().unwrap_or_default();

    Ok(versions
        .iter()
        .filter(|version| include_snapshots || version["type"] == "release")
        .map(|version| GameVersion {
            id: version["id"].as_str().unwrap_or_default().to_owned(),
            kind: version["type"].as_str().unwrap_or_default().to_owned(),
            release_time: version["releaseTime"].as_str().unwrap_or_default().to_owned(),
        })
        .collect())
}

/// Loader-Einträge für eine Minecraft-Version: erst Fabric, dann Legacy Fabric.
/// Gibt den passenden Meta-Server und die Einträge zurück (leer = nicht unterstützt).
async fn loader_entries(http: &reqwest::Client, game_version: &str) -> (&'static str, Vec<Value>) {
    for base in [FABRIC_META_URL, LEGACY_FABRIC_META_URL] {
        let response = http.get(format!("{base}/versions/loader/{game_version}")).send().await;
        if let Ok(response) = response.and_then(|r| r.error_for_status()) {
            let entries: Vec<Value> = response.json().await.unwrap_or_default();
            if !entries.is_empty() {
                return (base, entries);
            }
        }
    }
    (FABRIC_META_URL, Vec::new())
}

/// Liste der Fabric-Loader-Versionen (stabile zuerst). Leer = Version wird nicht unterstützt.
/// Für alte Versionen wie 1.8.9 kommen die Einträge von Legacy Fabric.
#[tauri::command]
pub async fn fabric_loaders(state: State<'_, LauncherState>, game_version: String) -> Result<Vec<String>> {
    let (_, entries) = loader_entries(&state.http, &game_version).await;
    let mut stable = Vec::new();
    let mut unstable = Vec::new();
    for entry in entries {
        let Some(version) = entry["loader"]["version"].as_str() else { continue };
        if entry["loader"]["stable"].as_bool().unwrap_or(false) {
            stable.push(version.to_owned());
        } else {
            unstable.push(version.to_owned());
        }
    }
    stable.extend(unstable);
    Ok(stable)
}

pub async fn vanilla_version_json(http: &reqwest::Client, paths: &Paths, id: &str) -> Result<Value> {
    let find = |manifest: &Value| {
        manifest["versions"]
            .as_array()
            .and_then(|versions| versions.iter().find(|version| version["id"] == id))
            .cloned()
    };

    let entry = match find(&version_manifest(http, paths, false).await?) {
        Some(entry) => entry,
        None => find(&version_manifest(http, paths, true).await?)
            .ok_or_else(|| Error::msg(format!("Minecraft {id} wurde nicht gefunden.")))?,
    };

    let url = entry["url"].as_str().unwrap_or_default();
    let cache = paths.versions().join(id).join(format!("{id}.json"));
    fetch_json_cached(http, url, &cache, entry["sha1"].as_str()).await
}

pub async fn fabric_version_json(
    http: &reqwest::Client,
    paths: &Paths,
    game_version: &str,
    loader_version: Option<&str>,
) -> Result<Value> {
    let (base, entries) = loader_entries(http, game_version).await;
    if entries.is_empty() {
        return Err(Error::msg(format!("Fabric unterstützt Minecraft {game_version} nicht.")));
    }
    let loader = match loader_version {
        Some(version) => version.to_owned(),
        None => entries
            .iter()
            .find(|entry| entry["loader"]["stable"].as_bool().unwrap_or(false))
            .or_else(|| entries.first())
            .and_then(|entry| entry["loader"]["version"].as_str())
            .map(str::to_owned)
            .ok_or_else(|| Error::msg(format!("Fabric unterstützt Minecraft {game_version} nicht.")))?,
    };

    let id = format!("fabric-loader-{loader}-{game_version}");
    let cache = paths.versions().join(&id).join(format!("{id}.json"));
    let url = format!("{base}/versions/loader/{game_version}/{loader}/profile/json");
    fetch_json_cached(http, &url, &cache, None).await
}

fn library_key(name: &str) -> String {
    // group:artifact[:classifier] – die Version wird ignoriert
    let parts: Vec<&str> = name.split('@').next().unwrap_or(name).split(':').collect();
    match parts.as_slice() {
        [group, artifact, _, classifier, ..] => format!("{group}:{artifact}:{classifier}"),
        [group, artifact, ..] => format!("{group}:{artifact}"),
        _ => name.to_owned(),
    }
}

/// Legt das Fabric-Profil über die Vanilla-Version (wie "inheritsFrom" im offiziellen Launcher).
pub fn merge_versions(vanilla: &Value, child: &Value) -> Value {
    let mut merged = vanilla.clone();
    merged["jar"] = vanilla["id"].clone();
    merged["id"] = child["id"].clone();
    if let Some(main_class) = child.get("mainClass") {
        merged["mainClass"] = main_class.clone();
    }

    let child_libraries = child["libraries"].as_array().cloned().unwrap_or_default();
    let overridden: HashSet<String> = child_libraries
        .iter()
        .filter_map(|library| library["name"].as_str().map(library_key))
        .collect();
    let mut libraries = child_libraries;
    libraries.extend(
        vanilla["libraries"]
            .as_array()
            .into_iter()
            .flatten()
            .filter(|library| {
                library["name"]
                    .as_str()
                    .is_none_or(|name| !overridden.contains(&library_key(name)))
            })
            .cloned(),
    );
    merged["libraries"] = Value::Array(libraries);

    for kind in ["game", "jvm"] {
        if let Some(extra) = child["arguments"][kind].as_array()
            && let Some(list) = merged["arguments"][kind].as_array_mut() {
                list.extend(extra.iter().cloned());
            }
    }
    merged
}

/* ========================================
   REGELN
======================================== */

pub fn os_name() -> &'static str {
    if cfg!(target_os = "macos") {
        "osx"
    } else if cfg!(target_os = "windows") {
        "windows"
    } else {
        "linux"
    }
}

#[derive(Default)]
pub struct Features {
    pub custom_resolution: bool,
    pub quick_play_multiplayer: bool,
}

impl Features {
    fn get(&self, key: &str) -> bool {
        match key {
            "has_custom_resolution" => self.custom_resolution,
            "is_quick_play_multiplayer" => self.quick_play_multiplayer,
            _ => false,
        }
    }
}

fn rule_matches(rule: &Value, features: &Features) -> bool {
    if let Some(os) = rule.get("os") {
        if let Some(name) = os["name"].as_str()
            && name != os_name() {
                return false;
            }
        if let Some(arch) = os["arch"].as_str() {
            let matches = match arch {
                "x86" => cfg!(target_pointer_width = "32"),
                "arm64" | "aarch64" => cfg!(target_arch = "aarch64"),
                _ => cfg!(target_arch = "x86_64"),
            };
            if !matches {
                return false;
            }
        }
        // Versionsregeln betreffen nur uralte Systeme (z. B. macOS 10.5)
        if os.get("version").is_some() {
            return false;
        }
    }

    if let Some(required) = rule["features"].as_object() {
        for (key, value) in required {
            if Some(features.get(key)) != value.as_bool() {
                return false;
            }
        }
    }
    true
}

pub fn rules_allow(rules: Option<&Value>, features: &Features) -> bool {
    let Some(rules) = rules.and_then(Value::as_array) else {
        return true;
    };
    let mut allowed = false;
    for rule in rules {
        if rule_matches(rule, features) {
            allowed = rule["action"] == "allow";
        }
    }
    allowed
}

/* ========================================
   BIBLIOTHEKEN
======================================== */

fn maven_path(name: &str) -> Option<String> {
    let (coords, extension) = name.split_once('@').unwrap_or((name, "jar"));
    let parts: Vec<&str> = coords.split(':').collect();
    let [group, artifact, version, rest @ ..] = parts.as_slice() else {
        return None;
    };
    let classifier = rest.first().map(|value| format!("-{value}")).unwrap_or_default();
    Some(format!(
        "{}/{artifact}/{version}/{artifact}-{version}{classifier}.{extension}",
        group.replace('.', "/")
    ))
}

pub struct Libraries {
    pub classpath: Vec<PathBuf>,
    pub native_jars: Vec<PathBuf>,
    pub downloads: Vec<DownloadTask>,
}

fn task_from_download(info: &Value, path: PathBuf) -> Option<DownloadTask> {
    let url = info["url"].as_str().filter(|url| !url.is_empty())?;
    Some(DownloadTask {
        url: url.to_owned(),
        path,
        sha1: info["sha1"].as_str().map(str::to_owned),
        size: info["size"].as_u64(),
        executable: false,
    })
}

pub fn resolve_libraries(version: &Value, paths: &Paths, features: &Features) -> Libraries {
    let root = paths.libraries();
    let mut result = Libraries { classpath: Vec::new(), native_jars: Vec::new(), downloads: Vec::new() };
    let mut seen = HashSet::new();

    for library in version["libraries"].as_array().into_iter().flatten() {
        if !rules_allow(library.get("rules"), features) {
            continue;
        }
        let name = library["name"].as_str().unwrap_or_default();

        // Normale Bibliothek (Mojang-Format mit downloads.artifact oder Maven-Format von Fabric)
        let artifact = &library["downloads"]["artifact"];
        if artifact.is_object() {
            let relative = artifact["path"].as_str().map(str::to_owned).or_else(|| maven_path(name));
            if let Some(relative) = relative {
                let path = root.join(relative);
                if let Some(task) = task_from_download(artifact, path.clone()) {
                    result.downloads.push(task);
                }
                if seen.insert(path.clone()) {
                    result.classpath.push(path);
                }
            }
        } else if library.get("downloads").is_none()
            && library.get("natives").is_none()
            && let Some(relative) = maven_path(name) {
                let base = library["url"].as_str().unwrap_or(LIBRARIES_URL);
                let base = if base.ends_with('/') { base.to_owned() } else { format!("{base}/") };
                let path = root.join(&relative);
                result.downloads.push(DownloadTask {
                    url: format!("{base}{relative}"),
                    path: path.clone(),
                    sha1: library["sha1"].as_str().map(str::to_owned),
                    size: library["size"].as_u64(),
                    executable: false,
                });
                if seen.insert(path.clone()) {
                    result.classpath.push(path);
                }
            }

        // Alte Natives (bis 1.18): eigene Classifier-JARs, die entpackt werden müssen
        if let Some(classifier) = library["natives"][os_name()].as_str() {
            let classifier = classifier.replace("${arch}", if cfg!(target_pointer_width = "64") { "64" } else { "32" });
            let info = &library["downloads"]["classifiers"][classifier.as_str()];
            if let Some(relative) = info["path"].as_str() {
                let path = root.join(relative);
                if let Some(task) = task_from_download(info, path.clone()) {
                    result.downloads.push(task);
                }
                result.native_jars.push(path);
            } else if library.get("downloads").is_none()
                && let Some(relative) = maven_path(&format!("{name}:{classifier}"))
            {
                // Maven-Format ohne downloads (Legacy Fabric, z. B. gepatchtes LWJGL 2): nur die Native-JAR
                let base = library["url"].as_str().unwrap_or(LIBRARIES_URL);
                let base = if base.ends_with('/') { base.to_owned() } else { format!("{base}/") };
                let path = root.join(&relative);
                result.downloads.push(DownloadTask {
                    url: format!("{base}{relative}"),
                    path: path.clone(),
                    sha1: None,
                    size: None,
                    executable: false,
                });
                result.native_jars.push(path);
            }
        }
    }

    result
}

pub fn extract_natives(jars: &[PathBuf], target: &Path) -> Result<()> {
    let marker = target.join(".extracted");
    if marker.exists() {
        return Ok(());
    }
    std::fs::create_dir_all(target)?;

    for jar in jars {
        let file = std::fs::File::open(jar)?;
        let mut archive = zip::ZipArchive::new(file)?;
        for index in 0..archive.len() {
            let mut entry = archive.by_index(index)?;
            let Some(relative) = entry.enclosed_name() else { continue };
            if entry.is_dir() || relative.starts_with("META-INF") {
                continue;
            }
            let output = target.join(relative);
            if let Some(parent) = output.parent() {
                std::fs::create_dir_all(parent)?;
            }
            let mut writer = std::fs::File::create(output)?;
            std::io::copy(&mut entry, &mut writer)?;
        }
    }

    std::fs::write(marker, b"")?;
    Ok(())
}

/* ========================================
   CLIENT & ASSETS
======================================== */

pub async fn install_client(http: &reqwest::Client, paths: &Paths, version: &Value, progress: &Progress) -> Result<PathBuf> {
    let jar_id = version["jar"].as_str().or_else(|| version["id"].as_str()).unwrap_or_default();
    let path = paths.versions().join(jar_id).join(format!("{jar_id}.jar"));
    let info = &version["downloads"]["client"];

    let task = task_from_download(info, path.clone())
        .ok_or_else(|| Error::msg("Für diese Version gibt es keinen Client-Download."))?;
    download_all(http, vec![task], progress, "client", "Minecraft-Client").await?;
    Ok(path)
}

pub struct Assets {
    pub index_id: String,
    pub root: PathBuf,
    /// Pfad für ${game_assets} (alte Versionen mit "virtuellen" Assets)
    pub legacy_dir: PathBuf,
}

pub async fn install_assets(
    http: &reqwest::Client,
    paths: &Paths,
    version: &Value,
    game_dir: &Path,
    progress: &Progress,
) -> Result<Assets> {
    let reference = &version["assetIndex"];
    let index_id = reference["id"].as_str().unwrap_or("legacy").to_owned();
    let root = paths.assets();
    let index = fetch_json_cached(
        http,
        reference["url"].as_str().unwrap_or_default(),
        &root.join("indexes").join(format!("{index_id}.json")),
        reference["sha1"].as_str(),
    )
    .await?;

    let objects = index["objects"].as_object().cloned().unwrap_or_default();
    let object_path = |hash: &str| root.join("objects").join(&hash[..2]).join(hash);

    let tasks = objects
        .values()
        .filter_map(|object| {
            let hash = object["hash"].as_str()?;
            Some(DownloadTask {
                url: format!("{RESOURCES_URL}/{}/{hash}", &hash[..2]),
                path: object_path(hash),
                sha1: Some(hash.to_owned()),
                size: object["size"].as_u64(),
                executable: false,
            })
        })
        .collect();
    download_all(http, tasks, progress, "assets", "Assets").await?;

    let map_to_resources = index["map_to_resources"].as_bool().unwrap_or(false);
    let is_virtual = index["virtual"].as_bool().unwrap_or(false);
    let legacy_dir = if map_to_resources {
        game_dir.join("resources")
    } else {
        root.join("virtual").join(&index_id)
    };

    if map_to_resources || is_virtual {
        for (name, object) in &objects {
            let Some(hash) = object["hash"].as_str() else { continue };
            let target = legacy_dir.join(name);
            if !target.exists() {
                if let Some(parent) = target.parent() {
                    tokio::fs::create_dir_all(parent).await?;
                }
                tokio::fs::copy(object_path(hash), &target).await?;
            }
        }
    }

    Ok(Assets { index_id, root, legacy_dir })
}

/* ========================================
   JAVA-RUNTIME
======================================== */

fn java_platform() -> &'static str {
    match (std::env::consts::OS, std::env::consts::ARCH) {
        ("macos", "aarch64") => "mac-os-arm64",
        ("macos", _) => "mac-os",
        ("windows", "aarch64") => "windows-arm64",
        ("windows", "x86") => "windows-x86",
        ("windows", _) => "windows-x64",
        ("linux", "x86") => "linux-i386",
        _ => "linux",
    }
}

fn java_executable(base: &Path) -> PathBuf {
    if cfg!(target_os = "macos") {
        base.join("jre.bundle/Contents/Home/bin/java")
    } else if cfg!(target_os = "windows") {
        base.join("bin").join("javaw.exe")
    } else {
        base.join("bin").join("java")
    }
}

pub async fn ensure_java(http: &reqwest::Client, paths: &Paths, version: &Value, progress: &Progress) -> Result<PathBuf> {
    let component = version["javaVersion"]["component"].as_str().unwrap_or("jre-legacy");
    let all = fetch_json_cached(http, JAVA_MANIFEST_URL, &paths.runtimes().join("all.json"), None).await?;

    let mut platform = java_platform();
    let has_runtime = |platform: &str| {
        all[platform][component].as_array().is_some_and(|entries| !entries.is_empty())
    };
    // Für alte Versionen (Java 8) gibt es keine ARM-Runtime – dann läuft die x64-Version über Rosetta
    if !has_runtime(platform) && platform == "mac-os-arm64" {
        platform = "mac-os";
    }
    if !has_runtime(platform) {
        return Err(Error::msg(format!("Keine Java-Runtime \"{component}\" für dieses System verfügbar.")));
    }

    let entry = &all[platform][component][0]["manifest"];
    let base = paths.runtimes().join(component).join(platform);
    let manifest = fetch_json_cached(
        http,
        entry["url"].as_str().unwrap_or_default(),
        &paths.runtimes().join(component).join(format!("{platform}.manifest.json")),
        entry["sha1"].as_str(),
    )
    .await?;

    let mut tasks = Vec::new();
    let mut links = Vec::new();
    for (relative, file) in manifest["files"].as_object().into_iter().flatten() {
        let path = base.join(relative);
        match file["type"].as_str() {
            Some("directory") => tokio::fs::create_dir_all(&path).await?,
            Some("file") => {
                if let Some(mut task) = task_from_download(&file["downloads"]["raw"], path) {
                    task.executable = file["executable"].as_bool().unwrap_or(false);
                    tasks.push(task);
                }
            }
            Some("link") => {
                if let Some(target) = file["target"].as_str() {
                    links.push((path, target.to_owned()));
                }
            }
            _ => {}
        }
    }
    download_all(http, tasks, progress, "java", "Java-Runtime").await?;

    #[cfg(unix)]
    for (path, target) in links {
        if tokio::fs::symlink_metadata(&path).await.is_err() {
            if let Some(parent) = path.parent() {
                tokio::fs::create_dir_all(parent).await?;
            }
            tokio::fs::symlink(target, &path).await?;
        }
    }
    #[cfg(not(unix))]
    let _ = links;

    let java = java_executable(&base);
    if !java.exists() {
        return Err(Error::msg("Die Java-Runtime ist unvollständig. Bitte erneut starten."));
    }
    Ok(java)
}

/* ========================================
   ARGUMENTE
======================================== */

fn collect_arguments(list: Option<&Value>, features: &Features, output: &mut Vec<String>) {
    for argument in list.and_then(Value::as_array).into_iter().flatten() {
        match argument {
            Value::String(value) => output.push(value.clone()),
            Value::Object(_) if rules_allow(argument.get("rules"), features) => match &argument["value"] {
                Value::String(value) => output.push(value.clone()),
                Value::Array(values) => output.extend(values.iter().filter_map(Value::as_str).map(str::to_owned)),
                _ => {}
            },
            _ => {}
        }
    }
}

pub fn supports_quick_play(version: &Value) -> bool {
    version["arguments"]["game"].to_string().contains("quickPlayMultiplayer")
}

pub fn build_arguments(
    version: &Value,
    features: &Features,
    jvm_prefix: Vec<String>,
    placeholders: &HashMap<&str, String>,
) -> Vec<String> {
    let mut jvm = jvm_prefix;
    let mut game = Vec::new();

    if version["arguments"].is_object() {
        collect_arguments(version["arguments"].get("jvm"), features, &mut jvm);
        collect_arguments(version["arguments"].get("game"), features, &mut game);
    } else {
        // Altes Format (bis 1.12): feste JVM-Argumente + minecraftArguments
        if cfg!(target_os = "macos") {
            jvm.push("-XstartOnFirstThread".into());
        }
        jvm.extend(["-Djava.library.path=${natives_directory}", "-cp", "${classpath}"].map(String::from));
        game.extend(
            version["minecraftArguments"]
                .as_str()
                .unwrap_or_default()
                .split_whitespace()
                .map(str::to_owned),
        );
        if features.custom_resolution {
            game.extend(["--width", "${resolution_width}", "--height", "${resolution_height}"].map(String::from));
        }
    }

    let main_class = version["mainClass"].as_str().unwrap_or_default().to_owned();
    jvm.into_iter()
        .chain(std::iter::once(main_class))
        .chain(game)
        .map(|argument| substitute(&argument, placeholders))
        .collect()
}

fn substitute(argument: &str, placeholders: &HashMap<&str, String>) -> String {
    let mut result = argument.to_owned();
    for (key, value) in placeholders {
        let token = format!("${{{key}}}");
        if result.contains(&token) {
            result = result.replace(&token, value);
        }
    }
    result
}

#[cfg(test)]
mod tests {
    use super::*;

    fn test_paths() -> Paths {
        let root = std::env::var("NOVA_TEST_DIR").expect("NOVA_TEST_DIR setzen");
        Paths { root: PathBuf::from(root) }
    }

    #[test]
    fn maven_paths() {
        assert_eq!(
            maven_path("org.ow2.asm:asm:9.7.1").as_deref(),
            Some("org/ow2/asm/asm/9.7.1/asm-9.7.1.jar")
        );
        assert_eq!(
            maven_path("org.lwjgl:lwjgl:3.3.3:natives-macos-arm64").as_deref(),
            Some("org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3-natives-macos-arm64.jar")
        );
    }

    #[test]
    fn rules() {
        let features = Features::default();
        let osx_only: Value = serde_json::json!([{ "action": "allow", "os": { "name": "osx" } }]);
        assert_eq!(rules_allow(Some(&osx_only), &features), cfg!(target_os = "macos"));

        // 1.8.9: erlaubt außer auf macOS 10.5 – darf auf aktuellen Systemen nicht blockieren
        let legacy: Value = serde_json::json!([
            { "action": "allow" },
            { "action": "disallow", "os": { "name": "osx", "version": "^10\\.5\\.\\d$" } }
        ]);
        assert!(rules_allow(Some(&legacy), &features));

        let resolution: Value = serde_json::json!([{ "action": "allow", "features": { "has_custom_resolution": true } }]);
        assert!(!rules_allow(Some(&resolution), &features));
        assert!(rules_allow(Some(&resolution), &Features { custom_resolution: true, ..Default::default() }));
    }

    /// Echte Installation gegen die Mojang-/Fabric-Server (ohne Assets, die sind sehr groß).
    /// NOVA_TEST_DIR=/tmp/x cargo test install_versions -- --ignored --nocapture
    #[tokio::test(flavor = "multi_thread")]
    #[ignore]
    async fn install_versions() {
        let http = reqwest::Client::new();
        let paths = test_paths();
        let progress = Progress::silent();

        for (game_version, fabric) in [("26.3", true), ("1.21.4", true), ("1.8.9", true)] {
            let vanilla = vanilla_version_json(&http, &paths, game_version).await.map_err(|e| e.message().to_owned()).unwrap();
            let version = if fabric {
                let child = fabric_version_json(&http, &paths, game_version, None).await.map_err(|e| e.message().to_owned()).unwrap();
                merge_versions(&vanilla, &child)
            } else {
                vanilla
            };

            let java = ensure_java(&http, &paths, &version, &progress).await.map_err(|e| e.message().to_owned()).unwrap();
            let client = install_client(&http, &paths, &version, &progress).await.map_err(|e| e.message().to_owned()).unwrap();

            let features = Features { custom_resolution: true, ..Default::default() };
            let libraries = resolve_libraries(&version, &paths, &features);
            download_all(&http, libraries.downloads, &progress, "libraries", "Bibliotheken").await.map_err(|e| e.message().to_owned()).unwrap();
            for path in libraries.classpath.iter().chain([&client]) {
                assert!(path.exists(), "fehlt: {}", path.display());
            }

            let natives = paths.natives(version["id"].as_str().unwrap());
            extract_natives(&libraries.native_jars, &natives).map_err(|e| e.message().to_owned()).unwrap();

            let output = std::process::Command::new(&java).arg("-version").output().unwrap();
            println!(
                "{} → {} Bibliotheken, {} Native-JARs, main {}\n{}",
                version["id"],
                libraries.classpath.len(),
                libraries.native_jars.len(),
                version["mainClass"],
                String::from_utf8_lossy(&output.stderr).lines().next().unwrap_or_default()
            );
            assert!(output.status.success());

            let placeholders = HashMap::from([("auth_player_name", "Test".to_owned())]);
            let args = build_arguments(&version, &features, vec!["-Xmx2G".into()], &placeholders);
            assert!(args.contains(&"--username".to_owned()));
            assert!(args.contains(&"Test".to_owned()));
        }
    }
}
