package org.dstadler.audio.buffer;

import org.dstadler.audio.stream.Stream;
import org.dstadler.commons.testing.TestHelpers;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

public class BufferPersistenceDTOTest {
    @Test
    public void testLive() {
        Stream stream = new Stream();
        stream.setUrl("url1");
        stream.setStreamType(Stream.StreamType.live);

        BufferPersistenceDTO dto = BufferPersistenceDTO.builder().
                buffer(new Chunk[] { new Chunk(new byte[] {1}, "meta", 123)},
                        0, 1, 2).
                stream(stream, true, true).
                build();
        assertArrayEquals(new Chunk[] { new Chunk(new byte[] {1}, "meta", 123)}, dto.getBuffer());
        assertEquals(0, dto.getNextGet());
        assertEquals(1, dto.getNextAdd());
        assertEquals(2, dto.getFill());
        assertEquals("url1", dto.getStream().getUrl());
        assertEquals(Stream.StreamType.live, dto.getStream().getStreamType());
        assertEquals(0, dto.getNextDownloadPosition());
        assertTrue(dto.isPlaying());
        assertTrue(dto.isDownloadWhilePaused());

        assertEquals(0, dto.getNumberOfDiskChunks());
        assertEquals(0, dto.getNumberOfDiskFiles());
        assertNull(dto.getDataDir());

        TestHelpers.ToStringTest(dto);
    }

    @Test
    public void testDownload() {
        Stream stream = new Stream();
        stream.setUrl("url1");
        stream.setStreamType(Stream.StreamType.download);

        BufferPersistenceDTO dto = BufferPersistenceDTO.builder().
                nextDownloadPosition(3).
                stream(stream, false, false).
                build();
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

    @Test
    public void testToString() {
        Stream stream = new Stream();
        BufferPersistenceDTO dto = BufferPersistenceDTO.builder().
                nextDownloadPosition(3).
                stream(stream, false, false).
                build();
        TestHelpers.ToStringTest(dto);

        dto = BufferPersistenceDTO.builder().build();
        TestHelpers.ToStringTest(dto);

        dto = BufferPersistenceDTO.builder().
                buffer(null, 1, 1, 1).
                stream(stream, false, false).
                data(1, 1, new File(".")).
                build();
        TestHelpers.ToStringTest(dto);

        assertEquals(1, dto.getNumberOfDiskChunks());
        assertEquals(1, dto.getNumberOfDiskFiles());
        assertEquals(new File("."), dto.getDataDir());
    }

    @Test
    public void testChunkCount() {
        BufferPersistenceDTO dto = BufferPersistenceDTO.builder().
                chunkCount(2343).build();

        assertEquals(2343, dto.getChunkCount());
    }

    // helper method to get coverage of the unused constructor
    @Test
    public void testPrivateConstructor() throws Exception {
        org.dstadler.commons.testing.PrivateConstructorCoverage.executePrivateConstructor(BufferPersistenceDTO.class);
    }
}
