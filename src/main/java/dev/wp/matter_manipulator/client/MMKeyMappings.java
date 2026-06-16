package dev.wp.matter_manipulator.client;

import dev.wp.matter_manipulator.MMMod;

import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = MMMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class MMKeyMappings {
    public static final String CATEGORY = "key.categories." + MMMod.MODID;

    /**
     * Modifier key gating the other bindings.
     * If bound (default LCtrl), it must be held; if unbound, the other keys work bare.
     */
    public static final KeyMapping CONTROL = new KeyMapping(key("ctrl"), InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_CONTROL, CATEGORY);

    public static final KeyMapping LOCK_POSITIONS = new KeyMapping(key("lock_positions"), InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, CATEGORY);

    public static final KeyMapping CUT = new KeyMapping(key("cut"), InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X, CATEGORY);

    public static final KeyMapping COPY = new KeyMapping(key("copy"), InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, CATEGORY);

    public static final KeyMapping PASTE = new KeyMapping(key("paste"), InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, CATEGORY);

    public static final KeyMapping RESET = new KeyMapping(key("reset"), InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z, CATEGORY);

    /** Build start/cancel — unbound by default (sneak-right-click is the primary way to build). */
    public static final KeyMapping START_BUILD = new KeyMapping(key("start_build"), InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY);

    public static final KeyMapping CANCEL_BUILD = new KeyMapping(key("cancel_build"), InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY);

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(CONTROL);
        event.register(LOCK_POSITIONS);
        event.register(CUT);
        event.register(COPY);
        event.register(PASTE);
        event.register(RESET);
        event.register(START_BUILD);
        event.register(CANCEL_BUILD);
    }

    private static String key(String key) {
        return "key." + MMMod.MODID + "." + key;
    }
}
