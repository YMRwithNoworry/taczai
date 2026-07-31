package alku.taczai.config;

import alku.taczai.Config;
import alku.taczai.teammate.TeammateManager;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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
        double[] headshotRate = {Config.headshotRate};

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
        aiming.addEntry(entries.startDoubleField(Component.translatable("config.taczai.headshot_rate"), headshotRate[0])
                .setDefaultValue(100.0).setMin(0.0).setMax(100.0)
                .setSaveConsumer(value -> headshotRate[0] = value).build());
        ConfigCategory teammates = builder.getOrCreateCategory(Component.translatable("config.taczai.teammates"));
        Set<UUID> selectedTeammates = new HashSet<>(TeammateManager.getSavedUuids());
        Map<UUID, String> teammateNames = new HashMap<>(TeammateManager.getSavedNames());
        Map<UUID, Player> onlinePlayers = new HashMap<>();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            for (Player player : minecraft.level.players()) {
                if (player == minecraft.player) continue;
                onlinePlayers.put(player.getUUID(), player);
                teammateNames.put(player.getUUID(), player.getName().getString());
            }
        }

        Set<UUID> configurablePlayers = new HashSet<>(selectedTeammates);
        configurablePlayers.addAll(onlinePlayers.keySet());
        configurablePlayers.stream()
                .sorted(Comparator.comparing(uuid -> teammateNames.getOrDefault(uuid, uuid.toString()), String.CASE_INSENSITIVE_ORDER))
                .forEach(uuid -> {
                    String name = teammateNames.getOrDefault(uuid, "");
                    boolean online = onlinePlayers.containsKey(uuid);
                    Component label = Component.literal(name.isBlank() ? uuid.toString() : name);
                    Component tooltip = Component.literal(uuid.toString())
                            .append(Component.literal("\n"))
                            .append(Component.translatable(online ? "config.taczai.player_online" : "config.taczai.player_offline"));
                    teammates.addEntry(entries.startBooleanToggle(label, selectedTeammates.contains(uuid))
                            .setYesNoTextSupplier(selected -> Component.translatable(
                                    selected ? "config.taczai.teammate_yes" : "config.taczai.teammate_no"))
                            .setTooltip(tooltip)
                            .setSaveConsumer(selected -> {
                                if (selected) selectedTeammates.add(uuid);
                                else selectedTeammates.remove(uuid);
                            }).build());
                });

        if (configurablePlayers.isEmpty()) {
            teammates.addEntry(entries.startTextDescription(Component.translatable("config.taczai.teammates_empty")).build());
        }

        builder.setSavingRunnable(() -> {
            Config.updateAiming(range[0], speed[0], fov[0], head[0], fire[0], headshotRate[0]);
            TeammateManager.replaceAndSave(selectedTeammates, teammateNames);
        });
        return builder.build();
    }
}
