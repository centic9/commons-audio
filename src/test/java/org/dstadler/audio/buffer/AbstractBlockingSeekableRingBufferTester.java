package org.dstadler.audio.buffer;

import org.apache.commons.lang3.RandomUtils;
import org.dstadler.commons.testing.MemoryLeakVerifier;
import org.dstadler.commons.testing.TestHelpers;
import org.dstadler.commons.testing.ThreadTestHelper;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public abstract class AbstractBlockingSeekableRingBufferTester {
    private static final Chunk CHUNK_1 = new Chunk(new byte[] { 1, 2, 3 }, "", 0);
    private static final Chunk CHUNK_2 = new Chunk(new byte[] { 4, 5, 6, 7 }, "", 0);
    private static final int NUMBER_OF_THREADS = 10;
    private static final int NUMBER_OF_TESTS = 100000;

    private final MemoryLeakVerifier verifier = new MemoryLeakVerifier();

    protected SeekableRingBuffer<Chunk> buffer = getBlockingSeekableRingBuffer();

    abstract SeekableRingBuffer<Chunk> getBlockingSeekableRingBuffer();

    @After
    public void tearDown() {
        verifier.assertGarbageCollected();
    }

    @Test
    public void empty() {
        assertEquals(0, buffer.fill());
        assertEquals(0, buffer.size());
        assertEquals(9, buffer.capacity());
        assertFalse(buffer.full());
        assertNull(buffer.peek());
        assertEquals(0, buffer.bufferedForward());
        assertEquals(0, buffer.bufferedBackward());
    }

    @Test
    public void addAndGet() {
        buffer.add(CHUNK_1);
        assertEquals(CHUNK_1, buffer.peek());
        assertEquals(CHUNK_1, buffer.next());
        assertNull(buffer.peek());

        buffer.close();
        assertNull(buffer.peek());
        assertNull(buffer.next());
    }

    @Test
    public void addAndGet2() {
        buffer.add(CHUNK_1);
        buffer.add(CHUNK_2);
        assertEquals(CHUNK_1, buffer.next());
        assertEquals(CHUNK_2, buffer.next());
    }

    @Test
    public void buffered() {
        assertEquals(0, buffer.bufferedForward());
        assertEquals(0, buffer.bufferedBackward());

        buffer.add(CHUNK_1);
        assertEquals(1, buffer.bufferedForward());
        assertEquals(0, buffer.bufferedBackward());

        buffer.add(CHUNK_2);
        assertEquals(2, buffer.bufferedForward());
        assertEquals(0, buffer.bufferedBackward());

        assertArrayEquals(CHUNK_1.getData(), buffer.next().getData());
        assertEquals(1, buffer.bufferedForward());
        assertEquals(1, buffer.bufferedBackward());

        assertArrayEquals(CHUNK_2.getData(), buffer.next().getData());
        assertEquals(0, buffer.bufferedForward());
        assertEquals(2, buffer.bufferedBackward());

        buffer.add(CHUNK_1);
        buffer.add(CHUNK_1);
        buffer.add(CHUNK_1);
        buffer.add(CHUNK_1);
        buffer.add(CHUNK_1);
        buffer.add(CHUNK_1);
        buffer.add(CHUNK_1);
        assertEquals(7, buffer.bufferedForward());
        assertEquals(2, buffer.bufferedBackward());

        buffer.add(CHUNK_1);
        assertEquals(8, buffer.bufferedForward());
        assertEquals(1, buffer.bufferedBackward());

        buffer.add(CHUNK_1);
        assertEquals(9, buffer.bufferedForward());
        assertEquals(0, buffer.bufferedBackward());

        buffer.add(CHUNK_1);
        assertEquals(9, buffer.bufferedForward());
        assertEquals(0, buffer.bufferedBackward());

        assertNotNull(buffer.next());
        assertEquals(8, buffer.bufferedForward());
        assertEquals(1, buffer.bufferedBackward());

        assertNotNull(buffer.next());
        assertEquals(7, buffer.bufferedForward());
        assertEquals(2, buffer.bufferedBackward());

        assertNotNull(buffer.next());
        assertNotNull(buffer.next());
        assertNotNull(buffer.next());
        assertNotNull(buffer.next());
        assertNotNull(buffer.next());
        assertNotNull(buffer.next());
        assertEquals(1, buffer.bufferedForward());
        assertEquals(8, buffer.bufferedBackward());

        assertNotNull(buffer.next());
        assertEquals(0, buffer.bufferedForward());
        assertEquals(9, buffer.bufferedBackward());
    }

    @Test
    public void testClose() {
        buffer.add(CHUNK_1);
        buffer.add(CHUNK_2);
        assertEquals(CHUNK_1, buffer.next());
        assertEquals(CHUNK_2, buffer.next());

        buffer.add(CHUNK_2);
        assertEquals(CHUNK_2, buffer.peek());
        assertEquals(CHUNK_2, buffer.peek());

        buffer.close();
        assertNull(buffer.peek());
        assertNull(buffer.next());
    }

    @Test
    public void testSizes() {
        assertTrue(buffer.empty());
        assertFalse(buffer.full());
        assertEquals(9, buffer.capacity());
        assertEquals(0, buffer.size());
        assertEquals(0, buffer.fill());

        buffer.reset();
        assertTrue(buffer.empty());
        assertFalse(buffer.full());
        assertEquals(9, buffer.capacity());
        assertEquals(0, buffer.size());
        assertEquals(0, buffer.fill());

        buffer.add(CHUNK_1);
        assertFalse(buffer.empty());
        assertFalse(buffer.full());
        assertEquals(9, buffer.capacity());
        assertEquals(1, buffer.fill());

        buffer.reset();
        assertTrue(buffer.empty());
        assertFalse(buffer.full());
        assertEquals(9, buffer.capacity());
        assertEquals(0, buffer.size());
        assertEquals(0, buffer.fill());

        for(int i = 0;i < 8;i++) {
            buffer.add(CHUNK_1);
            assertFalse(buffer.empty());
            assertFalse(buffer.full());
            assertEquals(9, buffer.capacity());
            assertEquals(i+1, buffer.size());
            assertEquals(i+1, buffer.fill());
        }

        buffer.add(CHUNK_1);
        assertFalse(buffer.empty());
        assertTrue(buffer.full());
        assertEquals(9, buffer.capacity());
        assertEquals(9, buffer.size());
        assertEquals(9, buffer.fill());

        buffer.add(CHUNK_1);
        assertFalse(buffer.empty());
        assertTrue(buffer.full());
        assertEquals(9, buffer.capacity());
        assertEquals(9, buffer.size());
        assertEquals(9, buffer.fill());
    }

    @Test
    public void addAndGetMany() {
        for(byte i = 0;i < 20;i++) {
            buffer.add(new Chunk(new byte[] { i }, "", 0));
            assertEquals(Math.min(i+1, 9), buffer.size());
            assertEquals(Math.min(i+1, 9), buffer.fill());
        }
        assertEquals(9, buffer.size());
        assertEquals(9, buffer.fill());
        for(byte i = 11;i < 20;i++) {
            assertEquals(new Chunk(new byte[] { i }, "", 0), buffer.next());
            assertEquals(9, buffer.fill());
        }
        assertEquals(0, buffer.size());
    }

    @Test
    public void testMultipleThreads() throws Throwable {
        ThreadTestHelper helper =
                new ThreadTestHelper(NUMBER_OF_THREADS, NUMBER_OF_TESTS);

        helper.executeTest(new ThreadTestHelper.TestRunnable() {
            @Override
            public void doEnd(int threadNum) {
                buffer.close();
            }

            @Override
            public void run(int threadNum, int iter) {
                int slot = iter % 6;
                switch (slot) {
                    case 0:
                        buffer.add(CHUNK_1);
                        break;
                    case 1:
                        buffer.empty();
                        buffer.full();
                        buffer.size();
                        buffer.fill();
                        assertEquals(9, buffer.capacity());
                        break;
                    case 2:
                        // might be null on close()
                        buffer.next();
                        break;
                    case 3:
                        buffer.reset();
                        break;
                    case 4:
                        buffer.seek(RandomUtils.nextInt(0, 20));
                        break;
                    case 5:
                        buffer.seek(RandomUtils.nextInt(0, 20)* (-1));
                        break;
                }
            }
        });
    }

    @Test
    public void testSeekInEmptyBuffer() {
        assertEquals("Cannot seek 0 buffer", 0, buffer.seek(0));
        assertEquals("Cannot seek forward in empty buffer", 0, buffer.seek(1));

        assertEquals(0, buffer.size());
        assertEquals("Can seek backwards", -1, buffer.seek(-1));
        assertEquals(1, buffer.size());
        assertEquals("Can seek forward now", 1, buffer.seek(1));
        assertEquals(0, buffer.size());
    }

    @Test
    public void testSeekInFilledBuffer() {
        buffer.add(CHUNK_1);
        buffer.add(CHUNK_2);

        assertEquals("Cannot seek 0 buffer even when not empty", 0, buffer.seek(0));

        assertEquals(CHUNK_1, buffer.peek());
        assertEquals(2, buffer.size());
        assertEquals("Can seek forward in filled buffer", 1, buffer.seek(1));
        assertEquals(CHUNK_2, buffer.peek());
        assertEquals(1, buffer.size());

        assertEquals("Can seek backwards in filled buffer", -1, buffer.seek(-1));
        assertEquals(CHUNK_1, buffer.peek());
        assertEquals(2, buffer.size());

        assertEquals("Can seek forward again", 1, buffer.seek(1));
        assertEquals(CHUNK_2, buffer.peek());
        assertEquals(1, buffer.size());
        assertEquals(2, buffer.fill());
    }

    @Test
    public void testSeekInFullBuffer() {
        for(byte i = 0;i < 15;i++) {
            buffer.add(new Chunk(new byte[] { i }, "", 0));
        }
        assertTrue(buffer.full());
        assertFalse(buffer.empty());
        assertEquals(new Chunk(new byte[] { 6 }, "", 0), buffer.peek());
        assertEquals(9, buffer.size());
        assertEquals(9, buffer.fill());

        assertEquals("Cannot seek backwards in full buffer", 0, buffer.seek(-1));
        assertEquals(new Chunk(new byte[] { 6 }, "", 0), buffer.peek());
        assertEquals(9, buffer.size());
        assertEquals(9, buffer.fill());

        assertEquals("Can seek forward in full buffer, making it non-full", 1, buffer.seek(1));
        assertEquals(new Chunk(new byte[] { 7 }, "", 0), buffer.peek());
        assertFalse(buffer.full());
        assertFalse(buffer.empty());
        assertEquals(8, buffer.size());
        assertEquals(9, buffer.fill());

        assertEquals("Can seek backwards now, making the buffer full again", -1, buffer.seek(-1));
        assertEquals(new Chunk(new byte[] { 6 }, "", 0), buffer.peek());
        assertTrue(buffer.full());
        assertFalse(buffer.empty());
        assertEquals(9, buffer.size());
        assertEquals(9, buffer.fill());

        assertEquals("Can seek forward up to empty() in full buffer", 9, buffer.seek(20));
        assertNull("Buffer is empty now as we forward up to head", buffer.peek());
        assertFalse(buffer.full());
        assertTrue(buffer.empty());
        assertEquals(0, buffer.size());
        assertEquals(9, buffer.fill());

        assertEquals("Can seek backwards up to full() in full buffer", -9, buffer.seek(-20));
        assertEquals(new Chunk(new byte[] { 6 }, "", 0), buffer.peek());
        assertTrue(buffer.full());
        assertFalse(buffer.empty());
        assertEquals(9, buffer.size());
        assertEquals(9, buffer.fill());
    }

    @Test
    public void testToString() {
        TestHelpers.ToStringTest(buffer);
        buffer.add(CHUNK_1);
        TestHelpers.ToStringTest(buffer);

        for(byte i = 0;i < 20;i++) {
            buffer.add(new Chunk(new byte[] { i }, "", 0));
        }
        TestHelpers.ToStringTest(buffer);
    }

    @Test
    public void testMemoryLeaks() {
        // load some data into the buffer
        for(byte i = 0;i < 15;i++) {
            buffer.add(new Chunk(new byte[] { i }, "", 0));
        }
        assertNotNull(buffer.peek());

        // then close it and unset the member to ensure nothing
        // keeps the class in memory
        buffer.close();
        verifier.addObject(buffer);
        buffer = null;
    }
}
