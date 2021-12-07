package org.dstadler.audio.buffer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.dstadler.audio.stream.Stream;
import org.dstadler.commons.testing.ThreadTestHelper;
import org.junit.After;
import org.junit.Test;

public class DiskBasedBlockingSeekableRingBufferTest extends AbstractBlockingSeekableRingBufferTester {
	private static File TEMP_DIR;

	@After
	public void tearDownTempDir() throws IOException {
		FileUtils.deleteDirectory(getTempDir());
	}

	private File getTempDir() throws IOException {
		if (TEMP_DIR == null) {
			TEMP_DIR = File.createTempFile("PiRadioBuffer", ".dir");
			assertTrue(getTempDir().delete());
		}
		return TEMP_DIR;
	}

	@Override
	protected SeekableRingBuffer<Chunk> getBlockingSeekableRingBuffer() {
		try {
			return new DiskBasedBlockingSeekableRingBuffer(10, 3, getTempDir());
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	@Test
	public void testPersistence() throws IOException {
		DiskBasedBlockingSeekableRingBuffer localBuffer = new DiskBasedBlockingSeekableRingBuffer(10, 3, getTempDir());
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
		assertNotNull(dto.getTempDir());

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
		DiskBasedBlockingSeekableRingBuffer localBuffer = new DiskBasedBlockingSeekableRingBuffer(10, 3, getTempDir());

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
	}

	@Test
	public void testAddDiskBuffer() throws IOException {
		String[] list = getTempDir().list();
		assertNotNull(list);
		assertEquals("Should not have disk-files before",
				0, list.length);

		buffer.add(new Chunk(new byte[] { 1 }, "", 0));

		list = getTempDir().list();
		assertNotNull(list);
		assertEquals("Should not have disk-files before",
				0, list.length);

		buffer.add(new Chunk(new byte[] { 2 }, "", 0));
		buffer.add(new Chunk(new byte[] { 3 }, "", 0));
		buffer.add(new Chunk(new byte[] { 4 }, "", 0));

		list = getTempDir().list();
		assertNotNull(list);
		assertEquals("Should not have disk-files before",
				1, list.length);
	}

	@Override
	protected ThreadTestHelper createThreadTestHelper() {
		return new ThreadTestHelper(20, 500);
	}
}
