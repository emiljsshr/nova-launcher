import {
  AdditiveBlending,
  BufferGeometry,
  Float32BufferAttribute,
  FrontSide,
  Group,
  Mesh,
  MeshBasicMaterial,
  MeshStandardMaterial,
  NearestFilter,
  LinearFilter,
  SRGBColorSpace,
  TextureLoader,
  type Texture,
} from "three";
import {
  createCapeBehavior,
  createPetBehavior,
  createWingBehavior,
  hash,
  type CapeConfig,
  type PetConfig,
  type Pose,
  type WingConfig,
} from "./petBehavior";

// Lädt 3D-Cosmetics im Bedrock-Format (Blockbench-Export) aus public/cosmetics/<id>/
// und baut daraus eine three.js-Gruppe, die an den Körper von skinview3d gehängt wird.
// Die Mod rechnet dieselben Formeln in gg.nova.core.cosmetic.CosmeticModel nach.

type Vec3 = [number, number, number];
type Channel = (string | number)[] | Record<string, (string | number)[]>;

type FaceName = "north" | "east" | "south" | "west" | "up" | "down";
type FaceUv = { uv: [number, number]; uv_size: [number, number] };
type GeoCube = {
  origin: Vec3;
  size: Vec3;
  uv: [number, number] | Partial<Record<FaceName, FaceUv>>;
  pivot?: Vec3;
  rotation?: Vec3;
  mirror?: boolean;
  inflate?: number;
};
type GeoBone = { name: string; parent?: string; pivot?: Vec3; rotation?: Vec3; mirror?: boolean; cubes?: GeoCube[] };
type Geometry = {
  "minecraft:geometry": {
    description: { texture_width: number; texture_height: number };
    bones: GeoBone[];
  }[];
};
type AnimationFile = {
  animations: Record<
    string,
    {
      loop?: boolean;
      animation_length?: number;
      bones?: Record<string, { rotation?: Channel; position?: Channel; scale?: Channel | number }>;
    }
  >;
};

export type CosmeticManifest = {
  id: string;
  attach: "body" | "head" | "root";
  model: string;
  texture: string;
  glow?: { texture: string; intensity?: string | number };
  sparkles?: {
    count: number;
    min: Vec3;
    max: Vec3;
    size: number;
    rise: number;
    uv: [number, number, number, number];
  };
  animationFile?: string;
  // Rollenname ("idle", "fly", "sit") → Name in der Animationsdatei
  animations?: Record<string, string>;
  pet?: PetConfig;
  wings?: WingConfig;
  cape?: CapeConfig;
  emote?: { trigger?: string; duration?: number };
  trail?: { stride?: number; life?: number; spread?: number; width?: number; length?: number; uv: [number, number, number, number] };
};

export type CosmeticModel = {
  object: Group;
  // Woran das Modell in skinview3d hängt: Körper, Kopf oder die ganze Figur (Boden-Effekte)
  attach: "body" | "head" | "root";
  update: (seconds: number) => void;
  dispose: () => void;
};

/* ========================================
   MOLANG (nur das, was Animationen brauchen)
======================================== */

type Expr = (time: number) => number;

const DEG = Math.PI / 180;

function compileMolang(source: string): Expr {
  const tokens = source.toLowerCase().match(/\d+\.?\d*|[a-z_][a-z0-9_.]*|[-+*/()]/g) ?? [];
  let index = 0;

  const peek = () => tokens[index];
  const next = () => tokens[index++];

  function primary(): Expr {
    const token = next();
    if (token === undefined) throw new Error(`Molang unvollständig: ${source}`);
    if (token === "(") {
      const inner = sum();
      next();
      return inner;
    }
    if (token === "-") {
      const operand = primary();
      return (t) => -operand(t);
    }
    if (/^\d/.test(token)) {
      const value = Number(token);
      return () => value;
    }
    if (token === "query.anim_time" || token === "q.anim_time") return (t) => t;
    if (token === "math.pi") return () => Math.PI;

    const fn = {
      "math.sin": (x: number) => Math.sin(x * DEG),
      "math.cos": (x: number) => Math.cos(x * DEG),
      "math.abs": Math.abs,
    }[token];
    if (fn && peek() === "(") {
      next();
      const argument = sum();
      next();
      return (t) => fn(argument(t));
    }
    // Unbekannte Variablen sind in Molang 0
    return () => 0;
  }

  function product(): Expr {
    let left = primary();
    while (peek() === "*" || peek() === "/") {
      const op = next();
      const right = primary();
      const l = left;
      left = op === "*" ? (t) => l(t) * right(t) : (t) => l(t) / right(t);
    }
    return left;
  }

  function sum(): Expr {
    let left = product();
    while (peek() === "+" || peek() === "-") {
      const op = next();
      const right = product();
      const l = left;
      left = op === "+" ? (t) => l(t) + right(t) : (t) => l(t) - right(t);
    }
    return left;
  }

  return sum();
}

