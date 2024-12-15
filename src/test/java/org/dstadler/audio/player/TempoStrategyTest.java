package org.dstadler.audio.player;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.Collection;

import static org.dstadler.audio.player.BufferBasedTempoStrategy.DEFAULT_KEEP_AREA_SECONDS;
import static org.dstadler.audio.player.BufferBasedTempoStrategy.DEFAULT_SPEED_STEP;
import static org.dstadler.audio.player.TempoStrategy.ADAPTIVE_PARAMS_PATTERN;
import static org.junit.jupiter.api.Assertions.*;

public class TempoStrategyTest {
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                // only the ones without param are 'valid'
                { TempoStrategy.DEFAULT, true, false },
                { TempoStrategy.ADAPTIVE, true, false },
                { TempoStrategy.CONSTANT, true, false },

                { "", false, false },
                { "unknown", false, false },
                { TempoStrategy.DEFAULT + ":", false, false },
                { TempoStrategy.ADAPTIVE_PREFIX, false, false },
                { TempoStrategy.ADAPTIVE_PREFIX + "150:0.01", false, false },
                { TempoStrategy.ADAPTIVE_PREFIX + "abcd", false, false },
                { TempoStrategy.CONSTANT_1, false, false },
                { TempoStrategy.CONSTANT_PREFIX + "0.3", false, false },

                // this one is failing parsing
                { TempoStrategy.CONSTANT_PREFIX + "abcd", false, true },
        });
    }

    @MethodSource("data")
    @ParameterizedTest(name = "Tempo-Strategy: {0}, isValid: {1}, isFailing: {2}")
    public void testCreate(String tempoStrategy, boolean ignoredIsValid, boolean isFailing) {
        try {
            assertNotNull(TempoStrategy.create(tempoStrategy, null));
            if (isFailing) {
                fail("Should catch exception here");
            }
        } catch (NumberFormatException e) {
            // expected only if specified in the parameters
            if (!isFailing) {
                throw e;
            }
        }
    }

    @MethodSource("data")
    @ParameterizedTest(name = "Tempo-Strategy: {0}, isValid: {1}, isFailing: {2}")
    public void testValidate(String tempoStrategy, boolean isValid, boolean ignoredIsFailing) {
        try {
            TempoStrategy.validate(tempoStrategy);
            if (!isValid) {
                fail("Should not be valid here");
            }
        } catch (IllegalStateException e) {
            // expected only if specified in the parameters
            if (isValid) {
                throw e;
            }
        }
    }

    @MethodSource("data")
    @ParameterizedTest(name = "Tempo-Strategy: {0}, isValid: {1}, isFailing: {2}")
    public void testValidateNull(String ignoredTempoStrategy, boolean ignoredIsValid, boolean ignoredIsFailing) {
        assertThrows(NullPointerException.class, () ->
            TempoStrategy.validate(null));
    }

    @MethodSource("data")
    @ParameterizedTest(name = "Tempo-Strategy: {0}, isValid: {1}, isFailing: {2}")
    public void testValidateEmpty(String ignoredTempoStrategy, boolean ignoredIsValid, boolean ignoredIsFailing) {
        assertThrows(IllegalStateException.class, () ->
            TempoStrategy.validate(""));
    }

    @MethodSource("data")
    @ParameterizedTest(name = "Tempo-Strategy: {0}, isValid: {1}, isFailing: {2}")
    public void testValidateInvalid(String ignoredTempoStrategy, boolean ignoredIsValid, boolean ignoredIsFailing) {
        assertThrows(IllegalStateException.class, () ->
            TempoStrategy.validate("unknown"));
    }

    @MethodSource("data")
    @ParameterizedTest(name = "Tempo-Strategy: {0}, isValid: {1}, isFailing: {2}")
    public void testValidateInvalid2(String ignoredTempoStrategy, boolean ignoredIsValid, boolean ignoredIsFailing) {
        assertThrows(IllegalStateException.class, () ->
            TempoStrategy.validate(TempoStrategy.CONSTANT_PREFIX + "a.b"));
    }

    @MethodSource("data")
    @ParameterizedTest(name = "Tempo-Strategy: {0}, isValid: {1}, isFailing: {2}")
    public void testValidateInvalid3(String ignoredTempoStrategy, boolean ignoredIsValid, boolean ignoredIsFailing) {
        assertThrows(IllegalStateException.class, () ->
            TempoStrategy.validate(" constant"));
    }

    @MethodSource("data")
    @ParameterizedTest(name = "Tempo-Strategy: {0}, isValid: {1}, isFailing: {2}")
    public void testConstants(String ignoredTempoStrategy, boolean ignoredIsValid, boolean ignoredIsFailing) {
        assertEquals("constant", TempoStrategy.CONSTANT);
        assertEquals("constant:", TempoStrategy.CONSTANT_PREFIX);
        assertEquals("constant:1.0", TempoStrategy.CONSTANT_1);

        assertEquals(TempoStrategy.CONSTANT, new ConstantTempoStrategy(1.0f).name());
        assertEquals(TempoStrategy.ADAPTIVE, new BufferBasedTempoStrategy(() -> null, DEFAULT_KEEP_AREA_SECONDS, DEFAULT_SPEED_STEP).name());
    }

    @MethodSource("data")
    @ParameterizedTest(name = "Tempo-Strategy: {0}, isValid: {1}, isFailing: {2}")
    public void testAdaptiveParamsPattern(String ignoredTempoStrategy, boolean ignoredIsValid, boolean ignoredIsFailing) {
        assertFalse(ADAPTIVE_PARAMS_PATTERN.matcher("").matches());
        assertFalse(ADAPTIVE_PARAMS_PATTERN.matcher(":").matches());
        assertFalse(ADAPTIVE_PARAMS_PATTERN.matcher("-12:2.123").matches());
        assertFalse(ADAPTIVE_PARAMS_PATTERN.matcher("12:-2.123").matches());

        assertTrue(ADAPTIVE_PARAMS_PATTERN.matcher("0:0").matches());
        assertTrue(ADAPTIVE_PARAMS_PATTERN.matcher("300:0.05").matches());
        assertTrue(ADAPTIVE_PARAMS_PATTERN.matcher("3000000:2.123").matches());
    }
}
