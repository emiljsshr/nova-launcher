package gg.nova.core.cosmetic;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 * Rendert ein 3D-Cosmetic ohne Minecraft mit dem Mod-Kern (CosmeticModel): eine Vorschau mit Spielerfigur
 * und ein Vorschaubild für die Store-Karten (thumbnail.png, transparent). Bei Pets spielt es zusätzlich das
 * Verhalten für einen stillstehenden Spieler durch (preview-pet-<sekunde>.png).
 *
 *   tools/cosmetics/render.sh public/cosmetics/frost-wings
 */
public final class Render {
	private static final int SUPERSAMPLE = 2;
	/** Emotes: feste Pose mitten im Ablauf; Spuren: ein paar Abdrücke statt eines Modells. */
	private static CosmeticModel.Pose posePreset;
	private static List<float[]> decalPreset;
	private static float[] decalUv;

	private final int width;
	private final int height;
	private final float scale;
	private final float centerX;
	private final float centerY;
	private final float yaw;
	private final float pitch;
	private final float[] color;
	private final float[] alpha;
	private final float[] depth;

	private Render(int width, int height, float scale, float centerX, float centerY, float yawDegrees, float pitchDegrees) {
		this.width = width * SUPERSAMPLE;
		this.height = height * SUPERSAMPLE;
		this.scale = scale * SUPERSAMPLE;
		this.centerX = centerX * SUPERSAMPLE;
		this.centerY = centerY * SUPERSAMPLE;
		this.yaw = (float) Math.toRadians(yawDegrees);
		this.pitch = (float) Math.toRadians(pitchDegrees);
		this.color = new float[this.width * this.height * 3];
		this.alpha = new float[this.width * this.height];
		this.depth = new float[this.width * this.height];
		java.util.Arrays.fill(depth, Float.NEGATIVE_INFINITY);
	}

