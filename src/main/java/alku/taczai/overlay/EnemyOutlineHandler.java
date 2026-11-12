package alku.taczai.overlay;

import alku.taczai.aimbot.TaczHitboxCompat;
import alku.taczai.teammate.TeammateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.List;

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
        double renderRange = minecraft.options.renderDistance().get() * 16.0;
        AABB renderArea = localPlayer.getBoundingBox().inflate(renderRange);
        List<Entity> enemies = minecraft.level.getEntitiesOfClass(
                Entity.class,
                renderArea,
                entity -> isEnemyTarget(localPlayer, entity)
        );
        if (enemies.isEmpty()) return;

        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        RenderSystem.disableDepthTest();
        try {
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            builder.begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR);
            for (Entity enemy : enemies) {
                for (List<Vec3> corners : TaczHitboxCompat.getHitboxCorners(enemy, localPlayer)) {
                    renderBoxEdges(event, builder, corners, camera);
                }
            }
            BufferUploader.drawWithShader(builder.end());
        } finally {
            RenderSystem.enableDepthTest();
        }
    }

    private static void renderBoxEdges(
            RenderLevelStageEvent event,
            BufferBuilder builder,
            List<Vec3> corners,
            Vec3 camera
    ) {
        if (corners.size() != 8) return;

        var pose = event.getPoseStack().last();
        for (int[] edge : TaczHitboxCompat.boxEdges()) {
            Vec3 from = corners.get(edge[0]).subtract(camera);
            Vec3 to = corners.get(edge[1]).subtract(camera);
            builder.vertex(pose.pose(), (float) from.x, (float) from.y, (float) from.z)
                    .color(RED, GREEN, BLUE, 1.0F)
                    .endVertex();
            builder.vertex(pose.pose(), (float) to.x, (float) to.y, (float) to.z)
                    .color(RED, GREEN, BLUE, 1.0F)
                    .endVertex();
        }
    }

    static boolean isEnemyTarget(Player localPlayer, Entity entity) {
        if (localPlayer == null || entity == null) return false;
        boolean teammate = entity instanceof Player player && TeammateManager.isEffectiveTeammate(localPlayer, player);
        return shouldRenderTargetable(
                entity == localPlayer,
                teammate,
                entity.isAlive(),
                entity.isPickable(),
                entity.isSpectator()
        );
    }

    static boolean shouldRenderTargetable(
            boolean samePlayer,
            boolean teammate,
            boolean alive,
            boolean pickable,
            boolean spectator
    ) {
        return !samePlayer && !teammate && alive && pickable && !spectator;
    }
}
