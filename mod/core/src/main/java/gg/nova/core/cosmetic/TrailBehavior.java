package gg.nova.core.cosmetic;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Spur aus cosmetic.json ("trail"): legt beim Laufen abwechselnd links und rechts Abdrücke in die Welt, die
 * einige Sekunden liegen bleiben und dann schrumpfen. Die Abdrücke haben Weltkoordinaten; beim Zeichnen werden
 * sie in den Modellraum des Spielers umgerechnet – so bleiben sie liegen, während er weiterläuft.
 * Gleiche Regeln wie src/components/cosmetics/petBehavior.ts im Launcher.
 */
final class TrailBehavior {
	/** Modellraum des Spielers: Minecraft skaliert ihn auf 15/16 und verschiebt ihn um 1,501 Blöcke. */
	private static final double MODEL_SCALE = 0.9375;
	private static final double MODEL_OFFSET = 1.501;

	private final double stride;
	private final double life;
	private final double spread;
	private final float width;
	private final float length;
	final List<Object> uv;

	/** Je Abdruck: Welt x, y, z, Blickrichtung (Grad, Minecraft-Yaw), Entstehungszeit. */
	private final List<double[]> prints = new ArrayList<double[]>();
	private double[] last;
	private boolean left;

	TrailBehavior(Map<String, Object> trail) {
		this.stride = Json.number(trail.get("stride"), 0.8F);
		this.life = Json.number(trail.get("life"), 3.0F);
		this.spread = Json.number(trail.get("spread"), 0.14F);
		this.width = Json.number(trail.get("width"), 3.0F);
		this.length = Json.number(trail.get("length"), 5.0F);
		this.uv = Json.list(trail.get("uv"));
	}

	/** Neue Position des Spielers (Blöcke) – legt einen Abdruck, wenn er eine Schrittlänge auf dem Boden gelaufen ist. */
	synchronized void step(double x, double y, double z, float yaw, double now, boolean grounded) {
		prints.removeIf(print -> now - print[4] > life);
		if (last == null || !grounded) {
			last = new double[] {x, y, z};
			return;
		}
		if (Math.hypot(x - last[0], z - last[2]) < stride) {
			return;
		}
		// Abwechselnd links und rechts der Laufrichtung
		double side = left ? spread : -spread;
		left = !left;
		double rad = Math.toRadians(yaw);
		prints.add(new double[] {x + Math.cos(rad) * side, y, z + Math.sin(rad) * side, yaw, now});
		last = new double[] {x, y, z};
	}

	/** Abdrücke im Modellraum des Spielers (Bedrock-Pixel) für {@link CosmeticModel#renderDecals}. */
	synchronized List<float[]> decals(double now, double x, double y, double z, float bodyYaw) {
		List<float[]> result = new ArrayList<float[]>();
		// Welt = P + Ry(180 - Yaw) · diag(-1, -1, 1) · 15/16 · (m + (0, -1,501, 0)) – hier umgekehrt
		double angle = Math.toRadians(-(180.0 - bodyYaw));
		double cos = Math.cos(angle), sin = Math.sin(angle);
		for (double[] print : prints) {
			double age = (now - print[4]) / life;
			float shrink = (float) Math.min(1.0, (1.0 - age) / 0.3);
			if (shrink <= 0.02F) {
				continue;
			}
			double dx = print[0] - x, dy = print[1] - y, dz = print[2] - z;
			double rx = dx * cos + dz * sin;
			double rz = -dx * sin + dz * cos;
			double mx = -rx / MODEL_SCALE, my = -dy / MODEL_SCALE + MODEL_OFFSET, mz = rz / MODEL_SCALE;
			// Blickrichtung des Abdrucks ebenso umrechnen; Bedrock-Fläche zeigt mit der Spitze nach -Z
			double yaw = Math.toRadians(print[3]);
			double fx = -Math.sin(yaw), fz = Math.cos(yaw);
			double bx = -(fx * cos + fz * sin), bz = -fx * sin + fz * cos;
			float turn = (float) Math.toDegrees(Math.atan2(-bx, -bz));
			result.add(new float[] {
				(float) (mx * 16), (float) (24 - my * 16) + 0.15F, (float) (mz * 16), turn, width * shrink, length * shrink,
			});
		}
		return result;
	}
}
