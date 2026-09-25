package alku.taczai.overlay;

import alku.taczai.Config;
import alku.taczai.aimbot.TargetSelector;
import alku.taczai.keybind.KeyMappings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

/**
 * Draws the auto-aim FOV cone as a ring around the crosshair.
 *
 * <p>{@link TargetSelector} accepts a target as soon as one of its sample points
 * is within {@link Config#aimbotFov} degrees of the look direction, so the cone
 * is a circle centred on the crosshair. The radius follows from the pinhole
 * projection used to render the world: a half angle of {@code a} degrees covers
 * {@code tan(a)} world units per unit of distance, and one unit of distance is
 * {@code guiHeight / 2 * m11} GUI pixels, where {@code m11} is the vertical
 * entry of the projection matrix.
 */
@OnlyIn(Dist.CLIENT)
public final class FovCircleRenderer {
    /** Vertical FOV assumed until the first viewport event arrives. */
    private static final double DEFAULT_FOV = 70.0;
    /** tan() runs away towards infinity at 90 degrees, so the cone is capped just below it. */
    static final double MAX_DRAWABLE_ANGLE = 89.9;
    /** Below this the ring is not readable and only adds clutter. */
    static final double MIN_RADIUS = 2.0;
    private static final double RING_HALF_THICKNESS = 0.5;
    private static final double HALO_HALF_THICKNESS = 1.5;
    private static final int HALO_COLOR = 0x90000000;
    private static final int RING_COLOR = 0xD0E8FFFF;
    private static final int LOCKED_COLOR = 0xD055FF55;

    private static double viewportFov = DEFAULT_FOV;

    private FovCircleRenderer() {
    }

    /**
     * Records the FOV the world is rendered with, sprint and zoom modifiers included.
     * Values outside the sane range are ignored so a stray event cannot poison the ring.
     */
    public static void updateViewportFov(double fovDegrees) {
        if (!Double.isFinite(fovDegrees) || fovDegrees < 1.0 || fovDegrees > 179.0) return;
        viewportFov = fovDegrees;
    }

    static double viewportFov() {
        return viewportFov;
    }

    public static void render(GuiGraphics guiGraphics, int screenWidth, int screenHeight) {
        if (!KeyMappings.aimbotEnabled || !Config.showFovCircle) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) return;
        if (!minecraft.options.getCameraType().isFirstPerson()) return;

        Matrix4f projection = minecraft.gameRenderer.getProjectionMatrix(viewportFov);
        double radius = computeRadiusPixels(screenHeight, projection.m11(), Config.aimbotFov);
        if (!(radius >= MIN_RADIUS)) return;
        // Past the screen diagonal all that is left on screen is the flat middle
        // of the arc; capping keeps the scanline loop bounded.
        radius = Math.min(radius, Math.hypot(screenWidth, screenHeight));

        int centerX = screenWidth / 2;
        int centerY = screenHeight / 2;
        boolean locked = TargetSelector.getConfirmedTarget() != null;

        drawRing(guiGraphics, centerX, centerY, radius, HALO_HALF_THICKNESS, HALO_COLOR);
        drawRing(guiGraphics, centerX, centerY, radius, RING_HALF_THICKNESS, locked ? LOCKED_COLOR : RING_COLOR);
    }

    /**
     * Radius in GUI pixels of the circle marking the FOV cone.
     *
     * @param guiHeight     height of the GUI in GUI pixels
     * @param projectionM11 vertical entry of the world projection matrix, {@code 1 / tan(fov / 2)}
     * @param angleDegrees  half angle of the cone in degrees
     */
    static double computeRadiusPixels(double guiHeight, double projectionM11, double angleDegrees) {
        if (!Double.isFinite(guiHeight) || guiHeight <= 0.0) return 0.0;
        if (!Double.isFinite(projectionM11) || projectionM11 <= 0.0) return 0.0;
        return guiHeight * 0.5 * projectionM11 * Math.tan(Math.toRadians(clampAngle(angleDegrees)));
    }

    static double clampAngle(double angleDegrees) {
        if (!Double.isFinite(angleDegrees)) return 0.0;
        return Math.max(0.0, Math.min(MAX_DRAWABLE_ANGLE, angleDegrees));
    }

    /**
     * Rasterises the ring one scanline at a time, with {@code halfThickness}
     * measured along both axes, so the stroke keeps a constant width all around.
     */
    static void drawRing(GuiGraphics guiGraphics, int centerX, int centerY, double radius, double halfThickness, int color) {
        if (!(radius > 0.0) || !(halfThickness > 0.0)) return;

        int maxRow = (int) Math.floor(radius + halfThickness);
        for (int dy = -maxRow; dy <= maxRow; dy++) {
            double outerHalf = ringOuterHalfWidth(radius, halfThickness, dy);
            if (!(outerHalf > 0.0)) continue;

            int from = (int) Math.round(ringInnerHalfWidth(radius, halfThickness, dy));
            int to = Math.max(from + 1, (int) Math.round(outerHalf));
            int y = centerY + dy;
            guiGraphics.fill(centerX + from, y, centerX + to, y + 1, color);
            guiGraphics.fill(centerX - to, y, centerX - from, y + 1, color);
        }
    }

    /** Horizontal distance from the ring centre to its outer edge on the scanline {@code dy}. */
    static double ringOuterHalfWidth(double radius, double halfThickness, int dy) {
        return halfWidthAt(radius + Math.max(0.0, halfThickness), dy);
    }

    /** Horizontal distance from the ring centre to its inner edge on the scanline {@code dy}. */
    static double ringInnerHalfWidth(double radius, double halfThickness, int dy) {
        return halfWidthAt(Math.max(0.0, radius - Math.max(0.0, halfThickness)), dy);
    }

    private static double halfWidthAt(double circleRadius, int dy) {
        if (!Double.isFinite(circleRadius) || circleRadius <= 0.0) return 0.0;
        double row = Math.abs((double) dy);
        if (row >= circleRadius) return 0.0;
        return Math.sqrt(circleRadius * circleRadius - row * row);
    }
}