	public static void main(String[] args) throws IOException {
		File dir = new File(args[0]);
		float seconds = args.length > 1 ? Float.parseFloat(args[1]) : 1.2F;
		Map<String, Object> manifest = Json.object(Json.parse(read(new File(dir, "cosmetic.json"))));
		String animation = manifest.containsKey("animationFile") ? read(new File(dir, (String) manifest.get("animationFile"))) : null;
		CosmeticModel model = CosmeticModel.parse(read(new File(dir, (String) manifest.get("model"))), animation, manifest);
		BufferedImage texture = ImageIO.read(new File(dir, (String) manifest.get("texture")));
		if (manifest.containsKey("emote")) {
			posePreset = new CosmeticModel.Pose();
			posePreset.add("burst", 1.0F);
			posePreset.time = Json.number(Json.object(manifest.get("thumbnail")).get("seconds"), 0.45F);
		}
		if (manifest.containsKey("trail")) {
			decalPreset = new ArrayList<>();
			for (int i = 0; i < 4; i++) {
				decalPreset.add(new float[] {i % 2 == 0 ? 2.2F : -2.2F, 0.15F, 6 - i * 10.5F, 0, 3, 5});
			}
			decalUv = model.uvRect(Json.list(Json.object(manifest.get("trail")).get("uv")));
		}
		Object glowFile = Json.object(manifest.get("glow")).get("texture");
		BufferedImage glow = glowFile != null ? ImageIO.read(new File(dir, (String) glowFile)) : null;

		// Vorschau: schräg von hinten mit Spielerfigur, dunkler Hintergrund
		Render preview = new Render(1200, 800, 190, 600, 330, 24, 10);
		preview.player();
		preview.draw(model, texture, glow, seconds);
		ImageIO.write(preview.image(new float[] {0.05f, 0.08f, 0.17f}), "png", new File(args.length > 2 ? args[2] : "preview.png"));

		// Vorschau von vorne
		Render front = new Render(1200, 800, 190, 600, 330, 180 + 18, 6);
		front.player();
		front.draw(model, texture, glow, seconds);
		ImageIO.write(front.image(new float[] {0.05f, 0.08f, 0.17f}), "png", new File(args.length > 3 ? args[3] : "preview-front.png"));

		// Store-Vorschaubild: nur das Cosmetic, transparent und auf die Bildgröße eingepasst
		Map<String, Object> view = Json.object(manifest.get("thumbnail"));
		float thumbSeconds = Json.number(view.get("seconds"), seconds);
		Render thumb = fitted(model, thumbSeconds, 512, Json.number(view.get("yaw"), 20), Json.number(view.get("pitch"), 8));
		thumb.draw(model, texture, glow, thumbSeconds);
		ImageIO.write(thumb.image(null), "png", new File(dir, "thumbnail.png"));

		// Flügel mit Verhalten: zusätzlich angelegt von hinten und von der Seite
		if (manifest.containsKey("wings")) {
			File outDir = new File(args.length > 2 ? args[2] : "preview.png").getAbsoluteFile().getParentFile();
			CosmeticModel.Pose closed = new CosmeticModel.Pose();
			closed.add("closed", 1.0F);
			float[][] views = {{24, 10}, {90, 8}};
			String[] names = {"preview-closed.png", "preview-closed-side.png"};
			for (int i = 0; i < views.length; i++) {
				Render frame = new Render(1200, 800, 190, 600, 330, views[i][0], views[i][1]);
				frame.player();
				frame.draw(model, texture, glow, seconds, closed);
				ImageIO.write(frame.image(new float[] {0.05f, 0.08f, 0.17f}), "png", new File(outDir, names[i]));
			}
		}

		// Pet: Verhalten Schritt für Schritt durchspielen (Spieler steht still) und einzelne Momente zeigen
		Map<String, Object> pet = Json.object(manifest.get("pet"));
		if (!pet.isEmpty()) {
			PetBehavior behavior = new PetBehavior(pet, model.pivot(String.valueOf(pet.get("root"))));
			float[] moments = {6.5F, 7.4F, 11.0F, 14.0F};
			File outDir = new File(args.length > 2 ? args[2] : "preview.png").getAbsoluteFile().getParentFile();
			int next = 0;
			for (int step = 0; step <= 300 && next < moments.length; step++) {
				float now = step * 0.05F;
				CosmeticModel.Pose pose = behavior.pose(now, now, false);
				if (now + 1e-4 >= moments[next]) {
					Render frame = new Render(1200, 800, 150, 600, 300, 200, 14);
					frame.player();
					frame.draw(model, texture, glow, now, pose);
					String name = String.format(java.util.Locale.ROOT, "preview-pet-%04.1f.png", moments[next]);
					ImageIO.write(frame.image(new float[] {0.05f, 0.08f, 0.17f}), "png", new File(outDir, name));
					next++;
				}
			}
		}
	}

	private static Render fitted(CosmeticModel model, float seconds, int size, float yaw, float pitch) {
		Render probe = new Render(1, 1, 1, 0, 0, yaw, pitch);
		float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
		for (float[] v : collect(model, seconds, CosmeticModel.Pass.BASE)) {
			float[] p = probe.project(v[0], v[1], v[2]);
			minX = Math.min(minX, p[0] / SUPERSAMPLE);
			maxX = Math.max(maxX, p[0] / SUPERSAMPLE);
			minY = Math.min(minY, p[1] / SUPERSAMPLE);
			maxY = Math.max(maxY, p[1] / SUPERSAMPLE);
		}
		float scale = size * 0.86F / Math.max(maxX - minX, maxY - minY);
		return new Render(size, size, scale, size / 2.0F - (minX + maxX) / 2 * scale, size / 2.0F - (minY + maxY) / 2 * scale, yaw, pitch);
	}

	private static String read(File file) throws IOException {
		return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
	}

	/* ---------- Projektion: Körperraum (Blöcke, x links, y unten, z hinten) → Bild ---------- */

