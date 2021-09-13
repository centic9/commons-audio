package org.dstadler.audio.fm4;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.dstadler.commons.testing.MemoryLeakVerifier;
import org.dstadler.commons.testing.ThreadTestHelper;
import org.dstadler.commons.util.ExecutorUtil;
import org.junit.After;
import org.junit.Assume;
import org.junit.Test;

import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import static org.dstadler.audio.fm4.FM4Stream.FM4_STREAM_URL_BASE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class FM4CacheTest {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final MemoryLeakVerifier verifier = new MemoryLeakVerifier();

    @After
    public void tearDown() throws InterruptedException {
        // ensure no threads are left
        ThreadTestHelper.waitForThreadToFinishSubstring("FM4", 1_000);
        ThreadTestHelper.assertNoThreadLeft("Should not have a thread with 'FM4', see the thread-dump in the logs",
                "FM4");

        verifier.assertGarbageCollected();
    }

    @Test
    public void testCache() {
        try (FM4Cache cache = new FM4Cache(new FM4())) {
            assertEquals(0, cache.size());

            cache.refresh();
            assertTrue("Cache should not be empty now, check stdout for error messages while fetching the data",
                    cache.size() > 0);

            assertNotNull(cache.get("4UL"));
            assertNull(cache.get("NOT EXISTING"));

            assertNotNull(cache.allStreams());
            assertTrue(cache.allStreams().size() > 0);

            verifier.addObject(cache);
        }

        // cache itself is not static, so size should be zero again now
        try (FM4Cache cache = new FM4Cache(new FM4())) {
            assertEquals(0, cache.size());

            verifier.addObject(cache);
        }
    }

    @Test
    public void testGetNextNotFound() {
        try (FM4Cache cache = new FM4Cache(new FM4() {
            @Override
            public List<FM4Stream> fetchStreams() throws IOException {
                // only load two shows to make testing quick
                List<FM4Stream> fm4Streams = super.fetchStreams();
                return fm4Streams.subList(0, Math.min(2, fm4Streams.size()));
            }
        })) {
            cache.refresh();

            assertNull(cache.getNext(null));

            ObjectNode node = objectMapper.createObjectNode();
            node.put("programKey", "ABC");
            node.put("title", "");
            node.put("subtitle", "");
            node.put("href", "");
            node.put("startISO", "");
            node.put("start", Long.MAX_VALUE);
            node.put("end", 1L);

            assertNull(cache.getNext(new FM4Stream(node, FM4_STREAM_URL_BASE)));

            verifier.addObject(cache);
        }
    }

    @Test
    public void testGetNextFound() {
        try (FM4Cache cache = new FM4Cache(new FM4() {
            @Override
            public List<FM4Stream> fetchStreams() throws IOException {
                // only load two shows to make testing quick
                List<FM4Stream> fm4Streams = super.fetchStreams();
                return fm4Streams.subList(0, Math.min(2, fm4Streams.size()));
            }
        })) {
            cache.refresh();

            Collection<FM4Stream> fm4Streams = cache.allStreams();

            Assume.assumeTrue("Expecting some streams, but had: " + cache.allStreams(),
                    cache.allStreams().size() >= 2);

            Iterator<FM4Stream> it = fm4Streams.iterator();

            FM4Stream stream1 = it.next();
            FM4Stream stream2 = it.next();
            assertNotEquals(stream1.getStart(), stream2.getStart());

            if(stream1.getStart() > stream2.getStart()) {
                FM4Stream next = cache.getNext(stream2);
                assertNotNull(next);
                assertEquals(stream1.getProgramKey(), next.getProgramKey());

                assertNull("Stream 1 should be the last one, had: " + fm4Streams,
                        cache.getNext(stream1));
            } else {
                FM4Stream next = cache.getNext(stream1);
                assertNotNull(next);
                assertEquals(stream2.getProgramKey(), next.getProgramKey());

                assertNull("Stream 2 should be the last one, had: " + fm4Streams,
                        cache.getNext(stream2));
            }

            verifier.addObject(cache);
        }
    }

    @Test
    public void testGetPreviousNotFound() {
        try (FM4Cache cache = new FM4Cache(new FM4() {
            @Override
            public List<FM4Stream> fetchStreams() throws IOException {
                // only load two shows to make testing quick
                List<FM4Stream> fm4Streams = super.fetchStreams();
                return fm4Streams.subList(0, Math.min(2, fm4Streams.size()));
            }
        })) {
            cache.refresh();

            assertNull(cache.getNext(null));

            ObjectNode node = objectMapper.createObjectNode();
            node.put("programKey", "ABC");
            node.put("title", "");
            node.put("subtitle", "");
            node.put("href", "");
            node.put("startISO", "");
            node.put("start", Long.MAX_VALUE);
            node.put("end", 1L);

            assertNull(cache.getPrevious(new FM4Stream(node, FM4_STREAM_URL_BASE)));

            verifier.addObject(cache);
        }
    }

    @Test
    public void testGetPreviousFound() {
        try (FM4Cache cache = new FM4Cache(new FM4() {
            @Override
            public List<FM4Stream> fetchStreams() throws IOException {
                // only load two shows to make testing quick
                List<FM4Stream> fm4Streams = super.fetchStreams();
                return fm4Streams.subList(0, Math.min(2, fm4Streams.size()));
            }
        })) {
            cache.refresh();

            Collection<FM4Stream> fm4Streams = cache.allStreams();

            Assume.assumeTrue("Expecting some streams, but had: " + cache.allStreams(),
                    cache.allStreams().size() >= 2);

            Iterator<FM4Stream> it = fm4Streams.iterator();

            FM4Stream stream1 = it.next();
            FM4Stream stream2 = it.next();
            assertNotEquals(stream1.getStart(), stream2.getStart());

            if(stream1.getStart() < stream2.getStart()) {
                FM4Stream previous = cache.getPrevious(stream2);
                assertNotNull(previous);
                assertEquals(stream1.getProgramKey(), previous.getProgramKey());

                assertNull("Stream 1 should be the first one, had: " + fm4Streams,
                        cache.getPrevious(stream1));
            } else {
                FM4Stream previous = cache.getPrevious(stream1);
                assertNotNull(previous);
                assertEquals(stream2.getProgramKey(), previous.getProgramKey());

                assertNull("Stream 2 should be the first one, had: " + fm4Streams,
                        cache.getPrevious(stream2));
            }

            verifier.addObject(cache);
        }
    }

    @Test
    public void testGetNextByStreamURLNotFound() throws IOException {
        try (FM4Cache cache = new FM4Cache(new FM4() {
            @Override
            public List<FM4Stream> fetchStreams() throws IOException {
                // only load two shows to make testing quick
                List<FM4Stream> fm4Streams = super.fetchStreams();
                return fm4Streams.subList(0, Math.min(2, fm4Streams.size()));
            }
        })) {
            cache.refresh();

            assertNull(cache.getNextByStreamURL(null));
            assertNull(cache.getNextByStreamURL(""));
            assertNull(cache.getNextByStreamURL("blabla"));

            verifier.addObject(cache);
        }
    }

    @Test
    public void testGetNextByStreamURLFound() throws IOException {
        try (FM4Cache cache = new FM4Cache(new FM4() {
            @Override
            public List<FM4Stream> fetchStreams() throws IOException {
                // only load two shows to make testing quick
                List<FM4Stream> fm4Streams = super.fetchStreams();
                return fm4Streams.subList(0, Math.min(2, fm4Streams.size()));
            }
        })) {
            cache.refresh();

            Collection<FM4Stream> fm4Streams = cache.allStreams();

            Assume.assumeTrue("Expecting some streams, but had: " + cache.allStreams(),
                    cache.allStreams().size() >= 2);

            Iterator<FM4Stream> it = fm4Streams.iterator();

            FM4Stream stream1 = it.next();
            FM4Stream stream2 = it.next();
            assertNotEquals(stream1.getStart(), stream2.getStart());

            String url1 = stream1.getStreams().get(0);
            String url2 = stream2.getStreams().get(0);
            if(stream1.getStart() > stream2.getStart()) {
                FM4Stream next = cache.getNextByStreamURL(url2);
                assertNotNull(next);
                assertEquals(stream1.getProgramKey(), next.getProgramKey());

                assertNull("Stream 1 should be the last one, had: " + fm4Streams,
                        cache.getNextByStreamURL(url1));
            } else {
                FM4Stream next = cache.getNextByStreamURL(url1);
                assertNotNull(next);
                assertEquals(stream2.getProgramKey(), next.getProgramKey());

                assertNull("Stream 2 should be the last one, had: " + fm4Streams,
                        cache.getNextByStreamURL(url2));
            }

            verifier.addObject(cache);
        }
    }

    @Test
    public void testIOException() {
        try (FM4Cache cache = new FM4Cache(new FM4() {
            @Override
            public List<FM4Stream> fetchStreams() throws IOException {
                throw new IOException("Test-exception");
            }
        })) {
            verifier.addObject(cache);

            cache.refresh();
        }
    }

    @Test
    public void testThread() throws InterruptedException {
        try (FM4Cache cache = new FM4Cache(new FM4())) {
            // wait for the thread be started
            for (int i = 0; i < 10; i++) {
                if (ExecutorUtil.lookupThread("FM4Cache") != null) {
                    break;
                }
                Thread.sleep(10);
            }

            // we should have the thread started now
            assertNotNull("Need a thread names 'FM4Cache' to be running now",
                    ExecutorUtil.lookupThread("FM4Cache"));

            assertNotNull(cache);

            verifier.addObject(cache);
        }
    }
}
