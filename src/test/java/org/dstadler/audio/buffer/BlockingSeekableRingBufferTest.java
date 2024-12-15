package org.dstadler.audio.buffer;

import org.dstadler.audio.stream.Stream;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class BlockingSeekableRingBufferTest extends AbstractBlockingSeekableRingBufferTester {
    @Override
    protected SeekableRingBuffer<Chunk> getBlockingSeekableRingBuffer() {
        return new BlockingSeekableRingBuffer(10);
    }

    @Test
    public void testPersistence() throws IOException {
        try (BlockingSeekableRingBuffer localBuffer = new BlockingSeekableRingBuffer(10)) {
            for (byte i = 0; i < 15; i++) {
                localBuffer.add(new Chunk(new byte[]{i}, "", 0));
            }

            Stream stream = new Stream();
            stream.setUrl("url1");
            stream.setStreamType(Stream.StreamType.live);

            // get the persistence
            final BufferPersistenceDTO dto = localBuffer.toPersistence(stream, false, false);
            assertNotNull(dto);
            assertEquals("url1", dto.getStream().getUrl());

            // check what next returns
            final Chunk next = localBuffer.next();
            assertEquals(new Chunk(new byte[]{6}, "", 0), next);

            // check the local buffer
            assertFalse(localBuffer.full());
            assertFalse(localBuffer.empty());
            assertEquals(8, localBuffer.size());
            assertEquals(9, localBuffer.fill());

            // check the DTO
            assertEquals(6, dto.getNextGet());
            assertEquals(5, dto.getNextAdd());
            assertEquals(9, dto.getFill());
            assertEquals(10, dto.getBuffer().length);
            assertEquals(0, dto.getNumberOfDiskFiles());
            assertEquals(0, dto.getNumberOfDiskChunks());
            assertNull(dto.getDataDir());

            // then convert the DTO back into a buffer and do a next() as well
            try (BlockingSeekableRingBuffer back = BlockingSeekableRingBuffer.fromPersistence(dto)) {
                assertEquals(next, back.next());
            }

            // and finally ensure the state is the same
            assertFalse(localBuffer.full());
            assertFalse(localBuffer.empty());
            assertEquals(8, localBuffer.size());
            assertEquals(9, localBuffer.fill());
        }
    }

	@Test
    public void testToStringBuffer() {
        try (BlockingSeekableRingBuffer localBuffer = new BlockingSeekableRingBuffer(10)) {
            assertTrue(localBuffer.empty());
            assertFalse(localBuffer.full());
            assertTrue(localBuffer.toString().contains("empty=true"),
                    "Had: " + localBuffer);
            assertTrue(localBuffer.toString().contains("full=false"),
                    "Had: " + localBuffer);

            for (byte i = 0; i < 15; i++) {
                localBuffer.add(new Chunk(new byte[]{i}, "", 0));
            }

            assertFalse(localBuffer.empty());
            assertTrue(localBuffer.full());
            assertTrue(localBuffer.toString().contains("empty=false"),
                    "Had: " + localBuffer);
            assertTrue(localBuffer.toString().contains("full=true"),
                    "Had: " + localBuffer);
        }
    }

    @Test
    public void testFailedToRead() {
        BufferPersistenceDTO dto = new BufferPersistenceDTO(
                0, null, false, false);
        //noinspection resource
        assertThrows(IOException.class,
                () -> BlockingSeekableRingBuffer.fromPersistence(dto));
    }
}
