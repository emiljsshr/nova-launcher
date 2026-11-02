package gg.nova.core.cosmetic;

import java.util.Map;

/**
 * Cape aus cosmetic.json ("cape"): hängt im Stand mit leichter Welle ("idle") und hebt sich nach hinten, je
 * schneller der Spieler über Grund ist; dabei flattert es stärker ("run"). Das Tempo wird geglättet, damit
 * das Cape nicht ruckt. Die Drehung liegt auf dem obersten Knochen ("bone"), die Welle steckt in den
 * Animationen der Streifen darunter.
 * Gleiche Regeln wie src/components/cosmetics/petBehavior.ts im Launcher.
 */
final class CapeBehavior {
	/** Zeitkonstante der Glättung in Sekunden. */
	private static final double SMOOTHING = 0.25;

	private final float rest;
	private final float perSpeed;
	private final float max;
	private final float runSpeed;

	private double smoothed;
	private double lastTime = Double.NaN;

	CapeBehavior(Map<String, Object> cape) {
		this.rest = Json.number(cape.get("rest"), 6.0F);
		this.perSpeed = Json.number(cape.get("perSpeed"), 9.0F);
		this.max = Json.number(cape.get("max"), 70.0F);
		this.runSpeed = Math.max(0.1F, Json.number(cape.get("runSpeed"), 5.6F));
	}

	/** Pose zum Zeitpunkt {@code now} (Sekunden) bei {@code speed} Blöcken pro Sekunde. */
	synchronized CosmeticModel.Pose pose(double now, double speed) {
		double delta = Double.isNaN(lastTime) ? 0.0 : Math.max(0.0, Math.min(0.2, now - lastTime));
		lastTime = now;
		smoothed += (speed - smoothed) * (1.0 - Math.exp(-delta / SMOOTHING));

		float run = (float) Math.max(0.0, Math.min(1.0, smoothed / runSpeed));
		CosmeticModel.Pose pose = new CosmeticModel.Pose();
		pose.add("idle", 1.0F - run);
		pose.add("run", run);
		// Negatives X hebt das untere Ende nach hinten (vom Rücken weg)
		pose.rootRotation[0] = -Math.min(max, rest + perSpeed * (float) smoothed);
		return pose;
	}
}
