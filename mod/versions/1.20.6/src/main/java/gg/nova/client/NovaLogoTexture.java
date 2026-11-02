package gg.nova.client;

import java.io.IOException;
import java.io.InputStream;
import net.minecraft.client.resource.metadata.TextureResourceMetadata;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.ResourceTexture;
import net.minecraft.resource.ResourceManager;

/**
 * Lädt das Logo direkt aus der Mod-JAR – wie Vanillas Mojang-Logo aus dem Standard-Ressourcenpaket.
 * So ist es schon sichtbar, bevor beim Start die Ressourcenpakete fertig geladen sind.
 */
public class NovaLogoTexture extends ResourceTexture {
	private static final String PATH = "/assets/nova/textures/gui/logo.png";

	public NovaLogoTexture() {
		super(NovaClient.LOGO);
	}

	@Override
	protected ResourceTexture.TextureData loadTextureData(ResourceManager resourceManager) {
		try (InputStream stream = NovaLogoTexture.class.getResourceAsStream(PATH)) {
			if (stream == null) {
				return new ResourceTexture.TextureData(new IOException("Nova-Logo fehlt in der Mod: " + PATH));
			}
			// blur = true: das Logo wird stark verkleinert und soll weich skaliert werden
			return new ResourceTexture.TextureData(new TextureResourceMetadata(true, true), NativeImage.read(stream));
		} catch (IOException exception) {
			return new ResourceTexture.TextureData(exception);
		}
	}
}