	private float[] project(float x, float y, float z) {
		// Kamera hinter dem Spieler, Blick nach -Z; Gieren um die Hochachse, dann Neigen
		float cy = (float) Math.cos(yaw), sy = (float) Math.sin(yaw);
		float x1 = x * cy - z * sy;
		float z1 = x * sy + z * cy;
		float cp = (float) Math.cos(pitch), sp = (float) Math.sin(pitch);
		float y2 = y * cp - z1 * sp;
		float z2 = y * sp + z1 * cp;
		return new float[] {centerX - x1 * scale, centerY + y2 * scale, z2};
	}

	private float[] rotateNormal(float x, float y, float z) {
		float cy = (float) Math.cos(yaw), sy = (float) Math.sin(yaw);
		float x1 = x * cy - z * sy;
		float z1 = x * sy + z * cy;
		float cp = (float) Math.cos(pitch), sp = (float) Math.sin(pitch);
		return new float[] {-x1, y * cp - z1 * sp, y * sp + z1 * cp};
	}

	/* ---------- Zeichnen ---------- */

	private void draw(CosmeticModel model, BufferedImage texture, BufferedImage glow, float seconds) {
		draw(model, texture, glow, seconds, null);
	}

	private void draw(CosmeticModel model, BufferedImage texture, BufferedImage glow, float seconds, CosmeticModel.Pose pose) {
		List<float[]> base = collect(model, seconds, CosmeticModel.Pass.BASE, pose);
		for (int i = 0; i < base.size(); i += 4) {
			quad(base.subList(i, i + 4), texture, false, 1);
		}
		if (glow != null) {
			float intensity = model.glowIntensity(seconds);
			List<float[]> light = collect(model, seconds, CosmeticModel.Pass.GLOW, pose);
			for (int i = 0; i < light.size(); i += 4) {
				quad(light.subList(i, i + 4), glow, true, intensity);
			}
		}
	}

	private static List<float[]> collect(CosmeticModel model, float seconds, CosmeticModel.Pass pass) {
		return collect(model, seconds, pass, null);
	}

	private static List<float[]> collect(CosmeticModel model, float seconds, CosmeticModel.Pass pass, CosmeticModel.Pose pose) {
		final List<float[]> vertices = new ArrayList<>();
		CosmeticModel.QuadSink sink = (x, y, z, u, v, nx, ny, nz) -> vertices.add(new float[] {x, y, z, u, v, nx, ny, nz});
		if (decalPreset != null) {
			model.renderDecals(sink, decalPreset, decalUv);
			return vertices;
		}
		if (pose == null) {
			pose = posePreset;
		}
		if (pose != null) {
			model.render(sink, seconds, pass, pose);
		} else {
			model.render(sink, seconds, pass);
		}
		return vertices;
	}

	/** Einfache Spielerfigur aus Quadern als Größenvergleich (Körperraum, Hals bei y = 0). */
	private void player() {
		box(-4, 0, -2, 8, 12, 4, new float[] {0.20f, 0.33f, 0.62f});
		box(-4, -8, -4, 8, 8, 8, new float[] {0.36f, 0.26f, 0.2f});
		box(4, 0, -2, 4, 12, 4, new float[] {0.17f, 0.28f, 0.55f});
		box(-8, 0, -2, 4, 12, 4, new float[] {0.17f, 0.28f, 0.55f});
		box(-4, 12, -2, 4, 12, 4, new float[] {0.13f, 0.16f, 0.32f});
		box(0, 12, -2, 4, 12, 4, new float[] {0.13f, 0.16f, 0.32f});
	}

