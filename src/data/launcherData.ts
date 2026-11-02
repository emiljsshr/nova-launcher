
import type { HeadVariant, ServerIconVariant } from "../components/PixelIcon";

// Platzhalter-Inhalte für den Launcher.
// Werden ersetzt, sobald ein Backend (News, Server-Status, Freunde, Store) existiert.

/* ========================================
   HOME
======================================== */

export type HeroSlide = {
  id: string;
  badge: string;
  titleTop: string;
  titleBottom: string;
  description: string;
};

export const heroSlides: HeroSlide[] = [
  {
    id: "frost-season",
    badge: "NEW · UPDATE 2.4",
    titleTop: "FROST",
    titleBottom: "SEASON",
    description:
      "Neue Cosmetics, 30% mehr FPS durch den überarbeiteten Renderer und die Nova Arena als neuer Partner.",
  },
  {
    id: "frost-cup",
    badge: "EVENT · SAMSTAG",
    titleTop: "FROST CUP",
    titleBottom: "TURNIER",
    description:
      "128 Spieler, 1v1 in der Nova Arena. Melde dich jetzt an und sichere dir exklusive Cosmetics.",
  },
  {
    id: "nova-capes",
    badge: "STORE · NEU",
    titleTop: "WINTER",
    titleBottom: "CAPES",
    description:
      "Drei neue Capes im Frost-Look – ab sofort im Store und direkt in deiner Garderobe.",
  },
];

export const upcomingEvent = {
  label: "SA · 20:00",
  title: "Frost Cup Turnier",
  details: "Nova Arena · 1v1 · 128 Spieler",
};

/* ========================================
   PARTNER SERVER
======================================== */

export type ServerCategory = "PVP" | "SURVIVAL" | "SKYBLOCK" | "MINIGAMES" | "CREATIVE";

export type PartnerServer = {
  id: string;
  name: string;
  modes: string;
  categories: ServerCategory[];
  players: number;
  maxPlayers: number;
  version: string;
  region: string;
  // Platzhalter-Adressen, echte Adressen kommen vom Partner
  address: string;
  description: string;
  icon: ServerIconVariant;
  featured?: boolean;
};

export const partnerServers: PartnerServer[] = [
  {
    id: "glacier",
    name: "Glacier Network",
    modes: "Skyblock · Bedwars",
    categories: ["SKYBLOCK", "MINIGAMES"],
    players: 12480,
    maxPlayers: 20000,
    version: "1.8 – 1.21",
    region: "EU",
    address: "glacier.example",
    description:
      "Eines der größten Netzwerke im Nova-Partnerprogramm: Skyblock mit eigener Wirtschaft, schnelle Bedwars-Runden und wöchentliche Events.",
    icon: "ice",
    featured: true,
  },
  {
    id: "nova-arena",
    name: "Nova Arena",
    modes: "PvP · Practice",
    categories: ["PVP"],
    players: 8214,
    maxPlayers: 12000,
    version: "1.8.9",
    region: "EU",
    address: "arena.example",
    description:
      "Ranked 1v1, Kits und Turniere. Offizieller Austragungsort des Frost Cup.",
    icon: "arena",
  },
  {
    id: "blockhaven",
    name: "Blockhaven",
    modes: "Survival · SMP",
    categories: ["SURVIVAL"],
    players: 3902,
    maxPlayers: 6000,
    version: "1.21.4",
    region: "EU",
    address: "blockhaven.example",
    description:
      "Entspanntes Vanilla+ SMP mit Grundstücksschutz, Community-Projekten und Spieler-Shops.",
    icon: "grass",
  },
  {
    id: "pixel-isles",
    name: "Pixel Isles",
    modes: "Skyblock · OneBlock",
    categories: ["SKYBLOCK"],
    players: 2140,
    maxPlayers: 4000,
    version: "1.20 – 1.21",
    region: "EU",
    address: "isles.example",
    description:
      "Insel-Abenteuer mit Quests, Minions und einem saisonalen OneBlock-Modus.",
    icon: "sand",
  },
  {
    id: "emberfall",
    name: "Emberfall",
    modes: "Factions · Raids",
    categories: ["PVP", "SURVIVAL"],
    players: 1675,
    maxPlayers: 3000,
    version: "1.21.1",
    region: "EU",
    address: "emberfall.example",
    description:
      "Hardcore-Factions mit Raids, Belagerungen und monatlichem Map-Reset.",
    icon: "lava",
  },
  {
    id: "crafthaus",
    name: "Crafthaus",
    modes: "Creative · Citybuild",
    categories: ["CREATIVE"],
    players: 980,
    maxPlayers: 2000,
    version: "1.21.4",
    region: "EU",
    address: "crafthaus.example",
    description:
      "Plots, WorldEdit und Bauwettbewerbe – für alle, die lieber bauen als kämpfen.",
    icon: "amethyst",
  },
];

