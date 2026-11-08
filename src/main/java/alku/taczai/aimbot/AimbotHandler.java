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

@OnlyIn(Dist.CLIENT)
public class AimbotHandler {
    /** TACZ returns COOL_DOWN while the remaining client cooldown is 50 ms or more. */
    private static final long SHOOT_COOLDOWN_THRESHOLD = 50L;

    private LivingEntity lockedTarget = null;
    private LivingEntity decisionTarget = null;
    private AimDecision aimDecision = null;
    private boolean forcedAim = false;

    /** Aim the local camera is rendered at while the target remains locked. */
    private boolean cameraAimActive = false;
    private float cameraAimYaw = 0.0F;
    private float cameraAimPitch = 0.0F;

    /** Cached aim for frame-rate fire attempts; shot cooldown remains owned by TACZ. */
    private boolean autoFirePending = false;
    private float autoFireYaw = 0.0F;
    private float autoFirePitch = 0.0F;

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
        RotationHelper.applySnapRotation(player, targetRot[0], targetRot[1]);
        holdCameraOnAim(targetRot[0], targetRot[1]);
        autoFirePending = Config.autoFire;
        if (autoFirePending) {
            autoFireYaw = targetRot[0];
            autoFirePitch = targetRot[1];
        }
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !autoFirePending || !Config.autoFire) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null
                || mc.level == null
                || mc.screen != null
                || !KeyMappings.aimbotEnabled
                || !isHoldingTaczGun(player)
                || !TargetSelector.isTrackableTarget(player, lockedTarget)) {
            autoFirePending = false;
            return;
        }

        handleAutoFire(player, autoFireYaw, autoFirePitch);
    }

    /** Renders the local view at the current aim while the target remains locked. */
    @SubscribeEvent
    public void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (!cameraAimActive) return;

        Minecraft mc = Minecraft.getInstance();
        if (!(event.getCamera().getEntity() instanceof LocalPlayer cameraPlayer) || cameraPlayer != mc.player) return;

        event.setYaw(cameraAimYaw);
        event.setPitch(Mth.clamp(cameraAimPitch, -90.0F, 90.0F));
    }

    private void holdCameraOnAim(float targetYaw, float targetPitch) {
        cameraAimActive = true;
        cameraAimYaw = targetYaw;
        cameraAimPitch = targetPitch;
    }

    private void releaseCameraAim() {
        cameraAimActive = false;
        autoFirePending = false;
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

    private void handleAutoFire(LocalPlayer localPlayer, float targetYaw, float targetPitch) {
        IClientPlayerGunOperator operator = IClientPlayerGunOperator.fromLocalPlayer(localPlayer);
        if (operator == null) return;

        IGunOperator gunOperator = IGunOperator.fromLivingEntity(localPlayer);
        boolean reloading = gunOperator.getSynReloadState().getStateType().isReloading();
        long shootCooldown = operator.getClientShootCoolDown();
        if (!shouldAutoFire(reloading, shootCooldown)) return;

        RotationHelper.applySnapRotation(localPlayer, targetYaw, targetPitch);
        syncAimToServer(localPlayer);

        ShootResult result = operator.shoot();
        if (result == ShootResult.SUCCESS) {
            autoFirePending = false;
            clearAimDecision();
        }
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