	private void box(float x, float y, float z, float w, float h, float d, float[] rgb) {
		float[][] c = new float[8][];
		for (int i = 0; i < 8; i++) {
			c[i] = project((x + ((i & 1) != 0 ? w : 0)) / 16, (y + ((i & 2) != 0 ? h : 0)) / 16, (z + ((i & 4) != 0 ? d : 0)) / 16);
		}
		int[][] faces = {{0, 1, 3, 2}, {4, 5, 7, 6}, {0, 2, 6, 4}, {1, 3, 7, 5}, {0, 1, 5, 4}, {2, 3, 7, 6}};
		float[] shades = {0.75f, 0.95f, 0.8f, 0.8f, 1.0f, 0.6f};
		for (int f = 0; f < 6; f++) {
			float[] shaded = {rgb[0] * shades[f], rgb[1] * shades[f], rgb[2] * shades[f]};
			triangle(c[faces[f][0]], c[faces[f][1]], c[faces[f][2]], null, null, null, null, false, shaded, 1);
			triangle(c[faces[f][0]], c[faces[f][2]], c[faces[f][3]], null, null, null, null, false, shaded, 1);
		}
	}

	private void quad(List<float[]> corners, BufferedImage texture, boolean additive, float intensity) {
		float[][] p = new float[4][];
		float[][] uv = new float[4][];
		for (int i = 0; i < 4; i++) {
			float[] v = corners.get(i);
			p[i] = project(v[0], v[1], v[2]);
			uv[i] = new float[] {v[3], v[4]};
		}
		float[] n = rotateNormal(corners.get(0)[5], corners.get(0)[6], corners.get(0)[7]);
		// Nur die Seite, die zur Kamera zeigt – wie im Spiel mit Backface-Culling
		if (n[2] < 0) {
			return;
		}
		float light = additive ? intensity : 0.45f + 0.6f * Math.max(0, n[0] * -0.25f + n[1] * -0.55f + n[2] * 0.8f);
		triangle(p[0], p[1], p[2], uv[0], uv[1], uv[2], texture, additive, null, light);
		triangle(p[0], p[2], p[3], uv[0], uv[2], uv[3], texture, additive, null, light);
	}

	private void triangle(float[] a, float[] b, float[] c, float[] ta, float[] tb, float[] tc,
			BufferedImage texture, boolean additive, float[] flat, float light) {
		int minX = Math.max(0, (int) Math.floor(Math.min(a[0], Math.min(b[0], c[0]))));
		int maxX = Math.min(width - 1, (int) Math.ceil(Math.max(a[0], Math.max(b[0], c[0]))));
		int minY = Math.max(0, (int) Math.floor(Math.min(a[1], Math.min(b[1], c[1]))));
		int maxY = Math.min(height - 1, (int) Math.ceil(Math.max(a[1], Math.max(b[1], c[1]))));
		float area = (b[0] - a[0]) * (c[1] - a[1]) - (c[0] - a[0]) * (b[1] - a[1]);
		if (Math.abs(area) < 1e-6f) {
			return;
		}
		for (int y = minY; y <= maxY; y++) {
			for (int x = minX; x <= maxX; x++) {
				float px = x + 0.5f, py = y + 0.5f;
				float w0 = ((b[0] - px) * (c[1] - py) - (c[0] - px) * (b[1] - py)) / area;
				float w1 = ((c[0] - px) * (a[1] - py) - (a[0] - px) * (c[1] - py)) / area;
				float w2 = 1 - w0 - w1;
				if (w0 < 0 || w1 < 0 || w2 < 0) {
					continue;
				}
				float z = w0 * a[2] + w1 * b[2] + w2 * c[2];
				int index = y * width + x;
				float r, g, bl, al = 1;
				if (flat != null) {
					r = flat[0];
					g = flat[1];
					bl = flat[2];
				} else {
					float u = w0 * ta[0] + w1 * tb[0] + w2 * tc[0];
					float v = w0 * ta[1] + w1 * tb[1] + w2 * tc[1];
					float[] texel = sample(texture, u, v);
					r = texel[0];
					g = texel[1];
					bl = texel[2];
					al = texel[3];
				}
				if (additive) {
					if (z < depth[index] - 0.002f) {
						continue;
					}
					float add = light * al;
					color[index * 3] += r * add;
					color[index * 3 + 1] += g * add;
					color[index * 3 + 2] += bl * add;
					alpha[index] = Math.min(1, alpha[index] + Math.max(r, Math.max(g, bl)) * add);
					continue;
				}
				if (al < 0.5f || z < depth[index]) {
					continue;
				}
				depth[index] = z;
				color[index * 3] = r * light;
				color[index * 3 + 1] = g * light;
				color[index * 3 + 2] = bl * light;
				alpha[index] = 1;
			}
		}
	}

