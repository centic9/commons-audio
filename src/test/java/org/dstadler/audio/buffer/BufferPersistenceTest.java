package org.dstadler.audio.buffer;

import org.dstadler.audio.stream.Stream;
import org.dstadler.commons.logging.jdk.LoggerFactory;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;

import static org.dstadler.audio.buffer.Chunk.CHUNK_SIZE;
import static org.junit.jupiter.api.Assertions.*;

public class BufferPersistenceTest {
    private final static Logger log = LoggerFactory.make();

    @Test
    public void testWriteReadEmpty() throws IOException {
        assertFalse(BufferPersistence.hasBufferOnDisk(null));
        assertFalse(BufferPersistence.hasBufferOnDisk(new File("not existing")));

        File file = File.createTempFile("BufferPersistence", ".bson");
        assertTrue(file.delete());
        try {
            assertFalse(BufferPersistence.hasBufferOnDisk(file));

            Stream stream = new Stream();
            stream.setUrl("url1");
            stream.setStreamType(Stream.StreamType.live);

            final BlockingSeekableRingBuffer buffer = new BlockingSeekableRingBuffer(10);
            BufferPersistence.writeBufferToDisk(file, buffer.toPersistence(stream, false, false));

            assertTrue(BufferPersistence.hasBufferOnDisk(file));

            final BufferPersistenceDTO dto = BufferPersistence.readBufferFromDisk(file);
            assertEquals("url1", dto.getStream().getUrl());
            assertEquals(Stream.StreamType.live, dto.getStream().getStreamType());
			assertFalse(dto.isPlaying());
			assertFalse(dto.isDownloadWhilePaused());

            BlockingSeekableRingBuffer  back = BlockingSeekableRingBuffer.fromPersistence(dto);
            assertTrue(BufferPersistence.hasBufferOnDisk(file));

            assertEquals(0, back.fill());
            compare(buffer, back);

            assertTrue(file.exists());
            assertTrue(file.delete());
        } finally {
            // make sure to clean up even on exception
            assertTrue(!file.exists() || file.delete());
        }
    }

    @Test
    public void testWriteReadWithData() throws IOException {
        assertFalse(BufferPersistence.hasBufferOnDisk(null));
        assertFalse(BufferPersistence.hasBufferOnDisk(new File("not existing")));

        File file = File.createTempFile("BufferPersistence", ".bson");
        assertTrue(file.delete());
        try {
            assertFalse(BufferPersistence.hasBufferOnDisk(file));

            BlockingSeekableRingBuffer buffer = new BlockingSeekableRingBuffer(10);
            buffer.add(new Chunk(new byte[] {1,2,3,4,5}, "meta data", 12345L));
            assertEquals(1, buffer.fill());

            Stream stream = new Stream();
            stream.setUrl("url1");
            stream.setStreamType(Stream.StreamType.download);

            BufferPersistence.writeBufferToDisk(file, buffer.toPersistence(stream, true, true));

            assertTrue(BufferPersistence.hasBufferOnDisk(file));

            final BufferPersistenceDTO dto = BufferPersistence.readBufferFromDisk(file);
            assertEquals("url1", dto.getStream().getUrl());
            assertEquals(Stream.StreamType.download, dto.getStream().getStreamType());
            assertTrue(dto.isPlaying());
			assertTrue(dto.isDownloadWhilePaused());

            BlockingSeekableRingBuffer back = BlockingSeekableRingBuffer.fromPersistence(dto);
            assertTrue(BufferPersistence.hasBufferOnDisk(file));

            assertEquals(1, back.fill());
            assertEquals(1, back.size());
            compare(buffer, back);

            assertTrue(file.exists());
            assertTrue(file.delete());
        } finally {
            // make sure to clean up even on exception
            assertTrue(!file.exists() || file.delete());
        }
    }

    private void compare(SeekableRingBuffer<Chunk> buffer, SeekableRingBuffer<Chunk> back) {
        assertEquals(buffer.fill(), back.fill());
        assertEquals(buffer.size(), back.size());
        assertEquals(buffer.capacity(), back.capacity());
        //assertEquals(buffer.getChunksWrittenPerSecond(), back.getChunksWrittenPerSecond(), 0.01);
        assertEquals(buffer.empty(), back.empty());
        assertEquals(buffer.peek(), back.peek());
        assertEquals(buffer.full(), back.full());
    }

