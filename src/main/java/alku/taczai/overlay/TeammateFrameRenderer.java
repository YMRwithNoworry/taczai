package alku.taczai.overlay;

import alku.taczai.keybind.KeyMappings;
import alku.taczai.teammate.TeammateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class TeammateFrameRenderer {
    @SubscribeEvent
    public void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;

        Minecraft minecraft = Minecraft.getInstance();
        Player localPlayer = minecraft.player;
        if (localPlayer == null || minecraft.level == null) return;

        PoseStack poseStack = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();

        RenderSystem.disableDepthTest();
        try {
            for (Player player : minecraft.level.players()) {
                boolean teammate = TeammateManager.isEffectiveTeammate(localPlayer, player);
                if (!shouldRender(KeyMappings.aimbotEnabled, player == localPlayer, teammate, player.isAlive())) continue;

                AABB box = player.getBoundingBox().inflate(0.03).move(-camera.x, -camera.y, -camera.z);
                LevelRenderer.renderLineBox(poseStack, buffers.getBuffer(RenderType.lines()), box, 0.1F, 1.0F, 0.1F, 1.0F);
            }
            buffers.endBatch(RenderType.lines());
        } finally {
            RenderSystem.enableDepthTest();
        }
    }

    static boolean shouldRender(boolean enabled, boolean samePlayer, boolean teammate, boolean alive) {
        return enabled && !samePlayer && teammate && alive;
    }
}
