package gg.nova.client.mixin;

import net.minecraft.client.option.GameOptions;
import org.spongepowered.asm.mixin.Mixin;

/** In 1.8.9 sind Optionen öffentliche Felder; leer, weil alle Versionen dieselbe Mixin-Liste teilen. */
@Mixin(GameOptions.class)
public interface OptionAccessor {
}
