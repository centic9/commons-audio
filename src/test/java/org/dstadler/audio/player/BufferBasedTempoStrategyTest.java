package org.dstadler.audio.player;

import org.dstadler.audio.buffer.BlockingSeekableRingBuffer;
import org.dstadler.audio.buffer.CountingSeekableRingBuffer;
import org.dstadler.audio.buffer.CountingSeekableRingBufferImpl;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.dstadler.audio.player.BufferBasedTempoStrategy.DEFAULT_KEEP_AREA_SECONDS;
import static org.dstadler.audio.player.BufferBasedTempoStrategy.DEFAULT_SPEED_STEP;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class BufferBasedTempoStrategyTest {
    private final AtomicInteger fill = new AtomicInteger();
    private final AtomicInteger size = new AtomicInteger();

    // mock the implementation with values for the test, only getChunksWrittenPerSecond(),
    // fill(), size() and capacity() are called on the buffer
    private final CountingSeekableRingBuffer buffer = new CountingSeekableRingBufferImpl(new BlockingSeekableRingBuffer(10)) {
        @Override
        public int size() {
            return size.get();
        }

        @Override
        public int fill() {
            return fill.get();
        }

        @Override
        public double getChunksWrittenPerSecond() {
            // to make test-computations easier use 1.0 here
            return 1.0;
        }
    };

    private final BufferBasedTempoStrategy strategy = new BufferBasedTempoStrategy(() -> buffer, DEFAULT_KEEP_AREA_SECONDS, DEFAULT_SPEED_STEP);

    @Test
    public void testCalculationEmpty() {
        assertEquals(1.0f, strategy.calculateTempo(), 0.01);
    }

    @Test
    public void testCalculationSmallBuffer() {
        fill.set(100);
        size.set(0);
        assertEquals(1.0f, strategy.calculateTempo(), 0.01);

        // limit 50 (200/4)
        fill.set(200);
        size.set(0);
        assertEquals(0.95f, strategy.calculateTempo(), 0.01);

        // limit 100 (400/4)
        fill.set(400);
        size.set(0);
        assertEquals(0.90f, strategy.calculateTempo(), 0.01);

        // limit 150 (600/4)
        fill.set(600);
        size.set(0);
        assertEquals(0.85f, strategy.calculateTempo(), 0.01);

        // limit 200 (800/4)
        fill.set(800);
        size.set(0);
        assertEquals(0.80f, strategy.calculateTempo(), 0.01);
    }

    @Test
    public void testCalculationNoBackwardsBuffer() {
        fill.set(1000);
        size.set(1000);
        assertEquals(1.2f, strategy.calculateTempo(), 0.01);
    }

    @Test
    public void testCalculationSomeBackwardsBuffer2() {
        fill.set(4000);
        size.set(3900);
        assertEquals(1.15f, strategy.calculateTempo(), 0.01);
    }

    @Test
    public void testCalculationSomeBackwardsBuffer3() {
        fill.set(4000);
        size.set(3840);
        assertEquals(1.1f, strategy.calculateTempo(), 0.01);
    }

    @Test
    public void testCalculationSomeBackwardsBuffer() {
        fill.set(4000);
        size.set(3750);
        assertEquals(1.05f, strategy.calculateTempo(), 0.01);
    }

    @Test
    public void testCalculationSomeBackwardsBufferNoCap() {
        fill.set(1000);
        size.set(875);
        assertEquals(1.1f, strategy.calculateTempo(), 0.01);
    }

    @Test
    public void testCalculationSomeMoreBackwardsBuffer() {
        fill.set(4000);
        size.set(3650);
        assertEquals(1.0f, strategy.calculateTempo(), 0.01);
    }

    @Test
    public void testCalculationSomeMoreBackwardsBufferNoCap() {
        fill.set(1000);
        size.set(775);
        assertEquals(1.05f, strategy.calculateTempo(), 0.01);
    }

    @Test
    public void testCalculationNoForwardBuffer() {
        fill.set(4000);
        size.set(0);
        assertEquals(0.8f, strategy.calculateTempo(), 0.01);
    }

    @Test
    public void testCalculationSomeForwardBuffer() {
        fill.set(4000);
        size.set(80);
        assertEquals(0.85f, strategy.calculateTempo(), 0.01);
    }

    @Test
    public void testCalculationSomeForwardBuffer2() {
        fill.set(4000);
        size.set(155);
        assertEquals(0.9f, strategy.calculateTempo(), 0.01);
    }

    @Test
    public void testCalculationSomeForwardBuffer3() {
        fill.set(4000);
        size.set(250);
        assertEquals(0.95f, strategy.calculateTempo(), 0.01);
    }

    @Test
    public void testCalculationSomeForwardBufferNoCap() {
        fill.set(1000);
        size.set(150);
        assertEquals(0.9f, strategy.calculateTempo(), 0.01);
    }

    @Test
    public void testCalculationNoForwardBufferNoCap() {
        fill.set(1000);
        size.set(0);
        assertEquals(0.8f, strategy.calculateTempo(), 0.01);
    }

    @Test
    public void testCalculationSomeMoreForwardBuffer() {
        fill.set(4000);
        size.set(700);
        assertEquals(1.0f, strategy.calculateTempo(), 0.01);
    }

    @Test
    public void testCalculationSomeMoreForwardBufferNoCap() {
        fill.set(1000);
        size.set(200);
        assertEquals(0.95f, strategy.calculateTempo(), 0.01);
    }

    @Test
    public void testBug() {
        fill.set(214);
        size.set(19);
        assertEquals(0.95f, strategy.calculateTempo(), 0.01);
    }

    @Test
    public void testWithChunksPerSecond() {
        // test with some chunksWrittenPerSecond as well
        try (CountingSeekableRingBuffer buffer = new TestBufferImpl()) {
            BufferBasedTempoStrategy strategy = new BufferBasedTempoStrategy(() -> buffer, DEFAULT_KEEP_AREA_SECONDS, DEFAULT_SPEED_STEP);

            assertEquals(0.95f, strategy.calculateTempo(), 0.01);
        }
    }

    @Test
    public void testWithDifferentKeepArea() {
        try (CountingSeekableRingBuffer buffer = new TestBufferImpl()) {
            BufferBasedTempoStrategy strategy = new BufferBasedTempoStrategy(() -> buffer, 600, DEFAULT_SPEED_STEP);

            assertEquals(1f, strategy.calculateTempo(), 0.01, "With larger keep area we should not apply any change in tempo here and thus should get back 1.0f");
        }
    }

    @Test
    public void testWithDifferentSpeedStep() {
        // test with some chunksWrittenPerSecond as well
        try (CountingSeekableRingBuffer buffer = new TestBufferImpl()) {
            BufferBasedTempoStrategy strategy = new BufferBasedTempoStrategy(() -> buffer, DEFAULT_KEEP_AREA_SECONDS, 0.01f);

            assertEquals(0.99f, strategy.calculateTempo(), 0.01, "With smaller speedStep we should get back a smaller slowdown value of 0.99f instead of default 0.95f");
        }
    }

    @Test
    public void testName() {
        assertEquals("adaptive", strategy.name());
    }

    private static class TestBufferImpl extends CountingSeekableRingBufferImpl {
        public TestBufferImpl() {
            super(new BlockingSeekableRingBuffer(10));
        }

        @Override
        public int size() {
            return 12;
        }

        @Override
        public int fill() {
            return 307;
        }

        @Override
        public double getChunksWrittenPerSecond() {
            return 1.49;
        }
    }
}
