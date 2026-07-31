package alku.taczai.aimbot;

import alku.taczai.Config;
import alku.taczai.keybind.AimbotTargetChangedEvent;
import alku.taczai.keybind.KeyMappings;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.entity.IGunOperator;
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
    private boolean forcedAim = false;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            forcedAim = false;
            return;
        }
        if (mc.level == null) return;

        Player player = mc.player;

        if (!isHoldingTaczGun(player)) {
            lockedTarget = null;
            updateForcedAim(mc.player, false);
            return;
        }

        if (!KeyMappings.aimbotEnabled) {
            lockedTarget = null;
            updateForcedAim(mc.player, false);
            return;
        }

        lockedTarget = TargetSelector.getActiveTarget(player);
        if (lockedTarget == null) {
            updateForcedAim(mc.player, false);
            return;
        }

        updateForcedAim(mc.player, Config.autoFire);

        float[] targetRot = RotationHelper.getTargetRotation(player, lockedTarget);
        RotationHelper.applySmoothRotation(player, targetRot[0], targetRot[1]);
        if (Config.autoFire && mc.screen == null) handleAutoFire(player, lockedTarget);
    }

    private void handleAutoFire(Player player, LivingEntity target) {
        if (player instanceof LocalPlayer localPlayer) {
            IClientPlayerGunOperator operator = IClientPlayerGunOperator.fromLocalPlayer(localPlayer);
            if (operator == null) return;

            IGunOperator gunOperator = IGunOperator.fromLivingEntity(localPlayer);
            boolean reloading = gunOperator.getSynReloadState().getStateType().isReloading();
            boolean stateLocked = operator.getDataHolder().clientStateLock;
            long shootCooldown = operator.getClientShootCoolDown();
            boolean nextShotHitsTarget = TargetSelector.willNextShotHitTarget(player, target);
            if (!shouldAutoFire(nextShotHitsTarget, stateLocked, reloading, shootCooldown)) return;

            syncAimToServer(localPlayer);
            operator.shoot();
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
        boolean blockingAction = stateLocked && shootCooldown <= 0;
        return nextShotHitsTarget && !blockingAction && !reloading;
    }

    @SubscribeEvent
    public void onTargetChanged(AimbotTargetChangedEvent event) {
        if (event.getTarget() == null) {
            lockedTarget = null;
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
