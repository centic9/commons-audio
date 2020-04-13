package org.dstadler.audio.fm4;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class FM4CacheTest {

    @Test
    public void testCache() {
        try (FM4Cache cache = new FM4Cache()) {
            assertEquals(0, cache.size());

            cache.refresh();
            assertTrue(cache.size() > 0);

            assertNotNull(cache.get("4UL"));
            assertNull(cache.get("NOT EXISTING"));

            assertNotNull(cache.allStreams());
            assertTrue(cache.allStreams().size() > 0);
        }

        // cache itself is not static, so size should be zero again now
        try (FM4Cache cache = new FM4Cache()) {
            assertEquals(0, cache.size());
        }
    }
}
