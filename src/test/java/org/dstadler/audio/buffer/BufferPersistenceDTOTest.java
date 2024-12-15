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

    @Test
    public void testToString() {
        Stream stream = new Stream();
        BufferPersistenceDTO dto = new BufferPersistenceDTO(3, stream, false, false);
        TestHelpers.ToStringTest(dto);

        dto = new BufferPersistenceDTO(0, 0, null,
                0, 0, 0, stream, false, false);
        TestHelpers.ToStringTest(dto);

        dto = new BufferPersistenceDTO(1, 1, new File("."),
                1, 1, 1, stream, false, false);
        TestHelpers.ToStringTest(dto);

        assertEquals(1, dto.getNumberOfDiskChunks());
        assertEquals(1, dto.getNumberOfDiskFiles());
        assertEquals(new File("."), dto.getDataDir());
    }

	// helper method to get coverage of the unused constructor
	@Test
	public void testPrivateConstructor() throws Exception {
		org.dstadler.commons.testing.PrivateConstructorCoverage.executePrivateConstructor(BufferPersistenceDTO.class);
	}
}