// Auf der Startseite werden die drei größten Partner gezeigt
export const partnerPreviews = partnerServers.slice(0, 3);

/* ========================================
   FREUNDE
======================================== */

export type Friend = {
  id: string;
  name: string;
  activity: string;
  status: "playing" | "launcher";
  head: HeadVariant;
};

export const friends: Friend[] = [
  { id: "iceypvp", name: "IceyPvP", activity: "Glacier Network · Bedwars", status: "playing", head: "frost" },
  { id: "lumi", name: "Lumi_", activity: "Nova Arena · Practice", status: "playing", head: "blonde" },
  { id: "kraxel", name: "Kraxel", activity: "Im Launcher", status: "launcher", head: "dark" },
];

/* ========================================
   COSMETICS & STORE
======================================== */

export type CosmeticType = "CAPE" | "HEAD" | "WINGS" | "AURA" | "PET" | "TRAIL";
export type Rarity = "COMMON" | "RARE" | "EPIC" | "LEGENDARY";

export type Cosmetic = {
  id: string;
  name: string;
  type: CosmeticType;
  rarity: Rarity;
  description: string;
  price: number;
  owned: boolean;
  badge?: "NEU" | "EXKLUSIV";
  // 3D-Modell unter public/cosmetics/<id>/ – Vorschau im Store und sichtbar im Spiel
  model?: boolean;
};

export const cosmeticTypes: CosmeticType[] = ["CAPE", "HEAD", "WINGS", "AURA", "PET", "TRAIL"];

// Kurzname für Tabs und Slots
export const cosmeticTypeLabels: Record<CosmeticType, string> = {
  CAPE: "CAPES",
  HEAD: "KOPF",
  WINGS: "FLÜGEL",
  AURA: "AURA",
  PET: "PETS",
  TRAIL: "SPUREN",
};

// Überschrift der Abschnitte in der Garderobe
export const cosmeticSectionLabels: Record<CosmeticType, string> = {
  CAPE: "CAPES",
  HEAD: "KOPF-DEKO",
  WINGS: "FLÜGEL",
  AURA: "AURA",
  PET: "PETS",
  TRAIL: "SPUREN",
};

export const rarityLabels: Record<Rarity, string> = {
  COMMON: "GEWÖHNLICH",
  RARE: "SELTEN",
  EPIC: "EPISCH",
  LEGENDARY: "LEGENDÄR",
};

