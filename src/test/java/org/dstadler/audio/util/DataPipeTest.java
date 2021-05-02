package org.dstadler.audio.util;


import org.apache.commons.lang3.RandomUtils;
import org.dstadler.commons.testing.TestHelpers;
import org.dstadler.commons.testing.ThreadTestHelper;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class DataPipeTest {
    @Test
    public void testDataPipeNotStarted() throws IOException, InterruptedException {
        DataPipe pipe = new DataPipe();

        assertFalse(pipe.isRunning());
        assertNull(pipe.getInput());
        assertThrows(NullPointerException.class, () -> pipe.write(new byte[0]));
        assertFalse(pipe.clearBuffer());
        pipe.waitAllConsumed();
        TestHelpers.ToStringTest(pipe);

        pipe.close();
        TestHelpers.ToStringTest(pipe);
    }

    @Test
    public void testDataPipeCreated() throws IOException, InterruptedException {
        DataPipe pipe = new DataPipe();

        pipe.createPipe();
        TestHelpers.ToStringTest(pipe);

        assertTrue(pipe.isRunning());
        assertNotNull(pipe.getInput());
        assertTrue(pipe.clearBuffer());
        pipe.waitAllConsumed();

        pipe.write(new byte[] {0});
        pipe.close();

        assertFalse(pipe.isRunning());
        assertNull(pipe.getInput());
        assertThrows(NullPointerException.class, () -> pipe.write(new byte[0]));
        assertFalse(pipe.clearBuffer());
        pipe.waitAllConsumed();

        TestHelpers.ToStringTest(pipe);
    }

    private static final int NUMBER_OF_THREADS = 10;
    private static final int NUMBER_OF_TESTS = 2000;

    @Test
    public void testMultipleThreads() throws Throwable {
        DataPipe pipe = new DataPipe();

        pipe.createPipe();

        ThreadTestHelper helper =
                new ThreadTestHelper(NUMBER_OF_THREADS, NUMBER_OF_TESTS);

        helper.executeTest((threadNum, itNum) -> {
            int rnd = RandomUtils.nextInt(0, 6);

            switch (rnd) {
                case 0:
                    assertTrue(pipe.isRunning());
                    break;
                case 1:
                    assertNotNull(pipe.getInput());
                    break;
                case 2:
                    pipe.write(new byte[0]);
                    break;
                case 3:
                    assertTrue(pipe.clearBuffer());
                    break;
                case 4:
                    pipe.waitAllConsumed();
                    break;
                case 5:
                    synchronized (pipe) {
                        TestHelpers.ToStringTest(pipe);
                    }
                    break;
                // close is not called to always be "running"
                // however createPipe will re-create the pipe frequently
                default:
                    fail("Unexpected random: " + rnd);
            }
        });
    }

    @Test
    public void testReadAndWrite() throws IOException, InterruptedException {
        AtomicReference<IOException> exc = new AtomicReference<>();

        try (DataPipe pipe = new DataPipe()) {
            pipe.createPipe();

            Thread writer = new Thread(() -> {
                for (int i = 0; i < 100000; i++) {
                    try {
                        pipe.write(new byte[]{(byte) (i % 256)});
                    } catch (IOException e) {
                        exc.set(e);
                    }
                }
            });
            writer.start();

            Thread reader = new Thread(() -> {
                for (int i = 0; i < 100000; i++) {
                    try {
                        assertEquals("Failed at index " + i,
                                i % 256, pipe.getInput().read());
                    } catch (IOException e) {
                        exc.set(e);
                    }
                }
            });
            reader.start();

            writer.join();
            reader.join();

            if (exc.get() != null) {
                throw exc.get();
            }

            assertEquals(0, pipe.getInput().available());
        }
    }

    @Test
    public void testClearBuffer() throws IOException {
        try (DataPipe pipe = new DataPipe()) {
            pipe.createPipe();

            pipe.write(new byte[] {1, 2, 3});

            assertEquals(3, pipe.getInput().available());

            pipe.clearBuffer();
            assertEquals(0, pipe.getInput().available());
        }
    }
}