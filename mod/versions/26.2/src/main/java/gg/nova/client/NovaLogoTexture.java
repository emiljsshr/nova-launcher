package gg.nova.client;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.io.InputStream;
import net.minecraft.client.renderer.texture.MipmapStrategy;
import net.minecraft.client.renderer.texture.ReloadableTexture;
import net.minecraft.client.renderer.texture.TextureContents;
import net.minecraft.client.resources.metadata.texture.TextureMetadataSection;
import net.minecraft.server.packs.resources.ResourceManager;

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
			return new TextureContents(NativeImage.read(stream), new TextureMetadataSection(true, true, MipmapStrategy.MEAN, 0.0F));
		}
	}
}