    @Test
    public void testMicroBenchmark() throws IOException {
        assertFalse(BufferPersistence.hasBufferOnDisk(null));
        assertFalse(BufferPersistence.hasBufferOnDisk(new File("not existing")));

        File file = File.createTempFile("BufferPersistence", ".bson");
        assertTrue(file.delete());
        try {
            try (BlockingSeekableRingBuffer buffer = new BlockingSeekableRingBuffer(5000)) {

                log.info("Creating data structure");
                for (int i = 0; i < 5000; i++) {
                    byte[] data = new byte[CHUNK_SIZE];
                    for (int d = 0; d < CHUNK_SIZE; d++) {
                        data[d] = (byte) d;
                    }
                    buffer.add(new Chunk(data, "meta data", System.currentTimeMillis()));
                }
                assertEquals(4999, buffer.fill());

                for (int i = 0; i < 5; i++) {
                    log.info("Start run " + i);

                    Stream stream = new Stream();
                    stream.setUrl("url1");
                    stream.setStreamType(Stream.StreamType.live);

                    long start = System.currentTimeMillis();
                    BufferPersistence.writeBufferToDisk(file, buffer.toPersistence(stream, false, false));
                    log.info("Writing for run " + i + " took " + (System.currentTimeMillis() - start) + "ms");

                    assertTrue(BufferPersistence.hasBufferOnDisk(file));

                    start = System.currentTimeMillis();
                    final BufferPersistenceDTO dto = BufferPersistence.readBufferFromDisk(file);
                    assertEquals("url1", dto.getStream().getUrl());
                    assertEquals(Stream.StreamType.live, dto.getStream().getStreamType());
                    assertFalse(dto.isPlaying());
                    assertFalse(dto.isDownloadWhilePaused());

                    try (BlockingSeekableRingBuffer back = BlockingSeekableRingBuffer.fromPersistence(dto)) {
                        log.info("Reading for run " + i + " took " + (System.currentTimeMillis() - start) + "ms");

                        assertTrue(BufferPersistence.hasBufferOnDisk(file));
                        assertEquals(4999, back.fill());
                        assertEquals(4999, back.size());
                    }
                }

                assertTrue(file.exists());
                assertTrue(file.delete());
            }
        } finally {
            // make sure to clean up even on exception
            assertTrue(!file.exists() || file.delete());
        }
    }

    @Test
    public void testInvalidStartPosition() throws IOException {
        File tempPersist = File.createTempFile("RangeDownloadingBuffer", ".bin");
        try {
            // first get a "next download position" > 0 persisted
            String url = new File("src/test/resources/test.bin").getAbsolutePath();
            try (RangeDownloadingBuffer buffer = new RangeDownloadingBuffer(url,
                    "", null, 100, Chunk.CHUNK_SIZE, null)) {

                buffer.next();
                assertEquals(1, buffer.bufferedBackward());

                Stream stream = new Stream();
                stream.setUrl(url);
                BufferPersistenceDTO dto = buffer.toPersistence(stream, false, false);
                assertNotNull(dto);
                BufferPersistence.writeBufferToDisk(tempPersist, dto);
            }

            // restore from previous persisted data
            BufferPersistenceDTO dtoBack = BufferPersistence.readBufferFromDisk(tempPersist);

            try (RangeDownloadingBuffer buffer = RangeDownloadingBuffer.fromPersistence(dtoBack, 100, Chunk.CHUNK_SIZE)) {
                assertEquals(0, buffer.bufferedBackward(), "Zero expected here because we restored the buffer");

                buffer.next();

                Stream stream = new Stream();
                BufferPersistenceDTO dto = buffer.toPersistence(stream, false, false);
                assertNotNull(dto);
            }
        } finally {
            assertTrue(!tempPersist.exists() || tempPersist.delete());
        }
    }

    // helper method to get coverage of the unused constructor
    @Test
    public void testPrivateConstructor() throws Exception {
        org.dstadler.commons.testing.PrivateConstructorCoverage.executePrivateConstructor(BufferPersistence.class);
    }
}
