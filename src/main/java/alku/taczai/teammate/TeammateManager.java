package alku.taczai.teammate;

import alku.taczai.Config;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TeammateManager {
    private static final Set<UUID> TEAMMATES = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, String> NAMES = new ConcurrentHashMap<>();

    private TeammateManager() {
    }

    public static void loadFromConfig() {
        replaceSaved(Config.teammateUuids, Config.teammateNames);
    }

    public static LinkedHashSet<UUID> parseUuids(Collection<String> values) {
        LinkedHashSet<UUID> parsed = new LinkedHashSet<>();
        for (String value : values) {
            try {
                parsed.add(UUID.fromString(value));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return parsed;
    }

    public static List<String> serializeUuids(Collection<UUID> values) {
        return values.stream().sorted(Comparator.comparing(UUID::toString)).map(UUID::toString).toList();
    }

    public static void replaceSaved(Collection<String> uuids, Collection<String> names) {
        TEAMMATES.clear();
        TEAMMATES.addAll(parseUuids(uuids));
        NAMES.clear();
        for (String entry : names) {
            int separator = entry.indexOf('|');
            if (separator <= 0) continue;
            try {
                UUID uuid = UUID.fromString(entry.substring(0, separator));
                if (TEAMMATES.contains(uuid)) NAMES.put(uuid, entry.substring(separator + 1));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public static boolean toggle(UUID uuid, String name) {
        if (TEAMMATES.remove(uuid)) {
            NAMES.remove(uuid);
            return false;
        }
        TEAMMATES.add(uuid);
        NAMES.put(uuid, name);
        return true;
    }

    public static boolean toggleAndSave(Player player) {
        boolean added = toggle(player.getUUID(), player.getName().getString());
        save();
        return added;
    }

    public static void replaceAndSave(Collection<UUID> uuids, Map<UUID, String> names) {
        replace(uuids, names);
        save();
    }

    static void replace(Collection<UUID> uuids, Map<UUID, String> names) {
        TEAMMATES.clear();
        TEAMMATES.addAll(uuids);
        NAMES.clear();
        uuids.forEach(uuid -> NAMES.put(uuid, names.getOrDefault(uuid, "")));
    }

    public static boolean isLocalTeammate(UUID uuid) {
        return TEAMMATES.contains(uuid);
    }

    public static boolean isEffectiveTeammate(Player local, Player candidate) {
        return candidate != local && (isLocalTeammate(candidate.getUUID()) || local.isAlliedTo(candidate));
    }

    public static Map<UUID, String> getSavedNames() {
        return Map.copyOf(NAMES);
    }

    public static Set<UUID> getSavedUuids() {
        return Set.copyOf(TEAMMATES);
    }

    public static void removeAndSave(UUID uuid) {
        TEAMMATES.remove(uuid);
        NAMES.remove(uuid);
        save();
    }

    private static void save() {
        List<String> names = new ArrayList<>();
        serializeUuids(TEAMMATES).forEach(uuid -> {
            UUID id = UUID.fromString(uuid);
            names.add(uuid + "|" + NAMES.getOrDefault(id, ""));
        });
        Config.saveTeammates(serializeUuids(TEAMMATES), names);
    }
}
