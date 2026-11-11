package alku.taczai.overlay;

import alku.taczai.teammate.TeammateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
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

        Vec3 camera = event.getCamera().getPosition();
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        boolean builderStarted = false;

        RenderSystem.disableDepthTest();
        try {
            for (Player player : minecraft.level.players()) {
                if (player == localPlayer || !player.isAlive()) continue;
                boolean teammate = TeammateManager.isEffectiveTeammate(localPlayer, player);
                if (!shouldOutline(false, teammate, player.isAlive())) continue;

                if (!builderStarted) {
                    RenderSystem.setShader(GameRenderer::getPositionColorShader);
                    builder.begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR);
                    builderStarted = true;
                }
                AABB box = player.getBoundingBox().inflate(0.03).move(-camera.x, -camera.y, -camera.z);
                LevelRenderer.renderLineBox(
                        event.getPoseStack(),
                        builder,
                        box,
                        RED,
                        GREEN,
                        BLUE,
                        1.0F
                );
            }

            if (builderStarted) BufferUploader.drawWithShader(builder.end());
        } finally {
            RenderSystem.enableDepthTest();
        }
    }

    static boolean shouldOutline(boolean samePlayer, boolean teammate, boolean alive) {
        return !samePlayer && !teammate && alive;
    }
}
