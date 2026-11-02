package gg.nova.core.cosmetic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Bedrock-Geometrie (Blockbench-Export) mit Animation, Leucht-Ebene und Partikeln, versionsunabhängig.
 * Rechnet Knochen und Würfel in Vierecke um und gibt sie im Körperraum des Spielermodells aus
 * (Blöcke, y nach unten, Rücken bei +Z, Ursprung am Hals). Die Versionsschicht legt nur noch die
 * Körpertransformation davor und schreibt die Ecken in ihren VertexConsumer.
 * Gleiche Regeln wie src/components/cosmetics/bedrockModel.ts im Launcher.
 */
public final class CosmeticModel {
	/** Empfängt die Ecken: je vier hintereinander bilden ein Viereck, gegen den Uhrzeigersinn von außen. */
	public interface QuadSink {
		void vertex(float x, float y, float z, float u, float v, float nx, float ny, float nz);
	}

	/** BASE: Textur mit Licht (Cutout). GLOW: Leucht-Textur additiv und ohne Schatten, dazu die Partikel. */
	public enum Pass {
		BASE, GLOW
	}

	/** Bedrock-Höhe des Halses (Oberkante des Körpers) in Pixeln. */
	private static final float NECK_Y = 24.0F;

	private static final class Cube {
		final float[] from = new float[3];
		final float[] to = new float[3];
		final float[] pivot = new float[3];
		final float[] rotation = new float[3];
		/** Je Fläche (north, east, south, west, up, down): u links, u rechts, v oben, v unten – oder null. */
		final float[][] uv = new float[6][];
	}

	private static final class Bone {
		final String name;
		final float[] pivot;
		final float[] rest;
		Bone parent;
		final List<Cube> cubes = new ArrayList<Cube>();

		Bone(String name, float[] pivot, float[] rest) {
			this.name = name;
			this.pivot = pivot;
			this.rest = rest;
		}
	}

	/** Fester Wert, Molang oder Keyframes mit linearer Interpolation. */
	private static final class Channel {
		final float[] times;
		final Molang.Expr[][] values;

		Channel(Object source) {
			Map<String, Object> frames = Json.object(source);
			if (frames.isEmpty()) {
				times = new float[] {0.0F};
				values = new Molang.Expr[][] {vector(source)};
				return;
			}
			TreeMap<Float, Object> sorted = new TreeMap<Float, Object>();
			for (Map.Entry<String, Object> entry : frames.entrySet()) {
				sorted.put(Float.valueOf(entry.getKey()), entry.getValue());
			}
			times = new float[sorted.size()];
			values = new Molang.Expr[sorted.size()][];
			int i = 0;
			for (Map.Entry<Float, Object> entry : sorted.entrySet()) {
				times[i] = entry.getKey();
				values[i] = vector(entry.getValue());
				i++;
			}
		}

		private static Molang.Expr[] vector(Object source) {
			Molang.Expr[] result = new Molang.Expr[3];
			if (!(source instanceof List)) {
				Molang.Expr single = Molang.compile(source);
				result[0] = result[1] = result[2] = single;
				return result;
			}
			List<Object> list = Json.list(source);
			for (int i = 0; i < 3; i++) {
				result[i] = Molang.compile(i < list.size() ? list.get(i) : Float.valueOf(0.0F));
			}
			return result;
		}

		void eval(float time, float[] out) {
			int after = 0;
			while (after < times.length && times[after] <= time) {
				after++;
			}
			if (after == 0 || after == times.length) {
				Molang.Expr[] frame = values[after == 0 ? 0 : times.length - 1];
				for (int i = 0; i < 3; i++) {
					out[i] = frame[i].eval(time);
				}
				return;
			}
			float k = (time - times[after - 1]) / (times[after] - times[after - 1]);
			for (int i = 0; i < 3; i++) {
				float a = values[after - 1][i].eval(time);
				float b = values[after][i].eval(time);
				out[i] = a + (b - a) * k;
			}
		}
	}

	/** Eine Animation aus der Animationsdatei: Länge und Kanäle je Knochen. */
	private static final class Animation {
		float length;
		final Map<Bone, Channel> rotation = new HashMap<Bone, Channel>();
		final Map<Bone, Channel> position = new HashMap<Bone, Channel>();
		final Map<Bone, Channel> scale = new HashMap<Bone, Channel>();
	}

