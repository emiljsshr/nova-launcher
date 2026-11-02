use std::collections::HashMap;
use std::path::PathBuf;
use std::process::Stdio;
use std::time::Instant;

use serde::{Deserialize, Serialize};
use tauri::{AppHandle, Emitter, Manager, State};
use tokio::io::{AsyncBufReadExt, BufReader};
use tokio::process::Command;
use tokio::sync::oneshot;

use crate::auth;
use crate::bridge;
use crate::download::{download_all, Progress};
use crate::error::{Error, Result};
use crate::minecraft::{self, Features};
use crate::nova_mod;
use crate::profiles::{self, Loader};
use crate::state::{LauncherState, RunningGame};

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct LaunchOptions {
    memory_mb: u32,
    jvm_args: String,
    java_path: String,
    width: u32,
    height: u32,
    fullscreen: bool,
    /// Serveradresse für den Direktbeitritt (host oder host:port)
    server: Option<String>,
    /// Angelegte Cosmetics; die Nova-Mod liest sie aus `-Dnova.cosmetics`
    #[serde(default)]
    cosmetics: Vec<String>,
}

#[derive(Serialize, Clone)]
#[serde(rename_all = "camelCase")]
struct GameExit {
    profile_id: String,
    code: Option<i32>,
    seconds: u64,
}

#[derive(Serialize, Clone)]
#[serde(rename_all = "camelCase")]
struct GameLog {
    line: String,
    error: bool,
}

#[tauri::command]
pub async fn game_launch(
    app: AppHandle,
    state: State<'_, LauncherState>,
    profile_id: String,
    options: LaunchOptions,
) -> Result<()> {
    {
        let mut game = state.game.lock().await;
        if game.is_some() {
            return Err(Error::msg("Minecraft läuft bereits."));
        }
        // Platz reservieren, damit ein Doppelklick nicht zweimal startet
        *game = Some(RunningGame { profile_id: profile_id.clone(), kill: None });
    }

    let result = prepare_and_spawn(&app, &state, &profile_id, options).await;
    if result.is_err() {
        *state.game.lock().await = None;
    }
    result
}

#[tauri::command]
pub async fn game_stop(state: State<'_, LauncherState>) -> Result<()> {
    let mut game = state.game.lock().await;
    if let Some(kill) = game.as_mut().and_then(|game| game.kill.take()) {
        let _ = kill.send(());
    }
    Ok(())
}

/// Profil-ID des laufenden Spiels (oder null).
#[tauri::command]
pub async fn game_status(state: State<'_, LauncherState>) -> Result<Option<String>> {
    Ok(state.game.lock().await.as_ref().map(|game| game.profile_id.clone()))
}

fn split_server(address: &str) -> (String, String) {
    match address.rsplit_once(':') {
        Some((host, port)) if port.parse::<u16>().is_ok() => (host.to_owned(), port.to_owned()),
        _ => (address.to_owned(), "25565".to_owned()),
    }
}

