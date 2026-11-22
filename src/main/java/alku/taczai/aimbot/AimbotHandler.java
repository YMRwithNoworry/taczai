package alku.taczai.aimbot;

import alku.taczai.Config;
import alku.taczai.keybind.AimbotTargetChangedEvent;
import alku.taczai.keybind.KeyMappings;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.entity.ShootResult;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.resource.index.CommonGunIndex;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.concurrent.ThreadLocalRandom;

@OnlyIn(Dist.CLIENT)
public class AimbotHandler {
    /** TACZ returns COOL_DOWN while the remaining client cooldown is 50 ms or more. */
    private static final long SHOOT_COOLDOWN_THRESHOLD = 50L;

    private LivingEntity lockedTarget = null;
    private LivingEntity decisionTarget = null;
    private AimDecision aimDecision = null;
    private boolean forcedAim = false;

    private Vec3 localAimPointOffset = null;

    private boolean aimActive = false;
    private long lastAimUpdateNanos = 0L;
    private final AimRotationController aimRotation = new AimRotationController();
    private boolean autoFireControllerHeld = false;
    private long autoFireLastShootTimestamp = Long.MIN_VALUE;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            TaczAutoFireCompat.setControllerShootDown(false);
            autoFireControllerHeld = false;
            lockedTarget = null;
            clearAimDecision();
            releaseAim();
            forcedAim = false;
            return;
        }
        if (mc.level == null) {
            releaseAutoFire(mc.player);
            lockedTarget = null;
            clearAimDecision();
            releaseAim();
            updateForcedAim(mc.player, false);
            return;
        }

        Player player = mc.player;

        if (!isHoldingTaczGun(player)) {
            releaseAutoFire(mc.player);
            lockedTarget = null;
            clearAimDecision();
            releaseAim();
            updateForcedAim(mc.player, false);
            return;
        }

        if (!KeyMappings.aimbotEnabled) {
            releaseAutoFire(mc.player);
            lockedTarget = null;
            clearAimDecision();
            releaseAim();
            updateForcedAim(mc.player, false);
            return;
        }

        if (!TargetSelector.isTrackableTarget(player, lockedTarget)) {
            LivingEntity previousTarget = lockedTarget;
            lockedTarget = TargetSelector.getActiveTarget(player);
            if (lockedTarget != previousTarget) {
                releaseAim();
            }
        }
        if (lockedTarget == null) {
            releaseAutoFire(mc.player);
            clearAimDecision();
            releaseAim();
            updateForcedAim(mc.player, false);
            return;
        }

        AimDecision decision = getAimDecision(lockedTarget);
        float[] targetRot = RotationHelper.getTargetRotation(
                player, lockedTarget, decision, 1.0F, localAimPointOffset
        );
        updateAim(player, targetRot);
        updateForcedAim(mc.player, Config.autoFire);
        if (Config.autoFire && mc.screen == null) {
            handleAutoFire(mc.player, targetRot[0], targetRot[1]);
        } else {
            releaseAutoFire(mc.player);
        }
    }

    /** Refreshes the locked aim for the interpolated target position every rendered frame. */
    @SubscribeEvent
    public void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (!aimActive || lockedTarget == null) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || event.getCamera().getEntity() != mc.player) return;

        if (!KeyMappings.aimbotEnabled
                || !isHoldingTaczGun(mc.player)
                || !lockedTarget.isAlive()
                || lockedTarget.level() != mc.level
                || lockedTarget.distanceToSqr(mc.player) > Config.aimbotRange * Config.aimbotRange) {
            releaseAim();
            return;
        }

        AimDecision decision = getAimDecision(lockedTarget);
        float[] targetRot = RotationHelper.getTargetRotation(
                mc.player, lockedTarget, decision, (float) event.getPartialTick(), localAimPointOffset
        );
        updateAim(mc.player, targetRot);
        event.setYaw(mc.player.getYRot());
        event.setPitch(mc.player.getXRot());
    }

    private void updateAim(Player player, float[] targetRotation) {
        if (!aimActive) {
            aimRotation.reset(player.getYRot(), player.getXRot());
            aimActive = true;
        }

        // Tick and render updates share one clock so each interval advances the turn once.
        long now = System.nanoTime();
        double elapsedSeconds = lastAimUpdateNanos == 0L
                ? 1.0 / 60.0
                : (now - lastAimUpdateNanos) / 1_000_000_000.0;
        lastAimUpdateNanos = now;
        float[] rotation = aimRotation.update(targetRotation[0], targetRotation[1], elapsedSeconds);
        RotationHelper.applyRotation(player, rotation[0], rotation[1]);
    }

    private void releaseAim() {
        aimActive = false;
        lastAimUpdateNanos = 0L;
    }

    private AimDecision getAimDecision(LivingEntity target) {
        if (aimDecision == null
                || decisionTarget != target
                || !aimDecision.matches(Config.aimAtHead, Config.headshotRate)) {
            aimDecision = AimDecision.sample(Config.aimAtHead, Config.headshotRate);
            decisionTarget = target;
            Player player = Minecraft.getInstance().player;
            Vec3 aimPoint = TargetSelector.randomAimPoint(
                    player, target, aimDecision.headshot(), ThreadLocalRandom.current()
            );
            localAimPointOffset = aimPoint == null ? null : aimPoint.subtract(target.position());
        }
        return aimDecision;
    }

    private void clearAimDecision() {
        aimDecision = null;
        decisionTarget = null;
        localAimPointOffset = null;
    }

    private void handleAutoFire(LocalPlayer localPlayer, float targetYaw, float targetPitch) {
        syncAimToServer(localPlayer);

        IClientPlayerGunOperator operator = IClientPlayerGunOperator.fromLocalPlayer(localPlayer);
        if (operator == null) return;

        long shootTimestamp = operator.getDataHolder().clientShootTimestamp;
        if (autoFireControllerHeld
                && TaczAutoFireCompat.shouldReleaseAfterRecordedShot(
                        isContinuousFireMode(localPlayer),
                        autoFireLastShootTimestamp,
                        shootTimestamp
                )) {
            TaczAutoFireCompat.setControllerShootDown(false);
            autoFireControllerHeld = false;
            autoFireLastShootTimestamp = Long.MIN_VALUE;
            clearAimDecision();
            return;
        }

        if (!isAimAligned(localPlayer.getYRot(), localPlayer.getXRot(), targetYaw, targetPitch, 1.0F)) {
            releaseAutoFire(localPlayer);
            return;
        }

        if (!autoFireControllerHeld) {
            autoFireLastShootTimestamp = shootTimestamp;
        }
        if (TaczAutoFireCompat.setControllerShootDown(true)) {
            autoFireControllerHeld = true;
            return;
        }

        IGunOperator gunOperator = IGunOperator.fromLivingEntity(localPlayer);
        boolean reloading = gunOperator.getSynReloadState().getStateType().isReloading();
        long shootCooldown = operator.getClientShootCoolDown();
        if (!shouldAutoFire(reloading, shootCooldown)) return;

        ShootResult result = operator.shoot();
        if (result == ShootResult.SUCCESS) {
            clearAimDecision();
        }
    }

    private boolean isContinuousFireMode(LocalPlayer player) {
        ItemStack gunStack = player.getMainHandItem();
        IGun gun = IGun.getIGunOrNull(gunStack);
        if (gun == null) return false;

        FireMode fireMode = gun.getFireMode(gunStack);
        if (fireMode == FireMode.AUTO) return true;
        if (fireMode != FireMode.BURST) return false;

        try {
            CommonGunIndex index = TimelessAPI.getCommonGunIndex(gun.getGunId(gunStack)).orElse(null);
            return index != null
                    && index.getGunData().getBurstData() != null
                    && index.getGunData().getBurstData().isContinuousShoot();
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    private void releaseAutoFire(LocalPlayer player) {
        if (autoFireControllerHeld) {
            TaczAutoFireCompat.setControllerShootDown(false);
        }
        autoFireControllerHeld = false;
        autoFireLastShootTimestamp = Long.MIN_VALUE;
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

    static boolean isAimAligned(
            float currentYaw,
            float currentPitch,
            float targetYaw,
            float targetPitch,
            float toleranceDegrees
    ) {
        return Math.abs(Mth.wrapDegrees(targetYaw - currentYaw)) <= toleranceDegrees
                && Math.abs(targetPitch - currentPitch) <= toleranceDegrees;
    }

    static boolean shouldAutoFire(boolean reloading, long shootCooldown) {
        return !reloading
                && shootCooldown >= 0L
                && shootCooldown < SHOOT_COOLDOWN_THRESHOLD;
    }

    @SubscribeEvent
    public void onTargetChanged(AimbotTargetChangedEvent event) {
        if (event.getTarget() == null) {
            lockedTarget = null;
            clearAimDecision();
            releaseAim();
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
