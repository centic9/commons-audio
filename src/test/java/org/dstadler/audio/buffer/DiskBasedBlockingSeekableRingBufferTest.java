package org.dstadler.audio.buffer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.RandomUtils;
import org.dstadler.audio.stream.Stream;
import org.dstadler.commons.testing.ThreadTestHelper;
import org.junit.After;
import org.junit.Test;

public class DiskBasedBlockingSeekableRingBufferTest extends AbstractBlockingSeekableRingBufferTester {
	private static File DATA_DIR;

	@After
	public void tearDownDataDir() throws IOException {
		FileUtils.deleteDirectory(getDataDir());
	}

	private File getDataDir() throws IOException {
		if (DATA_DIR == null) {
			DATA_DIR = File.createTempFile("PiRadioBuffer", ".dir");
			assertTrue(getDataDir().delete());
		}
		return DATA_DIR;
	}

	@Override
	protected SeekableRingBuffer<Chunk> getBlockingSeekableRingBuffer() {
		try {
			return new DiskBasedBlockingSeekableRingBuffer(10, 3, getDataDir());
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	@Test
	public void testPersistence() throws IOException {
		DiskBasedBlockingSeekableRingBuffer localBuffer = new DiskBasedBlockingSeekableRingBuffer(10, 3, getDataDir());
		for(byte i = 0;i < 15;i++) {
			localBuffer.add(new Chunk(new byte[] { i }, "", 0));
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
		assertEquals(new Chunk(new byte[] {6}, "", 0), next);

		// check the local buffer
		assertFalse(localBuffer.full());
		assertFalse(localBuffer.empty());
		assertEquals(8, localBuffer.size());
		assertEquals(9, localBuffer.fill());

		// check the DTO
		assertEquals(6, dto.getNextGet());
		assertEquals(5, dto.getNextAdd());
		assertEquals(9, dto.getFill());
		assertNull(dto.getBuffer());
		assertEquals(3, dto.getNumberOfDiskFiles());
		assertEquals(10, dto.getNumberOfDiskChunks());
		assertNotNull(dto.getDataDir());

		// then convert the DTO back into a buffer and do a next() as well
		DiskBasedBlockingSeekableRingBuffer back = DiskBasedBlockingSeekableRingBuffer.fromPersistence(dto);
		assertEquals(next, back.next());

		// and finally ensure the state is the same
		assertFalse(localBuffer.full());
		assertFalse(localBuffer.empty());
		assertEquals(8, localBuffer.size());
		assertEquals(9, localBuffer.fill());
	}

	@Test
	public void testToStringBuffer() throws IOException {
		DiskBasedBlockingSeekableRingBuffer localBuffer = new DiskBasedBlockingSeekableRingBuffer(10, 3, getDataDir());

		assertTrue(localBuffer.empty());
		assertFalse(localBuffer.full());
		assertTrue("Had: " + localBuffer,
				localBuffer.toString().contains("empty=true"));
		assertTrue("Had: " + localBuffer,
				localBuffer.toString().contains("full=false"));

		for(byte i = 0;i < 15;i++) {
			localBuffer.add(new Chunk(new byte[] { i }, "", 0));
		}

		assertFalse(localBuffer.empty());
		assertTrue(localBuffer.full());
		assertTrue("Had: " + localBuffer,
				localBuffer.toString().contains("empty=false"));
		assertTrue("Had: " + localBuffer,
				localBuffer.toString().contains("full=true"));
		assertTrue("Had: " + localBuffer,
				localBuffer.toString().contains("isDirty="));
		assertTrue("Had: " + localBuffer,
				localBuffer.toString().contains("diskBufferRead="));
		assertTrue("Had: " + localBuffer,
				localBuffer.toString().contains("diskBufferWrite="));
		assertTrue("Had: " + localBuffer,
				localBuffer.toString().contains("numberOfDiskChunks="));
		assertTrue("Had: " + localBuffer,
				localBuffer.toString().contains("diskBufferReadPosition="));
		assertTrue("Had: " + localBuffer,
				localBuffer.toString().contains("diskBufferWritePosition="));
		assertTrue("Had: " + localBuffer,
				localBuffer.toString().contains("dataDir="));
	}

	@Test
	public void testAddDiskBuffer() throws IOException {
		String[] list = getDataDir().list();
		assertNotNull(list);
		assertEquals("Should not have disk-files before",
				0, list.length);

		buffer.add(new Chunk(new byte[] { 1 }, "", 0));

		list = getDataDir().list();
		assertNotNull(list);
		assertEquals("Should not have disk-files until first flush",
				0, list.length);

		buffer.add(new Chunk(new byte[] { 2 }, "", 0));
		buffer.add(new Chunk(new byte[] { 3 }, "", 0));

		list = getDataDir().list();
		assertNotNull(list);
		assertEquals("Should have disk files now",
				1, list.length);
		assertTrue(list[0].startsWith(DiskBasedBlockingSeekableRingBuffer.FILE_PREFIX));

		buffer.add(new Chunk(new byte[] { 4 }, "", 0));
		buffer.add(new Chunk(new byte[] { 5 }, "", 0));
		buffer.add(new Chunk(new byte[] { 6 }, "", 0));

		list = getDataDir().list();
		assertNotNull(list);
		assertEquals("Should have disk files now",
				2, list.length);
		assertTrue(list[1].startsWith(DiskBasedBlockingSeekableRingBuffer.FILE_PREFIX));

		buffer.add(new Chunk(new byte[] { 7 }, "", 0));
		buffer.add(new Chunk(new byte[] { 8 }, "", 0));
		buffer.add(new Chunk(new byte[] { 9 }, "", 0));

		list = getDataDir().list();
		assertNotNull(list);
		assertEquals("Should have disk files now",
				3, list.length);
		assertTrue(list[2].startsWith(DiskBasedBlockingSeekableRingBuffer.FILE_PREFIX));

		buffer.add(new Chunk(new byte[] { 10 }, "", 0));

		list = getDataDir().list();
		assertNotNull(list);
		assertEquals("Should have disk files now",
				4, list.length);
		assertTrue(list[3].startsWith(DiskBasedBlockingSeekableRingBuffer.FILE_PREFIX));

		buffer.add(new Chunk(new byte[] { 11 }, "", 0));
		buffer.add(new Chunk(new byte[] { 12 }, "", 0));
		buffer.add(new Chunk(new byte[] { 13 }, "", 0));

		list = getDataDir().list();
		assertNotNull(list);
		assertEquals("Should have disk files now",
				4, list.length);

		buffer.add(new Chunk(new byte[] { 14 }, "", 0));
		buffer.add(new Chunk(new byte[] { 15 }, "", 0));
		buffer.add(new Chunk(new byte[] { 16 }, "", 0));

		list = getDataDir().list();
		assertNotNull(list);
		assertEquals("Should not end up with more disk files than 4",
				4, list.length);
	}

	@Override
	protected ThreadTestHelper createThreadTestHelper() {
		return new ThreadTestHelper(20, 500);
	}

	@Test
	public void testLargeBuffer() throws IOException {
		DiskBasedBlockingSeekableRingBuffer localBuffer =
				new DiskBasedBlockingSeekableRingBuffer(1000, 33, getDataDir());

		for (int i = 0;i < 5000;i++) {
			localBuffer.add(new Chunk(RandomUtils.nextBytes(1), "", 0));

			if (RandomUtils.nextBoolean()) {
				assertNotNull(localBuffer.peek());
				assertNotNull(localBuffer.next());
			}

			if (RandomUtils.nextBoolean()) {
				int seek = localBuffer.seek(RandomUtils.nextInt(0, 5000)-2500);
				assertTrue("Had: " + seek + " with " + buffer,
						seek > -1000 && seek < 1000);
			}
		}
	}

	@Test
	public void testInvalidDataDir() {
		assertThrows(IllegalStateException.class, () ->
			new DiskBasedBlockingSeekableRingBuffer(1000, 33,
				new File(getDataDir(), "/_/ß\u0000")));
	}
}