	/**
	 * Was gerade gezeigt wird: mehrere Animationen mit Gewichten (Überblenden), dazu eine Verschiebung und
	 * Drehung des Wurzelknochens und Herzen über dem Spieler. Für Pets füllt das {@link PetBehavior}.
	 */
	public static final class Pose {
		final List<String> animations = new ArrayList<String>();
		final List<Float> weights = new ArrayList<Float>();
		final float[] rootOffset = new float[3];
		final float[] rootRotation = new float[3];
		/** Je Herz: x, y, z (Bedrock-Pixel), Größe, Drehung um die Hochachse in Grad. */
		final List<float[]> hearts = new ArrayList<float[]>();
		/** Animationszeit für alle Animationen dieser Pose (z. B. seit einem Auslöser) statt der Uhrzeit, oder NaN. */
		float time = Float.NaN;

		void add(String animation, float weight) {
			if (weight > 0.001F) {
				animations.add(animation);
				weights.add(weight);
			}
		}
	}

	/** Aufsteigende, funkelnde Lichtpunkte in einem Quader um das Cosmetic (nur im GLOW-Durchgang). */
	private static final class Sparkles {
		int count;
		float[] min;
		float[] max;
		float size;
		float rise;
		float[] uv;
	}

	private static final String[] FACE_NAMES = {"north", "east", "south", "west", "up", "down"};

