package org.dstadler.audio.buffer;

import org.dstadler.commons.testing.TestHelpers;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

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
}