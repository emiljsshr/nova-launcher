use std::collections::{HashMap, HashSet};

use serde::{Deserialize, Serialize};
use serde_json::{json, Value};

use crate::download::{download_all, sha1_hex, DownloadTask, Progress};
use crate::error::Result;
use crate::state::{read_json, write_json, Paths};

/// Performance-Mods von Modrinth, die der Launcher in Fabric-Profile legt (wie Lunar seine Optimierungen):
/// Sodium (Rendering), Lithium (Spiellogik), FerriteCore (Arbeitsspeicher), ImmediatelyFast (HUD und Text),
/// Entity Culling (verdeckte Entities nicht zeichnen). Abhängigkeiten (z. B. Fabric API) kommen mit.
const PROJECTS: &[&str] = &["sodium", "lithium", "ferrite-core", "immediatelyfast", "entityculling"];
const API: &str = "https://api.modrinth.com/v2";
const MARKER_FILE: &str = ".nova-performance.json";

/// Welche Dateien der Launcher selbst in den Mods-Ordner gelegt hat – nur die räumt er wieder weg.
#[derive(Serialize, Deserialize, Default)]
#[serde(rename_all = "camelCase")]
struct Marker {
    files: Vec<ManagedFile>,
}

#[derive(Serialize, Deserialize, Clone)]
#[serde(rename_all = "camelCase")]
struct ManagedFile {
    project: String,
    file: String,
    sha1: String,
}

#[derive(Deserialize)]
struct ModrinthVersion {
    project_id: String,
    version_type: String,
    files: Vec<ModrinthFile>,
    dependencies: Vec<ModrinthDependency>,
}

#[derive(Deserialize)]
struct ModrinthFile {
    url: String,
    filename: String,
    primary: bool,
    size: u64,
    hashes: HashMap<String, String>,
}

#[derive(Deserialize)]
struct ModrinthDependency {
    project_id: Option<String>,
    dependency_type: String,
}

/// Bringt die Performance-Mods eines Profils auf Stand: passende Versionen laden, alte und abgewählte
/// entfernen. Gibt die Namen der installierten Dateien zurück. Für 1.8.9 (Legacy Fabric) gibt es keine.
pub async fn sync_profile(
    http: &reqwest::Client,
    paths: &Paths,
    profile_id: &str,
    game_version: &str,
    is_fabric: bool,
    enabled: bool,
    progress: &Progress,
) -> Result<Vec<String>> {
    let instance = paths.instance(profile_id);
    let mods_dir = instance.join("mods");
    let marker_path = instance.join(MARKER_FILE);
    let previous: Marker = read_json(&marker_path).await?;

    let wanted = if enabled && is_fabric && game_version != "1.8.9" {
        progress.report("performance", "Performance-Mods werden geprüft", 0, 0);
        resolve(http, &mods_dir, &previous, game_version).await?
    } else {
        Vec::new()
    };

    // Nicht mehr gewünschte eigene Dateien entfernen (nur unverändert gebliebene)
    for old in &previous.files {
        if wanted.iter().any(|new| new.0.file == old.file) {
            continue;
        }
        let path = mods_dir.join(&old.file);
        if let Ok(bytes) = tokio::fs::read(&path).await
            && sha1_hex(&bytes).eq_ignore_ascii_case(&old.sha1)
        {
            tokio::fs::remove_file(&path).await?;
        }
    }

    let tasks = wanted
        .iter()
        .map(|file| DownloadTask {
            url: file.1.clone(),
            path: mods_dir.join(&file.0.file),
            sha1: Some(file.0.sha1.clone()),
            size: Some(file.2),
            executable: false,
        })
        .collect();
    download_all(http, tasks, progress, "performance", "Performance-Mods").await?;

    let marker = Marker { files: wanted.iter().map(|file| file.0.clone()).collect() };
    write_json(&marker_path, &marker, false).await?;
    Ok(marker.files.into_iter().map(|file| file.file).collect())
}

