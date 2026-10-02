package alku.taczai;

import alku.taczai.teammate.TeammateManager;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

import java.util.List;

@Mod.EventBusSubscriber(modid = Taczai.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.IntValue AIMBOT_RANGE = BUILDER
            .comment("Maximum distance (in blocks) for auto-aim to track targets")
            .defineInRange("aimbotRange", 150, 5, 256);

    private static final ForgeConfigSpec.IntValue AIM_TURN_MS = BUILDER
            .comment("How long the player model may take to swing onto a target, in milliseconds.",
                    "The rotation sent to the server is spread over that time (2-3 ticks), so",
                    "other players see the head turn instead of one snap. The model is exactly",
                    "on the enemy when the turn ends, and auto fire shoots from that tick on.")
            .defineInRange("aimTurnMs", 125, 100, 150);

    private static final ForgeConfigSpec.DoubleValue AIMBOT_FOV = BUILDER
            .comment("Maximum angular offset from the crosshair in degrees")
            .defineInRange("aimbotFov", 20.0, 1.0, 180.0);

    private static final ForgeConfigSpec.BooleanValue AIM_AT_HEAD = BUILDER
            .comment("Aim at head level (true) or body center (false)")
            .define("aimAtHead", true);

    private static final ForgeConfigSpec.BooleanValue AUTO_FIRE = BUILDER
            .comment("Auto-fire when crosshair is on target (true) or manual fire only (false)")
            .define("autoFire", true);

    private static final ForgeConfigSpec.DoubleValue HEADSHOT_RATE = BUILDER
            .comment("Chance for automatic aim to select the target head, in percent")
            .defineInRange("headshotRate", 100.0, 0.0, 100.0);

    private static final ForgeConfigSpec.BooleanValue SHOW_FOV_CIRCLE = BUILDER
            .comment("Draw the FOV auto-aim cone as a circle around the crosshair")
            .define("showFovCircle", true);

    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> TEAMMATE_UUIDS = BUILDER
            .comment("Persistent local teammate UUIDs")
            .defineListAllowEmpty("teammateUuids", List.of(), value -> value instanceof String);

    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> TEAMMATE_NAMES = BUILDER
            .comment("Last-known teammate names encoded as UUID|name")
            .defineListAllowEmpty("teammateNames", List.of(), value -> value instanceof String);

    static final ForgeConfigSpec SPEC = BUILDER.build();

    public static int aimbotRange;
    public static int aimTurnMs;
    public static double aimbotFov;
    public static boolean aimAtHead;
    public static boolean autoFire;
    public static boolean showFovCircle;
    public static double headshotRate = 100.0;
    public static List<String> teammateUuids = List.of();
    public static List<String> teammateNames = List.of();

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        aimbotRange = AIMBOT_RANGE.get();
        aimTurnMs = AIM_TURN_MS.get();
        aimbotFov = AIMBOT_FOV.get();
        aimAtHead = AIM_AT_HEAD.get();
        autoFire = AUTO_FIRE.get();
        showFovCircle = SHOW_FOV_CIRCLE.get();
        headshotRate = HEADSHOT_RATE.get();
        teammateUuids = List.copyOf(TEAMMATE_UUIDS.get());
        teammateNames = List.copyOf(TEAMMATE_NAMES.get());
        TeammateManager.loadFromConfig();
    }

    public static void saveTeammates(List<String> uuids, List<String> names) {
        teammateUuids = List.copyOf(uuids);
        teammateNames = List.copyOf(names);
        TEAMMATE_UUIDS.set(teammateUuids);
        TEAMMATE_NAMES.set(teammateNames);
        SPEC.save();
    }

    public static void updateAiming(int range, int turnMs, double fov, boolean head, boolean fire) {
        updateAiming(range, turnMs, fov, head, fire, headshotRate, showFovCircle);
    }

    public static void updateAiming(
            int range,
            int turnMs,
            double fov,
            boolean head,
            boolean fire,
            double configuredHeadshotRate
    ) {
        updateAiming(range, turnMs, fov, head, fire, configuredHeadshotRate, showFovCircle);
    }

    public static void updateAiming(
            int range,
            int turnMs,
            double fov,
            boolean head,
            boolean fire,
            double configuredHeadshotRate,
            boolean showCircle
    ) {
        double safeHeadshotRate = clampPercentage(configuredHeadshotRate);
        AIMBOT_RANGE.set(range);
        AIM_TURN_MS.set(turnMs);
        AIMBOT_FOV.set(fov);
        AIM_AT_HEAD.set(head);
        AUTO_FIRE.set(fire);
        HEADSHOT_RATE.set(safeHeadshotRate);
        SHOW_FOV_CIRCLE.set(showCircle);
        aimbotRange = range;
        aimTurnMs = turnMs;
        aimbotFov = fov;
        aimAtHead = head;
        autoFire = fire;
        headshotRate = safeHeadshotRate;
        showFovCircle = showCircle;
        SPEC.save();
    }

    static double clampPercentage(double value) {
        if (!Double.isFinite(value)) return 0.0;
        return Math.max(0.0, Math.min(100.0, value));
    }
}
