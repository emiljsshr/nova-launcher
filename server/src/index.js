import { fileURLToPath } from "node:url";
import { createApp } from "./app.js";
import { Store } from "./store.js";

// Einstellungen über Umgebungsvariablen:
//   PORT                 Port (Standard 8787)
//   NOVA_DATA            Datendatei (Standard data/store.json)
//   NOVA_NEWS            News-Datei (Standard data/news.json)
//   NOVA_ALLOW_OFFLINE=1 Offline-Konten zulassen (nur zum lokalen Testen)
const root = fileURLToPath(new URL("..", import.meta.url));
const port = Number(process.env.PORT ?? 8787);
const store = new Store(process.env.NOVA_DATA ?? `${root}data/store.json`);
store.dropExpiredTokens();

const app = createApp({
  store,
  newsFile: process.env.NOVA_NEWS ?? `${root}data/news.json`,
  sessionUrl: process.env.NOVA_SESSION_URL ?? "https://sessionserver.mojang.com",
  allowOffline: process.env.NOVA_ALLOW_OFFLINE === "1",
});

app.server.listen(port, () => console.log(`Nova-Server läuft auf Port ${port}`));

for (const signal of ["SIGINT", "SIGTERM"]) {
  process.on(signal, () => {
    app.close();
    process.exit(0);
  });
}
