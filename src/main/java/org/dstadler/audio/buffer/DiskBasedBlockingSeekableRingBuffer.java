package org.dstadler.audio.buffer;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.logging.Logger;

import org.dstadler.audio.stream.Stream;
import org.dstadler.audio.util.RuntimeInterruptedException;
import org.dstadler.commons.logging.jdk.LoggerFactory;
import org.dstadler.commons.util.SuppressForbidden;

import com.google.common.base.Preconditions;

/**
 * Implementation of the {@link SeekableRingBuffer} interface which
 * uses {@link Chunk} as data object to provide byte arrays together
 * with meta-data and timestamps at which the chunk was received.
 *
 * This implementation tries to store large parts of the data on
 * disk and only keeps some data in memory to keep main memory usage
 * at bay.
 *
 * It uses the concept of a virtual large buffer which represents the
 * data to the caller, but internally only parts of it are loaded
 * into memory and most of the data is persisted to disk and only
 * fetched when needed.
 */
public class DiskBasedBlockingSeekableRingBuffer implements SeekableRingBuffer<Chunk>, Persistable {
	private final static Logger log = LoggerFactory.make();

	private static final byte[] EMPTY = new byte[0];

	private final int numberOfDiskChunks;
	private final int numberOfDiskFiles;
	private final int numberOfChunks;
	private final File tempDir;

	/**
	 * Chunk-position of the in-memory data in the overall virtual buffer
	 */
	private int diskBufferReadPosition = 0;
	private int diskBufferWritePosition = 0;

	/**
	 * List of chunks that were either fetched from disk or written to the buffer.
	 *
	 * The data is at the diskBufferPosition
	 */
	private Chunk[] diskBufferRead;
	private Chunk[] diskBufferWrite;

	/**
	 * Indicates if the in-memory diskBuffer needs to be flushed
	 * before switching to another buffer
	 */
	private boolean isDirty;


	/**
	 * indicates the next position to read in the virtual buffer,
	 * there is no more data to read if nextGet == nextAdd
	 * this is always in the range [0, numberOfChunks[
	 */
	private int nextGet = 0;

	/**
	 * indicates the next position to write,
	 * this is always in the range [0, numberOfChunks[
	 */
	private int nextAdd = 0;

	private int fill = 0;

	/**
	 * This enables breaking the blocking wait in next(),
	 * set via calling close()
	 */
	private boolean stop = false;

	/**
	 * Initialize a new buffer with empty data.
	 *
	 * The overall size of the buffer is identified via the disk-buffer size.
	 *
	 * The memory buffer size controls how many blocks of data are stored to disk
	 *
	 * @param numberOfDiskChunks Number of byte-array chunks that are stored on disk
	 * @param numberOfDiskFiles Into how many files the disk-buffer is split. This also
	 *                          controls how big the in-memory area needs to be
	 * @param tempDir The directory where buffers can be persisted.
	 */
	public DiskBasedBlockingSeekableRingBuffer(int numberOfDiskChunks, int numberOfDiskFiles, File tempDir) {
		this.numberOfDiskChunks = numberOfDiskChunks;
		this.numberOfDiskFiles = numberOfDiskFiles;
		this.tempDir = tempDir;

		Preconditions.checkNotNull(tempDir, "Need a valid temporary directory");
		Preconditions.checkState((tempDir.exists() || tempDir.mkdirs()) && tempDir.isDirectory(),
				"Invalid temporary directory provided: %s, exists: %s, isDirectory: %s",
				tempDir, tempDir.exists(), tempDir.isDirectory());

		Preconditions.checkArgument(numberOfDiskChunks > 0, "Had disk chunks: %s", numberOfDiskChunks);
		Preconditions.checkArgument(numberOfDiskFiles > 0, "Had disk blocks: %s", numberOfDiskFiles);
		Preconditions.checkArgument(numberOfDiskChunks > numberOfDiskFiles,
				"Had disk chunks: %s and disk blocks: %s", numberOfDiskChunks, numberOfDiskFiles);

		this.numberOfChunks = numberOfDiskChunks / numberOfDiskFiles;

		this.diskBufferRead = new Chunk[numberOfChunks];
		this.diskBufferWrite = new Chunk[numberOfChunks];

		// initialize buffer with empty chunks
		for(int i = 0;i < numberOfChunks;i++) {
			this.diskBufferRead[i] = new Chunk(EMPTY, "", 0);
			this.diskBufferWrite[i] = new Chunk(EMPTY, "", 0);
		}
	}

