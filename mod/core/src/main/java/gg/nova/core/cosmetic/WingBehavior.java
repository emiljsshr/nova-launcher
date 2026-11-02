package gg.nova.core.cosmetic;

import java.util.Map;

/**
 * Flügel aus cosmetic.json ("wings"): angelegt ("closed"), solange der Spieler steht oder geht, ausgebreitet
 * ("open"), sobald er schnell unterwegs ist – Sprinten, Fliegen, Elytra. Gemessen wird nur das Tempo über
 * Grund (Blöcke pro Sekunde), damit es in jeder Minecraft-Version gleich funktioniert. Ein Abstand zwischen
 * Öffnen- und Schließen-Schwelle und kurze Wartezeiten verhindern Flattern; dazwischen wird überblendet.
 * Gleiche Regeln wie src/components/cosmetics/bedrockModel.ts im Launcher.
 */
final class WingBehavior {
	/** So lange muss das Tempo über der Schwelle liegen, bevor die Flügel aufgehen. */
	private static final double OPEN_DELAY = 0.12;
	/** So lange unter der Schwelle, bevor sie sich wieder anlegen. */
	private static final double CLOSE_DELAY = 0.5;

	private final float openSpeed;
	private final float closeSpeed;
	private final float blend;

	private boolean open;
	private double pendingSince = Double.NaN;
	private double lastTime = Double.NaN;
	private float weight;

	WingBehavior(Map<String, Object> wings) {
		this.openSpeed = Json.number(wings.get("openSpeed"), 5.0F);
		this.closeSpeed = Json.number(wings.get("closeSpeed"), 4.4F);
		this.blend = Math.max(0.05F, Json.number(wings.get("blend"), 0.35F));
	}

	/** Pose zum Zeitpunkt {@code now} (Sekunden) bei {@code speed} Blöcken pro Sekunde. */
	synchronized CosmeticModel.Pose pose(double now, double speed) {
		boolean wanted = open ? speed > closeSpeed : speed > openSpeed;
		if (wanted == open) {
			pendingSince = Double.NaN;
		} else if (Double.isNaN(pendingSince)) {
			pendingSince = now;
		} else if (now - pendingSince >= (wanted ? OPEN_DELAY : CLOSE_DELAY)) {
			open = wanted;
			pendingSince = Double.NaN;
		}

		double delta = Double.isNaN(lastTime) ? 0.0 : Math.max(0.0, Math.min(0.1, now - lastTime));
		lastTime = now;
		weight += (open ? 1 : -1) * (float) delta / blend;
		weight = Math.max(0.0F, Math.min(1.0F, weight));

		float eased = weight * weight * (3.0F - 2.0F * weight);
		CosmeticModel.Pose pose = new CosmeticModel.Pose();
		pose.add("closed", 1.0F - eased);
		pose.add("open", eased);
		return pose;
	}
}