function compileValue(value: string | number): Expr {
  return typeof value === "number" ? () => value : compileMolang(value);
}

type Vec3Expr = (time: number) => Vec3;

function compileVec(values: (string | number)[]): Vec3Expr {
  const [x, y, z] = values.map(compileValue);
  return (t) => [x(t), y(t), z(t)];
}

// Fester Wert/Molang oder Keyframes { "0.0": [...], "1.0": [...] } mit linearer Interpolation
function compileChannel(channel: Channel): Vec3Expr {
  if (Array.isArray(channel)) return compileVec(channel);

  const frames = Object.entries(channel)
    .map(([time, values]) => ({ time: Number(time), value: compileVec(values) }))
    .sort((a, b) => a.time - b.time);

  return (t) => {
    const after = frames.findIndex((frame) => frame.time > t);
    if (after === -1) return frames[frames.length - 1].value(t);
    if (after === 0) return frames[0].value(t);
    const a = frames[after - 1];
    const b = frames[after];
    const k = (t - a.time) / (b.time - a.time);
    const va = a.value(t);
    const vb = b.value(t);
    return [0, 1, 2].map((i) => va[i] + (vb[i] - va[i]) * k) as Vec3;
  };
}

/* ========================================
   GEOMETRIE
======================================== */

const FACE_NAMES: FaceName[] = ["north", "east", "south", "west", "up", "down"];

// Ecken jeder Fläche, von außen gesehen: oben links, oben rechts, unten rechts, unten links
const FACE_CORNERS: Vec3[][] = [
  [[1, 1, 0], [0, 1, 0], [0, 0, 0], [1, 0, 0]],
  [[1, 1, 1], [1, 1, 0], [1, 0, 0], [1, 0, 1]],
  [[0, 1, 1], [1, 1, 1], [1, 0, 1], [0, 0, 1]],
  [[0, 1, 0], [0, 1, 1], [0, 0, 1], [0, 0, 0]],
  [[0, 1, 0], [1, 1, 0], [1, 1, 1], [0, 1, 1]],
  [[1, 0, 0], [0, 0, 0], [0, 0, 1], [1, 0, 1]],
];
// Achsen, die eine Fläche aufspannt – hat eine davon Größe 0, ist die Fläche leer
const FACE_AXES = [[0, 1], [2, 1], [0, 1], [2, 1], [0, 2], [0, 2]];

type UvRect = [number, number, number, number] | null; // u links, u rechts, v oben, v unten (Texturpixel)

function faceRects(cube: GeoCube): UvRect[] {
  if (!Array.isArray(cube.uv)) {
    const faces = cube.uv;
    return FACE_NAMES.map((name, face) => {
      const source = faces[name];
      const [a, b] = FACE_AXES[face];
      if (!source || cube.size[a] === 0 || cube.size[b] === 0) return null;
      const [u, v] = source.uv;
      const [w, h] = source.uv_size;
      return [u, u + w, v, v + h];
    });
  }

  // Box-UV: Ausschnitt (u, v, Breite, Höhe) jeder Fläche relativ zur UV-Ecke
  const [w, h, d] = cube.size.map(Math.floor);
  const [u0, v0] = cube.uv;
  const rects = [
    [d, d, w, h],
    [0, d, d, h],
    [2 * d + w, d, w, h],
    [d + w, d, d, h],
    [d, 0, w, d],
    [d + w, 0, w, d],
  ];
  return rects.map(([du, dv, fw, fh]) => {
    if (fw === 0 || fh === 0) return null;
    const left = u0 + du;
    const right = left + fw;
    return cube.mirror ? [right, left, v0 + dv, v0 + dv + fh] : [left, right, v0 + dv, v0 + dv + fh];
  });
}

