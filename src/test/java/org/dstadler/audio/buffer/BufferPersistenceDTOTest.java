package org.dstadler.audio.buffer;

import org.dstadler.audio.stream.Stream;
import org.dstadler.commons.testing.TestHelpers;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class BufferPersistenceDTOTest {
    @Test
    public void testLive() {
        Stream stream = new Stream();
        stream.setUrl("url1");
        stream.setStreamType(Stream.StreamType.live);

        BufferPersistenceDTO dto = new BufferPersistenceDTO(new Chunk[] { new Chunk(new byte[] {1}, "meta", 123)},
                0, 1, 2, stream, true, true);
        assertArrayEquals(new Chunk[] { new Chunk(new byte[] {1}, "meta", 123)}, dto.getBuffer());
        assertEquals(0, dto.getNextGet());
        assertEquals(1, dto.getNextAdd());
        assertEquals(2, dto.getFill());
        assertEquals("url1", dto.getStream().getUrl());
        assertEquals(Stream.StreamType.live, dto.getStream().getStreamType());
        assertEquals(0, dto.getNextDownloadPosition());
        assertTrue(dto.isPlaying());
        assertTrue(dto.isDownloadWhilePaused());

        TestHelpers.ToStringTest(dto);
    }

    @Test
    public void testDownload() {
        Stream stream = new Stream();
        stream.setUrl("url1");
        stream.setStreamType(Stream.StreamType.download);

        BufferPersistenceDTO dto = new BufferPersistenceDTO(3, stream, false, false);
        assertNull(dto.getBuffer());
        assertEquals(0, dto.getNextGet());
        assertEquals(0, dto.getNextAdd());
        assertEquals(0, dto.getFill());
        assertEquals("url1", dto.getStream().getUrl());
        assertEquals(Stream.StreamType.download, dto.getStream().getStreamType());
        assertEquals(3, dto.getNextDownloadPosition());
        assertFalse(dto.isPlaying());
        assertFalse(dto.isDownloadWhilePaused());

        TestHelpers.ToStringTest(dto);
    }
}
