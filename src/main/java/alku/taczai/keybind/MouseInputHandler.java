package alku.taczai.keybind;

import alku.taczai.aimbot.TargetSelector;
import alku.taczai.teammate.TeammateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;

@OnlyIn(Dist.CLIENT)
public class MouseInputHandler {

    public static void register() {
        MinecraftForge.EVENT_BUS.register(new MouseInputHandler());
    }

    @SubscribeEvent
    public void onMouseButton(InputEvent.MouseButton.Pre event) {
        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_MIDDLE && event.getAction() == GLFW.GLFW_PRESS) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player == null || minecraft.screen != null) return;

            Player target = TargetSelector.raytracePlayer(minecraft.player);
            if (target == null) return;

            event.setCanceled(true);
            boolean added = TeammateManager.toggleAndSave(target);
            TargetSelector.resetIfTarget(target);
            String key = added ? "message.taczai.teammate_added" : "message.taczai.teammate_removed";
            minecraft.player.displayClientMessage(Component.translatable(key, target.getName()), true);
        }
    }
}