// Würfel relativ zu origin (Knochen- bzw. Würfel-Drehpunkt)
function cubeGeometry(from: Vec3, to: Vec3, rects: UvRect[], textureWidth: number, textureHeight: number) {
  const positions: number[] = [];
  const uvs: number[] = [];

  rects.forEach((rect, face) => {
    if (!rect) return;
    const [uL, uR, top, bottom] = rect;
    const corners = FACE_CORNERS[face].map((corner) => corner.map((c, i) => (c ? to[i] : from[i])));
    const faceUv = [
      [uL, top],
      [uR, top],
      [uR, bottom],
      [uL, bottom],
    ].map(([u, v]) => [u / textureWidth, 1 - v / textureHeight]);

    // Gegen den Uhrzeigersinn von außen: oben links, unten links, unten rechts / oben links, unten rechts, oben rechts
    for (const i of [0, 3, 2, 0, 2, 1]) {
      positions.push(...corners[i]);
      uvs.push(...faceUv[i]);
    }
  });

  const geometry = new BufferGeometry();
  geometry.setAttribute("position", new Float32BufferAttribute(positions, 3));
  geometry.setAttribute("uv", new Float32BufferAttribute(uvs, 2));
  geometry.computeVertexNormals();
  return geometry;
}

/* ========================================
   LADEN
======================================== */

const BASE = "/cosmetics";

async function fetchJson<T>(url: string): Promise<T> {
  const response = await fetch(url);
  if (!response.ok) throw new Error(`${url}: HTTP ${response.status}`);
  return response.json() as Promise<T>;
}

async function loadTexture(url: string, smooth: boolean) {
  const texture = (await new TextureLoader().loadAsync(url)) as Texture;
  // Pixel-Cosmetics scharf, HD-Texturen geglättet
  texture.magFilter = smooth ? LinearFilter : NearestFilter;
  texture.minFilter = smooth ? LinearFilter : NearestFilter;
  texture.colorSpace = SRGBColorSpace;
  return texture;
}

