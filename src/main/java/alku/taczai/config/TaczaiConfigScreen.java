package alku.taczai.config;

import alku.taczai.Config;
import alku.taczai.teammate.TeammateManager;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class TaczaiConfigScreen {
    private TaczaiConfigScreen() {
    }

    public static Screen create(Screen parent) {
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.translatable("config.taczai.title"));
        ConfigEntryBuilder entries = builder.entryBuilder();
        ConfigCategory aiming = builder.getOrCreateCategory(Component.translatable("config.taczai.aiming"));

        int[] range = {Config.aimbotRange};
        double[] speed = {Config.aimSpeed};
        double[] fov = {Config.aimbotFov};
        boolean[] head = {Config.aimAtHead};
        boolean[] fire = {Config.autoFire};

        aiming.addEntry(entries.startIntSlider(Component.translatable("config.taczai.range"), range[0], 5, 256)
                .setDefaultValue(150).setSaveConsumer(value -> range[0] = value).build());
        aiming.addEntry(entries.startDoubleField(Component.translatable("config.taczai.speed"), speed[0])
                .setDefaultValue(0.3).setMin(0.0).setMax(1.0).setSaveConsumer(value -> speed[0] = value).build());
        aiming.addEntry(entries.startDoubleField(Component.translatable("config.taczai.fov"), fov[0])
                .setDefaultValue(20.0).setMin(1.0).setMax(180.0).setSaveConsumer(value -> fov[0] = value).build());
        aiming.addEntry(entries.startBooleanToggle(Component.translatable("config.taczai.head"), head[0])
                .setDefaultValue(true).setSaveConsumer(value -> head[0] = value).build());
        aiming.addEntry(entries.startBooleanToggle(Component.translatable("config.taczai.autofire"), fire[0])
                .setDefaultValue(true).setSaveConsumer(value -> fire[0] = value).build());

        ConfigCategory teammates = builder.getOrCreateCategory(Component.translatable("config.taczai.teammates"));
        Set<UUID> removals = new HashSet<>();
        TeammateManager.getSavedUuids().stream().sorted().forEach(uuid -> {
            String name = TeammateManager.getSavedNames().getOrDefault(uuid, "");
            String label = name.isBlank() ? uuid.toString() : name + " (" + uuid + ")";
            teammates.addEntry(entries.startBooleanToggle(Component.literal(label), false)
                    .setYesNoTextSupplier(remove -> Component.translatable(remove ? "config.taczai.remove_yes" : "config.taczai.remove_no"))
                    .setSaveConsumer(remove -> {
                        if (remove) removals.add(uuid);
                    }).build());
        });

        builder.setSavingRunnable(() -> {
            Config.updateAiming(range[0], speed[0], fov[0], head[0], fire[0]);
            removals.forEach(TeammateManager::removeAndSave);
        });
        return builder.build();
    }
}