export const cosmetics: Cosmetic[] = [
  { id: "frost-cape", name: "Frost Cape", type: "CAPE", rarity: "RARE", description: "Gefrorene Kristalle auf Eisblau.", price: 600, owned: true },
  { id: "starfield", name: "Starfield", type: "CAPE", rarity: "EPIC", description: "Ein Stück Nachthimmel zum Umhängen.", price: 800, owned: false },
  { id: "aurora-cape", name: "Aurora", type: "CAPE", rarity: "EPIC", description: "Polarlicht, eingewebt in tiefes Petrol.", price: 950, owned: false, badge: "NEU" },
  { id: "purpur-waechter", name: "Purpur-Wächter", type: "CAPE", rarity: "LEGENDARY", description: "Dunkles Pflaume-Lila mit roten Kanten. In der Mitte ein Wappen mit glühender Klinge, unten gezackte Spitzen.", price: 1400, owned: true, badge: "NEU", model: true },
  { id: "founder", name: "Founder", type: "CAPE", rarity: "LEGENDARY", description: "Nur für Spieler der ersten Stunde.", price: 0, owned: true, badge: "EXKLUSIV" },

  { id: "purpur-krone", name: "Purpur-Krone", type: "HEAD", rarity: "LEGENDARY", description: "Reif aus dunklem Pflaume-Metall mit roten Kanten und goldenen Nieten, Samtpolster und glühenden roten Steinen.", price: 1300, owned: true, badge: "NEU", model: true },
  { id: "purpur-halo", name: "Purpur-Halo", type: "HEAD", rarity: "EPIC", description: "Ein dunkler Ring mit rot glühender Kante schwebt über deinem Kopf und dreht sich langsam.", price: 900, owned: true, badge: "NEU", model: true },
  { id: "purpur-hoerner", name: "Purpur-Hörner", type: "HEAD", rarity: "EPIC", description: "Gebogene Hörner aus Pflaume-Metall, die Spitzen glühen rot.", price: 1000, owned: true, badge: "NEU", model: true },
  { id: "ice-crown", name: "Eiskrone", type: "HEAD", rarity: "EPIC", description: "Geschliffen aus ewigem Eis.", price: 900, owned: true },
  { id: "halo", name: "Halo", type: "HEAD", rarity: "RARE", description: "Ein goldener Ring über deinem Kopf.", price: 450, owned: false },
  { id: "frost-horns", name: "Frost-Hörner", type: "HEAD", rarity: "EPIC", description: "Eiszapfen in Hornform.", price: 700, owned: false, badge: "NEU" },
  { id: "nova-beanie", name: "Nova Beanie", type: "HEAD", rarity: "COMMON", description: "Warm, gemütlich, Nova-Blau.", price: 200, owned: true },

  { id: "frost-wings", name: "Frostblitz-Schwingen", type: "WINGS", rarity: "LEGENDARY", description: "Glatte Federn von Eisweiß bis tiefem Blau. Durch jede Schwungfeder zuckt ein leuchtender Frost-Blitz, am Flügelbogen sitzen Eiskristalle.", price: 1200, owned: true, badge: "NEU", model: true },
  { id: "raven-wings", name: "Rabenschwingen", type: "WINGS", rarity: "EPIC", description: "Schwarze Rabenflügel mit kleinen dunkelroten Federn. Angelegt am Rücken, beim Sprinten und Fliegen breiten sie sich aus.", price: 1600, owned: true, badge: "NEU", model: true },
  { id: "crystal-wings", name: "Kristallflügel", type: "WINGS", rarity: "LEGENDARY", description: "Jede Feder ein einzelner Eiskristall.", price: 2000, owned: true },
  { id: "nebula", name: "Nebula", type: "WINGS", rarity: "LEGENDARY", description: "Aus der Leere zwischen den Sternen.", price: 1800, owned: false, badge: "NEU" },
  { id: "ice-dragon", name: "Eisdrache", type: "WINGS", rarity: "LEGENDARY", description: "Die Schwingen eines uralten Frostdrachen.", price: 2200, owned: false },

  { id: "snowfall", name: "Schneegestöber", type: "AURA", rarity: "RARE", description: "Leiser Schneefall, wo immer du stehst.", price: 500, owned: false },
  { id: "polar-light", name: "Polarlicht", type: "AURA", rarity: "LEGENDARY", description: "Ein Nordlicht, das dir überall hin folgt.", price: 2600, owned: false },
  { id: "void-ring", name: "Void Ring", type: "AURA", rarity: "EPIC", description: "Ein Ring aus End-Energie zu deinen Füßen.", price: 1400, owned: false },

  { id: "raven", name: "Rabe", type: "PET", rarity: "EPIC", description: "Kreist schwarz schimmernd neben dir und lässt dich nie aus den Augen.", price: 1500, owned: true, badge: "NEU", model: true },
  { id: "purpur-rabe", name: "Purpur-Rabe", type: "PET", rarity: "LEGENDARY", description: "Pflaume-lila Gefieder und rot glühende Augen – der Begleiter zum Purpur-Wächter.", price: 1600, owned: true, badge: "NEU", model: true },
  { id: "snow-fox", name: "Schneefuchs", type: "PET", rarity: "EPIC", description: "Dein treuer Begleiter durch jeden Schneesturm.", price: 1500, owned: false, badge: "NEU" },
  { id: "pixel-slime", name: "Pixel-Slime", type: "PET", rarity: "COMMON", description: "Hüpft. Glibbert. Liebt dich.", price: 300, owned: false },
  { id: "snow-owl", name: "Schneeeule", type: "PET", rarity: "RARE", description: "Sieht alles, sagt nichts.", price: 650, owned: false },

  // Spuren
  { id: "glut-spuren", name: "Glutspuren", type: "TRAIL", rarity: "RARE", description: "Bei jedem Schritt glimmt ein roter Abdruck auf und verglüht langsam.", price: 700, owned: true, badge: "NEU", model: true },
];