async fn prepare_and_spawn(
    app: &AppHandle,
    state: &LauncherState,
    profile_id: &str,
    options: LaunchOptions,
) -> Result<()> {
    let http = &state.http;
    let paths = &state.paths;
    let progress = Progress::new(app.clone());

    progress.report("account", "Konto wird geprüft", 0, 0);
    let account = auth::active_account(state).await?;
    let profile = profiles::find_profile(state, profile_id).await?;
    nova_mod::sync_profile_mod(
        paths,
        &profile.id,
        &profile.game_version,
        profile.loader == Loader::Fabric,
    )
    .await?;

    progress.report("version", "Version wird geladen", 0, 0);
    let vanilla = minecraft::vanilla_version_json(http, paths, &profile.game_version).await?;
    let version = match profile.loader {
        Loader::Vanilla => vanilla,
        Loader::Fabric => {
            let fabric = minecraft::fabric_version_json(
                http,
                paths,
                &profile.game_version,
                profile.loader_version.as_deref(),
            )
            .await?;
            minecraft::merge_versions(&vanilla, &fabric)
        }
    };
    let version_id = version["id"].as_str().unwrap_or(&profile.game_version).to_owned();

    let java = if options.java_path.trim().is_empty() {
        minecraft::ensure_java(http, paths, &version, &progress).await?
    } else {
        let path = PathBuf::from(options.java_path.trim());
        if !path.exists() {
            return Err(Error::msg("Der eingestellte Java-Pfad existiert nicht."));
        }
        path
    };

    let client_jar = minecraft::install_client(http, paths, &version, &progress).await?;

    let quick_play = options.server.is_some() && minecraft::supports_quick_play(&version);
    let features = Features {
        custom_resolution: !options.fullscreen,
        quick_play_multiplayer: quick_play,
    };

    let libraries = minecraft::resolve_libraries(&version, paths, &features);
    download_all(http, libraries.downloads, &progress, "libraries", "Bibliotheken").await?;

    let game_dir = paths.instance(profile_id);
    tokio::fs::create_dir_all(game_dir.join("mods")).await?;
    let assets = minecraft::install_assets(http, paths, &version, &game_dir, &progress).await?;

    progress.report("natives", "Natives werden entpackt", 0, 0);
    let natives_dir = paths.natives(&version_id);
    let native_jars = libraries.native_jars.clone();
    let natives_target = natives_dir.clone();
    tokio::task::spawn_blocking(move || minecraft::extract_natives(&native_jars, &natives_target))
        .await
        .map_err(|error| Error::msg(error.to_string()))??;

    let separator = if cfg!(target_os = "windows") { ";" } else { ":" };
    let classpath = libraries
        .classpath
        .iter()
        .chain(std::iter::once(&client_jar))
        .map(|path| path.to_string_lossy().into_owned())
        .collect::<Vec<_>>()
        .join(separator);

    let quick_play_target = options.server.clone().unwrap_or_default();
    let placeholders: HashMap<&str, String> = HashMap::from([
        ("auth_player_name", account.name.clone()),
        ("version_name", version_id.clone()),
        ("game_directory", game_dir.to_string_lossy().into_owned()),
        ("assets_root", assets.root.to_string_lossy().into_owned()),
        ("game_assets", assets.legacy_dir.to_string_lossy().into_owned()),
        ("assets_index_name", assets.index_id.clone()),
        ("auth_uuid", account.id.clone()),
        ("auth_access_token", account.access_token.clone()),
        ("auth_session", format!("token:{}:{}", account.access_token, account.id)),
        ("auth_xuid", account.xuid.clone()),
        ("clientid", String::new()),
        ("user_type", if account.offline { "legacy" } else { "msa" }.into()),
        ("user_properties", "{}".into()),
        ("version_type", version["type"].as_str().unwrap_or("release").to_owned()),
        ("resolution_width", options.width.to_string()),
        ("resolution_height", options.height.to_string()),
        ("natives_directory", natives_dir.to_string_lossy().into_owned()),
        ("library_directory", paths.libraries().to_string_lossy().into_owned()),
        ("classpath", classpath),
        ("classpath_separator", separator.into()),
        ("launcher_name", "nova-launcher".into()),
        ("launcher_version", env!("CARGO_PKG_VERSION").into()),
        ("quickPlayMultiplayer", quick_play_target),
    ]);

    let memory = options.memory_mb.clamp(1024, 32 * 1024);
    let mut jvm_prefix = vec![format!("-Xmx{memory}M"), format!("-Xms{}M", memory.min(1024))];
    jvm_prefix.extend(options.jvm_args.split_whitespace().map(str::to_owned));
    let cosmetics: Vec<&str> = options
        .cosmetics
        .iter()
        .map(String::as_str)
        .filter(|id| !id.is_empty() && id.bytes().all(|b| b.is_ascii_lowercase() || b.is_ascii_digit() || b == b'-'))
        .collect();
    if !cosmetics.is_empty() {
        jvm_prefix.push(format!("-Dnova.cosmetics={}", cosmetics.join(",")));
    }
    // Lokale Verbindung für Chat, Status und Cosmetics-Wechsel im Spiel
    let bridge = bridge::open(app.clone()).await?;
    jvm_prefix.extend(bridge.jvm_args());

    let mut arguments = minecraft::build_arguments(&version, &features, jvm_prefix, &placeholders);
    if options.fullscreen {
        arguments.push("--fullscreen".into());
    }
    if let (Some(server), false) = (&options.server, quick_play) {
        let (host, port) = split_server(server);
        arguments.extend(["--server".into(), host, "--port".into(), port]);
    }

    progress.report("starting", "Minecraft startet", 0, 0);
    let mut command = Command::new(&java);
    command
        .args(&arguments)
        .current_dir(&game_dir)
        .stdin(Stdio::null())
        .stdout(Stdio::piped())
        .stderr(Stdio::piped());
    #[cfg(target_os = "windows")]
    command.creation_flags(0x0800_0000); // CREATE_NO_WINDOW

    let mut child = match command.spawn() {
        Ok(child) => child,
        Err(error) => {
            bridge.close();
            return Err(Error::msg(format!("Java konnte nicht gestartet werden: {error}")));
        }
    };

    // Konsolenausgabe zeilenweise ans Frontend streamen
    if let Some(stdout) = child.stdout.take() {
        let app = app.clone();
        tauri::async_runtime::spawn(async move {
            let mut lines = BufReader::new(stdout).lines();
            while let Ok(Some(line)) = lines.next_line().await {
                let _ = app.emit("game-log", GameLog { line, error: false });
            }
        });
    }
    if let Some(stderr) = child.stderr.take() {
        let app = app.clone();
        tauri::async_runtime::spawn(async move {
            let mut lines = BufReader::new(stderr).lines();
            while let Ok(Some(line)) = lines.next_line().await {
                let _ = app.emit("game-log", GameLog { line, error: true });
            }
        });
    }

    let (kill_tx, kill_rx) = oneshot::channel::<()>();
    *state.game.lock().await = Some(RunningGame {
        profile_id: profile_id.to_owned(),
        kill: Some(kill_tx),
    });
    let _ = app.emit("game-started", profile_id);

    let app = app.clone();
    let profile_id = profile_id.to_owned();
    tauri::async_runtime::spawn(async move {
        let started = Instant::now();
        let finished = tokio::select! {
            status = child.wait() => Some(status),
            _ = kill_rx => None,
        };
        let status = match finished {
            Some(status) => status,
            None => {
                let _ = child.kill().await;
                child.wait().await
            }
        };

        let seconds = started.elapsed().as_secs();
        bridge.close();
        let state = app.state::<LauncherState>();
        *state.game.lock().await = None;
        *state.bridge.lock().await = None;
        let _ = profiles::record_session(&state, &profile_id, seconds).await;
        let _ = app.emit(
            "game-exit",
            GameExit { profile_id, code: status.ok().and_then(|status| status.code()), seconds },
        );
    });

    Ok(())
}
