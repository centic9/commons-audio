package org.dstadler.audio.buffer;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.dstadler.audio.stream.Stream;
import org.dstadler.commons.http.NanoHTTPD;
import org.dstadler.commons.testing.MemoryLeakVerifier;
import org.dstadler.commons.testing.MockRESTServer;
import org.dstadler.commons.testing.TestHelpers;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.dstadler.audio.buffer.Chunk.CHUNK_SIZE;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

@RunWith(Parameterized.class)
public class RangeDownloadingBufferTest {
    // just a sample file which should usually be available
    private static final String SAMPLE_URL = "https://file-examples-com.github.io/uploads/2017/10/file_example_JPG_500kB.jpg";

    private static final String SAMPLE_FILE = new File("src/test/resources/test.bin").getAbsolutePath();
    private static final String SAMPLE_FILE_URL = "file://" + new File("src/test/resources/test.bin").getAbsolutePath();

    private static final String SAMPLE_FILE2 = new File("src/test/resources/test2.bin").getAbsolutePath();
    private static final String SAMPLE_FILE2_URL = "file://" + new File("src/test/resources/test2.bin").getAbsolutePath();

    private final MemoryLeakVerifier verifier = new MemoryLeakVerifier();

    private RangeDownloadingBuffer buffer;

    @Parameterized.Parameters(name = "Sample: {0}, Chunks: {1}, Size: {2}/{3}, Meta: {4}")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                { SAMPLE_URL, 34, 555180, 555148, Pair.of("", 100L) },

                { SAMPLE_FILE, 36, 587241, 587207, Pair.of("", 100L) },
                { SAMPLE_FILE_URL, 36, 587241, 587207, Pair.of("", 100L) },

                { SAMPLE_FILE2, 30, 485375, 485347, Pair.of("", 100L) },
                { SAMPLE_FILE2_URL, 30, 485375, 485347, Pair.of("", 100L) },

                // null metadata
                { SAMPLE_FILE, 36, 587241, 587207, null },

