use serde_json::Value;
use tauri::{AppHandle, Emitter, Manager, State};
use tokio::io::{AsyncBufReadExt, AsyncWriteExt, BufReader};
use tokio::net::TcpListener;
use tokio::sync::mpsc;

use crate::error::{Error, Result};
use crate::state::LauncherState;

/// Lokale Verbindung zwischen Launcher und laufendem Spiel: JSON, eine Nachricht pro Zeile.
/// Die Nova-Mod verbindet sich mit `-Dnova.bridge=<host:port>` und schickt als erste Zeile
/// `-Dnova.bridge.token`. Nachrichten aus dem Spiel gehen als Event "game-bridge" ans Frontend,
/// `bridge_send` schickt eine Nachricht ins Spiel.
pub struct Bridge {
    pub address: String,
    pub token: String,
    task: tauri::async_runtime::JoinHandle<()>,
}

impl Bridge {
    pub fn jvm_args(&self) -> [String; 2] {
        [format!("-Dnova.bridge={}", self.address), format!("-Dnova.bridge.token={}", self.token)]
    }

    pub fn close(self) {
        self.task.abort();
    }
}

pub async fn open(app: AppHandle) -> Result<Bridge> {
    let listener = TcpListener::bind("127.0.0.1:0").await?;
    let address = listener.local_addr()?.to_string();
    let token = uuid::Uuid::new_v4().simple().to_string();

    let expected = token.clone();
    let task = tauri::async_runtime::spawn(async move {
        // Das Spiel kann sich nach einem Verbindungsabbruch erneut melden
        while let Ok((stream, _)) = listener.accept().await {
            let (read, mut write) = stream.into_split();
            let mut lines = BufReader::new(read).lines();
            if lines.next_line().await.ok().flatten().as_deref() != Some(expected.as_str()) {
                continue;
            }

            let (sender, mut outgoing) = mpsc::unbounded_channel::<String>();
            *app.state::<LauncherState>().bridge.lock().await = Some(sender);
            let _ = app.emit("game-bridge", serde_json::json!({ "type": "connected" }));

            let writer = tauri::async_runtime::spawn(async move {
                while let Some(line) = outgoing.recv().await {
                    if write.write_all(format!("{line}\n").as_bytes()).await.is_err() {
                        break;
                    }
                }
            });
            while let Ok(Some(line)) = lines.next_line().await {
                if let Ok(message) = serde_json::from_str::<Value>(&line) {
                    let _ = app.emit("game-bridge", message);
                }
            }
            writer.abort();
            *app.state::<LauncherState>().bridge.lock().await = None;
        }
    });

    Ok(Bridge { address, token, task })
}

#[tauri::command]
pub async fn bridge_send(state: State<'_, LauncherState>, message: Value) -> Result<()> {
    let bridge = state.bridge.lock().await;
    let sender = bridge.as_ref().ok_or_else(|| Error::msg("Das Spiel ist nicht verbunden."))?;
    sender
        .send(serde_json::to_string(&message)?)
        .map_err(|_| Error::msg("Das Spiel ist nicht verbunden."))
}
