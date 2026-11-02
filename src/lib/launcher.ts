
import { invoke, isTauri } from "@tauri-apps/api/core";
import type { ServerIconVariant } from "../components/PixelIcon";

// Typisierte Aufrufe der Rust-Commands (src-tauri/src)

export type Account = {
  id: string;
  name: string;
  skinUrl: string | null;
  skinVariant: "classic" | "slim" | null;
  active: boolean;
  offline: boolean;
};

export type LauncherInfo = {
  devMode: boolean;
  microsoftConfigured: boolean;
};

export type DeviceCode = {
  userCode: string;
  verificationUri: string;
  deviceCode: string;
  interval: number;
  expiresIn: number;
};

export type Loader = "vanilla" | "fabric";

export type GameProfile = {
  id: string;
  name: string;
  gameVersion: string;
  loader: Loader;
  loaderVersion: string | null;
  icon: ServerIconVariant;
  createdAt: number;
  lastPlayed: number | null;
  playSeconds: number;
  sessions: number;
};

export type ProfileInput = {
  name: string;
  gameVersion: string;
  loader: Loader;
  loaderVersion: string | null;
  icon: ServerIconVariant;
};

export type GameVersion = {
  id: string;
  kind: string;
  releaseTime: string;
};

export type LaunchOptions = {
  memoryMb: number;
  jvmArgs: string;
  javaPath: string;
  width: number;
  height: number;
  fullscreen: boolean;
  server: string | null;
  // Angelegte Cosmetics, die die Nova-Mod im Spiel zeigt
  cosmetics: string[];
  // Sodium, Lithium & Co. mitinstallieren
  performanceMods: boolean;
};

export type LaunchProgress = {
  stage: string;
  label: string;
  done: number;
  total: number;
};

export type GameLog = {
  line: string;
  error: boolean;
};

export type GameExit = {
  profileId: string;
  code: number | null;
  seconds: number;
};

export type SocialSession = {
  serverUrl: string;
  token: string;
  user: { id: string; name: string };
};

export type Screenshot = {
  profileId: string;
  profileName: string;
  file: string;
  path: string;
  takenAt: number;
  size: number;
};

export type DiscordPresence = {
  details: string;
  state: string | null;
  startedAt: number | null;
};

export const inTauri = isTauri();

function call<T>(command: string, args?: Record<string, unknown>) {
  if (!inTauri) {
    return Promise.reject(
      "Diese Funktion ist nur in der Desktop-App verfügbar (npm run tauri dev).",
    );
  }
  return invoke<T>(command, args);
}

export function errorMessage(error: unknown) {
  if (typeof error === "string") return error;
  if (error instanceof Error) return error.message;
  return "Unbekannter Fehler.";
}

export const launcher = {
  info: () => call<LauncherInfo>("launcher_info"),
  offlineLogin: (name: string) => call<Account[]>("auth_offline_login", { name }),
  startLogin: () => call<DeviceCode>("auth_start_login"),
  finishLogin: (code: DeviceCode) =>
    call<Account[]>("auth_finish_login", {
      deviceCode: code.deviceCode,
      interval: code.interval,
      expiresIn: code.expiresIn,
    }),
  cancelLogin: () => call<void>("auth_cancel_login"),

  listAccounts: () => call<Account[]>("accounts_list"),
  selectAccount: (id: string) => call<Account[]>("accounts_select", { id }),
  removeAccount: (id: string) => call<Account[]>("accounts_remove", { id }),

  skinData: (url: string) => call<string>("account_skin_data", { url }),
  uploadSkin: (path: string, variant: "classic" | "slim") =>
    call<Account[]>("account_upload_skin", { path, variant }),
  resetSkin: () => call<Account[]>("account_reset_skin"),
  nameAvailable: (name: string) =>
    call<"AVAILABLE" | "DUPLICATE" | "NOT_ALLOWED">("account_name_available", { name }),
  changeName: (name: string) => call<Account[]>("account_change_name", { name }),

  listVersions: (includeSnapshots: boolean) =>
    call<GameVersion[]>("versions_list", { includeSnapshots }),
  fabricLoaders: (gameVersion: string) =>
    call<string[]>("fabric_loaders", { gameVersion }),
  novaModVersions: () => call<string[]>("nova_mod_versions"),

  listProfiles: () => call<GameProfile[]>("profiles_list"),
  createProfile: (input: ProfileInput) =>
    call<GameProfile[]>("profiles_create", { input }),
  updateProfile: (id: string, input: ProfileInput) =>
    call<GameProfile[]>("profiles_update", { id, input }),
  deleteProfile: (id: string, deleteFiles: boolean) =>
    call<GameProfile[]>("profiles_delete", { id, deleteFiles }),
  openProfileFolder: (id: string) => call<void>("profiles_open_folder", { id }),
  openDataFolder: () => call<void>("open_data_folder"),

  launch: (profileId: string, options: LaunchOptions) =>
    call<void>("game_launch", { profileId, options }),
  stop: () => call<void>("game_stop"),
  status: () => call<string | null>("game_status"),

  // Nachricht an die Nova-Mod im laufenden Spiel
  bridgeSend: (message: unknown) => call<void>("bridge_send", { message }),

  socialServerUrl: () => call<string>("social_server_url"),
  socialLogin: () => call<SocialSession>("social_login"),
  socialLogout: () => call<void>("social_logout"),

  listScreenshots: () => call<Screenshot[]>("screenshots_list"),
  deleteScreenshot: (profileId: string, file: string) =>
    call<void>("screenshots_delete", { profileId, file }),
  openScreenshot: (profileId: string, file: string | null) =>
    call<void>("screenshots_open", { profileId, file }),

  discordAvailable: () => call<boolean>("discord_available"),
  setDiscordPresence: (presence: DiscordPresence | null) =>
    call<void>("discord_set_presence", { presence }),
};

export function formatPlayTime(seconds: number) {
  const hours = Math.floor(seconds / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  if (hours > 0) return `${hours} h ${String(minutes).padStart(2, "0")} min`;
  return `${minutes} min`;
}

export function formatRelative(timestamp: number | null) {
  if (!timestamp) return "Noch nie gespielt";
  const days = Math.floor((Date.now() / 1000 - timestamp) / 86400);
  if (days <= 0) return "Heute";
  if (days === 1) return "Gestern";
  return `Vor ${days} Tagen`;
}
