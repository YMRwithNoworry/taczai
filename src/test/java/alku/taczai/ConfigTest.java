package alku.taczai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfigTest {
    @Test
    void percentageValuesAreClampedToTheConfigurableRange() {
        assertEquals(0.0, Config.clampPercentage(-1.0));
        assertEquals(100.0, Config.clampPercentage(101.0));
        assertEquals(0.0, Config.clampPercentage(Double.NaN));
        assertEquals(42.5, Config.clampPercentage(42.5));
    }
}
