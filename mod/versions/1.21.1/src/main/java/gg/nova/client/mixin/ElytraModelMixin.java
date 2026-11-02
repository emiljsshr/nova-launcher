package gg.nova.client.mixin;

import gg.nova.core.cosmetic.Cosmetics;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.render.entity.model.ElytraEntityModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Dünne Elytra-Flügel (0,5 Pixel), wenn ein angelegtes Cosmetic eine eigene Elytra-Textur mitbringt. */
@Mixin(ElytraEntityModel.class)
public abstract class ElytraModelMixin {
	@ModifyArg(method = "getTexturedModelData", index = 6, at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/model/ModelPartBuilder;cuboid(FFFFFFLnet/minecraft/client/model/Dilation;)Lnet/minecraft/client/model/ModelPartBuilder;"))
	private static Dilation nova$thinWings(Dilation dilation) {
		return Cosmetics.elytraTexturePath() != null ? new Dilation(1.0F, 1.0F, Cosmetics.ELYTRA_DEPTH_DILATION) : dilation;
	}
}
