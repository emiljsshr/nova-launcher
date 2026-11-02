package gg.nova.client;

import java.io.IOException;
import java.io.InputStream;
import net.minecraft.client.resource.metadata.TextureResourceMetadata;
import net.minecraft.client.texture.MipmapStrategy;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.ReloadableTexture;
import net.minecraft.client.texture.TextureContents;
import net.minecraft.resource.ResourceManager;

/**
 * Lädt das Logo direkt aus der Mod-JAR – wie Vanillas Mojang-Logo aus dem Standard-Ressourcenpaket.
 * So ist es schon sichtbar, bevor beim Start die Ressourcenpakete fertig geladen sind.
 */
public class NovaLogoTexture extends ReloadableTexture {
	private static final String PATH = "/assets/nova/textures/gui/logo.png";

	public NovaLogoTexture() {
		super(NovaClient.LOGO);
	}

	@Override
	public TextureContents loadContents(ResourceManager resourceManager) throws IOException {
		try (InputStream stream = NovaLogoTexture.class.getResourceAsStream(PATH)) {
			if (stream == null) {
				throw new IOException("Nova-Logo fehlt in der Mod: " + PATH);
			}
			// blur = true: das Logo wird stark verkleinert und soll weich skaliert werden
			return new TextureContents(NativeImage.read(stream), new TextureResourceMetadata(true, true, MipmapStrategy.MEAN, 0.0F));
		}
	}
}
