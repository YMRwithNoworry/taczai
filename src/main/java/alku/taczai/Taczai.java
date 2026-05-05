package alku.taczai;

import alku.taczai.aimbot.AimbotHandler;
import alku.taczai.keybind.KeyMappings;
import alku.taczai.keybind.MouseInputHandler;
import alku.taczai.overlay.AimbotOverlay;
import com.mojang.logging.LogUtils;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(Taczai.MODID)
public class Taczai {

    public static final String MODID = "taczai";
    private static final Logger LOGGER = LogUtils.getLogger();

    public Taczai() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        modEventBus.addListener(this::commonSetup);

        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            modEventBus.addListener(this::clientSetup);
            modEventBus.addListener(this::registerKeyMappings);
        });

        MinecraftForge.EVENT_BUS.register(this);

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("TaczAI mod loaded successfully!");
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        KeyMappings.register();
        MouseInputHandler.register();
        MinecraftForge.EVENT_BUS.register(new AimbotHandler());
        MinecraftForge.EVENT_BUS.register(new AimbotOverlay());
    }

    private void registerKeyMappings(RegisterKeyMappingsEvent event) {
        KeyMappings.registerKeys(event);
    }
}
