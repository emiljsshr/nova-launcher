package gg.nova.client;

import gg.nova.core.mods.NovaGame;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Keyboard;

/** {@link NovaGame} für Minecraft 1.8.9: Spielzustand fürs HUD; Optionen sind hier noch einfache Felder. */
public final class NovaGameAdapter implements NovaGame {
	/** Werte vor dem Einschalten eines Moduls, zum Wiederherstellen. */
	private final Map<Tweak, Object> previous = new EnumMap<Tweak, Object>(Tweak.class);

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
		return MinecraftClient.getCurrentFps();
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
		return client().player.x;
	}

	@Override
	public double y() {
		return client().player.y;
	}

	@Override
	public double z() {
		return client().player.z;
	}

	@Override
	public float yaw() {
		return client().player.yaw;
	}

	@Override
	public boolean key(Key key) {
		GameOptions options = client().options;
		switch (key) {
			case FORWARD:
				return options.forwardKey.isPressed();
			case BACK:
				return options.backKey.isPressed();
			case LEFT:
				return options.leftKey.isPressed();
			case RIGHT:
				return options.rightKey.isPressed();
			case JUMP:
				return options.jumpKey.isPressed();
			case SNEAK:
				return options.sneakKey.isPressed();
			case ATTACK:
				return options.attackKey.isPressed();
			case USE:
				return options.useKey.isPressed();
			default:
				return Keyboard.isKeyDown(Keyboard.KEY_C);
		}
	}

	@Override
	public List<Object> armor() {
		List<Object> result = new ArrayList<Object>();
		// 1.8.9 speichert die Rüstung von den Füßen aufwärts
		ItemStack[] armor = client().player.inventory.armor;
		for (int slot = armor.length - 1; slot >= 0; slot--) {
			result.add(armor[slot]);
		}
		return result;
	}

	@Override
	public List<Object> inventory() {
		List<Object> result = new ArrayList<Object>();
		ItemStack[] main = client().player.inventory.main;
		for (int slot = 9; slot < 36 && slot < main.length; slot++) {
			result.add(main[slot]);
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
		List<Effect> result = new ArrayList<Effect>();
		for (StatusEffectInstance effect : client().player.getStatusEffectInstances()) {
			result.add(new Effect(I18n.translate(effect.getTranslationKey()), effect.getAmplifier(),
				effect.isPermanent() ? -1 : effect.getDuration()));
		}
		return result;
	}

	@Override
	public void setSprint(boolean pressed) {
		KeyBinding.setKeyPressed(client().options.sprintKey.getCode(), pressed);
	}

	@Override
	public void tweak(Tweak tweak, boolean enabled) {
		GameOptions options = client().options;
		if (enabled) {
			switch (tweak) {
				case FULLBRIGHT:
					this.previous.put(tweak, options.gamma);
					options.gamma = 16.0F;
					break;
				case NO_CLOUDS:
					this.previous.put(tweak, options.cloudMode);
					options.cloudMode = 0;
					break;
				case FEW_PARTICLES:
					this.previous.put(tweak, options.particle);
					options.particle = 2;
					break;
				default:
					this.previous.put(tweak, options.entityShadows);
					options.entityShadows = false;
					break;
			}
			return;
		}
		Object old = this.previous.remove(tweak);
		if (old == null) {
			return;
		}
		switch (tweak) {
			case FULLBRIGHT:
				options.gamma = (Float) old;
				break;
			case NO_CLOUDS:
				options.cloudMode = (Integer) old;
				break;
			case FEW_PARTICLES:
				options.particle = (Integer) old;
				break;
			default:
				options.entityShadows = (Boolean) old;
				break;
		}
	}
}
