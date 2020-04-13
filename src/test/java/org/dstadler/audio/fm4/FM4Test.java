package org.dstadler.audio.fm4;

import org.junit.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class FM4Test {
    private FM4 fm4 = new FM4();

    @Test
    public void testFetch() throws IOException {
        List<FM4Stream> fm4Streams = fm4.fetchStreams();

        assertNotNull(fm4Streams);
        assertFalse(fm4Streams.isEmpty());
    }

    @Test
    public void testFilter() throws IOException {
        List<FM4Stream> fm4Streams = fm4.filterStreams("4MO");

        assertNotNull(fm4Streams);
        assertFalse(fm4Streams.isEmpty());
    }

    @Test
    public void testFilterEmpty() throws IOException {
        List<FM4Stream> fm4Streams = fm4.filterStreams("NOT_FOUND");

        assertNotNull(fm4Streams);
        assertTrue(fm4Streams.isEmpty());
    }
}
