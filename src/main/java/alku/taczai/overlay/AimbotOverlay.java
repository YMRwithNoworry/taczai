package alku.taczai.overlay;

import alku.taczai.Config;
import alku.taczai.Taczai;
import alku.taczai.aimbot.TargetSelector;
import alku.taczai.keybind.KeyMappings;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Taczai.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class AimbotOverlay {

    @SubscribeEvent
    public static void registerOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.CROSSHAIR.id(), "aimbot_status", (gui, guiGraphics, partialTick, screenWidth, screenHeight) -> {
            renderOverlay(guiGraphics, screenWidth, screenHeight);
        });
    }

    private static void renderOverlay(GuiGraphics guiGraphics, int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        if (font == null) return;

        int rightX = screenWidth - 10;
        int y = 10;

        if (!KeyMappings.aimbotEnabled) {
            String text = "Aimbot: OFF [" + KeyMappings.TOGGLE_AIMBOT_KEY.getTranslatedKeyMessage().getString() + "]";
            int color = ChatFormatting.GRAY.getColor() != null ? ChatFormatting.GRAY.getColor() : 0xAAAAAA;
            guiGraphics.drawString(font, text, rightX - font.width(text), y, color);
            return;
        }

        String statusText = "Aimbot: ON";
        guiGraphics.drawString(font, statusText, rightX - font.width(statusText), y, 0x55FF55);

        y += 12;
        String modeStr = KeyMappings.aimMode == KeyMappings.AimMode.AUTO ? "AUTO" : "MANUAL";
        String modeText = "Mode: " + modeStr + " [" + KeyMappings.SWITCH_MODE_KEY.getTranslatedKeyMessage().getString() + "]";
        int modeColor = KeyMappings.aimMode == KeyMappings.AimMode.AUTO ? 0xFF5555 : 0x55FFFF;
        guiGraphics.drawString(font, modeText, rightX - font.width(modeText), y, modeColor);

        if (KeyMappings.aimMode == KeyMappings.AimMode.MANUAL) {
            y += 12;
            String midText = "Select: [Mouse Middle]";
            guiGraphics.drawString(font, midText, rightX - font.width(midText), y, 0x55FFFF);
        }

        if (Config.autoFire) {
            y += 12;
            String autoText = "AutoFire: ON";
            guiGraphics.drawString(font, autoText, rightX - font.width(autoText), y, 0x55FF55);
        }

        LivingEntity target = TargetSelector.getConfirmedTarget();
        if (target != null) {
            y += 12;
            String name = target.getName().getString();
            Player player = mc.player;
            if (player != null) {
                double dist = Math.sqrt(target.distanceToSqr(player));
                boolean los = TargetSelector.hasLineOfSight(player, target);

                boolean onTarget = false;
                Vec3 eyePos = player.getEyePosition();
                Vec3 lookVec = player.getLookAngle();
                Vec3 toTarget = target.getEyePosition().subtract(eyePos).normalize();
                double dot = lookVec.dot(toTarget);
                double angleDeg = Math.toDegrees(Math.acos(Math.min(1.0, Math.max(-1.0, dot))));
                onTarget = angleDeg < 5.0;

                String info = "Target: " + name + " [" + String.format("%.1f", dist) + "m]";
                int infoColor;
                if (!los) {
                    infoColor = ChatFormatting.RED.getColor();
                } else if (onTarget) {
                    infoColor = 0x55FF55;
                } else {
                    infoColor = 0xFFFF55;
                }
                guiGraphics.drawString(font, info, rightX - font.width(info), y, infoColor);

                if (onTarget && Config.autoFire) {
                    y += 12;
                    String firingText = ">>> FIRING <<<";
                    guiGraphics.drawString(font, firingText, rightX - font.width(firingText), y, 0xFF5555);
                }
            } else {
                String info = "Target: " + name;
                guiGraphics.drawString(font, info, rightX - font.width(info), y, 0xFFFF55);
            }
        } else if (KeyMappings.aimMode == KeyMappings.AimMode.MANUAL) {
            y += 12;
            String hint = "Look at enemy + Middle Click";
            guiGraphics.drawString(font, hint, rightX - font.width(hint), y, 0xAAAAAA);
        }
    }
}
