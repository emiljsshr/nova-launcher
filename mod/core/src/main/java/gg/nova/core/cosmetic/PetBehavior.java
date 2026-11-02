package gg.nova.core.cosmetic;

import java.util.List;
import java.util.Map;

/**
 * Verhalten eines Pets aus cosmetic.json ("pet"): schwebt am Platz ("idle"), fliegt ab und zu eine Runde um
 * den Spieler und verteilt dabei Herzen über ihm ("fly"), landet nach einigen Sekunden Stillstand neben ihm
 * ("sit") und fliegt wieder auf, sobald er sich bewegt. Gleitet der Spieler mit einer Elytra, fliegt das Pet
 * an "glide" neben ihm mit ("glide"); der Modellraum liegt dann längs der Flugrichtung, darum dreht es sich
 * mit. Zwischen den Animationen wird überblendet.
 * Gleiche Regeln wie src/components/cosmetics/bedrockModel.ts im Launcher.
 */
final class PetBehavior {
	private enum State { HOVER, CIRCLE, LANDING, SIT, TAKEOFF, GLIDE }

	private static final float LAND_SECONDS = 1.6F;
	private static final float TAKEOFF_SECONDS = 1.2F;
	private static final float BLEND_SECONDS = 0.4F;
	private static final float GLIDE_SECONDS = 0.6F;

	/** Drehpunkt des Wurzelknochens = Schwebeplatz (Bedrock-Pixel). */
	private final float[] hover;
	/** Sitzplatz des Wurzelknochens. */
	private final float[] sit;
	private final float sitAfter;
	private final float circleFirst;
	private final float circleMin;
	private final float circleMax;
	private final float circleDuration;
	private final float circleWiden;
	private final float circleRise;
	private final float heartInterval;
	private final float heartLife;
	private final float heartSize;
	private final float heartRise;
	private final float heartSpread;
	private final float heartHeight;
	/** Platz und Drehung des Wurzelknochens beim Gleitflug (Bedrock-Pixel, Grad). */
	private final float[] glidePosition;
	private final float[] glideRotation;

	private State state = State.HOVER;
	private double stateStart;
	private double nextCircle = Double.NaN;
	private int circles;
	private double heartsStart = Double.NEGATIVE_INFINITY;
	private int heartSeed;
	private final float[] from = new float[3];
	private final float[] current = new float[3];
	private final float[] fromRotation = new float[3];
	private final float[] currentRotation = new float[3];

	PetBehavior(Map<String, Object> pet, float[] hover) {
		this.hover = hover;
		Map<String, Object> sitSource = Json.object(pet.get("sit"));
		this.sit = Json.vec3(sitSource.get("position"), 0.0F);
		this.sitAfter = Json.number(sitSource.get("after"), 10.0F);
		Map<String, Object> circle = Json.object(pet.get("circle"));
		List<Object> every = Json.list(circle.get("every"));
		this.circleFirst = Json.number(circle.get("first"), 4.0F);
		this.circleMin = every.size() > 0 ? Json.number(every.get(0), 18.0F) : 18.0F;
		this.circleMax = every.size() > 1 ? Json.number(every.get(1), 35.0F) : 35.0F;
		this.circleDuration = Json.number(circle.get("duration"), 4.0F);
		this.circleWiden = Json.number(circle.get("widen"), 6.0F);
		this.circleRise = Json.number(circle.get("rise"), 4.0F);
		Map<String, Object> hearts = Json.object(pet.get("hearts"));
		this.heartInterval = Json.number(hearts.get("interval"), 0.25F);
		this.heartLife = Json.number(hearts.get("life"), 1.8F);
		this.heartSize = Json.number(hearts.get("size"), 2.5F);
		this.heartRise = Json.number(hearts.get("rise"), 7.0F);
		this.heartSpread = Json.number(hearts.get("spread"), 8.0F);
		this.heartHeight = Json.number(hearts.get("height"), 35.0F);
		Map<String, Object> glide = Json.object(pet.get("glide"));
		this.glidePosition = glide.containsKey("position") ? Json.vec3(glide.get("position"), 0.0F) : new float[] {-16, 22, 6};
		this.glideRotation = glide.containsKey("rotation") ? Json.vec3(glide.get("rotation"), 0.0F) : new float[] {90, 0, 0};
	}

