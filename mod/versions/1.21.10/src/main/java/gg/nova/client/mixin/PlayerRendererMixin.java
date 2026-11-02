package gg.nova.client.mixin;

import gg.nova.client.NovaCosmeticsFeature;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hängt die Nova-Cosmetics als zusätzliche Ebene an den Spieler-Renderer. */
@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerRendererMixin
		extends LivingEntityRenderer<AbstractClientPlayerEntity, PlayerEntityRenderState, PlayerEntityModel> {
	private PlayerRendererMixin(EntityRendererFactory.Context context, PlayerEntityModel model, float shadowRadius) {
		super(context, model, shadowRadius);
	}

	@Inject(method = "<init>", at = @At("TAIL"))
	private void nova$addCosmetics(EntityRendererFactory.Context context, boolean slim, CallbackInfo info) {
		this.addFeature(new NovaCosmeticsFeature(this));
	}
}
