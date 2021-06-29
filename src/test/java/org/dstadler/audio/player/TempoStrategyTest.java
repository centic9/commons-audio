package org.dstadler.audio.player;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

import static org.dstadler.audio.player.BufferBasedTempoStrategy.DEFAULT_KEEP_AREA_SECONDS;
import static org.dstadler.audio.player.BufferBasedTempoStrategy.DEFAULT_SPEED_STEP;
import static org.dstadler.audio.player.TempoStrategy.ADAPTIVE_PARAMS_PATTERN;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(Parameterized.class)
public class TempoStrategyTest {
    @Parameterized.Parameters(name = "Tempo-Strategy: {0}, isValid: {1}, isFailing: {2}")
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

    @Parameterized.Parameter
    public String tempoStrategy;

    @Parameterized.Parameter(1)
    public boolean isValid;

    @Parameterized.Parameter(2)
    public boolean isFailing;

    @Test
    public void testCreate() {
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

    @Test
    public void testValidate() {
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

    @Test(expected = NullPointerException.class)
    public void testValidateNull() {
        TempoStrategy.validate(null);
    }

    @Test(expected = IllegalStateException.class)
    public void testValidateEmpty() {
        TempoStrategy.validate("");
    }

    @Test(expected = IllegalStateException.class)
    public void testValidateInvalid() {
        TempoStrategy.validate("unknown");
    }

    @Test(expected = IllegalStateException.class)
    public void testValidateInvalid2() {
        TempoStrategy.validate(TempoStrategy.CONSTANT_PREFIX + "a.b");
    }

    @Test(expected = IllegalStateException.class)
    public void testValidateInvalid3() {
        TempoStrategy.validate(" constant");
    }

    @Test
    public void testConstants() {
        assertEquals("constant", TempoStrategy.CONSTANT);
        assertEquals("constant:", TempoStrategy.CONSTANT_PREFIX);
        assertEquals("constant:1.0", TempoStrategy.CONSTANT_1);

        assertEquals(TempoStrategy.CONSTANT, new ConstantTempoStrategy(1.0f).name());
        assertEquals(TempoStrategy.ADAPTIVE, new BufferBasedTempoStrategy(() -> null, DEFAULT_KEEP_AREA_SECONDS, DEFAULT_SPEED_STEP).name());
    }

    @Test
    public void testAdaptiveParamsPattern() {
        assertFalse(ADAPTIVE_PARAMS_PATTERN.matcher("").matches());
        assertFalse(ADAPTIVE_PARAMS_PATTERN.matcher(":").matches());
        assertFalse(ADAPTIVE_PARAMS_PATTERN.matcher("-12:2.123").matches());
        assertFalse(ADAPTIVE_PARAMS_PATTERN.matcher("12:-2.123").matches());

        assertTrue(ADAPTIVE_PARAMS_PATTERN.matcher("0:0").matches());
        assertTrue(ADAPTIVE_PARAMS_PATTERN.matcher("300:0.05").matches());
        assertTrue(ADAPTIVE_PARAMS_PATTERN.matcher("3000000:2.123").matches());
    }
}