	/**
	 * Pose zum Zeitpunkt {@code now} (Sekunden), wenn der Spieler seit {@code idle} Sekunden stillsteht und
	 * {@code gliding} mit einer Elytra gleitet.
	 */
	synchronized CosmeticModel.Pose pose(double now, double idle, boolean gliding) {
		if (Double.isNaN(nextCircle)) {
			nextCircle = now + circleFirst;
			stateStart = now;
		}
		boolean still = idle >= sitAfter && !gliding;
		double t = now - stateStart;

		// Gleitflug hat Vorrang vor allem anderen; danach zurück an den Schwebeplatz
		if (gliding && state != State.GLIDE) {
			begin(State.GLIDE, now, current);
		} else if (!gliding && state == State.GLIDE) {
			begin(State.TAKEOFF, now, current);
		}

		switch (state) {
			case HOVER:
				if (still) {
					begin(State.LANDING, now, current);
				} else if (now >= nextCircle) {
					begin(State.CIRCLE, now, current);
					heartsStart = now;
					heartSeed = circles * 97;
				}
				break;
			case CIRCLE:
				if (t >= circleDuration) {
					circles++;
					nextCircle = now + circleMin + (circleMax - circleMin) * CosmeticModel.hash(circles, 21);
					begin(State.HOVER, now, current);
				}
				break;
			case LANDING:
				if (!still) {
					begin(State.TAKEOFF, now, current);
				} else if (t >= LAND_SECONDS) {
					begin(State.SIT, now, current);
				}
				break;
			case SIT:
				if (!still) {
					begin(State.TAKEOFF, now, current);
				}
				break;
			case TAKEOFF:
				if (still) {
					begin(State.LANDING, now, current);
				} else if (t >= TAKEOFF_SECONDS) {
					// Nach dem Auffliegen nicht sofort eine Runde
					nextCircle = Math.max(nextCircle, now + 3.0);
					begin(State.HOVER, now, current);
				}
				break;
			default:
				break;
		}

		CosmeticModel.Pose pose = new CosmeticModel.Pose();
		float u;
		t = now - stateStart;
		switch (state) {
			case GLIDE: {
				u = smooth((float) (t / GLIDE_SECONDS));
				pose.add("glide", u);
				pose.add("fly", 1.0F - u);
				lerpTo(new float[] {glidePosition[0] - hover[0], glidePosition[1] - hover[1], glidePosition[2] - hover[2]}, u, 0.0F);
				for (int i = 0; i < 3; i++) {
					pose.rootRotation[i] = fromRotation[i] + (glideRotation[i] - fromRotation[i]) * u;
				}
				break;
			}
			case CIRCLE: {
				u = clamp((float) (t / circleDuration));
				float fly = smooth(Math.min(1.0F, Math.min((float) t, circleDuration - (float) t) / BLEND_SECONDS));
				pose.add("idle", 1.0F - fly);
				pose.add("fly", fly);
				// Kreis um die Hochachse des Spielers, startet und endet am Schwebeplatz
				double start = Math.atan2(hover[2], hover[0]);
				double angle = start + 2.0 * Math.PI * smooth(u);
				double radius = Math.hypot(hover[0], hover[2]) + circleWiden * Math.sin(Math.PI * u);
				current[0] = (float) (radius * Math.cos(angle)) - hover[0];
				current[1] = circleRise * (float) Math.sin(Math.PI * u);
				current[2] = (float) (radius * Math.sin(angle)) - hover[2];
				// Blick in Flugrichtung, in die Kurve gelegt
				pose.rootRotation[1] = (float) -Math.toDegrees(angle - start);
				pose.rootRotation[2] = -25.0F * (float) Math.sin(Math.PI * u);
				break;
			}
			case LANDING: {
				u = clamp((float) (t / LAND_SECONDS));
				float idleWeight = 1.0F - smoothRange(0.0F, 0.25F, u);
				float sitWeight = smoothRange(0.7F, 1.0F, u);
				pose.add("idle", idleWeight);
				pose.add("sit", sitWeight);
				pose.add("fly", Math.max(0.0F, 1.0F - idleWeight - sitWeight));
				lerpTo(sitOffset(), smooth(u), 3.0F * (float) Math.sin(Math.PI * u));
				pose.rootRotation[0] = -12.0F * (float) Math.sin(Math.PI * u);
				break;
			}
			case SIT:
				pose.add("sit", 1.0F);
				System.arraycopy(sitOffset(), 0, current, 0, 3);
				break;
			case TAKEOFF: {
				u = clamp((float) (t / TAKEOFF_SECONDS));
				float sitWeight = 1.0F - smoothRange(0.0F, 0.3F, u);
				float idleWeight = smoothRange(0.7F, 1.0F, u);
				pose.add("sit", sitWeight);
				pose.add("idle", idleWeight);
				pose.add("fly", Math.max(0.0F, 1.0F - idleWeight - sitWeight));
				lerpTo(new float[3], smooth(u), 2.0F * (float) Math.sin(Math.PI * u));
				// Aus dem Gleitflug zurückdrehen, sonst nur leicht nach oben neigen
				for (int i = 0; i < 3; i++) {
					pose.rootRotation[i] = fromRotation[i] * (1.0F - smooth(u));
				}
				pose.rootRotation[0] += 10.0F * (float) Math.sin(Math.PI * u);
				break;
			}
			default:
				pose.add("idle", 1.0F);
				current[0] = current[1] = current[2] = 0.0F;
				break;
		}
		System.arraycopy(current, 0, pose.rootOffset, 0, 3);
		System.arraycopy(pose.rootRotation, 0, currentRotation, 0, 3);
		addHearts(pose, now);
		return pose;
	}

