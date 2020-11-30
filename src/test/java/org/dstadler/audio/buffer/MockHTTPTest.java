package org.dstadler.audio.buffer;

import org.dstadler.commons.http.NanoHTTPD;
import org.dstadler.commons.testing.MockRESTServer;
import org.junit.Test;

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
}
