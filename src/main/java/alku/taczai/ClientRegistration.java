package alku.taczai;

import alku.taczai.config.TaczaiConfigScreen;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModLoadingContext;

final class ClientRegistration {
    private ClientRegistration() {
    }

    static void registerConfigScreen() {
        ModLoadingContext.get().registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((minecraft, parent) -> TaczaiConfigScreen.create(parent))
        );
    }
}
