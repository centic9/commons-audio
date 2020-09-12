package org.dstadler.audio.fm4;

import org.junit.Assume;
import org.junit.Test;

import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class FM4CacheTest {
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
        }

        // cache itself is not static, so size should be zero again now
        try (FM4Cache cache = new FM4Cache(new FM4())) {
            assertEquals(0, cache.size());
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

            if(stream1.getStart() > stream2.getStart()) {
                FM4Stream next = cache.getNextByStreamURL(stream2.getStreams().get(0));
                assertNotNull(next);
                assertEquals(stream1.getProgramKey(), next.getProgramKey());
            } else {
                FM4Stream next = cache.getNextByStreamURL(stream1.getStreams().get(0));
                assertNotNull(next);
                assertEquals(stream2.getProgramKey(), next.getProgramKey());
            }
        }
    }
}
