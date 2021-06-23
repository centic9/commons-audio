package org.dstadler.audio.player;

import org.junit.Test;

import static org.dstadler.audio.player.BufferBasedTempoStrategy.DEFAULT_KEEP_AREA_SECONDS;
import static org.dstadler.audio.player.BufferBasedTempoStrategy.DEFAULT_SPEED_STEP;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

public class TempoStrategyTest {
    @Test
    public void testCreate() {
        assertNotNull(TempoStrategy.create("", null));
        assertNotNull(TempoStrategy.create("default", null));
        assertNotNull(TempoStrategy.create("adaptive", null));
        assertNotNull(TempoStrategy.create("default:", null));
        assertNotNull(TempoStrategy.create("adaptive:", null));
        assertNotNull(TempoStrategy.create(TempoStrategy.CONSTANT_1, null));
        assertNotNull(TempoStrategy.create(TempoStrategy.CONSTANT_PREFIX + "0.3", null));

        assertNotNull(TempoStrategy.create("unknown", null));

        try {
            TempoStrategy.create(TempoStrategy.CONSTANT_PREFIX + "abcd", null);
            fail("Should catch exception here");
        } catch (NumberFormatException e) {
            // expected here
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
    public void testValidate() {
        TempoStrategy.validate("default");
        TempoStrategy.validate(TempoStrategy.CONSTANT);
        TempoStrategy.validate("adaptive");
    }

    @Test
    public void testConstants() {
        assertEquals("constant", TempoStrategy.CONSTANT);
        assertEquals("constant:", TempoStrategy.CONSTANT_PREFIX);
        assertEquals("constant:1.0", TempoStrategy.CONSTANT_1);

        assertEquals(TempoStrategy.CONSTANT, new ConstantTempoStrategy(1.0f).name());
        assertEquals(TempoStrategy.ADAPTIVE, new BufferBasedTempoStrategy(() -> null, DEFAULT_KEEP_AREA_SECONDS, DEFAULT_SPEED_STEP).name());
    }
}
