use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicUsize, Ordering};
use std::time::Duration;

use futures::stream::{self, StreamExt};
use serde::Serialize;
use sha1::{Digest, Sha1};
use tauri::{AppHandle, Emitter};

use crate::error::{Error, Result};

const PARALLEL_DOWNLOADS: usize = 16;
const RETRIES: usize = 3;

#[derive(Clone)]
pub struct DownloadTask {
    pub url: String,
    pub path: PathBuf,
    pub sha1: Option<String>,
    pub size: Option<u64>,
    pub executable: bool,
}

#[derive(Serialize, Clone)]
#[serde(rename_all = "camelCase")]
struct ProgressPayload<'a> {
    stage: &'a str,
    label: &'a str,
    done: usize,
    total: usize,
}

/// Meldet den Installationsfortschritt ans Frontend ("launch-progress").
#[derive(Clone)]
pub struct Progress {
    app: Option<AppHandle>,
}

impl Progress {
    pub fn new(app: AppHandle) -> Self {
        Self { app: Some(app) }
    }

    /// Ohne Fenster, z. B. in Tests
    #[cfg(test)]
    pub fn silent() -> Self {
        Self { app: None }
    }

    pub fn report(&self, stage: &str, label: &str, done: usize, total: usize) {
        if let Some(app) = &self.app {
            let _ = app.emit("launch-progress", ProgressPayload { stage, label, done, total });
        }
    }
}

/// Vorhandene Dateien gelten als gültig, wenn die Größe passt –
/// ein SHA1-Check aller Assets bei jedem Start wäre zu langsam.
async fn is_present(task: &DownloadTask) -> bool {
    match tokio::fs::metadata(&task.path).await {
        Ok(metadata) => task.size.is_none_or(|size| metadata.len() == size),
        Err(_) => false,
    }
}

pub fn sha1_hex(bytes: &[u8]) -> String {
    hex::encode(Sha1::digest(bytes))
}

async fn download_once(http: &reqwest::Client, task: &DownloadTask) -> Result<()> {
    let bytes = http.get(&task.url).send().await?.error_for_status()?.bytes().await?;

    if let Some(expected) = &task.sha1 {
        let actual = sha1_hex(&bytes);
        if !actual.eq_ignore_ascii_case(expected) {
            return Err(Error::msg(format!("Prüfsumme stimmt nicht: {}", task.url)));
        }
    }

    write_file(&task.path, &bytes, task.executable).await
}

pub async fn write_file(path: &Path, bytes: &[u8], executable: bool) -> Result<()> {
    if let Some(parent) = path.parent() {
        tokio::fs::create_dir_all(parent).await?;
    }

    let temp = path.with_extension("part");
    tokio::fs::write(&temp, bytes).await?;

    #[cfg(unix)]
    if executable {
        use std::os::unix::fs::PermissionsExt;
        tokio::fs::set_permissions(&temp, std::fs::Permissions::from_mode(0o755)).await?;
    }
    #[cfg(not(unix))]
    let _ = executable;

    tokio::fs::rename(&temp, path).await?;
    Ok(())
}

pub async fn download(http: &reqwest::Client, task: &DownloadTask) -> Result<()> {
    let mut last_error = Error::msg("Download fehlgeschlagen");
    for attempt in 0..RETRIES {
        match download_once(http, task).await {
            Ok(()) => return Ok(()),
            Err(error) => {
                last_error = error;
                tokio::time::sleep(Duration::from_millis(500 * (attempt as u64 + 1))).await;
            }
        }
    }
    Err(Error::msg(format!(
        "{} konnte nicht geladen werden: {}",
        task.url,
        last_error.message()
    )))
}

/// Lädt alle fehlenden Dateien parallel herunter.
pub async fn download_all(
    http: &reqwest::Client,
    tasks: Vec<DownloadTask>,
    progress: &Progress,
    stage: &str,
    label: &str,
) -> Result<()> {
    let mut missing = Vec::new();
    for task in tasks {
        if !is_present(&task).await {
            missing.push(task);
        }
    }

    let total = missing.len();
    if total == 0 {
        return Ok(());
    }

    let done = AtomicUsize::new(0);
    let step = (total / 100).max(1);
    progress.report(stage, label, 0, total);

    let results: Vec<Result<()>> = stream::iter(missing)
        .map(|task| {
            let done = &done;
            async move {
                download(http, &task).await?;
                let finished = done.fetch_add(1, Ordering::SeqCst) + 1;
                if finished.is_multiple_of(step) || finished == total {
                    progress.report(stage, label, finished, total);
                }
                Ok(())
            }
        })
        .buffer_unordered(PARALLEL_DOWNLOADS)
        .collect()
        .await;

    results.into_iter().collect()
}

/// Lädt JSON (z. B. Manifeste) und speichert es zusätzlich als Cache.
pub async fn fetch_json_cached(
    http: &reqwest::Client,
    url: &str,
    cache: &Path,
    sha1: Option<&str>,
) -> Result<serde_json::Value> {
    if let Ok(bytes) = tokio::fs::read(cache).await {
        let valid = sha1.is_none_or(|expected| sha1_hex(&bytes).eq_ignore_ascii_case(expected));
        if valid
            && let Ok(value) = serde_json::from_slice(&bytes) {
                return Ok(value);
            }
    }

    let bytes = http.get(url).send().await?.error_for_status()?.bytes().await?;
    if let Some(expected) = sha1
        && !sha1_hex(&bytes).eq_ignore_ascii_case(expected) {
            return Err(Error::msg(format!("Prüfsumme stimmt nicht: {url}")));
        }
    write_file(cache, &bytes, false).await?;
    Ok(serde_json::from_slice(&bytes)?)
}
