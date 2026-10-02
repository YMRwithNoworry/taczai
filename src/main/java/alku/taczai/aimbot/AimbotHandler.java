package alku.taczai.aimbot;

import alku.taczai.Config;
import alku.taczai.keybind.AimbotTargetChangedEvent;
import alku.taczai.keybind.KeyMappings;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.entity.ShootResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Arrays;

@OnlyIn(Dist.CLIENT)
public class AimbotHandler {
    /** TACZ returns COOL_DOWN while the remaining client cooldown is 50 ms or more. */
    private static final long SHOOT_COOLDOWN_THRESHOLD = 50L;
    /** TACZ only applies the full ADS accuracy bonus once aiming progress reaches 1. */
    private static final float AIM_PROGRESS_READY = 0.999F;
    /** Number of ticks of aim movement averaged to notice fast moving targets. */
    private static final int AIM_MOTION_SAMPLES = 3;

    private LivingEntity lockedTarget = null;
    private LivingEntity decisionTarget = null;
    private AimDecision aimDecision = null;
    private boolean forcedAim = false;

    /** Aim the local camera is rendered at while the player model turns gradually. */
    private boolean cameraAimActive = false;
    private float cameraAimYaw = 0.0F;
    private float cameraAimPitch = 0.0F;

    /** Recent per tick movement of the aim itself, used to keep up with fast targets. */
    private final float[] aimMotion = new float[AIM_MOTION_SAMPLES];
    private int aimMotionIndex = 0;
    private float previousAimYaw = Float.NaN;
    private float previousAimPitch = Float.NaN;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            clearAimDecision();
            releaseCameraAim();
            forcedAim = false;
            return;
        }
        if (mc.level == null) {
            lockedTarget = null;
            clearAimDecision();
            releaseCameraAim();
            updateForcedAim(mc.player, false);
            return;
        }

        Player player = mc.player;

        if (!isHoldingTaczGun(player)) {
            lockedTarget = null;
            clearAimDecision();
            releaseCameraAim();
            updateForcedAim(mc.player, false);
            return;
        }

        if (!KeyMappings.aimbotEnabled) {
            lockedTarget = null;
            clearAimDecision();
            releaseCameraAim();
            updateForcedAim(mc.player, false);
            return;
        }

        if (!TargetSelector.isTrackableTarget(player, lockedTarget)) {
            lockedTarget = TargetSelector.getActiveTarget(player);
        }
        if (lockedTarget == null) {
            clearAimDecision();
            releaseCameraAim();
            updateForcedAim(mc.player, false);
            return;
        }

        AimDecision decision = getAimDecision(lockedTarget);
        updateForcedAim(mc.player, Config.autoFire);

        float[] targetRot = RotationHelper.getTargetRotation(player, lockedTarget, decision);
        // The rotation the server sees - and therefore what other players see on the
        // model - is turned at a limited rate instead of snapping in a single tick.
        float maxStep = effectiveStep(Config.aimTurnSpeed, updateAimMotion(targetRot));
        boolean aimReached = RotationHelper.turnTowards(player, targetRot[0], targetRot[1], maxStep);
        holdCameraOnAim(targetRot[0], targetRot[1]);
        if (Config.autoFire && mc.screen == null) handleAutoFire(player, targetRot, aimReached);
    }

    /**
     * Renders the local view on the aim even while the player rotation is still
     * turning, so the shooter does not have to watch the crosshair travel.
     */
    @SubscribeEvent
    public void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (!cameraAimActive) return;

        Minecraft mc = Minecraft.getInstance();
        if (!(event.getCamera().getEntity() instanceof LocalPlayer cameraPlayer) || cameraPlayer != mc.player) return;

        float yawOffset = Mth.wrapDegrees(cameraAimYaw - cameraPlayer.getYRot());
        float pitchOffset = cameraAimPitch - cameraPlayer.getXRot();
        event.setYaw(event.getYaw() + yawOffset);
        event.setPitch(Mth.clamp(event.getPitch() + pitchOffset, -90.0F, 90.0F));
    }

    /**
     * Averages the last few ticks of aim movement. A single target switch makes the
     * aim jump once and must stay gradual, while a target that keeps moving faster
     * than the configured rate has to be followed or auto fire would never line up.
     */
    private float updateAimMotion(float[] targetRotation) {
        float motion = 0.0F;
        if (!Float.isNaN(previousAimYaw)) {
            motion = Math.max(
                    Math.abs(Mth.degreesDifference(previousAimYaw, targetRotation[0])),
                    Math.abs(Mth.degreesDifference(previousAimPitch, targetRotation[1]))
            );
        }
        previousAimYaw = targetRotation[0];
        previousAimPitch = targetRotation[1];

        aimMotion[aimMotionIndex] = motion;
        aimMotionIndex = (aimMotionIndex + 1) % aimMotion.length;

        float sum = 0.0F;
        for (float sample : aimMotion) sum += sample;
        return sum / aimMotion.length;
    }

    static float effectiveStep(double configuredStep, float averageAimMotion) {
        return (float) Math.max(configuredStep, averageAimMotion);
    }

    private void holdCameraOnAim(float targetYaw, float targetPitch) {
        cameraAimActive = true;
        cameraAimYaw = targetYaw;
        cameraAimPitch = targetPitch;
    }

    private void releaseCameraAim() {
        cameraAimActive = false;
        previousAimYaw = Float.NaN;
        previousAimPitch = Float.NaN;
        Arrays.fill(aimMotion, 0.0F);
        aimMotionIndex = 0;
    }

    private AimDecision getAimDecision(LivingEntity target) {
        if (aimDecision == null
                || decisionTarget != target
                || !aimDecision.matches(Config.aimAtHead, Config.headshotRate)) {
            aimDecision = AimDecision.sample(Config.aimAtHead, Config.headshotRate);
            decisionTarget = target;
        }
        return aimDecision;
    }

    private void clearAimDecision() {
        aimDecision = null;
        decisionTarget = null;
    }

    private void handleAutoFire(Player player, float[] targetRotation, boolean aimReached) {
        if (!(player instanceof LocalPlayer localPlayer)) return;

        IClientPlayerGunOperator operator = IClientPlayerGunOperator.fromLocalPlayer(localPlayer);
        if (operator == null) return;

        IGunOperator gunOperator = IGunOperator.fromLivingEntity(localPlayer);
        boolean reloading = gunOperator.getSynReloadState().getStateType().isReloading();
        boolean aimed = gunOperator.getSynAimingProgress() >= AIM_PROGRESS_READY;
        long shootCooldown = operator.getClientShootCoolDown();
        if (!shouldAutoFire(reloading, aimed, shootCooldown)) return;

        // TACZ takes the shot direction from the server side rotation, and the server
        // only knows the rotation that was already sent. Shooting before the visible
        // turn finished would fire along the still travelling rotation.
        if (!aimReached) return;

        // The turn is already at the aim at this point; this only removes the last
        // fraction of a degree, which other players cannot see.
        RotationHelper.applySnapRotation(localPlayer, targetRotation[0], targetRotation[1]);
        syncAimToServer(localPlayer);

        ShootResult result = operator.shoot();
        if (result == ShootResult.SUCCESS) clearAimDecision();
    }

    private void syncAimToServer(LocalPlayer player) {
        player.connection.send(new ServerboundMovePlayerPacket.Rot(
                player.getYRot(),
                player.getXRot(),
                player.onGround()
        ));
    }

    private void updateForcedAim(LocalPlayer player, boolean shouldAim) {
        IClientPlayerGunOperator operator = IClientPlayerGunOperator.fromLocalPlayer(player);
        if (operator == null) {
            forcedAim = false;
            return;
        }

        IGunOperator gunOperator = IGunOperator.fromLivingEntity(player);
        boolean clientAiming = operator.isAim();
        boolean serverAiming = gunOperator.getSynIsAiming();

        if (shouldAim && (!clientAiming || !serverAiming)) {
            // Re-send while the server has not confirmed the aim state yet. This
            // prevents a silent desync from permanently stalling ADS progress.
            operator.aim(true);
            forcedAim = true;
        } else if (!shouldAim && forcedAim) {
            operator.aim(false);
            forcedAim = false;
        }
    }

    static boolean shouldAutoFire(boolean reloading, boolean aimed, long shootCooldown) {
        return !reloading
                && aimed
                && shootCooldown >= 0L
                && shootCooldown < SHOOT_COOLDOWN_THRESHOLD;
    }

    @SubscribeEvent
    public void onTargetChanged(AimbotTargetChangedEvent event) {
        if (event.getTarget() == null) {
            lockedTarget = null;
            clearAimDecision();
        }
    }

    private boolean isHoldingTaczGun(Player player) {
        ItemStack mainHand = player.getItemBySlot(EquipmentSlot.MAINHAND);
        ItemStack offHand = player.getItemBySlot(EquipmentSlot.OFFHAND);
        return isTaczItem(mainHand) || isTaczItem(offHand);
    }

    private boolean isTaczItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.getItem() instanceof com.tacz.guns.api.item.IGun;
    }
}
