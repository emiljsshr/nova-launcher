mod auth;
mod bridge;
mod discord;
mod download;
mod error;
mod launch;
mod minecraft;
mod nova_mod;
mod profiles;
mod screenshots;
mod social;
mod state;

use tauri::Manager;

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_opener::init())
        .plugin(tauri_plugin_dialog::init())
        .plugin(tauri_plugin_updater::Builder::new().build())
        .plugin(tauri_plugin_process::init())
        .manage(discord::Discord::start())
        .setup(|app| {
            let root = app.path().app_data_dir()?;
            std::fs::create_dir_all(&root)?;
            app.manage(state::LauncherState::new(root));
            Ok(())
        })
        .invoke_handler(tauri::generate_handler![
            auth::launcher_info,
            auth::auth_offline_login,
            auth::auth_start_login,
            auth::auth_finish_login,
            auth::auth_cancel_login,
            auth::accounts_list,
            auth::accounts_select,
            auth::accounts_remove,
            auth::account_skin_data,
            auth::account_upload_skin,
            auth::account_reset_skin,
            auth::account_name_available,
            auth::account_change_name,
            minecraft::versions_list,
            minecraft::fabric_loaders,
            nova_mod::nova_mod_versions,
            profiles::profiles_list,
            profiles::profiles_create,
            profiles::profiles_update,
            profiles::profiles_delete,
            profiles::profiles_open_folder,
            profiles::open_data_folder,
            launch::game_launch,
            launch::game_stop,
            launch::game_status,
            bridge::bridge_send,
            social::social_server_url,
            social::social_login,
            social::social_logout,
            screenshots::screenshots_list,
            screenshots::screenshots_delete,
            screenshots::screenshots_open,
            discord::discord_set_presence,
            discord::discord_available,
        ])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
