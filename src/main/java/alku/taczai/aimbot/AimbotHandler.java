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
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

@OnlyIn(Dist.CLIENT)
public class AimbotHandler {
    private LivingEntity lockedTarget = null;
    private LivingEntity decisionTarget = null;
    private AimDecision aimDecision = null;
    private boolean forcedAim = false;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            clearAimDecision();
            forcedAim = false;
            return;
        }
        if (mc.level == null) {
            lockedTarget = null;
            clearAimDecision();
            updateForcedAim(mc.player, false);
            return;
        }

        Player player = mc.player;

        if (!isHoldingTaczGun(player)) {
            lockedTarget = null;
            clearAimDecision();
            updateForcedAim(mc.player, false);
            return;
        }

        if (!KeyMappings.aimbotEnabled) {
            lockedTarget = null;
            clearAimDecision();
            updateForcedAim(mc.player, false);
            return;
        }

        if (!TargetSelector.isTrackableTarget(player, lockedTarget)) {
            lockedTarget = TargetSelector.getActiveTarget(player);
        }
        if (lockedTarget == null) {
            clearAimDecision();
            updateForcedAim(mc.player, false);
            return;
        }

        AimDecision decision = getAimDecision(lockedTarget);
        updateForcedAim(mc.player, Config.autoFire);

        float[] targetRot = RotationHelper.getTargetRotation(player, lockedTarget, decision);
        RotationHelper.applySmoothRotation(player, targetRot[0], targetRot[1]);
        if (Config.autoFire && mc.screen == null) handleAutoFire(player, lockedTarget, decision, targetRot);
    }

    private AimDecision getAimDecision(LivingEntity target) {
        double configuredMissRate = Config.autoFire ? Config.missRate : 0.0;
        if (aimDecision == null
                || decisionTarget != target
                || !aimDecision.matches(Config.aimAtHead, Config.headshotRate, configuredMissRate)) {
            aimDecision = AimDecision.sample(Config.aimAtHead, Config.headshotRate, configuredMissRate);
            decisionTarget = target;
        }
        return aimDecision;
    }

    private void clearAimDecision() {
        aimDecision = null;
        decisionTarget = null;
    }

    private void handleAutoFire(Player player, LivingEntity target, AimDecision decision, float[] targetRotation) {
        if (player instanceof LocalPlayer localPlayer) {
            IClientPlayerGunOperator operator = IClientPlayerGunOperator.fromLocalPlayer(localPlayer);
            if (operator == null) return;

            IGunOperator gunOperator = IGunOperator.fromLivingEntity(localPlayer);
            boolean reloading = gunOperator.getSynReloadState().getStateType().isReloading();
            boolean stateLocked = operator.getDataHolder().clientStateLock;
            long shootCooldown = operator.getClientShootCoolDown();
            if (decision.intentionalMiss() && !RotationHelper.isAligned(player, targetRotation, 2.0F)) return;

            boolean nextShotHitsTarget = decision.intentionalMiss()
                    ? TargetSelector.hasClearShot(player)
                    : TargetSelector.willNextShotHitTarget(player, target);
            if (!shouldAutoFire(nextShotHitsTarget, decision.intentionalMiss(), stateLocked, reloading, shootCooldown)) return;

            syncAimToServer(localPlayer);
            ShootResult result = operator.shoot();
            if (result == ShootResult.SUCCESS) clearAimDecision();
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

        if (shouldAim && !operator.isAim()) {
            operator.aim(true);
            forcedAim = true;
        } else if (!shouldAim && forcedAim) {
            operator.aim(false);
            forcedAim = false;
        }
    }

    static boolean shouldAutoFire(
            boolean nextShotHitsTarget,
            boolean stateLocked,
            boolean reloading,
            long shootCooldown
    ) {
        return shouldAutoFire(nextShotHitsTarget, false, stateLocked, reloading, shootCooldown);
    }

    static boolean shouldAutoFire(
            boolean nextShotHitsTarget,
            boolean intentionalMiss,
            boolean stateLocked,
            boolean reloading,
            long shootCooldown
    ) {
        boolean blockingAction = stateLocked && shootCooldown <= 0;
        return (nextShotHitsTarget || intentionalMiss) && !blockingAction && !reloading;
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