                // some other metadata
                { SAMPLE_FILE, 36, 587241, 587207, Pair.of("some meta data", 76L) },
        });
    }

    @Parameterized.Parameter
    public String sample;
    @Parameterized.Parameter(1)
    public int expectedChunks;
    @Parameterized.Parameter(2)
    public int fileSize;
    @Parameterized.Parameter(3)
    public int fileSize2;
    @Parameterized.Parameter(4)
    public Pair<String, Long> metaData;

    @Before
    public void setUp() {
        try {
            buffer = new RangeDownloadingBuffer(sample, "", null, 10, CHUNK_SIZE, p -> metaData);
            verifier.addObject(buffer);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @After
    public void tearDown() {
        if(buffer != null) {
            TestHelpers.ToStringTest(buffer);

            buffer.close();

            // set to null to allow to check for memory leaks below
            buffer = null;
        }

        verifier.assertGarbageCollected();
    }

    @Test
    public void testBuffered() {
        assertEquals(expectedChunks, buffer.fill());
        assertEquals(expectedChunks, buffer.size());
        assertEquals(expectedChunks, buffer.capacity());
        assertTrue(buffer.full());

        assertEquals(0, buffer.bufferedForward());
        assertEquals(0, buffer.bufferedBackward());

        assertNotNull(buffer.peek());
        assertEquals(10, buffer.bufferedForward());
        assertEquals(0, buffer.bufferedBackward());

        assertNotNull(buffer.next());
        assertEquals(9, buffer.bufferedForward());
        assertEquals(1, buffer.bufferedBackward());

        assertNotNull(buffer.next());
        assertEquals(8, buffer.bufferedForward());
        assertEquals(2, buffer.bufferedBackward());

        assertNotNull(buffer.next());
        assertNotNull(buffer.next());
        assertNotNull(buffer.next());
        assertNotNull(buffer.next());
        assertNotNull(buffer.next());
        assertNotNull(buffer.next());
        assertNotNull(buffer.next());
        assertEquals(1, buffer.bufferedForward());
        assertEquals(9, buffer.bufferedBackward());

        assertNotNull(buffer.next());
        assertEquals(0, buffer.bufferedForward());
        assertEquals(10, buffer.bufferedBackward());

        assertNotNull(buffer.next());
        assertEquals("Having: " + buffer, 9, buffer.bufferedForward());
        assertEquals("internal buffer only stores 19 elements, thus one is already removed by adding 10 more",
                10, buffer.bufferedBackward());
    }

    @Test
    public void testInitialSize() {
        TestHelpers.ToStringTest(buffer);

        assertFalse(buffer.empty());
        assertTrue(buffer.full());
        assertEquals(expectedChunks, buffer.size());
        assertEquals(expectedChunks, buffer.capacity());
        assertEquals(expectedChunks, buffer.fill());
    }

    @Test
    public void testFillupBuffer() throws Exception {
        assertEquals(10, buffer.fillupBuffer(-1, -1));

        assertFalse(buffer.empty());
        assertTrue(buffer.full());
        assertEquals(expectedChunks, buffer.size());
        assertEquals(expectedChunks, buffer.capacity());
        assertEquals(expectedChunks, buffer.fill());

        assertEquals(0, buffer.fillupBuffer(-1, -1));

        assertFalse(buffer.empty());
        assertTrue(buffer.full());
        assertEquals(expectedChunks, buffer.size());
        assertEquals(expectedChunks, buffer.capacity());
        assertEquals(expectedChunks, buffer.fill());
    }

    @Test
    public void testFillupBufferMin() throws Exception {
        assertEquals(0, buffer.fillupBuffer(1000, 10));

        assertFalse(buffer.empty());
        assertTrue(buffer.full());
        assertEquals(expectedChunks, buffer.size());
        assertEquals(expectedChunks, buffer.capacity());
        assertEquals(expectedChunks, buffer.fill());

        assertEquals(8, buffer.fillupBuffer(2, 8));

        assertFalse(buffer.empty());
        assertTrue(buffer.full());
        assertEquals(expectedChunks, buffer.size());
        assertEquals(expectedChunks, buffer.capacity());
        assertEquals(expectedChunks, buffer.fill());
    }

    @Test
    public void testFillupBufferWithBufferData() throws Exception {
        for(int i = 0;i < 20;i++) {
            assertNotNull("Having: " + buffer, buffer.next());
        }

        assertEquals(fileSize/CHUNK_SIZE + 1 - 20, buffer.size());

        buffer.seek(-19);

        assertEquals(fileSize/CHUNK_SIZE, buffer.size());

        assertEquals(0, buffer.fillupBuffer(-1, -1));

        assertFalse(buffer.empty());
        assertTrue(buffer.full());
        assertEquals(expectedChunks-1, buffer.size());
        assertEquals(expectedChunks, buffer.capacity());
        assertEquals(expectedChunks, buffer.fill());

        buffer.seek(19);

        assertEquals(fileSize/CHUNK_SIZE + 1 - 20, buffer.size());

        assertEquals(10, buffer.fillupBuffer(-1, -1));
    }

    @Test
    public void testPeek() {
        Chunk peek = buffer.peek();
        assertNotNull(peek);
        assertEquals(CHUNK_SIZE, peek.getData().length);

        Chunk chunk = buffer.next();
        assertNotNull(chunk);
        assertEquals(CHUNK_SIZE, chunk.getData().length);
        assertEquals(metaData == null ? "" : metaData.getLeft(), chunk.getMetaData());
        assertEquals(metaData == null ? 0L : metaData.getRight(), chunk.getTimestamp());

        assertArrayEquals(peek.getData(), chunk.getData());
    }

    @Test
    public void testPeekIOException() throws IOException {
        final AtomicInteger calls = new AtomicInteger(0);
        try (MockRESTServer server = new MockRESTServer(() -> {
            if (calls.incrementAndGet() == 1) {
                // mock initial response with Accept-Ranges and Content-Length
                NanoHTTPD.Response response = new NanoHTTPD.Response("200", "text/html", "");
                response.addHeader("Accept-Ranges", "0-999");
                response.addHeader("Content-Length", "999");
                return response;
            }

            return new NanoHTTPD.Response("404", "text/html", StringUtils.repeat(" ", 999));
        })) {
            buffer.close();
            buffer = new RangeDownloadingBuffer("http://localhost:" + server.getPort(),
                    "testuser", "testpass", 10, CHUNK_SIZE, percentage -> Pair.of("", 0L));

            buffer.RETRY_SLEEP_TIME = 1;

            assertNull("Should return null because fetching data fails internall", buffer.peek());

            assertThrows(IllegalStateException.class,
                    () -> buffer.next());
        }
    }

    @Test
    public void testReadAtEnd() {
        assertEquals(expectedChunks, buffer.seek(999999));

        assertNull(buffer.peek());
        assertNull(buffer.peek());

        assertNull(buffer.next());
        assertNull(buffer.next());

        assertTrue(buffer.empty());
    }

    @Test
    public void testEmpty() throws IOException {
        // go nearly to the end of the buffer
        assertEquals(expectedChunks - 2, buffer.seek(buffer.size() - 2));

        // make sure we have the data in the buffer
        assertEquals(2, buffer.fillupBuffer(-1, 9999999));

        // make sure we can read more data
        assertNotNull(buffer.peek());

        // we should not report empty() now
        assertFalse(buffer.empty());
    }

    @Test
    public void testReadChunkAndSeek() {
        Chunk chunk = buffer.next();
        assertNotNull(chunk);
        assertEquals(CHUNK_SIZE, chunk.getData().length);

        assertEquals(expectedChunks-1, buffer.size());
        assertEquals(expectedChunks, buffer.capacity());
        assertEquals(expectedChunks, buffer.fill());

        assertEquals(expectedChunks-2, buffer.seek(expectedChunks-2));

        assertEquals(1, buffer.size());
        assertEquals(expectedChunks, buffer.capacity());
        assertEquals(expectedChunks, buffer.fill());
    }

    @Test
    public void testSeekOutside() {
        assertEquals(expectedChunks, buffer.size());
        assertEquals(expectedChunks, buffer.capacity());
        assertEquals(expectedChunks, buffer.fill());

        assertEquals(expectedChunks, buffer.seek(5000));
    }

    @Test
    public void testSeekBackwards() throws IOException {
        // initialize with a chunk-size of 1 to have exact chunk-counts in the tests below
        buffer = new RangeDownloadingBuffer(sample, "", null, 10, 1, percentage -> Pair.of("", 0L));

        Chunk chunk = buffer.next();
        assertNotNull(chunk);
        assertEquals(1, chunk.getData().length);

        assertEquals(fileSize, buffer.size());
        assertEquals(fileSize+1, buffer.capacity());
        assertEquals(fileSize+1, buffer.fill());

        assertEquals(expectedChunks-2, buffer.seek(expectedChunks-2));

        assertEquals(fileSize2, buffer.size());
        assertEquals(fileSize+1, buffer.capacity());
        assertEquals(fileSize+1, buffer.fill());

        assertEquals(-1*(expectedChunks-2), buffer.seek(-1*(expectedChunks-2)));

        assertEquals(fileSize, buffer.size());
        assertEquals(fileSize+1, buffer.capacity());
        assertEquals(fileSize+1, buffer.fill());

        assertEquals(-1, buffer.seek(-expectedChunks-2));

        assertEquals(fileSize+1, buffer.size());
        assertEquals(fileSize+1, buffer.capacity());
        assertEquals(fileSize+1, buffer.fill());

        assertEquals(0, buffer.seek(0));

        assertEquals(fileSize+1, buffer.size());
        assertEquals(fileSize+1, buffer.capacity());
        assertEquals(fileSize+1, buffer.fill());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void addFails() {
        buffer.add(null);
    }

    @Test
    public void testPersistence() throws IOException {
        buffer.next();

        assertFalse("Had: " + buffer, buffer.empty());
        assertTrue("Had: " + buffer, buffer.full());
        assertEquals(expectedChunks-1, buffer.size());
        assertEquals(expectedChunks, buffer.capacity());
        assertEquals(expectedChunks, buffer.fill());

        Stream stream = new Stream() {
            // use the same meta-data as the buffer
            @Override
            public Function<Double, Pair<String, Long>> getMetaDataFun() {
                return p -> metaData;
            }
        };
        stream.setUrl(sample);
        stream.setStreamType(Stream.StreamType.live);
        stream.setStartTimestamp(100L);

        // get the persistence
        final BufferPersistenceDTO dto = buffer.toPersistence(stream, false, false);
        assertNotNull(dto);
        assertEquals(sample, dto.getStream().getUrl());

        // then convert the DTO back into a buffer and then compare
        RangeDownloadingBuffer back = RangeDownloadingBuffer.fromPersistence(dto, 10, CHUNK_SIZE);
        assertEquals(toStringReplace(buffer), toStringReplace(back));

        // and finally ensure the state is the same
        assertFalse(back.empty());
        assertTrue(back.full());
        assertEquals(expectedChunks-1, back.size());
        assertEquals(expectedChunks, back.capacity());
        assertEquals(expectedChunks, back.fill());
    }

    private String toStringReplace(RangeDownloadingBuffer buffer) {
        return buffer.toString()
                .replaceAll("nextGet=\\d+", "")
                .replaceAll("nextAdd=\\d+", "")
                .replaceAll("nextDownloadPos=\\d+", "")
                .replaceAll("percentage=[\\d.,]+", "")
                .replaceAll("(?:empty|full)=(?:false|true)", "")
                .replaceAll("size=\\d+", "");
    }

    @Test
    public void testWithUser() throws IOException {
        buffer = new RangeDownloadingBuffer(sample, "testuser", "testpass", 10, CHUNK_SIZE, percentage -> Pair.of("", 0L));
    }

    @Test(expected = IOException.class)
    public void testWithUserAndAuthResponse() throws IOException {
        try (MockRESTServer server = new MockRESTServer("404", "text/html", "")) {
            buffer.close();

            new RangeDownloadingBuffer("http://localhost:" + server.getPort(),
                    "testuser", "testpass", 10, CHUNK_SIZE, percentage -> Pair.of("", 0L));
        }
    }

    @Test
    public void testToString() {
        TestHelpers.ToStringTest(buffer);
        assertNotNull(buffer.next());
        TestHelpers.ToStringTest(buffer);

        assertTrue("Had: " + buffer,
                buffer.toString().contains("empty=" + (buffer.empty() ? "true" : "false")));
        assertTrue("Had: " + buffer,
                buffer.toString().contains("full=" + (buffer.full() ? "true" : "false")));
    }

    @Ignore("Just used for testing download speed")
    @Test
    public void testSlowStartBenchmark() throws IOException, InterruptedException {
        long start = System.currentTimeMillis();
        System.out.println("Starting download: " + (System.currentTimeMillis() - start));
        try (RangeDownloadingBuffer buffer = new RangeDownloadingBuffer("https://loopstream01.apa.at/?channel=fm4&id=2020-03-22_0959_tl_54_7DaysSun6_95352.mp3",
                "", null, 500, CHUNK_SIZE, percentage -> Pair.of("", 0L))) {
            System.out.println("After startup: " + (System.currentTimeMillis() - start));

            for (int i = 0; i < 20; i++) {
                buffer.next();

                System.out.println("After next: " + (System.currentTimeMillis() - start));

                Thread.sleep(1000);
            }

            buffer.fillupBuffer(15, expectedChunks - 2);
        }

        System.out.println("After fillup: " + (System.currentTimeMillis() - start));
    }

    @Test
    public void testSeekNotEmpty() throws IOException {
        assertFalse("Not empty at the beginning", buffer.empty());

        assertEquals("Load 10 chunks", 10, buffer.fillupBuffer(0, 10));
        assertFalse("Not empty after initial fill-up", buffer.empty());

        assertEquals("Able to seek 20 chunks", 20, buffer.seek(20));
        assertFalse("Not empty after seeking", buffer.empty());

        int seeked = buffer.seek(300);
        assertTrue("Had: " + seeked, seeked >= 10);
        assertTrue("Should be at the end now", buffer.empty());
    }
}
