use std::collections::HashMap;
use std::path::{Path, PathBuf};
use std::sync::atomic::AtomicBool;

use serde::{de::DeserializeOwned, Serialize};
use tokio::sync::{mpsc, oneshot, Mutex};

use crate::error::Result;
use crate::social::SocialSession;

/// Ordnerstruktur im App-Datenverzeichnis.
#[derive(Clone)]
pub struct Paths {
    pub root: PathBuf,
}

impl Paths {
    pub fn versions(&self) -> PathBuf {
        self.root.join("versions")
    }

    pub fn libraries(&self) -> PathBuf {
        self.root.join("libraries")
    }

    pub fn assets(&self) -> PathBuf {
        self.root.join("assets")
    }

    pub fn runtimes(&self) -> PathBuf {
        self.root.join("runtime")
    }

    pub fn natives(&self, version_id: &str) -> PathBuf {
        self.root.join("natives").join(version_id)
    }

    pub fn instance(&self, profile_id: &str) -> PathBuf {
        self.root.join("instances").join(profile_id)
    }

    pub fn accounts_file(&self) -> PathBuf {
        self.root.join("accounts.json")
    }

    pub fn profiles_file(&self) -> PathBuf {
        self.root.join("profiles.json")
    }
}

pub struct RunningGame {
    pub profile_id: String,
    pub kill: Option<oneshot::Sender<()>>,
}

pub struct LauncherState {
    pub paths: Paths,
    pub http: reqwest::Client,
    pub game: Mutex<Option<RunningGame>>,
    /// Verhindert parallele Schreibzugriffe auf accounts.json / profiles.json
    pub store_lock: Mutex<()>,
    pub cancel_login: AtomicBool,
    /// Anmeldungen am Nova-Server je Minecraft-Konto
    pub social_tokens: Mutex<HashMap<String, SocialSession>>,
    /// Sendekanal zum laufenden Spiel (siehe bridge.rs)
    pub bridge: Mutex<Option<mpsc::UnboundedSender<String>>>,
}

impl LauncherState {
    pub fn new(root: PathBuf) -> Self {
        let http = reqwest::Client::builder()
            .user_agent(concat!("NovaLauncher/", env!("CARGO_PKG_VERSION")))
            .build()
            .expect("HTTP-Client konnte nicht erstellt werden");

        Self {
            paths: Paths { root },
            http,
            game: Mutex::new(None),
            store_lock: Mutex::new(()),
            cancel_login: AtomicBool::new(false),
            social_tokens: Mutex::new(HashMap::new()),
            bridge: Mutex::new(None),
        }
    }
}

pub async fn read_json<T: DeserializeOwned + Default>(path: &Path) -> Result<T> {
    match tokio::fs::read(path).await {
        Ok(bytes) => Ok(serde_json::from_slice(&bytes)?),
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => Ok(T::default()),
        Err(error) => Err(error.into()),
    }
}

/// Schreibt atomar (temp-Datei + rename), damit ein Absturz keine halben Dateien hinterlässt.
pub async fn write_json<T: Serialize>(path: &Path, value: &T, private: bool) -> Result<()> {
    if let Some(parent) = path.parent() {
        tokio::fs::create_dir_all(parent).await?;
    }

    let temp = path.with_extension("json.tmp");
    tokio::fs::write(&temp, serde_json::to_vec_pretty(value)?).await?;

    #[cfg(unix)]
    if private {
        use std::os::unix::fs::PermissionsExt;
        tokio::fs::set_permissions(&temp, std::fs::Permissions::from_mode(0o600)).await?;
    }
    #[cfg(not(unix))]
    let _ = private;

    tokio::fs::rename(&temp, path).await?;
    Ok(())
}

pub fn now_secs() -> u64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|duration| duration.as_secs())
        .unwrap_or(0)
}
