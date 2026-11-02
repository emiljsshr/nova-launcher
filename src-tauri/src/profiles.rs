use serde::{Deserialize, Serialize};
use tauri::{AppHandle, State};
use tauri_plugin_opener::OpenerExt;

use crate::error::{Error, Result};
use crate::nova_mod;
use crate::state::{now_secs, read_json, write_json, LauncherState};

#[derive(Serialize, Deserialize, Clone, Copy, PartialEq, Eq)]
#[serde(rename_all = "lowercase")]
pub enum Loader {
    Vanilla,
    Fabric,
}

#[derive(Serialize, Deserialize, Clone)]
#[serde(rename_all = "camelCase")]
pub struct GameProfile {
    pub id: String,
    pub name: String,
    pub game_version: String,
    pub loader: Loader,
    /// Leer = neueste stabile Loader-Version beim Start
    pub loader_version: Option<String>,
    pub icon: String,
    pub created_at: u64,
    pub last_played: Option<u64>,
    pub play_seconds: u64,
    pub sessions: u32,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ProfileInput {
    name: String,
    game_version: String,
    loader: Loader,
    loader_version: Option<String>,
    icon: String,
}

fn default_profiles() -> Vec<GameProfile> {
    vec![GameProfile {
        id: "nova-default".into(),
        name: "Nova 26.3".into(),
        game_version: "26.3".into(),
        loader: Loader::Fabric,
        loader_version: None,
        icon: "ice".into(),
        created_at: now_secs(),
        last_played: None,
        play_seconds: 0,
        sessions: 0,
    }]
}

pub async fn load_profiles(state: &LauncherState) -> Result<Vec<GameProfile>> {
    let path = state.paths.profiles_file();
    if !path.exists() {
        let profiles = default_profiles();
        write_json(&path, &profiles, false).await?;
        return Ok(profiles);
    }
    read_json(&path).await
}

async fn save_profiles(state: &LauncherState, profiles: &[GameProfile]) -> Result<()> {
    write_json(&state.paths.profiles_file(), &profiles, false).await
}

pub async fn find_profile(state: &LauncherState, id: &str) -> Result<GameProfile> {
    load_profiles(state)
        .await?
        .into_iter()
        .find(|profile| profile.id == id)
        .ok_or_else(|| Error::msg("Profil nicht gefunden."))
}

/// Wird nach jeder Spielsitzung aufgerufen.
pub async fn record_session(state: &LauncherState, id: &str, seconds: u64) -> Result<()> {
    let _guard = state.store_lock.lock().await;
    let mut profiles = load_profiles(state).await?;
    if let Some(profile) = profiles.iter_mut().find(|profile| profile.id == id) {
        profile.last_played = Some(now_secs());
        profile.play_seconds += seconds;
        profile.sessions += 1;
    }
    save_profiles(state, &profiles).await
}

fn validate(input: &ProfileInput) -> Result<()> {
    if input.name.trim().is_empty() || input.name.trim().chars().count() > 32 {
        return Err(Error::msg("Der Profilname muss 1–32 Zeichen lang sein."));
    }
    if input.game_version.trim().is_empty() {
        return Err(Error::msg("Bitte wähle eine Minecraft-Version."));
    }
    Ok(())
}

#[tauri::command]
pub async fn profiles_list(state: State<'_, LauncherState>) -> Result<Vec<GameProfile>> {
    let _guard = state.store_lock.lock().await;
    load_profiles(&state).await
}

#[tauri::command]
pub async fn profiles_create(state: State<'_, LauncherState>, input: ProfileInput) -> Result<Vec<GameProfile>> {
    validate(&input)?;
    let _guard = state.store_lock.lock().await;
    let mut profiles = load_profiles(&state).await?;

    let profile = GameProfile {
        id: uuid::Uuid::new_v4().to_string(),
        name: input.name.trim().to_owned(),
        game_version: input.game_version,
        loader: input.loader,
        loader_version: input.loader_version.filter(|version| !version.is_empty()),
        icon: input.icon,
        created_at: now_secs(),
        last_played: None,
        play_seconds: 0,
        sessions: 0,
    };

    nova_mod::sync_profile_mod(
        &state.paths,
        &profile.id,
        &profile.game_version,
        profile.loader == Loader::Fabric,
    )
    .await?;
    profiles.push(profile);

    save_profiles(&state, &profiles).await?;
    Ok(profiles)
}

#[tauri::command]
pub async fn profiles_update(
    state: State<'_, LauncherState>,
    id: String,
    input: ProfileInput,
) -> Result<Vec<GameProfile>> {
    validate(&input)?;
    let _guard = state.store_lock.lock().await;
    let mut profiles = load_profiles(&state).await?;
    let profile = profiles
        .iter_mut()
        .find(|profile| profile.id == id)
        .ok_or_else(|| Error::msg("Profil nicht gefunden."))?;

    profile.name = input.name.trim().to_owned();
    profile.game_version = input.game_version;
    profile.loader = input.loader;
    profile.loader_version = input.loader_version.filter(|version| !version.is_empty());
    profile.icon = input.icon;

    save_profiles(&state, &profiles).await?;
    Ok(profiles)
}

#[tauri::command]
pub async fn profiles_delete(
    state: State<'_, LauncherState>,
    id: String,
    delete_files: bool,
) -> Result<Vec<GameProfile>> {
    if state.game.lock().await.as_ref().is_some_and(|game| game.profile_id == id) {
        return Err(Error::msg("Das Profil läuft gerade und kann nicht gelöscht werden."));
    }

    let _guard = state.store_lock.lock().await;
    let mut profiles = load_profiles(&state).await?;
    if profiles.len() <= 1 {
        return Err(Error::msg("Mindestens ein Profil muss erhalten bleiben."));
    }
    profiles.retain(|profile| profile.id != id);
    save_profiles(&state, &profiles).await?;

    // Welten, Mods & Co. werden nur auf ausdrücklichen Wunsch gelöscht
    if delete_files {
        let dir = state.paths.instance(&id);
        if dir.exists() {
            tokio::fs::remove_dir_all(dir).await?;
        }
    }
    Ok(profiles)
}

#[tauri::command]
pub async fn profiles_open_folder(app: AppHandle, state: State<'_, LauncherState>, id: String) -> Result<()> {
    find_profile(&state, &id).await?;
    let dir = state.paths.instance(&id);
    tokio::fs::create_dir_all(dir.join("mods")).await?;
    app.opener()
        .open_path(dir.to_string_lossy(), None::<&str>)
        .map_err(|error| Error::msg(error.to_string()))
}

#[tauri::command]
pub async fn open_data_folder(app: AppHandle, state: State<'_, LauncherState>) -> Result<()> {
    tokio::fs::create_dir_all(&state.paths.root).await?;
    app.opener()
        .open_path(state.paths.root.to_string_lossy(), None::<&str>)
        .map_err(|error| Error::msg(error.to_string()))
}
