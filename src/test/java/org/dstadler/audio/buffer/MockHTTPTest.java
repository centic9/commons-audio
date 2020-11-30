package org.dstadler.audio.buffer;

import org.apache.commons.lang3.RandomUtils;
import org.dstadler.commons.http.NanoHTTPD;
import org.dstadler.commons.testing.MockRESTServer;
import org.dstadler.commons.testing.ThreadTestHelper;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

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
                NanoHTTPD.Response response = new NanoHTTPD.Response("200", "application/binary", "");
                response.addHeader("Accept-Ranges", "0-20000");
                response.addHeader("Content-Length", "20000");
                return response;
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

    private static final int NUMBER_OF_THREADS = 10;
    private static final int NUMBER_OF_TESTS = 2000;

    @Test
    public void testMultipleThreads() throws Throwable {
        ThreadTestHelper helper =
                new ThreadTestHelper(NUMBER_OF_THREADS, NUMBER_OF_TESTS);

        try (RangeDownloadingBuffer buffer = new RangeDownloadingBuffer(new File("src/test/resources/test.bin").getAbsolutePath(),
                "", null, 10, 10, p -> null)) {
            helper.executeTest((threadNum, itNum) -> {
                int rnd = RandomUtils.nextInt(0, 14);
                switch (rnd) {
                    case 0:
                        buffer.peek();
                        break;
                    case 1:
                        buffer.seek(RandomUtils.nextInt(0, 100) - 25);
                        break;
                    case 2:
                        buffer.fillupBuffer(-1, 10);
                        break;
                    case 3:
                        buffer.empty();
                        break;
                    case 4:
                        buffer.size();
                        break;
                    case 5:
                        buffer.full();
                        break;
                    case 6:
                        buffer.next();
                        break;
                    case 7:
                        buffer.bufferedBackward();
                        break;
                    case 8:
                        buffer.bufferedForward();
                        break;
                    case 9:
                        buffer.capacity();
                        break;
                    case 10:
                        try {
                            buffer.add(null);
                        } catch (UnsupportedOperationException e) {
                            // expected here
                        }
                        break;
                    case 11:
                        buffer.toPersistence(null, true);
                        break;
                    case 12:
                        // from time to time reset to beginning of the file
                        buffer.seek(-999999);
                        break;
                    case 13:
                        buffer.fill();
                        break;
                }
            });

        }
    }
}
