package dev.wp.matter_manipulator.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.wp.matter_manipulator.MMMod;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.api.distmarker.Dist;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = MMMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class MMKeyMappings {

    public static final String CATEGORY = "key.categories." + MMMod.MODID;

    public static final KeyMapping LOCK_POSITIONS = new KeyMapping(
        key("lock_positions"),
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_T,
        CATEGORY
    );

    public static final KeyMapping CUT = new KeyMapping(
        key("cut"),
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_X,
        CATEGORY
    );

    public static final KeyMapping COPY = new KeyMapping(
        key("copy"),
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_C,
        CATEGORY
    );

    public static final KeyMapping PASTE = new KeyMapping(
        key("paste"),
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_V,
        CATEGORY
    );

    public static final KeyMapping RESET = new KeyMapping(
        key("reset"),
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_Z,
        CATEGORY
    );

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(LOCK_POSITIONS);
        event.register(CUT);
        event.register(COPY);
        event.register(PASTE);
        event.register(RESET);
    }

    private static String key(String key){
        return "key." + MMMod.MODID + "." + key;
    }
}
