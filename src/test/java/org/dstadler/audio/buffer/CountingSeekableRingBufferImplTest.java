package org.dstadler.audio.buffer;

import org.dstadler.commons.testing.TestHelpers;
import org.dstadler.commons.testing.ThreadTestHelper;
import org.junit.Test;

import static org.dstadler.audio.buffer.CountingSeekableRingBufferImpl.DEFAULT_CHUNKS_PER_SECOND;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class CountingSeekableRingBufferImplTest extends AbstractBlockingSeekableRingBufferTester {
    private static final int NUMBER_OF_THREADS = 10;
    private static final int NUMBER_OF_TESTS = 10000;

    // reuse tests from the abstract tester here as well to ensure that the Counting implementation
    // delegates all calls correctly
    @Override
    SeekableRingBuffer<Chunk> getBlockingSeekableRingBuffer() {
        return new CountingSeekableRingBufferImpl(new BlockingSeekableRingBuffer(10));
    }

    private CountingSeekableRingBuffer getBuffer() {
        return (CountingSeekableRingBuffer)buffer;
    }

    @Test
    public void testCounting() {
        assertEquals(0, getBuffer().getChunksWrittenPerSecond(), 0);
        assertEquals(0, getBuffer().getChunksReadPerSecond(), 0);
        assertEquals(DEFAULT_CHUNKS_PER_SECOND, getBuffer().getChunksPerSecond(), 0.01);

        getBuffer().addNoStats(new Chunk(new byte[0], "", 0));
        assertEquals(0, getBuffer().getChunksWrittenPerSecond(), 0);
        assertEquals(0, getBuffer().getChunksReadPerSecond(), 0);
        assertEquals(DEFAULT_CHUNKS_PER_SECOND, getBuffer().getChunksPerSecond(), 0.01);

        buffer.add(new Chunk(new byte[0], "", 0));
        assertTrue(Double.isInfinite(getBuffer().getChunksWrittenPerSecond()));
        assertEquals(0, getBuffer().getChunksReadPerSecond(), 0);
        assertEquals(DEFAULT_CHUNKS_PER_SECOND, getBuffer().getChunksPerSecond(), 0.01);

        assertEquals(0, ((CountingSeekableRingBufferImpl)getBuffer()).getChunksReadOverall());
    }

    @Test
    public void testGetChunksWrittenPerSec() {
        assertEquals(0, getBuffer().getChunksWrittenPerSecond(), 0);

        getBuffer().add(new Chunk(new byte[] { 1 }, "", System.currentTimeMillis()-6000));
        getBuffer().add(new Chunk(new byte[] { 1 }, "", System.currentTimeMillis()-2000));
        getBuffer().add(new Chunk(new byte[] { 1 }, "", System.currentTimeMillis()-1000));

        assertEquals(0.6, getBuffer().getChunksWrittenPerSecond(), 0.01);
        assertEquals(0, getBuffer().getChunksReadPerSecond(), 0);
        assertEquals(0.6, getBuffer().getChunksPerSecond(), 0.01);
    }

    @Test
    public void testGetChunksReadPerSec() throws InterruptedException {
        assertEquals(0, getBuffer().getChunksWrittenPerSecond(), 0);

        long tms = System.currentTimeMillis() - 6000;
        getBuffer().add(new Chunk(new byte[] { 1 }, "", tms));
        getBuffer().add(new Chunk(new byte[] { 1 }, "", tms));
        getBuffer().add(new Chunk(new byte[] { 1 }, "", tms));
        getBuffer().add(new Chunk(new byte[] { 1 }, "", tms));
        getBuffer().add(new Chunk(new byte[] { 1 }, "", tms));

        long start = System.currentTimeMillis();

        final double writtenPerSecond = getBuffer().getChunksWrittenPerSecond();
        assertTrue("Had: " + writtenPerSecond, Double.isInfinite(writtenPerSecond));
        assertEquals(0, getBuffer().getChunksReadPerSecond(), 0);
        assertEquals(DEFAULT_CHUNKS_PER_SECOND, getBuffer().getChunksPerSecond(), 0.01);

        getBuffer().next();
        Thread.sleep(600);
        getBuffer().next();
        Thread.sleep(200);
        getBuffer().next();
        Thread.sleep(100);
        getBuffer().next();

        long end = System.currentTimeMillis();

        assertTrue(Double.isInfinite(getBuffer().getChunksWrittenPerSecond()));
        final double expected = 4 / ((double) (end - start)) * 1000;
        assertEquals(expected, getBuffer().getChunksReadPerSecond(), 0.01);
        assertEquals(expected, getBuffer().getChunksPerSecond(), 0.01);

        assertEquals(4, ((CountingSeekableRingBufferImpl)getBuffer()).getChunksReadOverall());
    }

    @Test
    public void testGetChunksWrittenPerSecMany() {
        // add two chunks per second
        for(int i = 0; i < 100;i++) {
            buffer.add(new Chunk(new byte[0], "", 500*i));
        }
        assertEquals(2.0202, getBuffer().getChunksWrittenPerSecond(), 0.01);
        assertEquals(0, getBuffer().getChunksReadPerSecond(), 0);
        assertEquals(2.0202, getBuffer().getChunksPerSecond(), 0.01);
    }

    @Test
    public void testGetChunksWrittenPerSecTimeJump() {
        assertEquals(0, getBuffer().getChunksWrittenPerSecond(), 0);

        long now = System.currentTimeMillis();
        getBuffer().add(new Chunk(new byte[] { 1 }, "", now - 64000));
        getBuffer().add(new Chunk(new byte[] { 1 }, "", now - 62000));
        getBuffer().add(new Chunk(new byte[] { 1 }, "", now - 2000));
        getBuffer().add(new Chunk(new byte[] { 1 }, "", now - 1000));

        assertEquals(1, getBuffer().getChunksWrittenPerSecond(), 0);
        assertEquals(0, getBuffer().getChunksReadPerSecond(), 0);
        assertEquals(1, getBuffer().getChunksPerSecond(), 0.01);
    }

    @Override
    @Test
    public void testToString() {
        TestHelpers.ToStringTest(buffer);

        getBuffer().addNoStats(new Chunk(new byte[0], "", 0));
        TestHelpers.ToStringTest(buffer);
    }

    @Test
    public void testThreaded() throws Throwable {
        ThreadTestHelper helper =
                new ThreadTestHelper(NUMBER_OF_THREADS, NUMBER_OF_TESTS);

        helper.executeTest(new ThreadTestHelper.TestRunnable() {
            @Override
            public void doEnd(int threadNum) {
                buffer.close();
            }

            @Override
            public void run(int threadNum, int iter) {
                if (iter % 2 == 0) {
                    assertTrue(getBuffer().getChunksWrittenPerSecond() >= 0);
                } else {
                    buffer.add(new Chunk(new byte[] {1,2,3}, "bla bla", 0));
                }
            }
        });
    }

    @Test
    public void testNullDelegate() {
        //noinspection ConstantConditions
        assertThrows(NullPointerException.class, () -> new CountingSeekableRingBufferImpl(null));
    }
}
