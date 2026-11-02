package gg.nova.client.mixin;

import net.minecraft.client.render.entity.PlayerEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;

/** 1.8.9 kennt keine Elytra; leer, weil alle Versionen dieselbe Mixin-Liste teilen. */
@Mixin(PlayerEntityRenderer.class)
public abstract class ElytraModelMixin {
}
