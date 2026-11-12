package alku.taczai.overlay;

import alku.taczai.aimbot.TaczHitboxCompat;
import alku.taczai.teammate.TeammateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
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

        PoseStack poseStack = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        double renderRange = minecraft.options.renderDistance().get() * 16.0;
        AABB renderArea = localPlayer.getBoundingBox().inflate(renderRange);
        List<Entity> enemies = minecraft.level.getEntitiesOfClass(
                Entity.class,
                renderArea,
                entity -> isEnemyTarget(localPlayer, entity)
        );
        if (enemies.isEmpty()) return;

        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer consumer = buffers.getBuffer(RenderType.lines());
        RenderSystem.disableDepthTest();
        try {
            for (Entity enemy : enemies) {
                for (List<Vec3> corners : TaczHitboxCompat.getRenderHitboxCorners(
                        enemy, event.getPartialTick()
                )) {
                    renderBoxEdges(poseStack, consumer, corners, camera);
                }
            }
            buffers.endBatch(RenderType.lines());
        } finally {
            RenderSystem.enableDepthTest();
        }
    }

    private static void renderBoxEdges(
            PoseStack poseStack,
            VertexConsumer consumer,
            List<Vec3> corners,
            Vec3 camera
    ) {
        if (corners.size() != 8) return;

        PoseStack.Pose pose = poseStack.last();
        for (int[] edge : TaczHitboxCompat.boxEdges()) {
            Vec3 from = corners.get(edge[0]).subtract(camera);
            Vec3 to = corners.get(edge[1]).subtract(camera);
            Vec3 normal = to.subtract(from).normalize();
            putVertex(consumer, pose, from, normal);
            putVertex(consumer, pose, to, normal);
        }
    }

    private static void putVertex(VertexConsumer consumer, PoseStack.Pose pose, Vec3 point, Vec3 normal) {
        consumer.vertex(pose.pose(), (float) point.x, (float) point.y, (float) point.z)
                .color(RED, GREEN, BLUE, 1.0F)
                .normal(pose.normal(), (float) normal.x, (float) normal.y, (float) normal.z)
                .endVertex();
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
