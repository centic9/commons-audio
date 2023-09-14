package org.dstadler.audio.buffer;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.RandomUtils;
import org.dstadler.commons.http.NanoHTTPD;
import org.dstadler.commons.testing.MockRESTServer;
import org.dstadler.commons.testing.ThreadTestHelper;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class MockHTTPTest {
    @Test
    public void testDownloadFails() throws IOException {
        AtomicInteger httpCalls = new AtomicInteger();
        AtomicBoolean fail = new AtomicBoolean();
        try (MockRESTServer server = new MockRESTServer(() -> {
            httpCalls.incrementAndGet();

            if(fail.get()) {
                return new NanoHTTPD.Response("404", "application/binary", "");
            } else {
                return acceptRangeResponse();
            }
        })) {
            try (RangeDownloadingBuffer buffer = new RangeDownloadingBuffer("http://localhost:" + server.getPort(),
                    "", null, 100, Chunk.CHUNK_SIZE, null)) {
                // use a very short retry-sleep to speed up this test
                buffer.RETRY_SLEEP_TIME = 1;

                // make the HTTP server return a failure
                fail.set(true);

                // expect an IOException because reading fails
                assertThrows(IOException.class, () -> buffer.fillupBuffer(-1, 50));
            }
        }

        assertEquals("Expecting one call initially and 10 retries",
                10 + 1, httpCalls.get());
    }

    @Test
    public void testDownloadInvalidContentType() throws IOException {
        AtomicInteger httpCalls = new AtomicInteger();
        AtomicBoolean secondCall = new AtomicBoolean();
        try (MockRESTServer server = new MockRESTServer(() -> {
            httpCalls.incrementAndGet();

            if(secondCall.get()) {
                // FM4 servers return HTTP 200 and a HTML-page if the show was removed after 7 days...
                return new NanoHTTPD.Response("200", "text/html; charset=utf-8", "abcdefghijklmnopqrstuvw");
            } else {
                return acceptRangeResponse();
            }
        })) {
            try (RangeDownloadingBuffer buffer = new RangeDownloadingBuffer("http://localhost:" + server.getPort(),
                    "", null, 100, Chunk.CHUNK_SIZE, null)) {
                // use a very short retry-sleep to speed up this test
                buffer.RETRY_SLEEP_TIME = 1;

                // make the HTTP server return a failure
                secondCall.set(true);

                // expect no data available any more for "text/html" content-type
                assertEquals(0, buffer.fillupBuffer(-1, 50));
            }
        }

        assertEquals("Expecting one call initially and one call to fetch data",
                1 + 1, httpCalls.get());
    }

    @Test
    public void testDownloadSucceeds() throws IOException {
        AtomicInteger httpCalls = new AtomicInteger();
        AtomicBoolean secondCall = new AtomicBoolean();
        try (MockRESTServer server = new MockRESTServer(() -> {
            httpCalls.incrementAndGet();

            if(secondCall.get()) {
                return new NanoHTTPD.Response("200", "audio/mpeg", RandomStringUtils.random(100));
            } else {
                return acceptRangeResponse();
            }
        })) {
            try (RangeDownloadingBuffer buffer = new RangeDownloadingBuffer("http://localhost:" + server.getPort(),
                    "", null, 100, Chunk.CHUNK_SIZE, null)) {
                // use a very short retry-sleep to speed up this test
                buffer.RETRY_SLEEP_TIME = 1;

                // make the HTTP server return a failure
                secondCall.set(true);

                // expect some chunks back
                assertEquals(2, buffer.fillupBuffer(-1, 50));
            }
        }

        assertEquals("Expecting one call initially and one call to fetch data",
                1 + 1, httpCalls.get());
    }

    @Ignore("URL is only temporary")
    @Test
    public void testFM4Stream() throws IOException {
        try (RangeDownloadingBuffer buffer = new RangeDownloadingBuffer("https://loopstream01.apa.at/?channel=fm4&id=2020-11-30_1459_tl_54_7DaysMon12_109215.mp3",
                "", null, 1000, Chunk.CHUNK_SIZE, p -> null)) {
            buffer.peek();

            buffer.fillupBuffer(-1, 50);

            buffer.seek(5016);
        }
    }

    private static NanoHTTPD.Response acceptRangeResponse() {
        NanoHTTPD.Response response = new NanoHTTPD.Response("200", "application/binary", "");
        response.addHeader("Accept-Ranges", "0-20000");
        response.addHeader("Content-Length", "20000");
        return response;
    }

    private static final int NUMBER_OF_THREADS = 10;
    private static final int NUMBER_OF_TESTS = 40000;

    private static final int CHUNK_SIZE = 9;
    private static final int BUFFERED_CHUNKS = 10;

    @Test
    public void testMultipleThreads() throws Throwable {
        // avoid lengthy log-output in this test
        Logger warnLogger = Logger.getLogger(RangeDownloadingBuffer.class.getName());
        Level prevLevel = warnLogger.getLevel();
        warnLogger.setLevel(Level.WARNING);

        try {
            ThreadTestHelper helper =
                    new ThreadTestHelper(NUMBER_OF_THREADS, NUMBER_OF_TESTS);

            try (RangeDownloadingBuffer buffer = new RangeDownloadingBuffer(new File("src/test/resources/test.bin").getAbsolutePath(),
                    "", null, BUFFERED_CHUNKS, CHUNK_SIZE, p -> null)) {
                helper.executeTest(new TestRunnable(buffer));
            }
        } finally {
            warnLogger.setLevel(prevLevel);
        }
    }

    private static class TestRunnable implements ThreadTestHelper.TestRunnable {
        private final RangeDownloadingBuffer buffer;

        public TestRunnable(RangeDownloadingBuffer buffer) {
            this.buffer = buffer;
        }

        @SuppressWarnings("deprecation")
        @Override
        public void run(int threadNum, int itNum) throws Exception {
            int rnd = RandomUtils.nextInt(0, 17);
            switch (rnd) {
                case 0:
                    buffer.peek();
                    break;
                case 1: {
                    int nrOfChunks = RandomUtils.nextInt(0, 100) - 25;
                    int seeked = buffer.seek(nrOfChunks);
                    if (nrOfChunks >= 0) {
                        assertTrue(seeked <= nrOfChunks);
                    } else {
                        assertTrue(seeked >= nrOfChunks);
                    }
                    break;
                }
                case 2:
                    int fetched = buffer.fillupBuffer(-1, 10);
                    assertTrue(fetched >= 0);
                    assertTrue(fetched <= 10);
                    break;
                case 3:
                    buffer.empty();
                    break;
                case 4: {
                    final int size;
                    final int capacity;
                    synchronized (buffer) {
                        size = buffer.size();
                        capacity = buffer.capacity();
                    }
                    assertTrue(size >= 0);
                    assertTrue("size: " + size + ", capacity: " + capacity + ", buffer: " + buffer,
                            // size can be a bit more as we compute it based on internal byte-position
                            size <= (capacity + BUFFERED_CHUNKS));
                    break;
                }
                case 5:
                    buffer.full();
                    break;
                case 6:
                    // next can hang in some cases where only close() would leave the wait-loop...
                    //buffer.next();
                    break;
                case 7: {
                    int available = buffer.bufferedBackward();
                    assertTrue("Had: " + available, available >= 0);
                    assertTrue(available <= buffer.capacity());
                    break;
                }
                case 8: {
                    int available = buffer.bufferedForward();
                    assertTrue("Had: " + available, available >= 0);
                    assertTrue(available <= buffer.capacity());
                    break;
                }
                case 9: {
                    int capacity = buffer.capacity();
                    assertTrue(capacity >= 0);
                    assertTrue(capacity >= (buffer.size() - BUFFERED_CHUNKS));   // size can be a bit higher
                    assertTrue(capacity >= buffer.fill());
                    break;
                }
                case 10:
                    try {
                        buffer.add(null);
                    } catch (UnsupportedOperationException e) {
                        // expected here
                    }
                    break;
                case 11:
                    BufferPersistenceDTO dto = buffer.toPersistence(null, true, true);
                    assertTrue(dto.getNextDownloadPosition() >= 0);
                    break;
                case 12:
                    // from time to time reset to beginning of the file
                    int seeked = buffer.seek(-999999);
                    assertTrue(seeked <= 0);
                    assertTrue(seeked * -1 <= buffer.capacity());
                    break;
                case 13: {
                    int fill = buffer.fill();
                    assertTrue(fill >= 0);
                    assertTrue(fill <= buffer.capacity());
                    break;
                }
                case 14: {
                    // another test of size without synchronized block
                    int size = buffer.size();
                    assertTrue("Having size: " + size, size >= 0);
                    break;
                }
                case 15:
                    // another test of capacity without synchronized block
                    int capacity = buffer.capacity();
                    assertTrue("Having capacity: " + capacity, capacity >= 0);
                    break;
                case 16: {
                    final int fill;
                    final int size;
                    synchronized (buffer) {
                        fill = buffer.fill();
                        size = buffer.size();
                    }
                    assertTrue(fill >= 0);
                    assertTrue(fill <= buffer.capacity());
                    assertTrue(fill >= size);
                    break;
                }
            }
        }
    }

    @Test
    public void testSeekWithFilledBuffer() throws IOException {
        try (RangeDownloadingBuffer buffer = new RangeDownloadingBuffer(new File("src/test/resources/test.bin").getAbsolutePath(),
                "", null, 10000, 1024, p -> null)) {
            assertEquals(574, buffer.fillupBuffer(0, 99999));
            assertFalse(buffer.empty());
            //System.out.println("Buffer: " + buffer);

            assertEquals(500, buffer.seek(500));
            assertFalse(buffer.empty());
            //System.out.println("Buffer: " + buffer);
        }
    }


    @Test
    public void testInterruptSleepInFillup() throws InterruptedException, IOException {
        AtomicInteger httpCalls = new AtomicInteger();
        AtomicBoolean fail = new AtomicBoolean();
        try (MockRESTServer server = new MockRESTServer(() -> {
            httpCalls.incrementAndGet();

            if(fail.get()) {
                return new NanoHTTPD.Response("404", "application/binary", "");
            } else {
                return acceptRangeResponse();
            }
        })) {
            try (RangeDownloadingBuffer buffer = new RangeDownloadingBuffer("http://localhost:" + server.getPort(),
                    "", null, 100, Chunk.CHUNK_SIZE, null)) {
                // make the HTTP server return a failure
                fail.set(true);

                CountDownLatch preStartLatch = new CountDownLatch(1);
                CountDownLatch startLatch = new CountDownLatch(1);
                AtomicReference<Exception> exc = new AtomicReference<>();

                Thread thread = new Thread("fillup") {
                    @Override
                    public void run() {
                        preStartLatch.countDown();

                        try {
                            startLatch.await();
                        } catch (InterruptedException e) {
                            exc.set(e);
                        }

                        try {
                            // this is expected to fail and start a re-try and sleep
                            assertEquals(0, buffer.fillupBuffer(-1, 100));
                        } catch (IOException | RuntimeException e) {
                            exc.set(e);
                        }
                    }
                };

                thread.start();

                // make sure the thread is read to do it's work
                preStartLatch.await();
                startLatch.countDown();

                // wait a bit to make sure we are in the Sleep
                Thread.sleep(500);

                // interrupt thread now
                thread.interrupt();

                // no other exception is expected before joining the thread
                if (exc.get() != null) {
                    throw new IllegalStateException("Unexpected exception in thread", exc.get());
                }

                // now the thread should stop quickly and should propagate the interrupted state
                thread.join();

                // no other exception is expected after joining the thread
                if (exc.get() != null) {
                    throw new IllegalStateException("Unexpected exception in thread", exc.get());
                }
            }
        }

        assertEquals("Expecting one call initially and first try",
                1 + 1, httpCalls.get());
    }
}
