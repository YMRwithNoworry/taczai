package alku.taczai.aimbot;

import alku.taczai.Config;
import alku.taczai.keybind.AimbotTargetChangedEvent;
import alku.taczai.keybind.KeyMappings;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.entity.ShootResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

@OnlyIn(Dist.CLIENT)
public class AimbotHandler {
    private LivingEntity lockedTarget = null;
    private static final double FIRE_ANGLE_THRESHOLD = 5.0;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        Player player = mc.player;

        if (KeyMappings.aimMode == KeyMappings.AimMode.MANUAL && KeyMappings.selectTargetPressed) {
            KeyMappings.consumeSelectTarget();
            TargetSelector.confirmTarget(player);
        } else {
            KeyMappings.consumeSelectTarget();
        }

        if (!isHoldingTaczGun(player)) {
            lockedTarget = null;
            return;
        }

        if (!KeyMappings.aimbotEnabled) {
            lockedTarget = null;
            return;
        }

        lockedTarget = TargetSelector.getActiveTarget(player);
        if (lockedTarget == null) return;

        float[] targetRot = RotationHelper.getTargetRotation(player, lockedTarget);
        RotationHelper.applySmoothRotation(player, targetRot[0], targetRot[1]);

        if (Config.autoFire) {
            handleAutoFire(mc, player, lockedTarget);
        }
    }

    private void handleAutoFire(Minecraft mc, Player player, LivingEntity target) {
        if (!isCrosshairOnTarget(player, target)) return;

        if (player instanceof LocalPlayer localPlayer) {
            IClientPlayerGunOperator operator = IClientPlayerGunOperator.fromLocalPlayer(localPlayer);
            if (operator != null) {
                ShootResult result = operator.shoot();
            }
        }
    }

    private boolean isCrosshairOnTarget(Player player, LivingEntity target) {
        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getLookAngle();
        Vec3 toTarget = target.getEyePosition().subtract(eyePos).normalize();
        double dot = lookVec.dot(toTarget);
        double angleDeg = Math.toDegrees(Math.acos(Math.min(1.0, Math.max(-1.0, dot))));
        return angleDeg < FIRE_ANGLE_THRESHOLD;
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