export const coinBalance = 2450;

export const currentCollection = "Frost Season · Drop 1";

export const featuredBundle = {
  title: "FROST BUNDLE",
  description: "Frostblitz-Schwingen, Frost-Hörner und Schneegestöber in einem Paket.",
  itemIds: ["frost-wings", "frost-horns", "snowfall"],
  price: 1900,
  originalPrice: 2400,
};

/* ========================================
   DISCORD
======================================== */

// Einladungslink zum Nova-Discord – noch nicht hinterlegt
export const DISCORD_INVITE_URL: string | null = null;

export const discordStats = [
  { label: "MITGLIEDER", value: "24.810" },
  { label: "ONLINE", value: "3.402" },
  { label: "CHANNELS", value: "38" },
];

export const discordChannels = [
  { name: "ankündigungen", description: "Updates, Patch Notes und Wartungen" },
  { name: "events", description: "Turniere, Giveaways und Community-Abende" },
  { name: "support", description: "Hilfe bei Launcher- und Spielproblemen" },
  { name: "partner-server", description: "Neuigkeiten aus dem Partnernetzwerk" },
  { name: "screenshots", description: "Deine schönsten Momente in Minecraft" },
];

export const discordAnnouncements = [
  {
    id: "d1",
    tag: "UPDATE",
    title: "Nova 2.4 – Frost Season ist live",
    body: "Neuer Renderer, Winter-Cosmetics und die Nova Arena als Partner. Alle Details in den Patch Notes.",
    date: "Heute",
  },
  {
    id: "d2",
    tag: "EVENT",
    title: "Frost Cup: Anmeldung geöffnet",
    body: "128 Plätze für das 1v1-Turnier am Samstag um 20:00 Uhr in der Nova Arena.",
    date: "Gestern",
  },
  {
    id: "d3",
    tag: "WARTUNG",
    title: "Kurze Wartung der Login-Server",
    body: "Am Donnerstag zwischen 03:00 und 04:00 Uhr kann es zu Verzögerungen beim Anmelden kommen.",
    date: "Vor 3 Tagen",
  },
];

/* ========================================
   EINSTELLUNGEN
======================================== */

export const MAX_MEMORY_GB = 12;

export type LauncherSettings = {
  memory: number;
  resolutionWidth: number;
  resolutionHeight: number;
  fullscreen: boolean;
  javaPath: string;
  jvmArgs: string;
  afterLaunch: "keep" | "minimize" | "hide";
  discordPresence: boolean;
  showFriendActivity: boolean;
};

export const defaultSettings: LauncherSettings = {
  memory: 4,
  resolutionWidth: 1280,
  resolutionHeight: 720,
  fullscreen: false,
  javaPath: "",
  jvmArgs: "-XX:+UseG1GC -XX:+UnlockExperimentalVMOptions",
  afterLaunch: "minimize",
  discordPresence: true,
  showFriendActivity: true,
};
