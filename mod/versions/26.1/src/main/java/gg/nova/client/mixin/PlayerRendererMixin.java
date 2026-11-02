package gg.nova.client.mixin;

import gg.nova.client.NovaCosmeticsLayer;
import net.minecraft.client.entity.ClientAvatarEntity;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hängt die Nova-Cosmetics als zusätzliche Ebene an den Spieler-Renderer. */
@Mixin(AvatarRenderer.class)
public abstract class PlayerRendererMixin<T extends Avatar & ClientAvatarEntity>
		extends LivingEntityRenderer<T, AvatarRenderState, PlayerModel> {
	private PlayerRendererMixin(EntityRendererProvider.Context context, PlayerModel model, float shadowRadius) {
		super(context, model, shadowRadius);
	}

	@Inject(method = "<init>", at = @At("TAIL"))
	private void nova$addCosmetics(EntityRendererProvider.Context context, boolean slim, CallbackInfo info) {
		this.addLayer(new NovaCosmeticsLayer(this));
	}
}
