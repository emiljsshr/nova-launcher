use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use tauri::State;

use crate::auth;
use crate::error::{Error, Result};
use crate::state::LauncherState;

// Adresse des Nova-Servers (Freunde, Chat, News). Beim Bauen setzen: NOVA_SERVER_URL=https://…
const SERVER_URL: Option<&str> = option_env!("NOVA_SERVER_URL");
const DEFAULT_SERVER_URL: &str = "http://localhost:8787";
const MOJANG_JOIN_URL: &str = "https://sessionserver.mojang.com/session/minecraft/join";

pub fn server_url() -> &'static str {
    SERVER_URL.map(str::trim).filter(|url| !url.is_empty()).unwrap_or(DEFAULT_SERVER_URL).trim_end_matches('/')
}

#[derive(Serialize, Deserialize, Clone)]
#[serde(rename_all = "camelCase")]
pub struct SocialUser {
    id: String,
    name: String,
}

#[derive(Serialize, Clone)]
#[serde(rename_all = "camelCase")]
pub struct SocialSession {
    server_url: String,
    token: String,
    user: SocialUser,
}

#[derive(Deserialize)]
struct LoginReply {
    token: String,
    user: SocialUser,
}

#[tauri::command]
pub fn social_server_url() -> String {
    server_url().to_owned()
}

/// Meldet das aktive Konto am Nova-Server an. Bewiesen wird der Besitz des Minecraft-Kontos wie beim
/// Betreten eines Minecraft-Servers: Der Launcher meldet bei Mojang einen Beitritt mit der vom
/// Nova-Server vergebenen serverId, der Nova-Server fragt bei Mojang nach. Das Minecraft-Token
/// verlässt dabei nie den Launcher.
#[tauri::command]
pub async fn social_login(state: State<'_, LauncherState>) -> Result<SocialSession> {
    let account = auth::active_account(&state).await?;
    let mut cached = state.social_tokens.lock().await;
    if let Some(session) = cached.get(&account.id) {
        return Ok(session.clone());
    }

    let http = &state.http;
    let base = server_url();
    let reply = if account.offline {
        post(http, &format!("{base}/api/auth/offline"), json!({ "name": account.name })).await?
    } else {
        let challenge = post(http, &format!("{base}/api/auth/challenge"), json!({})).await?;
        let server_id = challenge["serverId"].as_str().unwrap_or_default();
        let joined = http
            .post(MOJANG_JOIN_URL)
            .json(&json!({
                "accessToken": account.access_token,
                "selectedProfile": account.id.replace('-', ""),
                "serverId": server_id,
            }))
            .send()
            .await
            .map_err(|_| Error::msg("Mojang ist gerade nicht erreichbar."))?;
        if !joined.status().is_success() {
            return Err(Error::msg("Mojang hat die Anmeldung abgelehnt. Melde dich im Launcher neu an."));
        }
        post(http, &format!("{base}/api/auth/login"), json!({ "name": account.name, "serverId": server_id })).await?
    };

    let reply: LoginReply = serde_json::from_value(reply)?;
    let session = SocialSession { server_url: base.to_owned(), token: reply.token, user: reply.user };
    cached.insert(account.id, session.clone());
    Ok(session)
}

/// Vergisst das Token (z. B. wenn der Server es nicht mehr kennt), damit die nächste Anmeldung neu läuft.
#[tauri::command]
pub async fn social_logout(state: State<'_, LauncherState>) -> Result<()> {
    state.social_tokens.lock().await.clear();
    Ok(())
}

async fn post(http: &reqwest::Client, url: &str, body: Value) -> Result<Value> {
    let response = http
        .post(url)
        .json(&body)
        .send()
        .await
        .map_err(|_| Error::msg("Der Nova-Server ist nicht erreichbar."))?;
    let status = response.status();
    let value: Value = response.json().await.unwrap_or(Value::Null);
    if !status.is_success() {
        let message = value["error"].as_str().unwrap_or("Der Nova-Server hat die Anmeldung abgelehnt.");
        return Err(Error::msg(message));
    }
    Ok(value)
}
