package alku.taczai.overlay;

import alku.taczai.keybind.KeyMappings;
import alku.taczai.teammate.TeammateManager;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class EnemyOutlineHandler {
    private static final class OutlineRenderType {
        private static final RenderType THROUGH_WALL_LINES = RenderType.create(
                "taczai_enemy_outline",
                DefaultVertexFormat.POSITION_COLOR,
                VertexFormat.Mode.LINES,
                256,
                false,
                false,
                RenderType.CompositeState.builder()
                        .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getPositionColorShader))
                        .setDepthTestState(RenderStateAccess.noDepthTest())
                        .createCompositeState(false)
        );
    }
    private static final float RED = 1.0F;
    private static final float GREEN = 0.1F;
    private static final float BLUE = 0.1F;

    private abstract static class RenderStateAccess extends RenderStateShard {
        private RenderStateAccess() {
            super("taczai_render_state_access", () -> {}, () -> {});
        }

        private static DepthTestStateShard noDepthTest() {
            return NO_DEPTH_TEST;
        }
    }

    @SubscribeEvent
    public void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || !KeyMappings.aimbotEnabled) return;

        Minecraft minecraft = Minecraft.getInstance();
        Player localPlayer = minecraft.player;
        if (localPlayer == null || minecraft.level == null) return;

        Vec3 camera = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        boolean hasOutlines = false;

        for (Player player : minecraft.level.players()) {
            if (player == localPlayer || !player.isAlive()) continue;
            boolean teammate = TeammateManager.isEffectiveTeammate(localPlayer, player);
            if (!shouldOutline(true, false, teammate, true)) continue;

            AABB box = player.getBoundingBox().inflate(0.03).move(-camera.x, -camera.y, -camera.z);
            LevelRenderer.renderLineBox(
                    event.getPoseStack(),
                    buffers.getBuffer(OutlineRenderType.THROUGH_WALL_LINES),
                    box,
                    RED,
                    GREEN,
                    BLUE,
                    1.0F
            );
            hasOutlines = true;
        }

        if (hasOutlines) buffers.endBatch(OutlineRenderType.THROUGH_WALL_LINES);
    }

    static boolean shouldOutline(boolean enabled, boolean samePlayer, boolean teammate, boolean alive) {
        return enabled && !samePlayer && !teammate && alive;
    }
}