	/**
	 * Constructor for Serialization only, will construct the buffer in the state
	 * that it was serialized, i.e. the same chunks and the same positions
	 *
	 * @param numberOfDiskChunks Number of byte-array chunks that are stored on disk
	 * @param numberOfDiskFiles Into how many files the disk-buffer is split. This also
	 *                          controls how big the in-memory area needs to be
	 * @param tempDir The directory where buffers can be persisted.
	 * @param nextGet The position for the next get operation
	 * @param nextAdd The position for the next add operation
	 * @param fill The current fill value
	 */
	private DiskBasedBlockingSeekableRingBuffer(int numberOfDiskChunks, int numberOfDiskFiles, File tempDir,
			int nextGet, int nextAdd, int fill)
			throws IOException {
		this.numberOfDiskChunks = numberOfDiskChunks;
		this.numberOfDiskFiles = numberOfDiskFiles;
		this.tempDir = tempDir;

		Preconditions.checkNotNull(tempDir, "Need a valid temporary directory");
		Preconditions.checkState((tempDir.exists() || tempDir.mkdirs()) && tempDir.isDirectory(),
				"Invalid temporary directory provided: %s, exists: %s, isDirectory: %s",
				tempDir, tempDir.exists(), tempDir.isDirectory());

		Preconditions.checkArgument(numberOfDiskChunks > 0, "Had disk chunks: %s", numberOfDiskChunks);
		Preconditions.checkArgument(numberOfDiskFiles > 0, "Had disk blocks: %s", numberOfDiskFiles);
		Preconditions.checkArgument(numberOfDiskChunks > numberOfDiskFiles,
				"Had disk chunks: %s and disk blocks: %s", numberOfDiskChunks, numberOfDiskFiles);

		this.numberOfChunks = numberOfDiskChunks / numberOfDiskFiles;

		this.nextGet = nextGet;
		this.nextAdd = nextAdd;
		this.fill = fill;

		// set location for memory-chunks based on the current read- and write-position
		this.diskBufferReadPosition = getDiskPosition(nextGet);
		this.diskBufferWritePosition = getDiskPosition(nextAdd);

		// try to read the buffer from disk based on these positions
		this.diskBufferRead = readBuffer(tempDir, diskBufferReadPosition, numberOfChunks);
		this.diskBufferWrite = readBuffer(tempDir, diskBufferWritePosition, numberOfChunks);
	}

	private int getDiskPosition(int pos) {
		// make sure the position is always aligned at
		// same disk-chunk positions
		return pos - (pos % numberOfChunks);
	}

	/**
	 * Ensures that a dirty buffer is persisted to disk.
	 *
	 * @throws java.io.IOException If writing to the file fails.
	 */
	private void persistBuffer() throws IOException {
		if (isDirty) {
			File bufferFile = new File(tempDir, "PiRdadio-" + diskBufferWritePosition + ".bson");

			log.info("Writing buffer for position " + diskBufferWritePosition + " to file " + bufferFile);
			try (OutputStream stream = new BufferedOutputStream(new FileOutputStream(bufferFile))) {
				BufferPersistence.getMapper().writeValue(stream, diskBufferWrite);
			}
			log.fine("Done writing to " + bufferFile);

			isDirty = false;
		}
	}

	/**
	 * Fetches data for the current position into the buffer
	 *
	 * @throws IOException If reading from the file fails
	 */
	private static Chunk[] readBuffer(File tempDir, int diskBufferPosition, int numberOfChunks) throws IOException {
		File bufferFile = new File(tempDir, "PiRdadio-" + diskBufferPosition + ".bson");

		if (!bufferFile.exists()) {
			Chunk[] buffer = new Chunk[numberOfChunks];

			log.info("Could not read disk-buffer from " + tempDir);
			for(int i = 0;i < numberOfChunks;i++) {
				buffer[i] = new Chunk(EMPTY, "", 0);
			}
			return buffer;
		} else {
			log.info("Reading buffer from file " + bufferFile);
			try (InputStream stream = new BufferedInputStream(new FileInputStream(bufferFile))) {
				Chunk[] buffer = BufferPersistence.getMapper().readValue(stream, Chunk[].class);
				log.info("Read " + buffer.length + " chunks from file " + bufferFile);
				return buffer;
			}
		}
	}


