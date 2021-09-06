package org.dstadler.audio.util;

import org.apache.commons.lang3.RandomUtils;
import org.dstadler.commons.testing.TestHelpers;
import org.dstadler.commons.testing.ThreadTestHelper;
import org.dstadler.commons.util.ThreadDump;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.PipedOutputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.dstadler.audio.buffer.Chunk.CHUNK_SIZE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

public class ClearablePipedInputStreamTest {
    private final PipedOutputStream out = new PipedOutputStream();
    private ClearablePipedInputStream in;

    @Before
    public void setUp() throws IOException {
         in = new ClearablePipedInputStream(out, 5 * CHUNK_SIZE);
    }

    @Test
    public void testWaitAllConsumedNoData() throws IOException, InterruptedException {
        assertEquals(0, in.available());

        out.write(0);
        assertEquals(1, in.available());

        assertEquals(0, in.read());
        assertEquals(0, in.available());

        // nothing happens as no data is available anyway
        in.waitAllConsumed();

        assertEquals(0, in.available());
    }

    @Test
    public void testWaitAllConsumedWithData() throws IOException, InterruptedException {
        assertEquals(0, in.available());
        out.write(0);
        assertEquals(1, in.available());

        Thread th = new Thread("Consumer") {
            @Override
            public void run() {
                try {
                    Thread.sleep(100);

                    assertEquals(0, in.read());
                } catch (InterruptedException | IOException e) {
                    throw new RuntimeException(e);
                }
            }
        };
        th.start();

        // after the thread consumes the byte, we can continue here
        in.waitAllConsumed();

        th.join();

        assertEquals(0, in.available());
    }

    @Test
    public void testClearNoData() throws IOException {
        out.write(0);
        assertEquals(0, in.read());
        assertEquals(0, in.available());

        // nothing happens as no data is available anyway
        in.clearBuffer();

        assertEquals(0, in.available());
    }

    @Test
    public void testClearWithData() throws IOException {
        out.write(0);
        assertEquals(1, in.available());

        // nothing happens as no data is available anyway
        in.clearBuffer();

        assertEquals(0, in.available());
    }

    @Test
    public void testToString() throws IOException, InterruptedException {
        assertEquals(0, in.available());

        assertNotNull(out.toString());
        assertNotNull(in.toString());

        TestHelpers.ToStringTest(in);
        assertEquals(0, in.available());

        in.clearBuffer();
        TestHelpers.ToStringTest(in);
        assertEquals(0, in.available());

        in.waitAllConsumed();
        TestHelpers.ToStringTest(in);
        assertEquals(0, in.available());

        in.close();
        TestHelpers.ToStringTest(in);
    }

    // can currently only run two because PipedInputStream "binds" the read/write side to threads internally
    private static final int NUMBER_OF_THREADS = 2;
    private static final int NUMBER_OF_TESTS = 10000;

    @Test
    public void testMultipleThreads() throws Throwable {
        ThreadTestHelper helper =
                new ThreadTestHelper(NUMBER_OF_THREADS, NUMBER_OF_TESTS);

        AtomicBoolean stop = new AtomicBoolean();
        AtomicReference<Exception> exc = new AtomicReference<>();
        Thread th = new Thread("Consumer") {
            @Override
            public void run() {
                try {
                    while (!stop.get()) {
                        Thread.sleep(100);

                        // periodically clear or consume all to get stuck writers running again
                        if (RandomUtils.nextBoolean()) {
                            in.clearBuffer();
                        } else {
                            in.waitAllConsumed();
                        }
                    }
                } catch (Exception e) {
                    exc.set(e);
                }
            }
        };
        th.start();

        helper.executeTest((threadNum, itNum) -> {
            // we need to split this by thread
            try {
                switch (threadNum) {
                    case 0:
                        out.write(0);
                        break;
                    case 1:
                        // only read if there is something to read to avoid
                        // blocking reads
                        //noinspection SynchronizeOnNonFinalField
                        synchronized (in) {
                            if (in.available() > 0) {
                                assertEquals(0, in.read());
                            }
                        }
                        break;
                    default:
                        fail("Unexpected thread: " + threadNum);
                }
            } catch (Exception e) {
                ThreadDump threadDump = new ThreadDump(true, true);
                String threadDumpStr = threadDump.toString();

                // writing can fail in "thread 0" if thread "1" finished and stopped first
                if (e instanceof IOException &&
                        e.getMessage().contains("Read end dead") &&
                        Thread.currentThread().getName().startsWith("ThreadTestHelper-Thread 0") &&
                        !threadDumpStr.contains("ThreadTestHelper-Thread 1")) {
                    return;
                }

                System.out.println(threadDumpStr);

                throw new IllegalStateException("Failed for thread " + Thread.currentThread() + ": " + threadNum, e);
            }
        });

        // make sure to clean the buffer here to not have the thread being stuck on waitAllConsumed
        in.clearBuffer();

        stop.set(true);
        th.join();
        assertNull("Expected no exception, but had: " + exc.get(),
                exc.get());
    }
}
