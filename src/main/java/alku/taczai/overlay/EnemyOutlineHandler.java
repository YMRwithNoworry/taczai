package alku.taczai.overlay;

import alku.taczai.keybind.KeyMappings;
import alku.taczai.teammate.TeammateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.IdentityHashMap;
import java.util.Map;

public class EnemyOutlineHandler {
    private final Map<Player, Boolean> previousGlowStates = new IdentityHashMap<>();

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft minecraft = Minecraft.getInstance();
        Player localPlayer = minecraft.player;
        ClientLevel level = minecraft.level;
        if (localPlayer == null || level == null) {
            clearAll();
            return;
        }

        for (Player remotePlayer : level.players()) {
            boolean eligible = shouldOutline(
                    KeyMappings.aimbotEnabled,
                    remotePlayer == localPlayer,
                    TeammateManager.isEffectiveTeammate(localPlayer, remotePlayer),
                    remotePlayer.isAlive()
            );

            if (eligible) {
                boolean wasGlowing = previousGlowStates.computeIfAbsent(remotePlayer, player -> {
                    return player.hasGlowingTag();
                });
                if (!wasGlowing && !remotePlayer.hasGlowingTag()) {
                    remotePlayer.setGlowingTag(true);
                }
            } else {
                clear(remotePlayer);
            }
        }

        previousGlowStates.keySet().removeIf(player -> {
            if (level.players().contains(player)) return false;
            clearGlow(player, previousGlowStates.get(player));
            return true;
        });
    }

    static boolean shouldOutline(boolean enabled, boolean samePlayer, boolean allied, boolean alive) {
        return enabled && !samePlayer && !allied && alive;
    }

    static boolean shouldClearOwnedGlow(boolean owned, boolean currentlyGlowing, boolean wasGlowingBefore) {
        return owned && currentlyGlowing && !wasGlowingBefore;
    }

    private void clear(Player player) {
        Boolean wasGlowing = previousGlowStates.remove(player);
        if (wasGlowing != null) {
            clearGlow(player, wasGlowing);
        }
    }

    private void clearAll() {
        previousGlowStates.forEach(this::clearGlow);
        previousGlowStates.clear();
    }

    private void clearGlow(Player player, boolean wasGlowingBefore) {
        if (shouldClearOwnedGlow(true, player.hasGlowingTag(), wasGlowingBefore)) {
            player.setGlowingTag(false);
        }
    }
}
