// Verhalten eines Pets aus cosmetic.json ("pet"): schwebt am Platz ("idle"), fliegt ab und zu eine Runde um
// den Spieler und verteilt dabei Herzen über ihm ("fly"), landet nach einigen Sekunden Stillstand neben ihm
// ("sit") und fliegt wieder auf, sobald er sich bewegt. Gleiche Regeln wie PetBehavior.java im Mod-Kern.

export type Vec3 = [number, number, number];

export type PetConfig = {
  root: string;
  sit?: { position: Vec3; after?: number };
  circle?: { first?: number; every?: [number, number]; duration?: number; widen?: number; rise?: number };
  hearts?: {
    uv: [number, number, number, number];
    interval?: number;
    life?: number;
    size?: number;
    rise?: number;
    spread?: number;
    height?: number;
  };
};

export type Pose = {
  weights: Record<string, number>;
  // Animationszeit für alle Animationen (z. B. seit einem Auslöser) statt der Uhrzeit
  time?: number;
  rootOffset: Vec3;
  rootRotation: Vec3;
  // Je Herz: x, y, z (Bedrock-Pixel), Größe, Drehung um die Hochachse in Grad
  hearts: [number, number, number, number, number][];
};

type State = "HOVER" | "CIRCLE" | "LANDING" | "SIT" | "TAKEOFF";

const LAND_SECONDS = 1.6;
const TAKEOFF_SECONDS = 1.2;
const BLEND_SECONDS = 0.4;

// Gleiche Zufallswerte (0–1) wie in der Mod
export function hash(index: number, salt: number) {
  const value = Math.sin(index * 12.9898 + salt * 78.233) * 43758.5453;
  return value - Math.floor(value);
}

const clamp = (u: number) => Math.max(0, Math.min(1, u));
const smooth = (u: number) => {
  const c = clamp(u);
  return c * c * (3 - 2 * c);
};
const smoothRange = (start: number, end: number, u: number) => smooth((u - start) / (end - start));

export function createPetBehavior(config: PetConfig, hover: Vec3) {
  const sit = config.sit?.position ?? [0, 0, 0];
  const sitAfter = config.sit?.after ?? 10;
  const circleFirst = config.circle?.first ?? 4;
  const [circleMin, circleMax] = config.circle?.every ?? [18, 35];
  const circleDuration = config.circle?.duration ?? 4;
  const circleWiden = config.circle?.widen ?? 6;
  const circleRise = config.circle?.rise ?? 4;
  const hearts = config.hearts;
  const heartInterval = hearts?.interval ?? 0.25;
  const heartLife = hearts?.life ?? 1.8;
  const heartSize = hearts?.size ?? 2.5;
  const heartRise = hearts?.rise ?? 7;
  const heartSpread = hearts?.spread ?? 8;
  const heartHeight = hearts?.height ?? 35;

  let state: State = "HOVER";
  let stateStart = 0;
  let nextCircle = Number.NaN;
  let circles = 0;
  let heartsStart = Number.NEGATIVE_INFINITY;
  let heartSeed = 0;
  let from: Vec3 = [0, 0, 0];
  let current: Vec3 = [0, 0, 0];
  const sitOffset: Vec3 = [sit[0] - hover[0], sit[1] - hover[1], sit[2] - hover[2]];

  function begin(next: State, now: number) {
    from = [...current];
    state = next;
    stateStart = now;
  }

  function lerpTo(target: Vec3, k: number, arc: number) {
    current = [0, 1, 2].map((i) => from[i] + (target[i] - from[i]) * k) as Vec3;
    current[1] += arc;
  }

  return function pose(now: number, idle: number): Pose {
    if (Number.isNaN(nextCircle)) {
      nextCircle = now + circleFirst;
      stateStart = now;
    }
    const still = idle >= sitAfter;
    let t = now - stateStart;

    if (state === "HOVER") {
      if (still) begin("LANDING", now);
      else if (now >= nextCircle) {
        begin("CIRCLE", now);
        heartsStart = now;
        heartSeed = circles * 97;
      }
    } else if (state === "CIRCLE") {
      if (t >= circleDuration) {
        circles++;
        nextCircle = now + circleMin + (circleMax - circleMin) * hash(circles, 21);
        begin("HOVER", now);
      }
    } else if (state === "LANDING") {
      if (!still) begin("TAKEOFF", now);
      else if (t >= LAND_SECONDS) begin("SIT", now);
    } else if (state === "SIT") {
      if (!still) begin("TAKEOFF", now);
    } else if (state === "TAKEOFF") {
      if (still) begin("LANDING", now);
      else if (t >= TAKEOFF_SECONDS) {
        // Nach dem Auffliegen nicht sofort eine Runde
        nextCircle = Math.max(nextCircle, now + 3);
        begin("HOVER", now);
      }
    }

    const result: Pose = { weights: {}, rootOffset: [0, 0, 0], rootRotation: [0, 0, 0], hearts: [] };
    const add = (name: string, weight: number) => {
      if (weight > 0.001) result.weights[name] = weight;
    };
    t = now - stateStart;

    if (state === "CIRCLE") {
      const u = clamp(t / circleDuration);
      const fly = smooth(Math.min(1, Math.min(t, circleDuration - t) / BLEND_SECONDS));
      add("idle", 1 - fly);
      add("fly", fly);
      // Kreis um die Hochachse des Spielers, startet und endet am Schwebeplatz
      const start = Math.atan2(hover[2], hover[0]);
      const angle = start + 2 * Math.PI * smooth(u);
      const radius = Math.hypot(hover[0], hover[2]) + circleWiden * Math.sin(Math.PI * u);
      current = [
        radius * Math.cos(angle) - hover[0],
        circleRise * Math.sin(Math.PI * u),
        radius * Math.sin(angle) - hover[2],
      ];
      // Blick in Flugrichtung, in die Kurve gelegt
      result.rootRotation = [0, -((angle - start) * 180) / Math.PI, -25 * Math.sin(Math.PI * u)];
    } else if (state === "LANDING") {
      const u = clamp(t / LAND_SECONDS);
      const idleWeight = 1 - smoothRange(0, 0.25, u);
      const sitWeight = smoothRange(0.7, 1, u);
      add("idle", idleWeight);
      add("sit", sitWeight);
      add("fly", Math.max(0, 1 - idleWeight - sitWeight));
      lerpTo(sitOffset, smooth(u), 3 * Math.sin(Math.PI * u));
      result.rootRotation = [-12 * Math.sin(Math.PI * u), 0, 0];
    } else if (state === "SIT") {
      add("sit", 1);
      current = [...sitOffset];
    } else if (state === "TAKEOFF") {
      const u = clamp(t / TAKEOFF_SECONDS);
      const sitWeight = 1 - smoothRange(0, 0.3, u);
      const idleWeight = smoothRange(0.7, 1, u);
      add("sit", sitWeight);
      add("idle", idleWeight);
      add("fly", Math.max(0, 1 - idleWeight - sitWeight));
      lerpTo([0, 0, 0], smooth(u), 2 * Math.sin(Math.PI * u));
      result.rootRotation = [10 * Math.sin(Math.PI * u), 0, 0];
    } else {
      add("idle", 1);
      current = [0, 0, 0];
    }
    result.rootOffset = [...current];

    // Während der Runde steigen in festen Abständen Herzen über dem Kopf auf, drehen sich und verblassen
    if (hearts && now - heartsStart <= circleDuration + heartLife) {
      const count = Math.floor(circleDuration / heartInterval);
      for (let i = 0; i < count; i++) {
        const age = now - (heartsStart + i * heartInterval);
        if (age < 0 || age >= heartLife) continue;
        const life = age / heartLife;
        const index = heartSeed + i;
        const x = (hash(index, 11) - 0.5) * 2 * heartSpread + 0.6 * Math.sin(age * 4 + index);
        const z = (hash(index, 12) - 0.5) * 2 * heartSpread;
        const y = heartHeight + hash(index, 13) * 3 + heartRise * life;
        const size = heartSize * Math.min(1, age / 0.15) * (1 - life * life * life);
        const spin = age * 120 + 360 * hash(index, 14);
        result.hearts.push([x, y, z, size, spin]);
      }
    }
    return result;
  };
}