	@SuppressForbidden(reason = "Uses Object.notify() on purpose here")
	@Override
	public synchronized void add(Chunk chunk) {
		Preconditions.checkNotNull(chunk);
		Preconditions.checkState(nextAdd - diskBufferWritePosition >= 0 &&
				diskBufferWritePosition - nextAdd < numberOfChunks,
				"Did have invalid positions: write-pos: %s, nextAdd: %s, numberOfChunks: %s",
				diskBufferWritePosition, nextAdd, numberOfChunks);

		diskBufferWrite[nextAdd - diskBufferWritePosition] = chunk;
		isDirty = true;

		// we may also need to update the read-buffer
		if (nextAdd >= diskBufferReadPosition &&
			nextAdd < (diskBufferReadPosition + numberOfChunks)) {

			// compute the resulting position for access into the read-array
			diskBufferRead[nextAdd - diskBufferReadPosition] = chunk;
		}

		nextAdd = (nextAdd + 1) % numberOfDiskChunks;
		if(nextAdd == nextGet) {
			Preconditions.checkState(nextGet - diskBufferReadPosition >= 0 &&
							nextGet - diskBufferReadPosition < numberOfChunks,
					"Did have invalid positions: Read-pos: %s, nextGet: %s, numberOfChunks: %s",
					diskBufferReadPosition, nextGet, numberOfChunks);

			// we are overwriting the next to read, so we need to move nextGet forward as well
			nextGet = (nextGet + 1) % numberOfDiskChunks;

			checkReadBuffer();
		}

		checkWriteBuffer();

		// increase fill until we wrapped around at least once
		// so we know when the buffer is filled up with data
		if(fill != (numberOfDiskChunks - 1)) {
			fill++;
		}

		notify();
	}

	private void checkReadBuffer() {
		// check if the next get position is outside of the chunk that we have
		// available
		if (nextGet < diskBufferReadPosition ||
				(nextGet >= (diskBufferReadPosition + numberOfChunks))) {
			diskBufferReadPosition = getDiskPosition(nextGet);
			try {
				diskBufferRead = readBuffer(tempDir, diskBufferReadPosition, numberOfChunks);
			} catch (IOException e) {
				throw new IllegalStateException("Could not fetch buffer for reading at position " +
						diskBufferReadPosition + " from " + tempDir, e);
			}
		}
	}

	@SuppressForbidden(reason = "Uses Object.wait() on purpose here")
	@Override
	public synchronized Chunk next() {
		// wait until data is available
		while(empty() && !stop) {
			try {
				// waiting leaves the synchronized block so other threads
				// can do work while we wait here
				wait(100);
			} catch (InterruptedException e) {
				throw new RuntimeInterruptedException(e);
			}
		}

		if(stop) {
			return null;
		}

		Preconditions.checkState(nextGet - diskBufferReadPosition >= 0 &&
						nextGet - diskBufferReadPosition < numberOfChunks,
				"Did have invalid positions: Read-pos: %s, nextGet: %s, numberOfChunks: %s",
				diskBufferReadPosition, nextGet, numberOfChunks);

		// fetch item before we increase the pointer
		Chunk chunk = diskBufferRead[nextGet - diskBufferReadPosition];

		nextGet = (nextGet + 1) % numberOfDiskChunks;

		// make sure we fetch more from disk if necessary
		checkReadBuffer();

		return chunk;
	}

	@Override
	public synchronized Chunk peek() {
		if(empty() || stop) {
			return null;
		}

		Preconditions.checkState(nextGet - diskBufferReadPosition >= 0 &&
						nextGet - diskBufferReadPosition < numberOfChunks,
				"Did have invalid positions: Read-pos: %s, nextGet: %s, numberOfChunks: %s",
				diskBufferReadPosition, nextGet, numberOfChunks);

		return diskBufferRead[nextGet - diskBufferReadPosition];
	}

	private void checkWriteBuffer() {
		// check if the next get position is outside the chunk that we have available
		if (nextAdd < diskBufferWritePosition ||
				(nextAdd >= (diskBufferWritePosition + numberOfChunks))) {
			try {
				// make sure a dirty buffer is persisted
				persistBuffer();

				diskBufferWritePosition = getDiskPosition(nextAdd);

				// we read the previous chunk to keep the previous data if
				// we seek away and thus flush the memory-buffer to disk again
				diskBufferWrite = readBuffer(tempDir, diskBufferWritePosition, numberOfChunks);
			} catch (IOException e) {
				throw new IllegalStateException("Could not fetch current buffer for writing at position " +
						diskBufferWritePosition + " from " + tempDir, e);
			}
		}
	}

