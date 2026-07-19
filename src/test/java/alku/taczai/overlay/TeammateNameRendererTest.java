package alku.taczai.overlay;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TeammateNameRendererTest {
    @Test
    void teammateNamesAreGreen() {
        Component name = TeammateNameRenderer.greenName(Component.literal("Alice"));

        assertEquals("Alice", name.getString());
        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.GREEN), name.getStyle().getColor());
    }
}
