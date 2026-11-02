package gg.nova.client.mixin;

import gg.nova.core.cosmetic.Cosmetics;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.object.equipment.ElytraModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Dünne Elytra-Flügel (0,5 Pixel), wenn ein angelegtes Cosmetic eine eigene Elytra-Textur mitbringt. */
@Mixin(ElytraModel.class)
public abstract class ElytraModelMixin {
	@ModifyArg(method = "createLayer", index = 6, at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/model/geom/builders/CubeListBuilder;addBox(FFFFFFLnet/minecraft/client/model/geom/builders/CubeDeformation;)Lnet/minecraft/client/model/geom/builders/CubeListBuilder;"))
	private static CubeDeformation nova$thinWings(CubeDeformation deformation) {
		return Cosmetics.elytraTexturePath() != null ? new CubeDeformation(1.0F, 1.0F, Cosmetics.ELYTRA_DEPTH_DILATION) : deformation;
	}
}
