package alku.taczai;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

@Mod.EventBusSubscriber(modid = Taczai.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.IntValue AIMBOT_RANGE = BUILDER
            .comment("Maximum distance (in blocks) for auto-aim to track targets")
            .defineInRange("aimbotRange", 150, 5, 256);

    private static final ForgeConfigSpec.DoubleValue AIM_SPEED = BUILDER
            .comment("Aiming smoothness (0.0 = instant snap, 1.0 = very slow)")
            .defineInRange("aimSpeed", 0.3, 0.0, 1.0);

    private static final ForgeConfigSpec.BooleanValue AIM_AT_HEAD = BUILDER
            .comment("Aim at head level (true) or body center (false)")
            .define("aimAtHead", true);

    private static final ForgeConfigSpec.BooleanValue AUTO_FIRE = BUILDER
            .comment("Auto-fire when crosshair is on target (true) or manual fire only (false)")
            .define("autoFire", true);

    static final ForgeConfigSpec SPEC = BUILDER.build();

    public static int aimbotRange;
    public static double aimSpeed;
    public static boolean aimAtHead;
    public static boolean autoFire;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        aimbotRange = AIMBOT_RANGE.get();
        aimSpeed = AIM_SPEED.get();
        aimAtHead = AIM_AT_HEAD.get();
        autoFire = AUTO_FIRE.get();
    }
}