	private void begin(State next, double now, float[] offset) {
		System.arraycopy(offset, 0, from, 0, 3);
		System.arraycopy(currentRotation, 0, fromRotation, 0, 3);
		state = next;
		stateStart = now;
	}

	private float[] sitOffset() {
		return new float[] {sit[0] - hover[0], sit[1] - hover[1], sit[2] - hover[2]};
	}

	private void lerpTo(float[] target, float k, float arc) {
		for (int i = 0; i < 3; i++) {
			current[i] = from[i] + (target[i] - from[i]) * k;
		}
		current[1] += arc;
	}

	// Während der Runde steigen in festen Abständen Herzen über dem Kopf auf, drehen sich und verblassen
	private void addHearts(CosmeticModel.Pose pose, double now) {
		if (now - heartsStart > circleDuration + heartLife) {
			return;
		}
		int count = (int) (circleDuration / heartInterval);
		for (int i = 0; i < count; i++) {
			double age = now - (heartsStart + i * heartInterval);
			if (age < 0 || age >= heartLife) {
				continue;
			}
			float life = (float) (age / heartLife);
			int index = heartSeed + i;
			float x = (CosmeticModel.hash(index, 11) - 0.5F) * 2.0F * heartSpread
				+ 0.6F * (float) Math.sin(age * 4.0 + index);
			float z = (CosmeticModel.hash(index, 12) - 0.5F) * 2.0F * heartSpread;
			float y = heartHeight + CosmeticModel.hash(index, 13) * 3.0F + heartRise * life;
			float size = heartSize * Math.min(1.0F, (float) age / 0.15F) * (1.0F - life * life * life);
			float spin = (float) (age * 120.0) + 360.0F * CosmeticModel.hash(index, 14);
			pose.hearts.add(new float[] {x, y, z, size, spin});
		}
	}

	private static float clamp(float value) {
		return Math.max(0.0F, Math.min(1.0F, value));
	}

	private static float smooth(float u) {
		u = clamp(u);
		return u * u * (3.0F - 2.0F * u);
	}

	private static float smoothRange(float start, float end, float u) {
		return smooth((u - start) / (end - start));
	}
}