	@Override
	public synchronized int seek(int nrOfChunks) {
		// this is a very naive initial implementation which actually loops
		// to step forward/backward as long as possible and counts it's steps
		int stepped = 0;
		if(nrOfChunks > 0) {
			for(int i = 0;i < nrOfChunks;i++) {
				if(!incrementNextGet()) {
					break;
				}
				stepped++;
			}
		} else if (nrOfChunks < 0) {
			for(int i = 0;i > nrOfChunks;i--) {
				if(!decrementNextGet()) {
					break;
				}
				stepped--;
			}
		}

		// make sure we fetch more from disk if necessary
		checkReadBuffer();

		// TODO: check for switch to different read and write buffer
		return stepped;
	}

	private boolean incrementNextGet() {
		if(empty()) {
			// cannot increment further
			return false;
		}

		// advance by one
		nextGet = (nextGet + 1) % numberOfDiskChunks;
		return true;
	}

	private boolean decrementNextGet() {
		if(full()) {
			// cannot increment further
			return false;
		}

		// decrement by one
		if(nextGet == 0) {
			nextGet = numberOfDiskChunks - 1;
		} else {
			nextGet = nextGet - 1;
		}

		return true;
	}

	@Override
	public synchronized boolean empty() {
		//if head and tail are equal, we are empty
		return nextAdd == nextGet;
	}

	@Override
	public synchronized boolean full() {
		// If tail is ahead of the head by 1, we are full
		return ((nextAdd + 1) % numberOfDiskChunks) == nextGet;
	}

	@Override
	public synchronized int capacity() {
		// minus one because we cannot use all buffer-elements due
		// to head == tail meaning empty and (head - 1) == tail meaning full
		return numberOfDiskChunks - 1;
	}

	@Override
	public int size() {
		if(nextAdd >= nextGet) {
			return nextAdd - nextGet;
		} else {
			return numberOfDiskChunks - (nextGet - nextAdd);
		}
	}

	@Override
	public int fill() {
		return fill;
	}

	@Override
	public synchronized void reset() {
		nextAdd = nextGet;
		fill = 0;

		checkWriteBuffer();
	}

	@Override
	public synchronized int bufferedForward() {
		if(nextAdd >= nextGet) {
			return nextAdd - nextGet;
		} else if (fill == numberOfDiskChunks - 1) {
			return fill - nextGet + nextAdd + 1;
		} else {
			return fill - nextAdd;
		}
	}

	@Override
	public synchronized int bufferedBackward() {
		if(nextAdd >= nextGet) {
			return fill - nextAdd + nextGet;
		} else {
			return nextGet - nextAdd - 1;
		}
	}

	@Override
	public synchronized void close() {
		stop = true;
	}

	@Override
	public String toString() {
		return "DiskBasedBlockingSeekableRingBuffer{" +
				"diskBufferRead=" + (diskBufferRead == null ? "<null>" : diskBufferRead.length) +
				", diskBufferWrite=" + (diskBufferWrite == null ? "<null>" : diskBufferWrite.length) +
				", isDirty=" + isDirty +
				", numberOfDiskChunks=" + numberOfDiskChunks +
				", diskBufferReadPosition=" + diskBufferReadPosition +
				", diskBufferWritePosition=" + diskBufferWritePosition +
				", numberOfChunks=" + numberOfChunks +
				", numberOfDiskFiles=" + numberOfDiskFiles +
				", nextGet=" + nextGet +
				", nextAdd=" + nextAdd +
				", stop=" + stop +
				", capacity=" + capacity() +
				", size=" + size() +
				", empty=" + empty() +
				", full=" + full() +
				'}';
	}

	@Override
	public synchronized BufferPersistenceDTO toPersistence(Stream stream, boolean playing, boolean downloadWhilePaused) {
		// make sure any dirty writes are done
		try {
			persistBuffer();
		} catch (IOException e) {
			throw new IllegalStateException("With temp-dir: " + tempDir, e);
		}

		// only persist nextGet/nextAdd, we can re-create the positions and buffers from that
		return new BufferPersistenceDTO(numberOfDiskChunks, numberOfDiskFiles, tempDir,
				nextGet, nextAdd, fill, stream, playing, downloadWhilePaused);
	}

	public static DiskBasedBlockingSeekableRingBuffer fromPersistence(BufferPersistenceDTO dto) throws IOException {
		if(dto.getTempDir() == null || dto.getNumberOfDiskFiles() <= 0 || dto.getNumberOfDiskChunks() <= 0) {
			throw new IOException("Could not read buffer from persistent file, having: " + dto);
		}

		return new DiskBasedBlockingSeekableRingBuffer(dto.getNumberOfDiskChunks(), dto.getNumberOfDiskFiles(), dto.getTempDir(),
						dto.getNextGet(), dto.getNextAdd(), dto.getFill());
	}
}
