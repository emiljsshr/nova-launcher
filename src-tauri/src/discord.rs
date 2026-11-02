use std::sync::mpsc::{self, RecvTimeoutError, Sender};
use std::sync::Mutex;
use std::time::Duration;

use discord_rich_presence::activity::{Activity, Assets, Timestamps};
use discord_rich_presence::{DiscordIpc, DiscordIpcClient};
use serde::Deserialize;
use tauri::State;

// Discord-Anwendung (discord.com/developers → Applications). Beim Bauen setzen: NOVA_DISCORD_APP_ID=<id>
// Das große Bild heißt dort unter Rich Presence → Art Assets "nova".
const APP_ID: Option<&str> = option_env!("NOVA_DISCORD_APP_ID");
const RETRY: Duration = Duration::from_secs(15);

#[derive(Deserialize, Clone, PartialEq)]
#[serde(rename_all = "camelCase")]
pub struct Presence {
    details: String,
    state: Option<String>,
    /// Unix-Sekunden; Discord zeigt daraus "seit 12:34"
    started_at: Option<i64>,
}

/// Rich Presence läuft in einem eigenen Thread, weil die Discord-IPC blockiert. Ist Discord nicht
/// offen, versucht der Thread es alle 15 Sekunden erneut und setzt dann den letzten Stand.
pub struct Discord(Mutex<Option<Sender<Option<Presence>>>>);

impl Discord {
    pub fn start() -> Self {
        let Some(app_id) = APP_ID.map(str::trim).filter(|id| !id.is_empty()) else {
            return Self(Mutex::new(None));
        };
        let (sender, receiver) = mpsc::channel::<Option<Presence>>();
        let app_id = app_id.to_owned();
        std::thread::spawn(move || {
            let mut client = DiscordIpcClient::new(&app_id);
            let mut connected = false;
            let mut wanted: Option<Presence> = None;
            let mut shown: Option<Option<Presence>> = None;
            loop {
                match receiver.recv_timeout(RETRY) {
                    Ok(next) => wanted = next,
                    Err(RecvTimeoutError::Timeout) => {}
                    Err(RecvTimeoutError::Disconnected) => break,
                }
                if !connected {
                    connected = client.connect().is_ok();
                    shown = None;
                }
                if !connected || shown.as_ref() == Some(&wanted) {
                    continue;
                }
                let result = match &wanted {
                    Some(presence) => client.set_activity(activity(presence)),
                    None => client.clear_activity(),
                };
                if result.is_ok() {
                    shown = Some(wanted.clone());
                } else {
                    // Discord wurde geschlossen – beim nächsten Durchlauf neu verbinden
                    let _ = client.close();
                    connected = false;
                }
            }
            let _ = client.close();
        });
        Self(Mutex::new(Some(sender)))
    }
}

fn activity(presence: &Presence) -> Activity<'_> {
    let mut activity = Activity::new()
        .details(presence.details.as_str())
        .assets(Assets::new().large_image("nova").large_text("Nova Client"));
    if let Some(state) = &presence.state {
        activity = activity.state(state.as_str());
    }
    if let Some(start) = presence.started_at {
        activity = activity.timestamps(Timestamps::new().start(start));
    }
    activity
}

/// Setzt den Discord-Status oder entfernt ihn (null). Ohne konfigurierte App-ID passiert nichts.
#[tauri::command]
pub fn discord_set_presence(discord: State<'_, Discord>, presence: Option<Presence>) {
    if let Some(sender) = discord.0.lock().ok().and_then(|sender| sender.clone()) {
        let _ = sender.send(presence);
    }
}

#[tauri::command]
pub fn discord_available() -> bool {
    APP_ID.is_some_and(|id| !id.trim().is_empty())
}