/// Passende Dateien samt Abhängigkeiten; Projekte, die schon als fremde Datei im Mods-Ordner liegen, nicht doppelt.
async fn resolve(
    http: &reqwest::Client,
    mods_dir: &std::path::Path,
    previous: &Marker,
    game_version: &str,
) -> Result<Vec<(ManagedFile, String, u64)>> {
    let foreign = foreign_projects(http, mods_dir, previous).await;
    let mut queue: Vec<String> = PROJECTS.iter().map(|slug| (*slug).to_owned()).collect();
    let mut seen = HashSet::new();
    let mut result = Vec::new();

    while let Some(project) = queue.pop() {
        if !seen.insert(project.clone()) {
            continue;
        }
        let Some(version) = latest_version(http, &project, game_version).await? else {
            continue;
        };
        // Schon als eigene Datei des Spielers vorhanden, oder über Slug und ID doppelt angefragt
        if foreign.contains(&version.project_id) || project != version.project_id && !seen.insert(version.project_id.clone()) {
            continue;
        }
        let Some(file) = version.files.iter().find(|file| file.primary).or(version.files.first()) else {
            continue;
        };
        let Some(sha1) = file.hashes.get("sha1") else {
            continue;
        };
        for dependency in &version.dependencies {
            if dependency.dependency_type == "required"
                && let Some(id) = &dependency.project_id
            {
                queue.push(id.clone());
            }
        }
        result.push((
            ManagedFile { project: version.project_id.clone(), file: file.filename.clone(), sha1: sha1.clone() },
            file.url.clone(),
            file.size,
        ));
    }
    Ok(result)
}

/// Neueste Fabric-Version eines Projekts für diese Minecraft-Version (Releases vor Betas), oder None.
async fn latest_version(http: &reqwest::Client, project: &str, game_version: &str) -> Result<Option<ModrinthVersion>> {
    let response = http
        .get(format!("{API}/project/{project}/version"))
        .query(&[
            ("loaders", json!(["fabric"]).to_string()),
            ("game_versions", json!([game_version]).to_string()),
        ])
        .send()
        .await?;
    if response.status() == reqwest::StatusCode::NOT_FOUND {
        return Ok(None);
    }
    let mut versions: Vec<ModrinthVersion> = response.error_for_status()?.json().await?;
    let release = versions.iter().position(|version| version.version_type == "release");
    Ok(match release {
        Some(index) => Some(versions.swap_remove(index)),
        None if !versions.is_empty() => Some(versions.swap_remove(0)),
        None => None,
    })
}

/// Modrinth-Projekte der Dateien im Mods-Ordner, die nicht vom Launcher stammen (per SHA-1 nachgeschlagen).
async fn foreign_projects(http: &reqwest::Client, mods_dir: &std::path::Path, previous: &Marker) -> HashSet<String> {
    let mut hashes = Vec::new();
    if let Ok(mut entries) = tokio::fs::read_dir(mods_dir).await {
        while let Ok(Some(entry)) = entries.next_entry().await {
            let name = entry.file_name().to_string_lossy().into_owned();
            let ours = previous.files.iter().any(|file| file.file == name) || name.starts_with("nova-client-");
            if ours || !name.ends_with(".jar") {
                continue;
            }
            if let Ok(bytes) = tokio::fs::read(entry.path()).await {
                hashes.push(sha1_hex(&bytes));
            }
        }
    }
    if hashes.is_empty() {
        return HashSet::new();
    }
    let response = http
        .post(format!("{API}/version_files"))
        .json(&json!({ "hashes": hashes, "algorithm": "sha1" }))
        .send()
        .await;
    let Ok(response) = response else {
        return HashSet::new();
    };
    let found: HashMap<String, Value> = response.json().await.unwrap_or_default();
    found
        .values()
        .filter_map(|version| version["project_id"].as_str().map(str::to_owned))
        .collect()
}

#[cfg(test)]
mod tests {
    use super::*;

    /// Echte Modrinth-Abfrage: cargo test --lib performance -- --ignored --nocapture
    #[tokio::test]
    #[ignore]
    async fn installs_and_removes_for_1_21_4() {
        let root = std::env::temp_dir().join("nova-performance-test");
        let _ = tokio::fs::remove_dir_all(&root).await;
        let paths = Paths { root: root.clone() };
        let http = reqwest::Client::builder().user_agent("NovaLauncher/test").build().unwrap();
        let progress = Progress::silent();

        let files = sync_profile(&http, &paths, "p", "1.21.4", true, true, &progress).await.unwrap();
        println!("{files:?}");
        for name in ["sodium", "lithium", "ferritecore", "immediatelyfast", "entityculling"] {
            assert!(files.iter().any(|file| file.to_lowercase().contains(name)), "{name} fehlt");
        }
        for file in &files {
            assert!(paths.instance("p").join("mods").join(file).exists());
        }

        // Abgeschaltet: alles wieder weg
        assert!(sync_profile(&http, &paths, "p", "1.21.4", true, false, &progress).await.unwrap().is_empty());
        for file in &files {
            assert!(!paths.instance("p").join("mods").join(file).exists());
        }
    }
}
