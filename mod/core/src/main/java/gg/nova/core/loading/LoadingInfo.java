package gg.nova.core.loading;

/**
 * Alles, was der Ladebildschirm von der jeweiligen Minecraft-Version braucht.
 *
 * <ul>
 * <li>progress: Fortschritt des Ressourcen-Ladens (0–1)
 * <li>backgroundAlpha: Deckkraft von Himmel und Landschaft (Ein-/Ausblenden)
 * <li>contentAlpha: Deckkraft von Logo und Texten
 * <li>barAlpha: Deckkraft des Fortschrittsbalkens
 * <li>complete: Laden abgeschlossen (Menü wird eingeblendet)
 * <li>reloading: Ressourcen werden neu geladen (nicht der Spielstart)
 * <li>german: Spielsprache ist Deutsch (Übersetzungen sind beim Start noch nicht geladen)
 * </ul>
 */
public final class LoadingInfo {
	private final float progress;
	private final float backgroundAlpha;
	private final float contentAlpha;
	private final float barAlpha;
	private final boolean complete;
	private final boolean reloading;
	private final boolean german;
	private final String minecraftVersion;
	private final String novaVersion;
	private final String loaderName;
	private final String loaderVersion;
	private final int modCount;

	public LoadingInfo(float progress, float backgroundAlpha, float contentAlpha, float barAlpha, boolean complete, boolean reloading, boolean german, String minecraftVersion, String novaVersion, String loaderName, String loaderVersion, int modCount) {
		this.progress = progress;
		this.backgroundAlpha = backgroundAlpha;
		this.contentAlpha = contentAlpha;
		this.barAlpha = barAlpha;
		this.complete = complete;
		this.reloading = reloading;
		this.german = german;
		this.minecraftVersion = minecraftVersion;
		this.novaVersion = novaVersion;
		this.loaderName = loaderName;
		this.loaderVersion = loaderVersion;
		this.modCount = modCount;
	}

	public float progress() {
		return this.progress;
	}

	public float backgroundAlpha() {
		return this.backgroundAlpha;
	}

	public float contentAlpha() {
		return this.contentAlpha;
	}

	public float barAlpha() {
		return this.barAlpha;
	}

	public boolean complete() {
		return this.complete;
	}

	public boolean reloading() {
		return this.reloading;
	}

	public boolean german() {
		return this.german;
	}

	public String minecraftVersion() {
		return this.minecraftVersion;
	}

	public String novaVersion() {
		return this.novaVersion;
	}

	public String loaderName() {
		return this.loaderName;
	}

	public String loaderVersion() {
		return this.loaderVersion;
	}

	public int modCount() {
		return this.modCount;
	}
}
