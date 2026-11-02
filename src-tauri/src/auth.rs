use std::sync::atomic::Ordering;
use std::time::Duration;

use base64::Engine;
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use tauri::State;

use crate::error::{Error, Result};
use crate::state::{now_secs, read_json, write_json, LauncherState};

// Azure-App-Registrierung (öffentlicher Client, "Allow public client flows" aktiv).
// Beim Bauen setzen: NOVA_MS_CLIENT_ID=<client-id> npm run tauri dev
const MS_CLIENT_ID: Option<&str> = option_env!("NOVA_MS_CLIENT_ID");
const MS_SCOPE: &str = "XboxLive.signin offline_access";
const MS_DEVICE_CODE_URL: &str = "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode";
const MS_TOKEN_URL: &str = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
const XBL_AUTH_URL: &str = "https://user.auth.xboxlive.com/user/authenticate";
const XSTS_AUTH_URL: &str = "https://xsts.auth.xboxlive.com/xsts/authorize";
const MC_LOGIN_URL: &str = "https://api.minecraftservices.com/authentication/login_with_xbox";
const MC_PROFILE_URL: &str = "https://api.minecraftservices.com/minecraft/profile";

fn client_id() -> Result<&'static str> {
    match MS_CLIENT_ID {
        Some(id) if !id.trim().is_empty() => Ok(id.trim()),
        _ => Err(Error::msg(
            "Keine Microsoft Client-ID konfiguriert. Starte den Launcher mit NOVA_MS_CLIENT_ID=<deine Azure Client-ID>.",
        )),
    }
}

/* ========================================
   GESPEICHERTE KONTEN
======================================== */

#[derive(Serialize, Deserialize, Clone, Default)]
#[serde(rename_all = "camelCase")]
pub struct StoredAccount {
    pub id: String,
    pub name: String,
    pub refresh_token: String,
    pub access_token: String,
    pub expires_at: u64,
    pub xuid: String,
    pub skin_url: Option<String>,
    pub skin_variant: Option<String>,
    /// Offline-Testkonto ohne Microsoft-Anmeldung (nur in Debug-Builds nutzbar)
    #[serde(default)]
    pub offline: bool,
}

#[derive(Serialize, Deserialize, Default)]
#[serde(rename_all = "camelCase")]
struct AccountStore {
    active: Option<String>,
    accounts: Vec<StoredAccount>,
}

/// Was das Frontend sieht – ohne Tokens.
#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct AccountInfo {
    id: String,
    name: String,
    skin_url: Option<String>,
    skin_variant: Option<String>,
    active: bool,
    offline: bool,
}

fn to_infos(store: &AccountStore) -> Vec<AccountInfo> {
    store
        .accounts
        .iter()
        .map(|account| AccountInfo {
            id: account.id.clone(),
            name: account.name.clone(),
            skin_url: account.skin_url.clone(),
            skin_variant: account.skin_variant.clone(),
            active: store.active.as_deref() == Some(account.id.as_str()),
            offline: account.offline,
        })
        .collect()
}

async fn load_store(state: &LauncherState) -> Result<AccountStore> {
    read_json(&state.paths.accounts_file()).await
}

async fn save_store(state: &LauncherState, store: &AccountStore) -> Result<()> {
    write_json(&state.paths.accounts_file(), store, true).await
}

async fn upsert_account(state: &LauncherState, account: StoredAccount, make_active: bool) -> Result<()> {
    let _guard = state.store_lock.lock().await;
    let mut store = load_store(state).await?;

    if make_active || store.active.is_none() {
        store.active = Some(account.id.clone());
    }
    match store.accounts.iter_mut().find(|entry| entry.id == account.id) {
        Some(existing) => *existing = account,
        None => store.accounts.push(account),
    }

    save_store(state, &store).await
}

/* ========================================
   MICROSOFT → XBOX → MINECRAFT
======================================== */

#[derive(Deserialize)]
struct MsTokens {
    access_token: String,
    refresh_token: String,
}

