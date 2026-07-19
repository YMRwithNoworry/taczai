package alku.taczai.overlay;

import alku.taczai.keybind.KeyMappings;
import alku.taczai.teammate.TeammateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class EnemyOutlineHandler {
    private static final float RED = 1.0F;
    private static final float GREEN = 0.1F;
    private static final float BLUE = 0.1F;

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
                boolean enemy = shouldOutline(
                        KeyMappings.aimbotEnabled,
                        player == localPlayer,
                        TeammateManager.isEffectiveTeammate(localPlayer, player),
                        player.isAlive()
                );
                if (!enemy) continue;

                AABB box = player.getBoundingBox().inflate(0.03).move(-camera.x, -camera.y, -camera.z);
                LevelRenderer.renderLineBox(
                        poseStack,
                        buffers.getBuffer(RenderType.lines()),
                        box,
                        RED,
                        GREEN,
                        BLUE,
                        1.0F
                );
            }
            buffers.endBatch(RenderType.lines());
        } finally {
            RenderSystem.enableDepthTest();
        }
    }

    static boolean shouldOutline(boolean enabled, boolean samePlayer, boolean teammate, boolean alive) {
        return enabled && !samePlayer && !teammate && alive;
    }
}
