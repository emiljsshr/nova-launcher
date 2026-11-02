package gg.nova.client.mixin;

import gg.nova.client.NovaCosmeticsFeature;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hängt die Nova-Cosmetics als zusätzliche Ebene an den Spieler-Renderer. */
@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerRendererMixin extends LivingEntityRenderer<AbstractClientPlayerEntity> {
	private PlayerRendererMixin(EntityRenderDispatcher dispatcher, EntityModel model, float shadowSize) {
		super(dispatcher, model, shadowSize);
	}

	// Nur der Konstruktor mit Slim-Flag: der andere ruft ihn auf, sonst käme die Ebene doppelt dazu
	@Inject(method = "<init>(Lnet/minecraft/client/render/entity/EntityRenderDispatcher;Z)V", at = @At("TAIL"))
	private void nova$addCosmetics(EntityRenderDispatcher dispatcher, boolean slim, CallbackInfo info) {
		this.addFeature(new NovaCosmeticsFeature((PlayerEntityRenderer) (Object) this));
	}
}
