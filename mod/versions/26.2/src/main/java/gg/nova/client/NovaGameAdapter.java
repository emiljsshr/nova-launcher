package gg.nova.client;

import com.mojang.blaze3d.platform.InputConstants;
import gg.nova.client.mixin.OptionAccessor;
import gg.nova.core.mods.NovaGame;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/** {@link NovaGame} für Minecraft 26.2: Spielzustand fürs HUD und die Optionen der Module. */
public final class NovaGameAdapter implements NovaGame {
	private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	/** Werte vor dem Einschalten eines Moduls, zum Wiederherstellen. */
	private final Map<Tweak, Object> previous = new EnumMap<>(Tweak.class);

	private static Minecraft minecraft() {
		return Minecraft.getInstance();
	}

	@Override
	public String translate(String key) {
		return I18n.get(key);
	}

	@Override
	public boolean inWorld() {
		return minecraft().player != null && minecraft().level != null;
	}

	@Override
	public boolean screenOpen() {
		return minecraft().gui.screen() != null;
	}

	@Override
	public int fps() {
		return minecraft().getFps();
	}

	@Override
	public int ping() {
		Minecraft minecraft = minecraft();
		if (minecraft.getConnection() == null || minecraft.player == null) {
			return 0;
		}
		PlayerInfo info = minecraft.getConnection().getPlayerInfo(minecraft.player.getUUID());
		return info == null ? 0 : info.getLatency();
	}

	@Override
	public double x() {
		return minecraft().player.getX();
	}

	@Override
	public double y() {
		return minecraft().player.getY();
	}

	@Override
	public double z() {
		return minecraft().player.getZ();
	}

	@Override
	public float yaw() {
		return minecraft().player.getYRot();
	}

	@Override
	public boolean key(Key key) {
		Options options = minecraft().options;
		return switch (key) {
			case FORWARD -> options.keyUp.isDown();
			case BACK -> options.keyDown.isDown();
			case LEFT -> options.keyLeft.isDown();
			case RIGHT -> options.keyRight.isDown();
			case JUMP -> options.keyJump.isDown();
			case SNEAK -> options.keyShift.isDown();
			case ATTACK -> options.keyAttack.isDown();
			case USE -> options.keyUse.isDown();
			case ZOOM -> InputConstants.isKeyDown(minecraft().getWindow(), InputConstants.KEY_C);
		};
	}

	@Override
	public List<Object> armor() {
		List<Object> result = new ArrayList<>();
		for (EquipmentSlot slot : ARMOR) {
			ItemStack stack = minecraft().player.getItemBySlot(slot);
			result.add(stack.isEmpty() ? null : stack);
		}
		return result;
	}

	@Override
	public List<Object> inventory() {
		List<Object> result = new ArrayList<>();
		for (int slot = 9; slot < 36; slot++) {
			ItemStack stack = minecraft().player.getInventory().getItem(slot);
			result.add(stack.isEmpty() ? null : stack);
		}
		return result;
	}

	@Override
	public int[] durability(Object item) {
		ItemStack stack = (ItemStack) item;
		return stack.isDamageableItem() ? new int[] {stack.getMaxDamage() - stack.getDamageValue(), stack.getMaxDamage()} : null;
	}

	@Override
	public List<Effect> effects() {
		List<Effect> result = new ArrayList<>();
		for (MobEffectInstance effect : minecraft().player.getActiveEffects()) {
			result.add(new Effect(effect.getEffect().value().getDisplayName().getString(), effect.getAmplifier(),
				effect.isInfiniteDuration() ? -1 : effect.getDuration()));
		}
		return result;
	}

	@Override
	public void setSprint(boolean pressed) {
		minecraft().options.keySprint.setDown(pressed);
	}

	@Override
	@SuppressWarnings("unchecked")
	public void tweak(Tweak tweak, boolean enabled) {
		Options options = minecraft().options;
		OptionInstance<Object> option = (OptionInstance<Object>) (Object) switch (tweak) {
			case FULLBRIGHT -> options.gamma();
			case NO_CLOUDS -> options.cloudStatus();
			case FEW_PARTICLES -> options.particles();
			case NO_SHADOWS -> options.entityShadows();
		};
		if (enabled) {
			this.previous.put(tweak, option.get());
			Object value = switch (tweak) {
				case FULLBRIGHT -> 16.0;
				case NO_CLOUDS -> CloudStatus.OFF;
				case FEW_PARTICLES -> ParticleStatus.MINIMAL;
				case NO_SHADOWS -> Boolean.FALSE;
			};
			// Helligkeit über 100 % lässt der Regler nicht zu – direkt setzen
			((OptionAccessor) (Object) option).nova$setValue(value);
		} else if (this.previous.containsKey(tweak)) {
			((OptionAccessor) (Object) option).nova$setValue(this.previous.remove(tweak));
		}
	}
}
