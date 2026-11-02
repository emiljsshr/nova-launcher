use std::path::PathBuf;

use serde::Serialize;
use tauri::{AppHandle, State};
use tauri_plugin_opener::OpenerExt;

use crate::error::{Error, Result};
use crate::profiles;
use crate::state::LauncherState;

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Screenshot {
    profile_id: String,
    profile_name: String,
    file: String,
    /// Absoluter Pfad; das Frontend zeigt das Bild über das Asset-Protokoll an
    path: String,
    taken_at: u64,
    size: u64,
}

/// Alle Screenshots aus den screenshots-Ordnern der Profile, neueste zuerst.
#[tauri::command]
pub async fn screenshots_list(state: State<'_, LauncherState>) -> Result<Vec<Screenshot>> {
    let mut shots = Vec::new();
    for profile in profiles::load_profiles(&state).await? {
        let dir = state.paths.instance(&profile.id).join("screenshots");
        let Ok(mut entries) = tokio::fs::read_dir(&dir).await else {
            continue;
        };
        while let Some(entry) = entries.next_entry().await? {
            let file = entry.file_name().to_string_lossy().into_owned();
            if !file.to_ascii_lowercase().ends_with(".png") {
                continue;
            }
            let meta = entry.metadata().await?;
            let taken_at = meta
                .modified()
                .ok()
                .and_then(|time| time.duration_since(std::time::UNIX_EPOCH).ok())
                .map(|duration| duration.as_secs())
                .unwrap_or(0);
            shots.push(Screenshot {
                profile_id: profile.id.clone(),
                profile_name: profile.name.clone(),
                path: entry.path().to_string_lossy().into_owned(),
                file,
                taken_at,
                size: meta.len(),
            });
        }
    }
    shots.sort_by(|a, b| b.taken_at.cmp(&a.taken_at));
    Ok(shots)
}

#[tauri::command]
pub async fn screenshots_delete(state: State<'_, LauncherState>, profile_id: String, file: String) -> Result<()> {
    tokio::fs::remove_file(screenshot_path(&state, &profile_id, &file).await?).await?;
    Ok(())
}

/// Öffnet das Bild im Standardprogramm oder – ohne Datei – den Screenshot-Ordner des Profils.
#[tauri::command]
pub async fn screenshots_open(
    app: AppHandle,
    state: State<'_, LauncherState>,
    profile_id: String,
    file: Option<String>,
) -> Result<()> {
    let path = match file {
        Some(file) => screenshot_path(&state, &profile_id, &file).await?,
        None => {
            profiles::find_profile(&state, &profile_id).await?;
            let dir = state.paths.instance(&profile_id).join("screenshots");
            tokio::fs::create_dir_all(&dir).await?;
            dir
        }
    };
    app.opener()
        .open_path(path.to_string_lossy(), None::<&str>)
        .map_err(|error| Error::msg(error.to_string()))
}

// Nur Dateinamen direkt im screenshots-Ordner eines bekannten Profils
async fn screenshot_path(state: &LauncherState, profile_id: &str, file: &str) -> Result<PathBuf> {
    profiles::find_profile(state, profile_id).await?;
    let plain = !file.is_empty() && !file.contains(['/', '\\']) && file != "." && file != "..";
    if !plain || !file.to_ascii_lowercase().ends_with(".png") {
        return Err(Error::msg("Ungültiger Screenshot."));
    }
    Ok(state.paths.instance(profile_id).join("screenshots").join(file))
}
