package alku.taczai.overlay;

import alku.taczai.teammate.TeammateManager;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class TeammateNameRenderer {
    @SubscribeEvent
    public void onRenderNameTag(RenderNameTagEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(event.getEntity() instanceof Player player) || minecraft.player == null) return;
        if (!TeammateManager.isEffectiveTeammate(minecraft.player, player)) return;

        event.setContent(greenName(event.getContent()));
    }

    static Component greenName(Component name) {
        return Component.literal(name.getString()).withStyle(ChatFormatting.GREEN);
    }
}