export type WingConfig = { openSpeed?: number; closeSpeed?: number; blend?: number };

// Flügel aus cosmetic.json ("wings"): angelegt ("closed"), solange der Spieler steht oder geht, ausgebreitet
// ("open"), sobald er schneller ist. Gleiche Regeln wie WingBehavior.java im Mod-Kern.
export function createWingBehavior(config: WingConfig) {
  const openSpeed = config.openSpeed ?? 5;
  const closeSpeed = config.closeSpeed ?? 4.4;
  const blend = Math.max(0.05, config.blend ?? 0.35);
  let open = false;
  let pendingSince = Number.NaN;
  let lastTime = Number.NaN;
  let weight = 0;

  return function pose(now: number, speed: number): Pose {
    const wanted = open ? speed > closeSpeed : speed > openSpeed;
    if (wanted === open) pendingSince = Number.NaN;
    else if (Number.isNaN(pendingSince)) pendingSince = now;
    else if (now - pendingSince >= (wanted ? 0.12 : 0.5)) {
      open = wanted;
      pendingSince = Number.NaN;
    }

    const delta = Number.isNaN(lastTime) ? 0 : Math.max(0, Math.min(0.1, now - lastTime));
    lastTime = now;
    weight = clamp(weight + ((open ? 1 : -1) * delta) / blend);
    const eased = weight * weight * (3 - 2 * weight);
    return { weights: { closed: 1 - eased, open: eased }, rootOffset: [0, 0, 0], rootRotation: [0, 0, 0], hearts: [] };
  };
}

export type CapeConfig = { bone: string; rest?: number; perSpeed?: number; max?: number; runSpeed?: number };

// Cape aus cosmetic.json ("cape"): hängt im Stand mit leichter Welle ("idle"), hebt sich mit dem Tempo nach
// hinten und flattert stärker ("run"). Gleiche Regeln wie CapeBehavior.java im Mod-Kern.
export function createCapeBehavior(config: CapeConfig) {
  const rest = config.rest ?? 6;
  const perSpeed = config.perSpeed ?? 9;
  const max = config.max ?? 70;
  const runSpeed = Math.max(0.1, config.runSpeed ?? 5.6);
  let smoothed = 0;
  let lastTime = Number.NaN;

  return function pose(now: number, speed: number): Pose {
    const delta = Number.isNaN(lastTime) ? 0 : Math.max(0, Math.min(0.2, now - lastTime));
    lastTime = now;
    smoothed += (speed - smoothed) * (1 - Math.exp(-delta / 0.25));
    const run = clamp(smoothed / runSpeed);
    // Negatives X hebt das untere Ende nach hinten (vom Rücken weg)
    return {
      weights: { idle: 1 - run, run },
      rootOffset: [0, 0, 0],
      rootRotation: [-Math.min(max, rest + perSpeed * smoothed), 0, 0],
      hearts: [],
    };
  };
}