	// Ecken jeder Fläche von außen gesehen (oben links, oben rechts, unten rechts, unten links),
	// Reihenfolge north, east, south, west, up, down – wie im Launcher
	private static final int[][][] CORNERS = {
		{{1, 1, 0}, {0, 1, 0}, {0, 0, 0}, {1, 0, 0}},
		{{1, 1, 1}, {1, 1, 0}, {1, 0, 0}, {1, 0, 1}},
		{{0, 1, 1}, {1, 1, 1}, {1, 0, 1}, {0, 0, 1}},
		{{0, 1, 0}, {0, 1, 1}, {0, 0, 1}, {0, 0, 0}},
		{{0, 1, 0}, {1, 1, 0}, {1, 1, 1}, {0, 1, 1}},
		{{1, 0, 0}, {0, 0, 0}, {0, 0, 1}, {1, 0, 1}},
	};
	private static final float[][] NORMALS = {
		{0, 0, -1}, {1, 0, 0}, {0, 0, 1}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0},
	};
	// Welche Achsen eine Fläche aufspannt – fehlt eine (Größe 0), ist die Fläche leer
	private static final int[][] FACE_AXES = {{0, 1}, {2, 1}, {0, 1}, {2, 1}, {0, 2}, {0, 2}};

	private final List<Bone> bones = new ArrayList<Bone>();
	private final Map<String, Animation> animations;
	private final Molang.Expr glow;
	private final Sparkles sparkles;
	/** Knochen, den ein Pet-Verhalten verschiebt und dreht (oder null). */
	final String rootBone;
	/** Texturausschnitt der Herzen (u links, u rechts, v oben, v unten) oder null. */
	private final float[] heartUv;
	private float textureWidth = 64.0F;
	private float textureHeight = 64.0F;

	private CosmeticModel(List<Bone> bones, Map<String, Animation> animations, Molang.Expr glow, Sparkles sparkles,
			String rootBone, float[] heartUv) {
		this.bones.addAll(bones);
		this.animations = animations;
		this.glow = glow;
		this.sparkles = sparkles;
		this.rootBone = rootBone;
		this.heartUv = heartUv;
	}

	static CosmeticModel parse(String geometryJson, String animationJson, Map<String, Object> manifest) {
		Map<String, Object> geometry = Json.object(
			Json.list(Json.object(Json.parse(geometryJson)).get("minecraft:geometry")).get(0));
		Map<String, Object> description = Json.object(geometry.get("description"));
		float textureWidth = Json.number(description.get("texture_width"), 64.0F);
		float textureHeight = Json.number(description.get("texture_height"), 64.0F);

		List<Bone> bones = new ArrayList<Bone>();
		Map<String, Bone> byName = new HashMap<String, Bone>();
		for (Object entry : Json.list(geometry.get("bones"))) {
			Map<String, Object> source = Json.object(entry);
			Bone bone = new Bone(
				String.valueOf(source.get("name")),
				Json.vec3(source.get("pivot"), 0.0F),
				Json.vec3(source.get("rotation"), 0.0F));
			bone.parent = byName.get(String.valueOf(source.get("parent")));
			boolean boneMirror = Boolean.TRUE.equals(source.get("mirror"));
			for (Object cubeEntry : Json.list(source.get("cubes"))) {
				bone.cubes.add(cube(Json.object(cubeEntry), boneMirror, textureWidth, textureHeight));
			}
			bones.add(bone);
			byName.put(bone.name, bone);
		}

		// Animationen unter ihrem Rollennamen aus cosmetic.json ("idle", "fly", "sit" …)
		Map<String, Animation> animations = new HashMap<String, Animation>();
		Map<String, Object> animationFile = animationJson != null
			? Json.object(Json.object(Json.parse(animationJson)).get("animations"))
			: new HashMap<String, Object>();
		for (Map.Entry<String, Object> role : Json.object(manifest.get("animations")).entrySet()) {
			Map<String, Object> source = Json.object(animationFile.get(String.valueOf(role.getValue())));
			if (source.isEmpty()) {
				continue;
			}
			Animation animation = new Animation();
			animation.length = Json.number(source.get("animation_length"), 0.0F);
			for (Map.Entry<String, Object> entry : Json.object(source.get("bones")).entrySet()) {
				Bone bone = byName.get(entry.getKey());
				if (bone == null) {
					continue;
				}
				Map<String, Object> channels = Json.object(entry.getValue());
				if (channels.containsKey("rotation")) {
					animation.rotation.put(bone, new Channel(channels.get("rotation")));
				}
				if (channels.containsKey("position")) {
					animation.position.put(bone, new Channel(channels.get("position")));
				}
				if (channels.containsKey("scale")) {
					animation.scale.put(bone, new Channel(channels.get("scale")));
				}
			}
			animations.put(role.getKey(), animation);
		}

		Map<String, Object> pet = Json.object(manifest.get("pet"));
		// Pets drehen und verschieben ihren Wurzelknochen, Capes heben ihren obersten Streifen
		Map<String, Object> cape = Json.object(manifest.get("cape"));
		String rootBone = pet.containsKey("root") ? String.valueOf(pet.get("root"))
			: cape.containsKey("bone") ? String.valueOf(cape.get("bone")) : null;
		float[] heartUv = null;
		List<Object> heartSource = Json.list(Json.object(pet.get("hearts")).get("uv"));
		if (heartSource.size() == 4) {
			float u = Json.number(heartSource.get(0), 0), v = Json.number(heartSource.get(1), 0);
			float w = Json.number(heartSource.get(2), 0), h = Json.number(heartSource.get(3), 0);
			heartUv = new float[] {u / textureWidth, (u + w) / textureWidth, v / textureHeight, (v + h) / textureHeight};
		}

		Map<String, Object> glowSource = Json.object(manifest.get("glow"));
		Molang.Expr glow = Molang.compile(glowSource.containsKey("intensity") ? glowSource.get("intensity") : Float.valueOf(1.0F));

		Sparkles sparkles = null;
		Map<String, Object> sparkleSource = Json.object(manifest.get("sparkles"));
		if (!sparkleSource.isEmpty()) {
			sparkles = new Sparkles();
			sparkles.count = (int) Json.number(sparkleSource.get("count"), 0.0F);
			sparkles.min = Json.vec3(sparkleSource.get("min"), 0.0F);
			sparkles.max = Json.vec3(sparkleSource.get("max"), 0.0F);
			sparkles.size = Json.number(sparkleSource.get("size"), 0.5F);
			sparkles.rise = Json.number(sparkleSource.get("rise"), 4.0F);
			List<Object> uv = Json.list(sparkleSource.get("uv"));
			sparkles.uv = new float[] {
				Json.number(uv.get(0), 0) / textureWidth,
				(Json.number(uv.get(0), 0) + Json.number(uv.get(2), 0)) / textureWidth,
				Json.number(uv.get(1), 0) / textureHeight,
				(Json.number(uv.get(1), 0) + Json.number(uv.get(3), 0)) / textureHeight,
			};
		}
		CosmeticModel model = new CosmeticModel(bones, animations, glow, sparkles, rootBone, heartUv);
		model.textureWidth = textureWidth;
		model.textureHeight = textureHeight;
		return model;
	}

	private static Cube cube(Map<String, Object> source, boolean boneMirror, float textureWidth, float textureHeight) {
		float[] origin = Json.vec3(source.get("origin"), 0.0F);
		float[] size = Json.vec3(source.get("size"), 0.0F);
		float inflate = Json.number(source.get("inflate"), 0.0F);
		Object mirrorValue = source.get("mirror");
		boolean mirror = mirrorValue instanceof Boolean ? (Boolean) mirrorValue : boneMirror;

		Cube cube = new Cube();
		for (int i = 0; i < 3; i++) {
			cube.from[i] = origin[i] - inflate;
			cube.to[i] = origin[i] + size[i] + inflate;
		}
		float[] pivot = Json.vec3(source.get("pivot"), Float.NaN);
		for (int i = 0; i < 3; i++) {
			cube.pivot[i] = Float.isNaN(pivot[i]) ? origin[i] + size[i] / 2.0F : pivot[i];
		}
		System.arraycopy(Json.vec3(source.get("rotation"), 0.0F), 0, cube.rotation, 0, 3);

		Object uvSource = source.get("uv");
		if (uvSource instanceof Map) {
			// Flächen-UV: { "north": { "uv": [u, v], "uv_size": [w, h] }, … } in Texturpixeln
			Map<String, Object> faces = Json.object(uvSource);
			for (int face = 0; face < 6; face++) {
				Map<String, Object> faceSource = Json.object(faces.get(FACE_NAMES[face]));
				if (faceSource.isEmpty() || size[FACE_AXES[face][0]] == 0 || size[FACE_AXES[face][1]] == 0) {
					continue;
				}
				List<Object> uv = Json.list(faceSource.get("uv"));
				List<Object> uvSize = Json.list(faceSource.get("uv_size"));
				float u = Json.number(uv.get(0), 0);
				float v = Json.number(uv.get(1), 0);
				float w = Json.number(uvSize.get(0), 0);
				float h = Json.number(uvSize.get(1), 0);
				cube.uv[face] = new float[] {
					u / textureWidth, (u + w) / textureWidth, v / textureHeight, (v + h) / textureHeight,
				};
			}
			return cube;
		}

		float[] uv = Json.vec3(uvSource, 0.0F);
		float w = (float) Math.floor(size[0]);
		float h = (float) Math.floor(size[1]);
		float d = (float) Math.floor(size[2]);
		// Box-UV: Ausschnitt (u, v, Breite, Höhe) jeder Fläche relativ zur UV-Ecke
		float[][] rects = {
			{d, d, w, h},
			{0, d, d, h},
			{2 * d + w, d, w, h},
			{d + w, d, d, h},
			{d, 0, w, d},
			{d + w, 0, w, d},
		};
		for (int face = 0; face < 6; face++) {
			float[] rect = rects[face];
			if (rect[2] == 0 || rect[3] == 0) {
				continue;
			}
			float left = uv[0] + rect[0];
			float right = left + rect[2];
			float top = uv[1] + rect[1];
			float bottom = top + rect[3];
			if (mirror) {
				float swap = left;
				left = right;
				right = swap;
			}
			cube.uv[face] = new float[] {
				left / textureWidth, right / textureWidth, top / textureHeight, bottom / textureHeight,
			};
		}
		return cube;
	}

	/** Drehpunkt eines Knochens (Bedrock-Pixel) oder null. */
	float[] pivot(String name) {
		for (Bone bone : bones) {
			if (bone.name.equals(name)) {
				return bone.pivot.clone();
			}
		}
		return null;
	}

	/** Helligkeit der Leucht-Ebene (0–1) zum Zeitpunkt {@code seconds} – der „Puls“. */
	public float glowIntensity(float seconds) {
		float value = glow.eval(loopTime("idle", seconds));
		return Math.max(0.0F, Math.min(1.0F, value));
	}

	private float loopTime(String role, float seconds) {
		Animation animation = animations.get(role);
		return animation != null && animation.length > 0 ? seconds % animation.length : seconds;
	}

	/** Zeichnet einen Durchgang des Modells mit der Animation "idle" zum Zeitpunkt {@code seconds}. */
	public void render(QuadSink sink, float seconds, Pass pass) {
		Pose pose = new Pose();
		pose.add("idle", 1.0F);
		render(sink, seconds, pass, pose);
	}

	/** Zeichnet einen Durchgang des Modells in einer {@link Pose}. */
	public void render(QuadSink sink, float seconds, Pass pass, Pose pose) {
		Map<Bone, float[]> matrices = new HashMap<Bone, float[]>();
		float[] rotation = new float[3];
		float[] offset = new float[3];
		float[] scale = new float[3];
		float[] animated = new float[3];
		if (!Float.isNaN(pose.time)) {
			seconds = pose.time;
		}

		for (Bone bone : bones) {
			scale[0] = scale[1] = scale[2] = 1.0F;
			rotation[0] = bone.rest[0];
			rotation[1] = bone.rest[1];
			rotation[2] = bone.rest[2];
			offset[0] = offset[1] = offset[2] = 0.0F;
			// Überblenden: Beiträge aller Animationen nach Gewicht addieren
			for (int a = 0; a < pose.animations.size(); a++) {
				Animation animation = animations.get(pose.animations.get(a));
				if (animation == null) {
					continue;
				}
				float weight = pose.weights.get(a);
				float time = animation.length > 0 ? seconds % animation.length : seconds;
				Channel channel = animation.rotation.get(bone);
				if (channel != null) {
					channel.eval(time, animated);
					for (int i = 0; i < 3; i++) {
						rotation[i] += animated[i] * weight;
					}
				}
				channel = animation.position.get(bone);
				if (channel != null) {
					channel.eval(time, animated);
					for (int i = 0; i < 3; i++) {
						offset[i] += animated[i] * weight;
					}
				}
				channel = animation.scale.get(bone);
				if (channel != null) {
					channel.eval(time, animated);
					for (int i = 0; i < 3; i++) {
						scale[i] *= 1.0F + (animated[i] - 1.0F) * weight;
					}
				}
			}
			if (bone.name.equals(rootBone)) {
				for (int i = 0; i < 3; i++) {
					rotation[i] += pose.rootRotation[i];
					offset[i] += pose.rootOffset[i];
				}
			}

			// M = Eltern · T(pivot + offset) · Rz · Ry · Rx · S · T(-pivot)
			float[] matrix = bone.parent != null && matrices.containsKey(bone.parent)
				? matrices.get(bone.parent).clone()
				: Mat.identity();
			Mat.translate(matrix, bone.pivot[0] + offset[0], bone.pivot[1] + offset[1], bone.pivot[2] + offset[2]);
			Mat.rotate(matrix, rotation);
			Mat.scale(matrix, scale);
			Mat.translate(matrix, -bone.pivot[0], -bone.pivot[1], -bone.pivot[2]);
			matrices.put(bone, matrix);

			for (Cube cube : bone.cubes) {
				float[] cubeMatrix = matrix;
				if (cube.rotation[0] != 0 || cube.rotation[1] != 0 || cube.rotation[2] != 0) {
					cubeMatrix = matrix.clone();
					Mat.translate(cubeMatrix, cube.pivot[0], cube.pivot[1], cube.pivot[2]);
					Mat.rotate(cubeMatrix, cube.rotation);
					Mat.translate(cubeMatrix, -cube.pivot[0], -cube.pivot[1], -cube.pivot[2]);
				}
				emitCube(sink, cubeMatrix, cube.from, cube.to, cube.uv);
			}
		}

		if (pass == Pass.GLOW && sparkles != null) {
			renderSparkles(sink, seconds);
		}
		if (pass == Pass.BASE && heartUv != null) {
			renderHearts(sink, pose);
		}
	}

	/**
	 * Waagerechte Abziehbilder (z. B. Fußspuren) in Bedrock-Pixeln: je x, y, z, Drehung um die Hochachse (Grad),
	 * Breite, Länge. {@code uv}: Texturausschnitt (u, v, Breite, Höhe in Texturpixeln) – derselbe in beiden
	 * Durchgängen, die Leucht-Textur liefert dort das Leuchten.
	 */
	public void renderDecals(QuadSink sink, List<float[]> decals, float[] uv) {
		float[][] faces = new float[6][];
		faces[4] = uv;
		faces[5] = uv;
		float[] from = new float[3];
		float[] to = new float[3];
		for (float[] decal : decals) {
			float[] matrix = Mat.identity();
			Mat.translate(matrix, decal[0], decal[1], decal[2]);
			Mat.rotateY(matrix, decal[3]);
			from[0] = -decal[4] / 2;
			from[1] = 0;
			from[2] = -decal[5] / 2;
			to[0] = decal[4] / 2;
			to[1] = 0;
			to[2] = decal[5] / 2;
			emitCube(sink, matrix, from, to, faces);
		}
	}

	/** Texturausschnitt (u, v, Breite, Höhe in Texturpixeln) als u links, u rechts, v oben, v unten. */
	float[] uvRect(List<Object> source) {
		float u = Json.number(source.get(0), 0), v = Json.number(source.get(1), 0);
		float w = Json.number(source.get(2), 0), h = Json.number(source.get(3), 0);
		return new float[] {u / textureWidth, (u + w) / textureWidth, v / textureHeight, (v + h) / textureHeight};
	}

	/** Herzen als dünne Flächen, die sich um die Hochachse drehen (Vorder- und Rückseite). */
	private void renderHearts(QuadSink sink, Pose pose) {
		float[][] uv = new float[6][];
		uv[0] = heartUv;
		uv[2] = heartUv;
		float[] from = new float[3];
		float[] to = new float[3];
		for (float[] heart : pose.hearts) {
			float half = heart[3] / 2.0F;
			float[] matrix = Mat.identity();
			Mat.translate(matrix, heart[0], heart[1], heart[2]);
			Mat.rotateY(matrix, heart[4]);
			from[0] = -half;
			from[1] = -half;
			from[2] = 0;
			to[0] = half;
			to[1] = half;
			to[2] = 0;
			emitCube(sink, matrix, from, to, uv);
		}
	}

	private void renderSparkles(QuadSink sink, float seconds) {
		float[] identity = Mat.identity();
		float[][] uv = new float[6][];
		for (int face = 0; face < 6; face++) {
			uv[face] = sparkles.uv;
		}
		float height = sparkles.max[1] - sparkles.min[1];
		float[] from = new float[3];
		float[] to = new float[3];
		for (int i = 0; i < sparkles.count; i++) {
			float x = sparkles.min[0] + (sparkles.max[0] - sparkles.min[0]) * hash(i, 1);
			float z = sparkles.min[2] + (sparkles.max[2] - sparkles.min[2]) * hash(i, 3);
			float speed = sparkles.rise * (0.5F + hash(i, 4));
			float y = sparkles.min[1] + ((hash(i, 2) * height + seconds * speed) % height);
			// Funkeln: Größe pulsiert, am oberen und unteren Rand blendet der Punkt aus
			float edge = Math.min(1.0F, Math.min(y - sparkles.min[1], sparkles.max[1] - y) / (height * 0.15F));
			float twinkle = 0.55F + 0.45F * (float) Math.sin(seconds * (2.0F + hash(i, 5) * 3.0F) + hash(i, 6) * 6.283F);
			float half = sparkles.size * twinkle * edge * 0.5F;
			if (half <= 0.01F) {
				continue;
			}
			from[0] = x - half;
			from[1] = y - half;
			from[2] = z - half;
			to[0] = x + half;
			to[1] = y + half;
			to[2] = z + half;
			emitCube(sink, identity, from, to, uv);
		}
	}

	/** Gleiche Zufallswerte (0–1) wie im Launcher. */
	static float hash(int index, int salt) {
		double value = Math.sin(index * 12.9898 + salt * 78.233) * 43758.5453;
		return (float) (value - Math.floor(value));
	}

	private static void emitCube(QuadSink sink, float[] matrix, float[] from, float[] to, float[][] uvs) {
		float[] point = new float[3];
		float[] normal = new float[3];
		for (int face = 0; face < 6; face++) {
			float[] uv = uvs[face];
			if (uv == null) {
				continue;
			}
			Mat.applyRotation(matrix, NORMALS[face], normal);
			// Oben links, oben rechts, unten rechts, unten links: nach der y-Spiegelung unten
			// liegen die Ecken gegen den Uhrzeigersinn – so bleiben Flächen mit Backface-Culling sichtbar
			for (int corner = 0; corner < 4; corner++) {
				int[] c = CORNERS[face][corner];
				point[0] = c[0] == 0 ? from[0] : to[0];
				point[1] = c[1] == 0 ? from[1] : to[1];
				point[2] = c[2] == 0 ? from[2] : to[2];
				Mat.applyPoint(matrix, point);
				float u = corner == 0 || corner == 3 ? uv[0] : uv[1];
				float v = corner == 0 || corner == 1 ? uv[2] : uv[3];
				// Bedrock (Pixel, y nach oben) → Körperraum des Spielermodells (Blöcke, y nach unten)
				sink.vertex(point[0] / 16.0F, (NECK_Y - point[1]) / 16.0F, point[2] / 16.0F,
					u, v, normal[0], -normal[1], normal[2]);
			}
		}
	}

	/** 4×4-Matrizen, spaltenweise, als float[16]. */
	private static final class Mat {
		static float[] identity() {
			return new float[] {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
		}

		static void multiply(float[] m, float[] r) {
			float[] result = new float[16];
			for (int col = 0; col < 4; col++) {
				for (int row = 0; row < 4; row++) {
					float sum = 0;
					for (int k = 0; k < 4; k++) {
						sum += m[k * 4 + row] * r[col * 4 + k];
					}
					result[col * 4 + row] = sum;
				}
			}
			System.arraycopy(result, 0, m, 0, 16);
		}

		static void translate(float[] m, float x, float y, float z) {
			float[] t = identity();
			t[12] = x;
			t[13] = y;
			t[14] = z;
			multiply(m, t);
		}

		static void scale(float[] m, float[] s) {
			if (s[0] == 1 && s[1] == 1 && s[2] == 1) {
				return;
			}
			multiply(m, new float[] {s[0], 0, 0, 0, 0, s[1], 0, 0, 0, 0, s[2], 0, 0, 0, 0, 1});
		}

		/** Rz · Ry · Rx, Winkel in Grad. */
		static void rotate(float[] m, float[] degrees) {
			rotateZ(m, degrees[2]);
			rotateY(m, degrees[1]);
			rotateX(m, degrees[0]);
		}

		static void rotateX(float[] m, float degrees) {
			if (degrees == 0) {
				return;
			}
			float c = (float) Math.cos(Math.toRadians(degrees));
			float s = (float) Math.sin(Math.toRadians(degrees));
			multiply(m, new float[] {1, 0, 0, 0, 0, c, s, 0, 0, -s, c, 0, 0, 0, 0, 1});
		}

		static void rotateY(float[] m, float degrees) {
			if (degrees == 0) {
				return;
			}
			float c = (float) Math.cos(Math.toRadians(degrees));
			float s = (float) Math.sin(Math.toRadians(degrees));
			multiply(m, new float[] {c, 0, -s, 0, 0, 1, 0, 0, s, 0, c, 0, 0, 0, 0, 1});
		}

		static void rotateZ(float[] m, float degrees) {
			if (degrees == 0) {
				return;
			}
			float c = (float) Math.cos(Math.toRadians(degrees));
			float s = (float) Math.sin(Math.toRadians(degrees));
			multiply(m, new float[] {c, s, 0, 0, -s, c, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1});
		}

		static void applyPoint(float[] m, float[] p) {
			float x = p[0], y = p[1], z = p[2];
			p[0] = m[0] * x + m[4] * y + m[8] * z + m[12];
			p[1] = m[1] * x + m[5] * y + m[9] * z + m[13];
			p[2] = m[2] * x + m[6] * y + m[10] * z + m[14];
		}

		static void applyRotation(float[] m, float[] n, float[] out) {
			out[0] = m[0] * n[0] + m[4] * n[1] + m[8] * n[2];
			out[1] = m[1] * n[0] + m[5] * n[1] + m[9] * n[2];
			out[2] = m[2] * n[0] + m[6] * n[1] + m[10] * n[2];
		}
	}
}
