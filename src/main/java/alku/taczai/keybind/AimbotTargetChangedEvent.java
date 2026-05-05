package alku.taczai.keybind;

import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.eventbus.api.Event;

public class AimbotTargetChangedEvent extends Event {
    private final LivingEntity target;

    public AimbotTargetChangedEvent(LivingEntity target) {
        this.target = target;
    }

    public LivingEntity getTarget() {
        return target;
    }
}
