package org.dstadler.audio.util;


import org.apache.commons.rng.UniformRandomProvider;
import org.apache.commons.rng.simple.RandomSource;
import org.dstadler.commons.testing.TestHelpers;
import org.dstadler.commons.testing.ThreadTestHelper;
import org.junit.After;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

@SuppressWarnings("BusyWait")
public class DataPipeTest {
	private static final UniformRandomProvider RNG = RandomSource.XO_RO_SHI_RO_128_PP.create();

	private final AtomicReference<IOException> exc = new AtomicReference<>();
	private final AtomicReference<Long> lastWrite = new AtomicReference<>(System.currentTimeMillis());

	@After
	public void tearDown() throws IOException {
		if (exc.get() != null) {
			throw exc.get();
		}
	}

	@Test
    public void testDataPipeNotStarted() throws IOException, InterruptedException {
        DataPipe pipe = new DataPipe();

        assertFalse(pipe.isRunning());
        assertNull(pipe.getInput());
        assertThrows(NullPointerException.class, () -> pipe.write(new byte[0]));
        assertFalse(pipe.clearBuffer());
        pipe.waitAllConsumed();
        TestHelpers.ToStringTest(pipe);
		assertEquals(0, pipe.available());

        pipe.close();
        TestHelpers.ToStringTest(pipe);
		assertEquals(0, pipe.available());
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
		assertEquals(0, pipe.available());

        pipe.write(new byte[] {0});
		assertEquals(1, pipe.available());

        pipe.close();
		assertEquals(0, pipe.available());

        assertFalse(pipe.isRunning());
        assertNull(pipe.getInput());
        assertThrows(NullPointerException.class, () -> pipe.write(new byte[0]));
        assertFalse(pipe.clearBuffer());
        pipe.waitAllConsumed();
		assertEquals(0, pipe.available());

        TestHelpers.ToStringTest(pipe);
    }

    private static final int NUMBER_OF_THREADS = 20;
    private static final int NUMBER_OF_TESTS = 3000;

	@Test
    public void testMultipleThreads() throws Throwable {
        try (DataPipe pipe = new DataPipe()) {

			pipe.createPipe();

			ThreadTestHelper helper =
					new ThreadTestHelper(NUMBER_OF_THREADS, NUMBER_OF_TESTS);

			helper.executeTest((threadNum, itNum) -> {
				int rnd = RNG.nextInt(0, 7);

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
					case 6:
						pipe.available();
						break;
					// close is not called to always be "running"
					// however createPipe will re-create the pipe frequently
					default:
						fail("Unexpected random: " + rnd);
				}
			});
		}
    }

    @Test
    public void testReadAndWrite() throws IOException, InterruptedException {
        try (DataPipe pipe = new DataPipe()) {
            pipe.createPipe();

			final Thread writer = startWriterThread(pipe);

            Thread reader = new Thread(() -> {
                for (int i = 0; i < 100000; i++) {
                    try {
                        assertEquals("Failed at index " + i,
                                i % 256, pipe.getInput().read());
                    } catch (IOException e) {
                        exc.set(e);
                    }
                }
            }, "reader-thread");
            reader.start();

            writer.join();
            reader.join();

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

	@Test
	public void testCloseBlockedWriting() throws IOException, InterruptedException {
		try (DataPipe pipe = new DataPipe()) {
			pipe.createPipe();

			final Thread writer = startWriterThread(pipe);

			// wait until thread is blocked
			while (lastWrite.get() >= System.currentTimeMillis() - 100) {
				Thread.sleep(100);
			}

			pipe.close();

			writer.join();

			// ignore exceptions, can happen in writer-thread here
			exc.set(null);
		}
	}

	@Test
	public void testClearBlockedWriting() throws IOException, InterruptedException {
		try (DataPipe pipe = new DataPipe()) {
			pipe.createPipe();

			final Thread writer = startWriterThread(pipe);

			// wait until thread is blocked
			while (lastWrite.get() >= System.currentTimeMillis() - 100) {
				Thread.sleep(100);
			}

			pipe.clearBuffer();

			writer.join();
		}
	}

	private Thread startWriterThread(DataPipe pipe) {
		Thread writer = new Thread(() -> {
			for (int i = 0; i < 100000; i++) {
				try {
					pipe.write(new byte[] { (byte) (i % 256) });

					lastWrite.set(System.currentTimeMillis());
				} catch (IOException e) {
					exc.set(e);
					break;
				}
			}
		}, "writer-thread");
		writer.start();
		return writer;
	}
}