async fn error_text(response: reqwest::Response) -> String {
    let status = response.status();
    let body = response.text().await.unwrap_or_default();
    let detail = serde_json::from_str::<Value>(&body)
        .ok()
        .and_then(|value| {
            ["errorMessage", "error_description", "developerMessage", "error"]
                .iter()
                .find_map(|key| value.get(*key).and_then(Value::as_str).map(str::to_owned))
        })
        .unwrap_or(body);
    format!("HTTP {status}: {detail}")
}

async fn xbox_live_token(http: &reqwest::Client, ms_access_token: &str) -> Result<(String, String)> {
    let response = http
        .post(XBL_AUTH_URL)
        .json(&json!({
            "Properties": {
                "AuthMethod": "RPS",
                "SiteName": "user.auth.xboxlive.com",
                "RpsTicket": format!("d={ms_access_token}"),
            },
            "RelyingParty": "http://auth.xboxlive.com",
            "TokenType": "JWT",
        }))
        .send()
        .await?;

    if !response.status().is_success() {
        return Err(Error::msg(format!("Xbox-Live-Anmeldung fehlgeschlagen ({})", error_text(response).await)));
    }

    let body: Value = response.json().await?;
    let token = body["Token"].as_str().unwrap_or_default().to_owned();
    let user_hash = body["DisplayClaims"]["xui"][0]["uhs"].as_str().unwrap_or_default().to_owned();
    Ok((token, user_hash))
}

async fn xsts_token(http: &reqwest::Client, xbl_token: &str) -> Result<(String, String)> {
    let response = http
        .post(XSTS_AUTH_URL)
        .json(&json!({
            "Properties": { "SandboxId": "RETAIL", "UserTokens": [xbl_token] },
            "RelyingParty": "rp://api.minecraftservices.com/",
            "TokenType": "JWT",
        }))
        .send()
        .await?;

    if response.status() == reqwest::StatusCode::UNAUTHORIZED {
        let body: Value = response.json().await.unwrap_or_default();
        let message = match body["XErr"].as_u64() {
            Some(2148916233) => "Für dieses Microsoft-Konto existiert noch kein Xbox-Profil. Melde dich einmal auf xbox.com an.",
            Some(2148916235) => "Xbox Live ist in deinem Land nicht verfügbar.",
            Some(2148916236 | 2148916237) => "Dieses Konto muss auf xbox.com noch verifiziert werden.",
            Some(2148916238) => "Kinderkonten müssen von einem Erwachsenen zu einer Microsoft-Familie hinzugefügt werden.",
            _ => "XSTS-Anmeldung fehlgeschlagen.",
        };
        return Err(Error::msg(message));
    }
    if !response.status().is_success() {
        return Err(Error::msg(format!("XSTS-Anmeldung fehlgeschlagen ({})", error_text(response).await)));
    }

    let body: Value = response.json().await?;
    let token = body["Token"].as_str().unwrap_or_default().to_owned();
    let xuid = body["DisplayClaims"]["xui"][0]["xid"].as_str().unwrap_or("0").to_owned();
    Ok((token, xuid))
}

async fn minecraft_token(http: &reqwest::Client, user_hash: &str, xsts: &str) -> Result<(String, u64)> {
    let response = http
        .post(MC_LOGIN_URL)
        .json(&json!({ "identityToken": format!("XBL3.0 x={user_hash};{xsts}") }))
        .send()
        .await?;

    if response.status() == reqwest::StatusCode::FORBIDDEN {
        return Err(Error::msg(
            "Minecraft hat die Anmeldung abgelehnt. Neue Azure-Apps müssen erst von Mojang für die Minecraft-API freigeschaltet werden.",
        ));
    }
    if !response.status().is_success() {
        return Err(Error::msg(format!("Minecraft-Anmeldung fehlgeschlagen ({})", error_text(response).await)));
    }

    let body: Value = response.json().await?;
    let token = body["access_token"].as_str().unwrap_or_default().to_owned();
    let expires_in = body["expires_in"].as_u64().unwrap_or(86_400);
    Ok((token, expires_in))
}

