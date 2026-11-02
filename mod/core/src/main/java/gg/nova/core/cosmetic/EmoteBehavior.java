package gg.nova.core.cosmetic;

import java.util.Map;

/**
 * Emote aus cosmetic.json ("emote"): spielt einmal die Animation "burst" ab, wenn der Spieler sich duckt
 * ("trigger": "sneak"), und ist sonst unsichtbar. Die Animationszeit zählt ab dem Auslöser.
 * Gleiche Regeln wie src/components/cosmetics/petBehavior.ts im Launcher.
 */
final class EmoteBehavior {
	private final float duration;

	EmoteBehavior(Map<String, Object> emote) {
		this.duration = Json.number(emote.get("duration"), 1.0F);
	}

	/** Pose zum Zeitpunkt {@code now}, wenn das Ducken bei {@code triggeredAt} begann, oder null (nichts zeichnen). */
	CosmeticModel.Pose pose(double now, double triggeredAt) {
		double age = now - triggeredAt;
		if (age < 0 || age > duration) {
			return null;
		}
		CosmeticModel.Pose pose = new CosmeticModel.Pose();
		pose.add("burst", 1.0F);
		pose.time = (float) age;
		return pose;
	}
}
