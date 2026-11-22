package alku.taczai.aimbot;

import alku.taczai.Config;
import alku.taczai.keybind.AimbotTargetChangedEvent;
import alku.taczai.keybind.KeyMappings;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.entity.ShootResult;
import com.tacz.guns.api.event.common.GunReloadEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.resource.index.CommonGunIndex;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;

import java.util.concurrent.TimeUnit;

@OnlyIn(Dist.CLIENT)
public class AimbotHandler {
    /** TACZ returns COOL_DOWN while the remaining client cooldown is 50 ms or more. */
    private static final long SHOOT_COOLDOWN_THRESHOLD = 50L;
    // Match the timeout TACZ uses when waiting for the server to acknowledge a local reload.
    private static final long RELOAD_SYNC_TIMEOUT_NANOS = TimeUnit.MILLISECONDS.toNanos(250L);

    private LivingEntity lockedTarget = null;
    private LivingEntity decisionTarget = null;
    private AimDecision aimDecision = null;
    private boolean forcedAim = false;

    private boolean autoFireControllerHeld = false;
    private long autoFireLastShootTimestamp = Long.MIN_VALUE;
    private ItemStack pendingReloadGun;
    private long reloadRequestedAtNanos;

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onGunReload(GunReloadEvent event) {
        if (event.getLogicalSide() != LogicalSide.CLIENT || event.getEntity() != Minecraft.getInstance().player) return;

        pendingReloadGun = event.getGunItemStack();
        reloadRequestedAtNanos = System.nanoTime();
        releaseAutoFire();
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        updateAim(mc, 1.0F, true);
    }

    @SubscribeEvent
    public void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || event.getCamera().getEntity() != mc.player) return;

        if (updateAim(mc, (float) event.getPartialTick(), false)) {
            event.setYaw(mc.player.getYRot());
            event.setPitch(mc.player.getXRot());
        }
    }

    private boolean updateAim(Minecraft mc, float partialTick, boolean clientTick) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || !player.isAlive() || player.isSpectator()
                || !KeyMappings.aimbotEnabled || !isHoldingTaczGun(player)) {
            lockedTarget = null;
        } else if (isReloading(player) || mc.screen != null || mc.getOverlay() != null || mc.isPaused()
                || !mc.mouseHandler.isMouseGrabbed() || !mc.isWindowActive()) {
            releaseAutoFire();
            if (clientTick) updateForcedAim(player, false);
            return false;
        } else if (!TargetSelector.isTrackableTarget(player, lockedTarget)) {
            lockedTarget = TargetSelector.getActiveTarget(player);
        }

        if (lockedTarget == null) {
            clearAimDecision();
            releaseAutoFire();
            if (player == null) forcedAim = false;
            else if (clientTick) updateForcedAim(player, false);
            return false;
        }

        ItemStack gunStack = player.getMainHandItem();
        IGun gun = IGun.getIGunOrNull(gunStack);
        boolean autoFire = Config.autoFire && gun != null;
        AimDecision decision = getAimDecision(lockedTarget);
        // Shots use the current tick's positions even when triggered during rendering.
        float[] rotation = RotationHelper.getTargetRotation(player, lockedTarget, decision,
                autoFire ? 1.0F : partialTick);
        if (rotation == null) {
            releaseAutoFire();
            if (clientTick) updateForcedAim(player, false);
            return false;
        }
        RotationHelper.applyRotation(player, rotation[0], rotation[1]);
        if (clientTick) updateForcedAim(player, autoFire);
        if (autoFire) handleAutoFire(player, gunStack, gun, clientTick);
        else releaseAutoFire();
        return true;
    }

    private boolean isReloading(LocalPlayer player) {
        boolean syncedReloading = IGunOperator.fromLivingEntity(player).getSynReloadState().getStateType().isReloading();
        if (syncedReloading || pendingReloadGun != player.getMainHandItem()
                || System.nanoTime() - reloadRequestedAtNanos >= RELOAD_SYNC_TIMEOUT_NANOS) {
            pendingReloadGun = null;
        }
        return syncedReloading || pendingReloadGun != null;
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

    private void handleAutoFire(LocalPlayer localPlayer, ItemStack stack, IGun gun, boolean clientTick) {
        IClientPlayerGunOperator operator = IClientPlayerGunOperator.fromLocalPlayer(localPlayer);
        if (operator == null || !Float.isFinite(localPlayer.getYRot()) || !Float.isFinite(localPlayer.getXRot())) {
            releaseAutoFire();
            return;
        }

        FireMode mode = gun.getFireMode(stack);
        if (mode == FireMode.UNKNOWN) {
            releaseAutoFire();
            return;
        }
        CommonGunIndex index = TimelessAPI.getCommonGunIndex(gun.getGunId(stack)).orElse(null);
        if (index == null) {
            releaseAutoFire();
            return;
        }
        if (TaczAutoFireCompat.requiresCharge(index.getGunData(), mode)) {
            if (clientTick) updateChargeTrigger(localPlayer, operator, index, mode);
            return;
        }

        releaseAutoFire();
        long shootCooldown = operator.getClientShootCoolDown();
        if (!shouldAutoFire(shootCooldown)) return;

        syncAimToServer(localPlayer);
        ShootResult result = operator.shoot();
        if (result == ShootResult.SUCCESS) {
            clearAimDecision();
        }
    }

    private void updateChargeTrigger(
            LocalPlayer player, IClientPlayerGunOperator operator, CommonGunIndex index, FireMode mode
    ) {
        long shootTimestamp = operator.getDataHolder().clientShootTimestamp;
        boolean continuous = mode == FireMode.AUTO || (mode == FireMode.BURST
                && index.getGunData().getBurstData() != null
                && index.getGunData().getBurstData().isContinuousShoot());
        if (autoFireControllerHeld && shootTimestamp != autoFireLastShootTimestamp) {
            clearAimDecision();
            if (!continuous) {
                releaseAutoFire();
                return;
            }
        }
        autoFireLastShootTimestamp = shootTimestamp;
        syncAimToServer(player);
        autoFireControllerHeld = TaczAutoFireCompat.setControllerShootDown(true);
    }

    private void releaseAutoFire() {
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

    static boolean shouldAutoFire(long shootCooldown) {
        return shootCooldown >= 0L && shootCooldown < SHOOT_COOLDOWN_THRESHOLD;
    }

    @SubscribeEvent
    public void onTargetChanged(AimbotTargetChangedEvent event) {
        if (event.getTarget() == null) {
            lockedTarget = null;
            clearAimDecision();
            releaseAutoFire();
        }
    }

    private boolean isHoldingTaczGun(LocalPlayer player) {
        ItemStack mainHand = player.getItemBySlot(EquipmentSlot.MAINHAND);
        ItemStack offHand = player.getItemBySlot(EquipmentSlot.OFFHAND);
        return isTaczItem(mainHand) || isTaczItem(offHand);
    }

    private boolean isTaczItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.getItem() instanceof com.tacz.guns.api.item.IGun;
    }
}
