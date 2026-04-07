package org.dstadler.audio.buffer;

import org.dstadler.commons.testing.TestHelpers;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ChunkTest {
    @Test
    public void test() {
        // just a simple test to cover the class
        Chunk chunk = new Chunk(new byte[] { 1, 2, 3}, "some data", 12345L);
        assertArrayEquals(new byte[] { 1, 2, 3}, chunk.getData());
        assertEquals("some data", chunk.getMetaData());
        assertEquals(12345L, chunk.getTimestamp());
        assertEquals(3, chunk.size());

        Chunk equ = new Chunk(new byte[] { 1, 2, 3}, "some data", 12345L);
        Chunk notEqu = new Chunk(new byte[] { 1, 2, 4}, "some data", 12345L);
        TestHelpers.EqualsTest(chunk, equ, notEqu);

        TestHelpers.ToStringTest(chunk);
        TestHelpers.ToStringTest(equ);
        TestHelpers.ToStringTest(notEqu);
        TestHelpers.HashCodeTest(chunk, equ);

        notEqu = new Chunk(new byte[0], "some data", 12345L);
        assertEquals(0, notEqu.size());
        TestHelpers.EqualsTest(chunk, equ, notEqu);
        TestHelpers.ToStringTest(notEqu);
    }

    @Test
    public void testEmpty() {
        Chunk chunk = new Chunk(new byte[0], "", 0);
        Chunk equ = new Chunk(new byte[0], "", 0);
        Chunk notEqu = new Chunk(new byte[] {0}, "", 0);

        TestHelpers.EqualsTest(chunk, equ, notEqu);
        TestHelpers.ToStringTest(chunk);
        TestHelpers.ToStringTest(notEqu);
        TestHelpers.HashCodeTest(chunk, equ);
    }

    @Test
    public void testNullData() {
        assertThrows(NullPointerException.class, () -> new Chunk(null, "meta", 0));
    }

    @Test
    public void testNullMetaData() {
        assertThrows(NullPointerException.class, () -> new Chunk(new byte[0], null, 0));
    }

    @Test
    public void testChunkSizeConstant() {
        assertEquals(16384, Chunk.CHUNK_SIZE);
    }

    @Test
    public void testEqualsWithDifferentMetaDataSameData() {
        Chunk chunk1 = new Chunk(new byte[] {1, 2, 3}, "meta1", 100L);
        Chunk chunk2 = new Chunk(new byte[] {1, 2, 3}, "meta2", 200L);
        // equals is based on data only
        assertEquals(chunk1, chunk2);
        assertEquals(chunk1.hashCode(), chunk2.hashCode());
    }

    @Test
    public void testLargeChunk() {
        byte[] data = new byte[Chunk.CHUNK_SIZE];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (i % 256);
        }
        Chunk chunk = new Chunk(data, "large", System.currentTimeMillis());
        assertEquals(Chunk.CHUNK_SIZE, chunk.size());
        assertArrayEquals(data, chunk.getData());
    }
}