/// Übernimmt Name und aktiven Skin aus der Profil-Antwort der Minecraft-API.
fn apply_profile(account: &mut StoredAccount, profile: &Value) {
    if let Some(id) = profile["id"].as_str() {
        account.id = id.to_owned();
    }
    if let Some(name) = profile["name"].as_str() {
        account.name = name.to_owned();
    }

    let active_skin = profile["skins"]
        .as_array()
        .and_then(|skins| skins.iter().find(|skin| skin["state"] == "ACTIVE"));
    account.skin_url = active_skin.and_then(|skin| skin["url"].as_str()).map(str::to_owned);
    account.skin_variant = active_skin
        .and_then(|skin| skin["variant"].as_str())
        .map(|variant| variant.to_lowercase());
}

async fn fetch_profile(http: &reqwest::Client, access_token: &str) -> Result<Value> {
    let response = http.get(MC_PROFILE_URL).bearer_auth(access_token).send().await?;

    if response.status() == reqwest::StatusCode::NOT_FOUND {
        return Err(Error::msg(
            "Dieses Microsoft-Konto besitzt Minecraft: Java Edition nicht oder hat noch kein Spielerprofil.",
        ));
    }
    if !response.status().is_success() {
        return Err(Error::msg(format!("Profil konnte nicht geladen werden ({})", error_text(response).await)));
    }

    Ok(response.json().await?)
}

async fn account_from_ms_tokens(http: &reqwest::Client, tokens: MsTokens) -> Result<StoredAccount> {
    let (xbl, user_hash) = xbox_live_token(http, &tokens.access_token).await?;
    let (xsts, xuid) = xsts_token(http, &xbl).await?;
    let (access_token, expires_in) = minecraft_token(http, &user_hash, &xsts).await?;
    let profile = fetch_profile(http, &access_token).await?;

    let mut account = StoredAccount {
        refresh_token: tokens.refresh_token,
        access_token,
        expires_at: now_secs() + expires_in,
        xuid,
        ..Default::default()
    };
    apply_profile(&mut account, &profile);
    Ok(account)
}

async fn refresh_account(http: &reqwest::Client, account: &StoredAccount) -> Result<StoredAccount> {
    let response = http
        .post(MS_TOKEN_URL)
        .form(&[
            ("client_id", client_id()?),
            ("grant_type", "refresh_token"),
            ("refresh_token", account.refresh_token.as_str()),
            ("scope", MS_SCOPE),
        ])
        .send()
        .await?;

    if !response.status().is_success() {
        return Err(Error::msg(format!(
            "Sitzung von {} ist abgelaufen – bitte erneut anmelden. ({})",
            account.name,
            error_text(response).await
        )));
    }

    let tokens: MsTokens = response.json().await?;
    account_from_ms_tokens(http, tokens).await
}

/// Liefert das aktive Konto mit gültigem Minecraft-Token (erneuert es bei Bedarf).
pub async fn active_account(state: &LauncherState) -> Result<StoredAccount> {
    let store = load_store(state).await?;
    let account = store
        .active
        .as_ref()
        .and_then(|id| store.accounts.iter().find(|account| &account.id == id))
        .cloned()
        .ok_or_else(|| Error::msg("Bitte melde dich zuerst mit deinem Microsoft-Konto an."))?;

    if account.offline {
        // Release-Builds dürfen nur mit lizenziertem Microsoft-Konto starten
        if cfg!(debug_assertions) {
            return Ok(account);
        }
        return Err(Error::msg(
            "Offline-Testkonten gibt es nur im Entwicklungsmodus. Bitte melde dich mit Microsoft an.",
        ));
    }

    // 5 Minuten Puffer, damit das Token nicht mitten im Start abläuft
    if account.expires_at > now_secs() + 300 {
        return Ok(account);
    }

    let refreshed = refresh_account(&state.http, &account).await?;
    upsert_account(state, refreshed.clone(), false).await?;
    Ok(refreshed)
}

