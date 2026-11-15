package alku.taczai;

import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

@OnlyIn(Dist.CLIENT)
public final class FullbrightHandler {
    private static final int EFFECT_DURATION_TICKS = 1200;
    private static final int REFRESH_BEFORE_FADE_TICKS = 240;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || !minecraft.player.isAlive()) {
            return;
        }

        MobEffectInstance nightVision = minecraft.player.getEffect(MobEffects.NIGHT_VISION);
        if (nightVision == null || shouldRefreshNightVision(nightVision.getDuration())) {
            minecraft.player.addEffect(new MobEffectInstance(
                    MobEffects.NIGHT_VISION,
                    EFFECT_DURATION_TICKS,
                    0,
                    true,
                    false,
                    false
            ));
        }
    }

    static boolean shouldRefreshNightVision(int remainingTicks) {
        return remainingTicks <= REFRESH_BEFORE_FADE_TICKS;
    }
}
