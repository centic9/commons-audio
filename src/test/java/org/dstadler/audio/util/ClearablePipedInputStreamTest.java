package org.dstadler.audio.util;

import org.dstadler.commons.testing.TestHelpers;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.PipedOutputStream;

import static org.dstadler.audio.buffer.Chunk.CHUNK_SIZE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ClearablePipedInputStreamTest {
    private final PipedOutputStream out = new PipedOutputStream();
    private ClearablePipedInputStream in;

    @Before
    public void setUp() throws IOException {
         in = new ClearablePipedInputStream(out, 5 * CHUNK_SIZE);
    }

    @Test
    public void testWaitAllConsumedNoData() throws IOException, InterruptedException {
        out.write(0);
        assertEquals(0, in.read());
        assertEquals(0, in.available());

        // nothing happens as no data is available anyway
        in.waitAllConsumed();

        assertEquals(0, in.available());
    }

    @Test
    public void testWaitAllConsumedWithData() throws IOException, InterruptedException {
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
        assertNotNull(out.toString());
        assertNotNull(in.toString());

        TestHelpers.ToStringTest(in);

        in.clearBuffer();
        TestHelpers.ToStringTest(in);

        in.waitAllConsumed();
        TestHelpers.ToStringTest(in);

        in.close();
        TestHelpers.ToStringTest(in);
    }
}