/// Wie `active_account`, aber nur für echte Microsoft-Konten (Skin, Name).
async fn active_microsoft_account(state: &LauncherState) -> Result<StoredAccount> {
    let account = active_account(state).await?;
    if account.offline {
        return Err(Error::msg("Skin und Name lassen sich nur mit einem Microsoft-Konto ändern."));
    }
    Ok(account)
}

/// Offline-UUID wie im Vanilla-Server: MD5 von "OfflinePlayer:<name>" als UUID v3.
fn offline_uuid(name: &str) -> String {
    let mut bytes = md5::compute(format!("OfflinePlayer:{name}")).0;
    bytes[6] = (bytes[6] & 0x0f) | 0x30;
    bytes[8] = (bytes[8] & 0x3f) | 0x80;
    hex::encode(bytes)
}

/* ========================================
   COMMANDS: ANMELDUNG
======================================== */

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct LauncherInfo {
    dev_mode: bool,
    microsoft_configured: bool,
}

#[tauri::command]
pub fn launcher_info() -> LauncherInfo {
    LauncherInfo {
        dev_mode: cfg!(debug_assertions),
        microsoft_configured: client_id().is_ok(),
    }
}

/// Legt ein Offline-Testkonto an – nur im Entwicklungsmodus, um den Launcher ohne Azure-App zu testen.
#[tauri::command]
pub async fn auth_offline_login(state: State<'_, LauncherState>, name: String) -> Result<Vec<AccountInfo>> {
    if !cfg!(debug_assertions) {
        return Err(Error::msg("Offline-Testkonten gibt es nur im Entwicklungsmodus."));
    }
    let name = name.trim().to_owned();
    validate_name(&name)?;

    let account = StoredAccount {
        id: offline_uuid(&name),
        name,
        access_token: "0".into(),
        xuid: "0".into(),
        offline: true,
        ..Default::default()
    };
    upsert_account(&state, account, true).await?;
    Ok(to_infos(&load_store(&state).await?))
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct DeviceCode {
    user_code: String,
    verification_uri: String,
    device_code: String,
    interval: u64,
    expires_in: u64,
}

#[tauri::command]
pub async fn auth_start_login(state: State<'_, LauncherState>) -> Result<DeviceCode> {
    let response = state
        .http
        .post(MS_DEVICE_CODE_URL)
        .form(&[("client_id", client_id()?), ("scope", MS_SCOPE)])
        .send()
        .await?;

    if !response.status().is_success() {
        return Err(Error::msg(format!("Anmeldung konnte nicht gestartet werden ({})", error_text(response).await)));
    }

    let body: Value = response.json().await?;
    state.cancel_login.store(false, Ordering::SeqCst);

    Ok(DeviceCode {
        user_code: body["user_code"].as_str().unwrap_or_default().to_owned(),
        verification_uri: body["verification_uri"]
            .as_str()
            .unwrap_or("https://www.microsoft.com/link")
            .to_owned(),
        device_code: body["device_code"].as_str().unwrap_or_default().to_owned(),
        interval: body["interval"].as_u64().unwrap_or(5),
        expires_in: body["expires_in"].as_u64().unwrap_or(900),
    })
}

/// Wartet, bis der Code im Browser bestätigt wurde, und legt das Konto an.
#[tauri::command]
pub async fn auth_finish_login(
    state: State<'_, LauncherState>,
    device_code: String,
    interval: u64,
    expires_in: u64,
) -> Result<Vec<AccountInfo>> {
    let deadline = now_secs() + expires_in;
    let mut interval = interval.max(1);

    let tokens = loop {
        // Sekundenweise schlafen, damit "Abbrechen" sofort greift
        for _ in 0..interval {
            if state.cancel_login.load(Ordering::SeqCst) {
                return Err(Error::msg("Anmeldung abgebrochen."));
            }
            tokio::time::sleep(Duration::from_secs(1)).await;
        }
        if now_secs() > deadline {
            return Err(Error::msg("Der Code ist abgelaufen. Bitte starte die Anmeldung erneut."));
        }

        let response = state
            .http
            .post(MS_TOKEN_URL)
            .form(&[
                ("client_id", client_id()?),
                ("grant_type", "urn:ietf:params:oauth:grant-type:device_code"),
                ("device_code", device_code.as_str()),
            ])
            .send()
            .await?;

        if response.status().is_success() {
            break response.json::<MsTokens>().await?;
        }

        let body: Value = response.json().await.unwrap_or_default();
        match body["error"].as_str().unwrap_or_default() {
            "authorization_pending" => continue,
            "slow_down" => interval += 5,
            "authorization_declined" => return Err(Error::msg("Die Anmeldung wurde abgelehnt.")),
            "expired_token" => {
                return Err(Error::msg("Der Code ist abgelaufen. Bitte starte die Anmeldung erneut."))
            }
            _ => {
                return Err(Error::msg(
                    body["error_description"]
                        .as_str()
                        .unwrap_or("Anmeldung fehlgeschlagen.")
                        .to_owned(),
                ))
            }
        }
    };

    let account = account_from_ms_tokens(&state.http, tokens).await?;
    upsert_account(&state, account, true).await?;
    Ok(to_infos(&load_store(&state).await?))
}

#[tauri::command]
pub fn auth_cancel_login(state: State<'_, LauncherState>) {
    state.cancel_login.store(true, Ordering::SeqCst);
}

/* ========================================
   COMMANDS: KONTEN
======================================== */

#[tauri::command]
pub async fn accounts_list(state: State<'_, LauncherState>) -> Result<Vec<AccountInfo>> {
    Ok(to_infos(&load_store(&state).await?))
}

#[tauri::command]
pub async fn accounts_select(state: State<'_, LauncherState>, id: String) -> Result<Vec<AccountInfo>> {
    let _guard = state.store_lock.lock().await;
    let mut store = load_store(&state).await?;
    if !store.accounts.iter().any(|account| account.id == id) {
        return Err(Error::msg("Konto nicht gefunden."));
    }
    store.active = Some(id);
    save_store(&state, &store).await?;
    Ok(to_infos(&store))
}

#[tauri::command]
pub async fn accounts_remove(state: State<'_, LauncherState>, id: String) -> Result<Vec<AccountInfo>> {
    let _guard = state.store_lock.lock().await;
    let mut store = load_store(&state).await?;
    store.accounts.retain(|account| account.id != id);
    if store.active.as_deref() == Some(id.as_str()) {
        store.active = store.accounts.first().map(|account| account.id.clone());
    }
    save_store(&state, &store).await?;
    Ok(to_infos(&store))
}

/* ========================================
   COMMANDS: MINECRAFT-PROFIL
======================================== */

/// Lädt eine Skin-Textur und gibt sie als data-URL zurück (umgeht CORS im WebView).
#[tauri::command]
pub async fn account_skin_data(state: State<'_, LauncherState>, url: String) -> Result<String> {
    let allowed = url.starts_with("https://textures.minecraft.net/")
        || url.starts_with("http://textures.minecraft.net/");
    if !allowed {
        return Err(Error::msg("Ungültige Skin-Adresse."));
    }

    let bytes = state.http.get(&url).send().await?.error_for_status()?.bytes().await?;
    Ok(format!(
        "data:image/png;base64,{}",
        base64::engine::general_purpose::STANDARD.encode(bytes)
    ))
}

async fn update_from_profile_response(
    state: &LauncherState,
    mut account: StoredAccount,
    response: reqwest::Response,
    action: &str,
) -> Result<Vec<AccountInfo>> {
    if !response.status().is_success() {
        return Err(Error::msg(format!("{action} fehlgeschlagen ({})", error_text(response).await)));
    }

    let profile: Value = response.json().await?;
    apply_profile(&mut account, &profile);
    upsert_account(state, account, false).await?;
    Ok(to_infos(&load_store(state).await?))
}

fn validate_skin(bytes: &[u8]) -> Result<()> {
    const PNG_SIGNATURE: &[u8] = b"\x89PNG\r\n\x1a\n";
    if bytes.len() < 24 || &bytes[..8] != PNG_SIGNATURE {
        return Err(Error::msg("Der Skin muss eine PNG-Datei sein."));
    }

    // Breite und Höhe stehen im IHDR-Block direkt nach der Signatur
    let width = u32::from_be_bytes([bytes[16], bytes[17], bytes[18], bytes[19]]);
    let height = u32::from_be_bytes([bytes[20], bytes[21], bytes[22], bytes[23]]);
    if width != 64 || (height != 64 && height != 32) {
        return Err(Error::msg(format!(
            "Skins müssen 64×64 oder 64×32 Pixel groß sein (diese Datei: {width}×{height})."
        )));
    }
    Ok(())
}

#[tauri::command]
pub async fn account_upload_skin(
    state: State<'_, LauncherState>,
    path: String,
    variant: String,
) -> Result<Vec<AccountInfo>> {
    let variant = if variant == "slim" { "slim" } else { "classic" };
    let bytes = tokio::fs::read(&path).await?;
    validate_skin(&bytes)?;

    let account = active_microsoft_account(&state).await?;
    let form = reqwest::multipart::Form::new().text("variant", variant).part(
        "file",
        reqwest::multipart::Part::bytes(bytes)
            .file_name("skin.png")
            .mime_str("image/png")?,
    );

    let response = state
        .http
        .post(format!("{MC_PROFILE_URL}/skins"))
        .bearer_auth(&account.access_token)
        .multipart(form)
        .send()
        .await?;

    update_from_profile_response(&state, account, response, "Skin-Upload").await
}

#[tauri::command]
pub async fn account_reset_skin(state: State<'_, LauncherState>) -> Result<Vec<AccountInfo>> {
    let account = active_microsoft_account(&state).await?;
    let response = state
        .http
        .delete(format!("{MC_PROFILE_URL}/skins/active"))
        .bearer_auth(&account.access_token)
        .send()
        .await?;

    update_from_profile_response(&state, account, response, "Zurücksetzen des Skins").await
}

fn validate_name(name: &str) -> Result<()> {
    let valid = (3..=16).contains(&name.len())
        && name.chars().all(|char| char.is_ascii_alphanumeric() || char == '_');
    if valid {
        Ok(())
    } else {
        Err(Error::msg("Namen brauchen 3–16 Zeichen: Buchstaben, Zahlen und _."))
    }
}

/// Gibt "AVAILABLE", "DUPLICATE" oder "NOT_ALLOWED" zurück.
#[tauri::command]
pub async fn account_name_available(state: State<'_, LauncherState>, name: String) -> Result<String> {
    validate_name(&name)?;
    let account = active_microsoft_account(&state).await?;
    let response = state
        .http
        .get(format!("{MC_PROFILE_URL}/name/{name}/available"))
        .bearer_auth(&account.access_token)
        .send()
        .await?;

    if !response.status().is_success() {
        return Err(Error::msg(format!("Prüfung fehlgeschlagen ({})", error_text(response).await)));
    }
    let body: Value = response.json().await?;
    Ok(body["status"].as_str().unwrap_or("NOT_ALLOWED").to_owned())
}

#[tauri::command]
pub async fn account_change_name(state: State<'_, LauncherState>, name: String) -> Result<Vec<AccountInfo>> {
    validate_name(&name)?;
    let account = active_microsoft_account(&state).await?;
    let response = state
        .http
        .put(format!("{MC_PROFILE_URL}/name/{name}"))
        .bearer_auth(&account.access_token)
        .send()
        .await?;

    if response.status() == reqwest::StatusCode::FORBIDDEN {
        return Err(Error::msg("Der Name kann nur alle 30 Tage geändert werden."));
    }
    update_from_profile_response(&state, account, response, "Namensänderung").await
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn offline_uuid_matches_vanilla() {
        // Gleicher Wert wie UUID.nameUUIDFromBytes("OfflinePlayer:Notch") in Java
        assert_eq!(offline_uuid("Notch"), "b50ad385829d3141a2167e7d7539ba7f");
    }
}
