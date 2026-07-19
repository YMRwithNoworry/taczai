package alku.taczai.keybind;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;

@OnlyIn(Dist.CLIENT)
public class KeyMappings {
    private static final String KEY_CATEGORY = "key.category.taczai";
    private static final String KEY_TOGGLE_AIMBOT = "key.taczai.toggle_aimbot";

    public static final KeyMapping TOGGLE_AIMBOT_KEY = new KeyMapping(
            KEY_TOGGLE_AIMBOT,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            KEY_CATEGORY
    );

    public static boolean aimbotEnabled = false;

    public static void register() {
        MinecraftForge.EVENT_BUS.register(new KeyMappings());
    }

    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE_AIMBOT_KEY);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        if (TOGGLE_AIMBOT_KEY.consumeClick()) {
            aimbotEnabled = !aimbotEnabled;
            if (!aimbotEnabled) {
                MinecraftForge.EVENT_BUS.post(new AimbotTargetChangedEvent(null));
            }
        }

    }
}
