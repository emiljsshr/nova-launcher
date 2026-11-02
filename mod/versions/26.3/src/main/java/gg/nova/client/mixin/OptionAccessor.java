package gg.nova.client.mixin;

import net.minecraft.client.OptionInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Setzt den Wert einer Option ohne Prüfung (Fullbright braucht Helligkeit über 100 %). */
@Mixin(OptionInstance.class)
public interface OptionAccessor {
	@Accessor("value")
	void nova$setValue(Object value);
}