	private static float[] sample(BufferedImage image, float u, float v) {
		// Pixel-Texturen scharf wie im Launcher (unter 256 px), HD-Texturen bilinear
		if (image.getWidth() < 256) {
			int x = Math.max(0, Math.min(image.getWidth() - 1, (int) (u * image.getWidth())));
			int y = Math.max(0, Math.min(image.getHeight() - 1, (int) (v * image.getHeight())));
			int argb = image.getRGB(x, y);
			return new float[] {((argb >> 16) & 255) / 255f, ((argb >> 8) & 255) / 255f, (argb & 255) / 255f, (argb >>> 24) / 255f};
		}
		float fx = u * image.getWidth() - 0.5f, fy = v * image.getHeight() - 0.5f;
		int x0 = (int) Math.floor(fx), y0 = (int) Math.floor(fy);
		float dx = fx - x0, dy = fy - y0;
		float[] result = new float[4];
		for (int j = 0; j < 2; j++) {
			for (int i = 0; i < 2; i++) {
				int x = Math.max(0, Math.min(image.getWidth() - 1, x0 + i));
				int y = Math.max(0, Math.min(image.getHeight() - 1, y0 + j));
				int argb = image.getRGB(x, y);
				float weight = (i == 0 ? 1 - dx : dx) * (j == 0 ? 1 - dy : dy);
				float a = (argb >>> 24) / 255f;
				result[0] += ((argb >> 16) & 255) / 255f * a * weight;
				result[1] += ((argb >> 8) & 255) / 255f * a * weight;
				result[2] += (argb & 255) / 255f * a * weight;
				result[3] += a * weight;
			}
		}
		if (result[3] > 0) {
			result[0] /= result[3];
			result[1] /= result[3];
			result[2] /= result[3];
		}
		return result;
	}

	private BufferedImage image(float[] background) {
		int w = width / SUPERSAMPLE, h = height / SUPERSAMPLE;
		BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				float r = 0, g = 0, b = 0, a = 0;
				for (int j = 0; j < SUPERSAMPLE; j++) {
					for (int i = 0; i < SUPERSAMPLE; i++) {
						int index = (y * SUPERSAMPLE + j) * width + x * SUPERSAMPLE + i;
						float pa = alpha[index];
						float br = 0, bg = 0, bb = 0;
						if (background != null) {
							// Hintergrund mit leichtem Verlauf
							float t = (float) y / h;
							br = background[0] * (1.2f - 0.4f * t);
							bg = background[1] * (1.2f - 0.4f * t);
							bb = background[2] * (1.2f - 0.4f * t);
						}
						float base = Math.min(pa, 1);
						r += background != null ? color[index * 3] + br * (1 - base) : color[index * 3];
						g += background != null ? color[index * 3 + 1] + bg * (1 - base) : color[index * 3 + 1];
						b += background != null ? color[index * 3 + 2] + bb * (1 - base) : color[index * 3 + 2];
						a += background != null ? 1 : pa;
					}
				}
				float n = SUPERSAMPLE * SUPERSAMPLE;
				r /= n;
				g /= n;
				b /= n;
				a /= n;
				if (background == null && a > 0) {
					// Transparent: Farbe nicht vormultipliziert speichern
					r /= a;
					g /= a;
					b /= a;
				}
				int ai = Math.round(Math.min(1, a) * 255);
				image.setRGB(x, y, (ai << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b));
			}
		}
		return image;
	}

	private static int clamp(float value) {
		return Math.max(0, Math.min(255, Math.round(value * 255)));
	}
}
