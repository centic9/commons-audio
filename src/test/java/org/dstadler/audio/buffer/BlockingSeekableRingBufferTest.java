package org.dstadler.audio.buffer;

import org.dstadler.audio.stream.Stream;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

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
            assertTrue("Had: " + localBuffer,
                    localBuffer.toString().contains("empty=true"));
            assertTrue("Had: " + localBuffer,
                    localBuffer.toString().contains("full=false"));

            for (byte i = 0; i < 15; i++) {
                localBuffer.add(new Chunk(new byte[]{i}, "", 0));
            }

            assertFalse(localBuffer.empty());
            assertTrue(localBuffer.full());
            assertTrue("Had: " + localBuffer,
                    localBuffer.toString().contains("empty=false"));
            assertTrue("Had: " + localBuffer,
                    localBuffer.toString().contains("full=true"));
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
