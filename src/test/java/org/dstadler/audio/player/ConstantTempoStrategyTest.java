package org.dstadler.audio.player;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ConstantTempoStrategyTest {

    @Test
    public void test() {
        ConstantTempoStrategy strategy = new ConstantTempoStrategy(1.0f);
        assertEquals(1.0, strategy.calculateTempo(), 0.0001);

        strategy = new ConstantTempoStrategy(1.1f);
        assertEquals(1.1, strategy.calculateTempo(), 0.0001);

        strategy = new ConstantTempoStrategy(0.75f);
        assertEquals(0.75, strategy.calculateTempo(), 0.0001);
    }
}