export async function loadCosmeticModel(id: string): Promise<CosmeticModel> {
  const base = `${BASE}/${id}`;
  const manifest = await fetchJson<CosmeticManifest>(`${base}/cosmetic.json`);
  const geometryFile = await fetchJson<Geometry>(`${base}/${manifest.model}`);
  const { description, bones } = geometryFile["minecraft:geometry"][0];
  const smooth = description.texture_width >= 256;

  const [animationFile, texture, glowTexture] = await Promise.all([
    manifest.animationFile
      ? fetchJson<AnimationFile>(`${base}/${manifest.animationFile}`)
      : Promise.resolve(null),
    loadTexture(`${base}/${manifest.texture}`, smooth),
    manifest.glow ? loadTexture(`${base}/${manifest.glow.texture}`, smooth) : Promise.resolve(null),
  ]);

  // Jede Fläche nur von außen sichtbar – Vorder- und Rückseite dünner Flächen sind eigene Flächen
  const material = new MeshStandardMaterial({ map: texture, side: FrontSide, alphaTest: 0.5 });
  const glowMaterial = glowTexture
    ? new MeshBasicMaterial({
        map: glowTexture,
        side: FrontSide,
        blending: AdditiveBlending,
        transparent: true,
        depthWrite: false,
      })
    : null;

  const groups = new Map<string, { group: Group; pivot: Vec3; rest: Vec3 }>();
  const geometries: BufferGeometry[] = [];

  function addMeshes(target: Group, geometry: BufferGeometry) {
    geometries.push(geometry);
    target.add(new Mesh(geometry, material));
    if (glowMaterial) target.add(new Mesh(geometry, glowMaterial));
  }

  // Bedrock: y nach oben, Rücken bei +Z, Einheit Pixel. skinview3d: Rücken bei -Z, Ursprung des Körpers in
  // dessen Mitte (Bedrock-Höhe 18), Ursprung des Kopfes am Hals (Höhe 24).
  // Die Spiegelung an Z liegt auf der Wurzel, Knochen bleiben in Bedrock-Koordinaten.
  const attach = manifest.attach === "head" || manifest.attach === "root" ? manifest.attach : "body";
  const root = new Group();
  root.scale.set(1, 1, -1);
  // Ursprung der Figur liegt 16 Pixel über den Füßen
  root.position.y = attach === "head" ? -24 : attach === "root" ? -16 : -18;

  for (const bone of bones) {
    const pivot = bone.pivot ?? [0, 0, 0];
    const parent = bone.parent ? groups.get(bone.parent) : undefined;
    const group = new Group();
    group.name = bone.name;
    group.rotation.order = "ZYX";

    const parentPivot = parent?.pivot ?? [0, 0, 0];
    group.position.set(pivot[0] - parentPivot[0], pivot[1] - parentPivot[1], pivot[2] - parentPivot[2]);
    const rest = (bone.rotation ?? [0, 0, 0]) as Vec3;
    group.rotation.set(rest[0] * DEG, rest[1] * DEG, rest[2] * DEG);

    for (const source of bone.cubes ?? []) {
      const cube = { ...source, mirror: source.mirror ?? bone.mirror };
      const inflate = cube.inflate ?? 0;
      const rotation = cube.rotation ?? [0, 0, 0];
      const rotated = rotation.some((value) => value !== 0);
      // Gedrehte Würfel hängen in einer eigenen Gruppe am Würfel-Drehpunkt
      const origin = rotated ? (cube.pivot ?? pivot) : pivot;
      const from = cube.origin.map((value, i) => value - inflate - origin[i]) as Vec3;
      const to = cube.origin.map((value, i) => value + cube.size[i] + inflate - origin[i]) as Vec3;
      const geometry = cubeGeometry(from, to, faceRects(cube), description.texture_width, description.texture_height);

      if (rotated) {
        const holder = new Group();
        holder.rotation.order = "ZYX";
        holder.position.set(origin[0] - pivot[0], origin[1] - pivot[1], origin[2] - pivot[2]);
        holder.rotation.set(rotation[0] * DEG, rotation[1] * DEG, rotation[2] * DEG);
        addMeshes(holder, geometry);
        group.add(holder);
      } else {
        addMeshes(group, geometry);
      }
    }

    (parent?.group ?? root).add(group);
    groups.set(bone.name, { group, pivot, rest });
  }

  // Eiskristall-Partikel: Würfel mit weichem Lichtpunkt, steigen auf und funkeln
  const sparkleConfig = manifest.sparkles;
  const sparkles: Mesh[] = [];
  if (sparkleConfig && glowMaterial) {
    const [u, v, w, h] = sparkleConfig.uv;
    const rects = FACE_NAMES.map((): UvRect => [u, u + w, v, v + h]);
    const geometry = cubeGeometry([-0.5, -0.5, -0.5], [0.5, 0.5, 0.5], rects, description.texture_width, description.texture_height);
    geometries.push(geometry);
    for (let i = 0; i < sparkleConfig.count; i++) {
      const mesh = new Mesh(geometry, glowMaterial);
      sparkles.push(mesh);
      root.add(mesh);
    }
  }

  // Herzen eines Pets: dünne Flächen mit Vorder- und Rückseite, drehen sich um die Hochachse
  const heartConfig = manifest.pet?.hearts;
  const hearts: Group[] = [];
  if (heartConfig) {
    const [u, v, w, h] = heartConfig.uv;
    const rects: UvRect[] = [[u, u + w, v, v + h], null, [u, u + w, v, v + h], null, null, null];
    const geometry = cubeGeometry([-0.5, -0.5, 0], [0.5, 0.5, 0], rects, description.texture_width, description.texture_height);
    geometries.push(geometry);
    for (let i = 0; i < 40; i++) {
      const holder = new Group();
      holder.add(new Mesh(geometry, material));
      holder.visible = false;
      hearts.push(holder);
      root.add(holder);
    }
  }

  // Animationen unter ihrem Rollennamen
  const animations = Object.entries(manifest.animations ?? {}).flatMap(([role, name]) => {
    const animation = animationFile?.animations[name];
    if (!animation) return [];
    const channels = Object.entries(animation.bones ?? {}).flatMap(([bone, channel]) =>
      groups.has(bone)
        ? [{
            bone,
            rotation: channel.rotation ? compileChannel(channel.rotation) : null,
            position: channel.position ? compileChannel(channel.position) : null,
            scale: channel.scale === undefined ? null
              : typeof channel.scale === "number" ? compileVec([channel.scale, channel.scale, channel.scale])
              : compileChannel(channel.scale),
          }]
        : [],
    );
    return [{ role, length: animation.animation_length ?? 0, channels }];
  });
  const idleLength = animations.find((animation) => animation.role === "idle")?.length ?? 0;
  const glowIntensity = compileValue(manifest.glow?.intensity ?? 1);

  const pet = manifest.pet;
  const petPose = pet && groups.has(pet.root) ? createPetBehavior(pet, groups.get(pet.root)!.pivot) : null;
  const wingPose = manifest.wings ? createWingBehavior(manifest.wings) : null;
  const capePose = manifest.cape ? createCapeBehavior(manifest.cape) : null;
  // Knochen, den das Verhalten dreht und verschiebt: Wurzel eines Pets oder oberster Streifen eines Capes
  const rootBone = pet?.root ?? manifest.cape?.bone;

  // Spuren: Pool waagerechter Abdrücke (Ober- und Unterseite)
  const trail = manifest.trail;
  const prints: Group[] = [];
  if (trail) {
    const [u, v, w, h] = trail.uv;
    const rects: UvRect[] = [null, null, null, null, [u, u + w, v, v + h], [u, u + w, v, v + h]];
    const geometry = cubeGeometry([-0.5, 0, -0.5], [0.5, 0, 0.5], rects, description.texture_width, description.texture_height);
    geometries.push(geometry);
    for (let i = 0; i < 24; i++) {
      const holder = new Group();
      holder.add(new Mesh(geometry, material));
      if (glowMaterial) holder.add(new Mesh(geometry, glowMaterial));
      holder.visible = false;
      prints.push(holder);
      root.add(holder);
    }
  }

  function update(seconds: number) {
    // In der Vorschau bewegt sich der Spieler nie: stillstehend seit dem Öffnen. Flügel wechseln zur
    // Ansicht alle 4 Sekunden zwischen angelegt und offen (wie beim Sprinten), Capes zwischen Stehen und Laufen.
    const demoSpeed = Math.floor(seconds / 4) % 2 === 1 ? 6 : 0;
    const pose: Pose = petPose
      ? petPose(seconds, seconds)
      : wingPose
        ? wingPose(seconds, demoSpeed)
        : capePose
          ? capePose(seconds, demoSpeed)
          : { weights: { idle: 1 }, rootOffset: [0, 0, 0], rootRotation: [0, 0, 0], hearts: [] };

    // Emote: in der Vorschau alle 3 Sekunden auslösen, dazwischen unsichtbar
    if (manifest.emote) {
      const age = seconds % 3;
      const visible = age <= (manifest.emote.duration ?? 1);
      root.visible = visible;
      pose.weights = { burst: 1 };
      pose.time = age;
    }

    // Spuren: als liefe die Figur mit 4,3 Blöcken pro Sekunde geradeaus – Abdrücke wandern nach hinten
    if (trail) {
      const stride = trail.stride ?? 0.8;
      const life = trail.life ?? 3;
      const interval = stride / 4.3;
      const newest = Math.floor(seconds / interval);
      prints.forEach((holder, slot) => {
        const index = newest - slot;
        const age = seconds - index * interval;
        const shrink = Math.min(1, (1 - age / life) / 0.3);
        holder.visible = index >= 0 && shrink > 0.02;
        if (!holder.visible) return;
        const side = index % 2 === 0 ? 1 : -1;
        holder.position.set(side * (trail.spread ?? 0.14) * 16, 0.15, age * 4.3 * 16);
        holder.scale.set((trail.width ?? 3) * shrink, 1, (trail.length ?? 5) * shrink);
      });
    }

    // Überblenden: Beiträge aller Animationen nach Gewicht addieren
    const rotations = new Map<string, Vec3>();
    const offsets = new Map<string, Vec3>();
    const accumulate = (map: Map<string, Vec3>, bone: string, value: Vec3, weight: number) => {
      const sum = map.get(bone) ?? [0, 0, 0];
      map.set(bone, [sum[0] + value[0] * weight, sum[1] + value[1] * weight, sum[2] + value[2] * weight]);
    };
    const scales = new Map<string, Vec3>();
    const clock = pose.time ?? seconds;
    for (const animation of animations) {
      const weight = pose.weights[animation.role] ?? 0;
      if (weight <= 0) continue;
      const time = animation.length > 0 && pose.time === undefined ? clock % animation.length : clock;
      for (const channel of animation.channels) {
        if (channel.rotation) accumulate(rotations, channel.bone, channel.rotation(time), weight);
        if (channel.position) accumulate(offsets, channel.bone, channel.position(time), weight);
        if (channel.scale) {
          const value = channel.scale(time);
          const current = scales.get(channel.bone) ?? [1, 1, 1];
          scales.set(channel.bone, [0, 1, 2].map((i) => current[i] * (1 + (value[i] - 1) * weight)) as Vec3);
        }
      }
    }
    if (rootBone) {
      accumulate(rotations, rootBone, pose.rootRotation, 1);
      accumulate(offsets, rootBone, pose.rootOffset, 1);
    }

    for (const [name, { group, pivot, rest }] of groups) {
      const [x, y, z] = rotations.get(name) ?? [0, 0, 0];
      group.rotation.set((rest[0] + x) * DEG, (rest[1] + y) * DEG, (rest[2] + z) * DEG);
      const parentPivot = groups.get(group.parent?.name ?? "")?.pivot ?? [0, 0, 0];
      const [ox, oy, oz] = offsets.get(name) ?? [0, 0, 0];
      group.position.set(pivot[0] - parentPivot[0] + ox, pivot[1] - parentPivot[1] + oy, pivot[2] - parentPivot[2] + oz);
      const [sx, sy, sz] = scales.get(name) ?? [1, 1, 1];
      group.scale.set(sx, sy, sz);
    }

    hearts.forEach((holder, i) => {
      const heart = pose.hearts[i];
      holder.visible = Boolean(heart);
      if (!heart) return;
      const [x, y, z, size, spin] = heart;
      holder.position.set(x, y, z);
      holder.rotation.set(0, spin * DEG, 0);
      holder.scale.setScalar(Math.max(0.001, size));
    });

    if (glowMaterial) {
      const time = idleLength > 0 ? seconds % idleLength : seconds;
      glowMaterial.color.setScalar(Math.min(1, Math.max(0, glowIntensity(time))));
    }

    if (sparkleConfig) {
      const { min, max, size, rise } = sparkleConfig;
      const height = max[1] - min[1];
      sparkles.forEach((mesh, i) => {
        const x = min[0] + (max[0] - min[0]) * hash(i, 1);
        const z = min[2] + (max[2] - min[2]) * hash(i, 3);
        const speed = rise * (0.5 + hash(i, 4));
        const y = min[1] + ((hash(i, 2) * height + seconds * speed) % height);
        const edge = Math.min(1, Math.min(y - min[1], max[1] - y) / (height * 0.15));
        const twinkle = 0.55 + 0.45 * Math.sin(seconds * (2 + hash(i, 5) * 3) + hash(i, 6) * 6.283);
        mesh.position.set(x, y, z);
        mesh.scale.setScalar(Math.max(0.001, size * twinkle * edge));
      });
    }
  }

  update(0);

  return {
    object: root,
    attach,
    update,
    dispose() {
      geometries.forEach((geometry) => geometry.dispose());
      material.dispose();
      glowMaterial?.dispose();
      texture.dispose();
      glowTexture?.dispose();
    },
  };
}
