package org.dstadler.audio.buffer;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.dstadler.audio.stream.Stream;
import org.dstadler.commons.logging.jdk.LoggerFactory;
import org.dstadler.commons.testing.ThreadTestHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class DiskBasedBlockingSeekableRingBufferTest extends AbstractBlockingSeekableRingBufferTester {
	private static File DATA_DIR;

	@BeforeAll
	public static void beforeClass() throws IOException {
		LoggerFactory.initLogging();
	}

	@AfterEach
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
		try (DiskBasedBlockingSeekableRingBuffer localBuffer = new DiskBasedBlockingSeekableRingBuffer(10, 3, getDataDir())) {
			for (byte i = 0; i < 15; i++) {
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
			assertEquals(new Chunk(new byte[] { 6 }, "", 0), next);

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
			try (DiskBasedBlockingSeekableRingBuffer back = DiskBasedBlockingSeekableRingBuffer.fromPersistence(dto)) {
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
	public void testToStringBuffer() throws IOException {
		try (DiskBasedBlockingSeekableRingBuffer localBuffer = new DiskBasedBlockingSeekableRingBuffer(10, 3, getDataDir())) {

			assertTrue(localBuffer.empty());
			assertFalse(localBuffer.full());
			assertTrue(localBuffer.toString().contains("empty=true"),
					"Had: " + localBuffer);
			assertTrue(localBuffer.toString().contains("full=false"),
					"Had: " + localBuffer);

			for (byte i = 0; i < 15; i++) {
				localBuffer.add(new Chunk(new byte[] { i }, "", 0));
			}

			assertFalse(localBuffer.empty());
			assertTrue(localBuffer.full());
			assertTrue(localBuffer.toString().contains("empty=false"),
					"Had: " + localBuffer);
			assertTrue(localBuffer.toString().contains("full=true"),
					"Had: " + localBuffer);
			assertTrue(localBuffer.toString().contains("isDirty="),
					"Had: " + localBuffer);
			assertTrue(localBuffer.toString().contains("diskBufferRead="),
					"Had: " + localBuffer);
			assertTrue(localBuffer.toString().contains("diskBufferWrite="),
					"Had: " + localBuffer);
			assertTrue(localBuffer.toString().contains("numberOfDiskChunks="),
					"Had: " + localBuffer);
			assertTrue(localBuffer.toString().contains("diskBufferReadPosition="),
					"Had: " + localBuffer);
			assertTrue(localBuffer.toString().contains("diskBufferWritePosition="),
					"Had: " + localBuffer);
			assertTrue(localBuffer.toString().contains("dataDir="),
					"Had: " + localBuffer);
		}
	}

	@Test
	public void testAddDiskBuffer() throws IOException {
		String[] list = getDataDir().list();
		assertNotNull(list);
		assertEquals(0, list.length, "Should not have disk-files before");

		buffer.add(new Chunk(new byte[] { 1 }, "", 0));

		list = getDataDir().list();
		assertNotNull(list);
		assertEquals(0, list.length, "Should not have disk-files until first flush");

		buffer.add(new Chunk(new byte[] { 2 }, "", 0));
		buffer.add(new Chunk(new byte[] { 3 }, "", 0));

		list = getDataDir().list();
		assertNotNull(list);
		assertEquals(1, list.length, "Should have disk files now");
		assertTrue(list[0].startsWith(DiskBasedBlockingSeekableRingBuffer.FILE_PREFIX));

		buffer.add(new Chunk(new byte[] { 4 }, "", 0));
		buffer.add(new Chunk(new byte[] { 5 }, "", 0));
		buffer.add(new Chunk(new byte[] { 6 }, "", 0));

		list = getDataDir().list();
		assertNotNull(list);
		assertEquals(2, list.length, "Should have disk files now");
		assertTrue(list[1].startsWith(DiskBasedBlockingSeekableRingBuffer.FILE_PREFIX));

		buffer.add(new Chunk(new byte[] { 7 }, "", 0));
		buffer.add(new Chunk(new byte[] { 8 }, "", 0));
		buffer.add(new Chunk(new byte[] { 9 }, "", 0));

		list = getDataDir().list();
		assertNotNull(list);
		assertEquals(3, list.length, "Should have disk files now");
		assertTrue(list[2].startsWith(DiskBasedBlockingSeekableRingBuffer.FILE_PREFIX));

		buffer.add(new Chunk(new byte[] { 10 }, "", 0));

		list = getDataDir().list();
		assertNotNull(list);
		assertEquals(4, list.length, "Should have disk files now");
		assertTrue(list[3].startsWith(DiskBasedBlockingSeekableRingBuffer.FILE_PREFIX));

		buffer.add(new Chunk(new byte[] { 11 }, "", 0));
		buffer.add(new Chunk(new byte[] { 12 }, "", 0));
		buffer.add(new Chunk(new byte[] { 13 }, "", 0));

		list = getDataDir().list();
		assertNotNull(list);
		assertEquals(4, list.length, "Should have disk files now");

		buffer.add(new Chunk(new byte[] { 14 }, "", 0));
		buffer.add(new Chunk(new byte[] { 15 }, "", 0));
		buffer.add(new Chunk(new byte[] { 16 }, "", 0));

		list = getDataDir().list();
		assertNotNull(list);
		assertEquals(4, list.length, "Should not end up with more disk files than 4");
	}

	@Override
	protected ThreadTestHelper createThreadTestHelper() {
		return new ThreadTestHelper(20, 500);
	}

	@Test
	public void testLargeBuffer() throws IOException {
		try (DiskBasedBlockingSeekableRingBuffer localBuffer = new DiskBasedBlockingSeekableRingBuffer(1000, 33, getDataDir())) {

			for (int i = 0; i < 5000; i++) {
				localBuffer.add(new Chunk(RandomUtils.insecure().randomBytes(1), "", 0));

				if (RandomUtils.insecure().randomBoolean()) {
					assertNotNull(localBuffer.peek());
					assertNotNull(localBuffer.next());
				}

				if (RandomUtils.insecure().randomBoolean()) {
					int seek = localBuffer.seek(RandomUtils.insecure().randomInt(0, 5000) - 2500);
					assertTrue(seek > -1000 && seek < 1000,
							"Had: " + seek + " with " + buffer);
				}
			}
		}
	}

	@Test
	public void testInvalidDataDir() {
		//noinspection resource
		assertThrows(IllegalStateException.class, () ->
			new DiskBasedBlockingSeekableRingBuffer(1000, 33,
				new File(getDataDir(), "/_/ß\u0000")));
	}

	@Test
	public void testInvalidConstructorValuesNegative() throws IOException {
		Stream stream = new Stream();
		stream.setUrl("url1");
		stream.setStreamType(Stream.StreamType.live);

		final BufferPersistenceDTO dto = new BufferPersistenceDTO(100, 20,
				new File(getDataDir(), "test.bson"),
				-1, -1, 0, stream, false, false);

		//noinspection resource
		assertThrows(IllegalArgumentException.class, () ->
			DiskBasedBlockingSeekableRingBuffer.fromPersistence(dto));
	}

	@Test
	public void testInvalidConstructorValuesInvalidDataDir() {
		Stream stream = new Stream();
		stream.setUrl("url1");
		stream.setStreamType(Stream.StreamType.live);

		final BufferPersistenceDTO dto = new BufferPersistenceDTO(100, 20,
				null, 2, 1, 0, stream, false, false);

		//noinspection resource
		assertThrows(IOException.class, () ->
			DiskBasedBlockingSeekableRingBuffer.fromPersistence(dto));
	}

	@Test
	public void testInvalidConstructorValuesOutOfRange() throws IOException {
		Stream stream = new Stream();
		stream.setUrl("url1");
		stream.setStreamType(Stream.StreamType.live);

		final BufferPersistenceDTO dto = new BufferPersistenceDTO(100, 20,
				new File(getDataDir(), "test.bson"),
				2, 100, 0, stream, false, false);

		//noinspection resource
		assertThrows(IllegalArgumentException.class, () ->
			DiskBasedBlockingSeekableRingBuffer.fromPersistence(dto));
	}

	@Test
	public void testConstructFromPersistence() throws IOException {
		Stream stream = new Stream();
		stream.setUrl("url1");
		stream.setStreamType(Stream.StreamType.live);

		final BufferPersistenceDTO dto = new BufferPersistenceDTO(100, 20,
				new File(getDataDir(), "test.bson"),
				2, 99, 0, stream, false, false);

		try (DiskBasedBlockingSeekableRingBuffer localBuffer = DiskBasedBlockingSeekableRingBuffer.fromPersistence(dto)) {
			assertNotNull(localBuffer.peek());
		}
	}

	@Test
	public void testConcurrentAddRemove() {
		for (int i = 0; i < 100; i++) {
			buffer.add(new Chunk(new byte[0], "", 1));
			assertNotNull(buffer.peek());
			assertNotNull(buffer.next());
		}
	}

	@Test
	public void testFailingWrite() throws IOException {
		Stream stream = new Stream();
		stream.setUrl("url1");
		stream.setStreamType(Stream.StreamType.live);

		File dir = new File(getDataDir(), "test.bson");
		final BufferPersistenceDTO dto = new BufferPersistenceDTO(100, 20,
				dir, 99, 0, 0, stream, false, false);

		// create a directory so that writing the buffer-file fails
		File bufferFile = new File(dir, "AudioBuffer-5.bson");
		assertTrue(bufferFile.mkdirs());
		bufferFile = new File(dir, "AudioBuffer-25.bson");
		assertTrue(bufferFile.mkdirs());

		try (DiskBasedBlockingSeekableRingBuffer localBuffer = DiskBasedBlockingSeekableRingBuffer.fromPersistence(dto)) {
			for (int i = 0; i < 150; i++) {
				try {
					localBuffer.add(new Chunk(new byte[0], "", 1));
				} catch (IllegalStateException e) {
                    assertInstanceOf(FileNotFoundException.class, e.getCause(), "Had: " + ExceptionUtils.getStackTrace(e));
					assertTrue(e.getMessage().contains("Could not update current buffers for writing at position") ||
							e.getMessage().contains("Could not fetch buffer for reading at position"),
							"Had: " + ExceptionUtils.getStackTrace(e));
					assertTrue(e.getMessage().contains("position 5 ") ||
							e.getMessage().contains("position 10 ") ||
							e.getMessage().contains("position 25") ||
							e.getMessage().contains("position 30"),
							"Had: " + ExceptionUtils.getStackTrace(e));
				}
			}
		}
	}
}
