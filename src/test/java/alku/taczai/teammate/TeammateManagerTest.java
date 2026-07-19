package alku.taczai.teammate;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TeammateManagerTest {
    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void parsesUniqueValidUuidsAndSkipsInvalidEntries() {
        var parsed = TeammateManager.parseUuids(List.of(ALICE.toString(), "invalid", ALICE.toString(), BOB.toString()));
        assertEquals(List.of(ALICE, BOB), parsed.stream().toList());
    }

    @Test
    void serializesUuidsInStableOrder() {
        assertEquals(List.of(ALICE.toString(), BOB.toString()), TeammateManager.serializeUuids(List.of(BOB, ALICE)));
    }

    @Test
    void toggleAddsThenRemovesUuidAndTracksName() {
        TeammateManager.replaceSaved(List.of(), List.of());
        assertTrue(TeammateManager.toggle(ALICE, "Alice"));
        assertTrue(TeammateManager.isLocalTeammate(ALICE));
        assertEquals("Alice", TeammateManager.getSavedNames().get(ALICE));
        assertFalse(TeammateManager.toggle(ALICE, "Alice"));
        assertFalse(TeammateManager.isLocalTeammate(ALICE));
    }

    @Test
    void replaceReplacesTheWholeSelection() {
        TeammateManager.replaceSaved(List.of(ALICE.toString()), List.of(ALICE + "|Alice"));

        TeammateManager.replace(List.of(BOB), Map.of(BOB, "Bob"));

        assertFalse(TeammateManager.isLocalTeammate(ALICE));
        assertTrue(TeammateManager.isLocalTeammate(BOB));
        assertEquals(Map.of(BOB, "Bob"), TeammateManager.getSavedNames());
    }
}
