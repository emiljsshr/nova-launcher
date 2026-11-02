package gg.nova.client;

import gg.nova.client.mixin.OptionAccessor;
import gg.nova.core.mods.NovaGame;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.client.option.ParticlesMode;
import org.lwjgl.glfw.GLFW;

/** {@link NovaGame} für Minecraft 1.20.6: Spielzustand fürs HUD und die Optionen der Module. */
public final class NovaGameAdapter implements NovaGame {
	private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	/** Werte vor dem Einschalten eines Moduls, zum Wiederherstellen. */
	private final Map<Tweak, Object> previous = new EnumMap<>(Tweak.class);

	private static MinecraftClient client() {
		return MinecraftClient.getInstance();
	}

	@Override
	public String translate(String key) {
		return I18n.translate(key);
	}

	@Override
	public boolean inWorld() {
		return client().player != null && client().world != null;
	}

	@Override
	public boolean screenOpen() {
		return client().currentScreen != null;
	}

	@Override
	public int fps() {
		return client().getCurrentFps();
	}

	@Override
	public int ping() {
		MinecraftClient client = client();
		if (client.getNetworkHandler() == null || client.player == null) {
			return 0;
		}
		PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(client.player.getUuid());
		return entry == null ? 0 : entry.getLatency();
	}

	@Override
	public double x() {
		return client().player.getX();
	}

	@Override
	public double y() {
		return client().player.getY();
	}

	@Override
	public double z() {
		return client().player.getZ();
	}

	@Override
	public float yaw() {
		return client().player.getYaw();
	}

	@Override
	public boolean key(Key key) {
		GameOptions options = client().options;
		return switch (key) {
			case FORWARD -> options.forwardKey.isPressed();
			case BACK -> options.backKey.isPressed();
			case LEFT -> options.leftKey.isPressed();
			case RIGHT -> options.rightKey.isPressed();
			case JUMP -> options.jumpKey.isPressed();
			case SNEAK -> options.sneakKey.isPressed();
			case ATTACK -> options.attackKey.isPressed();
			case USE -> options.useKey.isPressed();
			case ZOOM -> InputUtil.isKeyPressed(client().getWindow().getHandle(), GLFW.GLFW_KEY_C);
		};
	}

	@Override
	public List<Object> armor() {
		List<Object> result = new ArrayList<>();
		for (EquipmentSlot slot : ARMOR) {
			ItemStack stack = client().player.getEquippedStack(slot);
			result.add(stack.isEmpty() ? null : stack);
		}
		return result;
	}

	@Override
	public List<Object> inventory() {
		List<Object> result = new ArrayList<>();
		for (int slot = 9; slot < 36; slot++) {
			ItemStack stack = client().player.getInventory().getStack(slot);
			result.add(stack.isEmpty() ? null : stack);
		}
		return result;
	}

	@Override
	public int[] durability(Object item) {
		ItemStack stack = (ItemStack) item;
		return stack.isDamageable() ? new int[] {stack.getMaxDamage() - stack.getDamage(), stack.getMaxDamage()} : null;
	}

	@Override
	public List<Effect> effects() {
		List<Effect> result = new ArrayList<>();
		for (StatusEffectInstance effect : client().player.getStatusEffects()) {
			result.add(new Effect(effect.getEffectType().value().getName().getString(), effect.getAmplifier(),
				effect.isInfinite() ? -1 : effect.getDuration()));
		}
		return result;
	}

	@Override
	public void setSprint(boolean pressed) {
		client().options.sprintKey.setPressed(pressed);
	}

	@Override
	@SuppressWarnings("unchecked")
	public void tweak(Tweak tweak, boolean enabled) {
		GameOptions options = client().options;
		SimpleOption<Object> option = (SimpleOption<Object>) (Object) switch (tweak) {
			case FULLBRIGHT -> options.getGamma();
			case NO_CLOUDS -> options.getCloudRenderMode();
			case FEW_PARTICLES -> options.getParticles();
			case NO_SHADOWS -> options.getEntityShadows();
		};
		if (enabled) {
			this.previous.put(tweak, option.getValue());
			Object value = switch (tweak) {
				case FULLBRIGHT -> 16.0;
				case NO_CLOUDS -> CloudRenderMode.OFF;
				case FEW_PARTICLES -> ParticlesMode.MINIMAL;
				case NO_SHADOWS -> Boolean.FALSE;
			};
			// Helligkeit über 100 % lässt der Regler nicht zu – direkt setzen
			((OptionAccessor) (Object) option).nova$setValue(value);
		} else if (this.previous.containsKey(tweak)) {
			((OptionAccessor) (Object) option).nova$setValue(this.previous.remove(tweak));
		}
	}
}
