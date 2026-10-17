package dev.b410.BossBarMessages;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DurationParserTest {

    @ParameterizedTest
    @CsvSource({"1s,20", "30s,600", "5m,6000", "1h,72000", "10S,200", " 2m ,2400"})
    void parsesValidDurations(String input, long ticks) {
        assertEquals(ticks, DurationParser.parseToTicks(input));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "0s", "10", "s", "5d", "-5s", "1.5s", "abc"})
    void rejectsInvalidDurations(String input) {
        assertThrows(IllegalArgumentException.class, () -> DurationParser.parseToTicks(input));
    }

    @Test
    void rejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> DurationParser.parseToTicks(null));
    }

    @Test
    void rejectsOverflow() {
        assertThrows(IllegalArgumentException.class, () -> DurationParser.parseToTicks("999999999h"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0s", "0m", "0h", ""})
    void allowZeroAcceptsZero(String input) {
        assertEquals(0L, DurationParser.parseToTicksAllowZero(input));
    }

    @Test
    void allowZeroStillParsesNormalValues() {
        assertEquals(600L, DurationParser.parseToTicksAllowZero("30s"));
        assertEquals(0L, DurationParser.parseToTicksAllowZero(null));
        assertThrows(IllegalArgumentException.class, () -> DurationParser.parseToTicksAllowZero("x"));
    }

    @ParameterizedTest
    @CsvSource({"0,0s", "20,1s", "1200,1m", "72000,1h", "1800,90s", "10,0s"})
    void formatsTicks(long ticks, String expected) {
        assertEquals(expected, DurationParser.formatTicks(ticks));
    }
}
