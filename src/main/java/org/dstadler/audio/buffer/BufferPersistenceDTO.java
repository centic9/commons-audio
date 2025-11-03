package org.dstadler.audio.buffer;

import java.io.File;

import org.apache.commons.lang3.ArrayUtils;
import org.dstadler.audio.stream.Stream;

/**
 * DTO class for persisting the audio buffer to disk.
 *
 * It only stores the values absolutely necessary to re-create
 * the buffer from disk with the exact same add- and get-position.
 *
 * This separation is useful as the actual ring-buffer holds
 * some more data that is not needed for serialization and
 * this separation shields against incompatibilities when new
 * fields are added to the actual ring-buffer implementation.
 *
 * Some additional information can be persisted to store
 * additional information across restarts of the application
 */
public class BufferPersistenceDTO {
    private final Chunk[] buffer;
    private final int nextGet;
    private final int nextAdd;
    private final int fill;

    private final long nextDownloadPosition;

    private final Stream stream;
    private final boolean playing;
    private final boolean downloadWhilePaused;

	private final int numberOfDiskChunks;
	private final int numberOfDiskFiles;
	private final File dataDir;

    private final long chunkCount;

    // default constructor for persistence
	private BufferPersistenceDTO(Chunk[] buffer, int nextGet, int nextAdd, int fill, long nextDownloadPosition,
            Stream stream, boolean playing, boolean downloadWhilePaused, int numberOfDiskChunks, int numberOfDiskFiles,
            File dataDir, long chunkCount) {
		// copy the array to be able to continue adding items to the buffer
		// while the data is written
		this.buffer = ArrayUtils.clone(buffer);
		this.nextGet = nextGet;
		this.nextAdd = nextAdd;
		this.fill = fill;

        this.nextDownloadPosition = nextDownloadPosition;

        this.stream = stream;
		this.playing = playing;
		this.downloadWhilePaused = downloadWhilePaused;

        this.numberOfDiskChunks = numberOfDiskChunks;
        this.numberOfDiskFiles = numberOfDiskFiles;
        this.dataDir = dataDir;

        this.chunkCount = chunkCount;
	}

	public Chunk[] getBuffer() {
        return buffer;
    }

    public int getNextGet() {
        return nextGet;
    }

    public int getNextAdd() {
        return nextAdd;
    }

    public int getFill() {
        return fill;
    }

    public Stream getStream() {
        return stream;
    }

    public long getNextDownloadPosition() {
        return nextDownloadPosition;
    }

    public boolean isPlaying() {
        return playing;
    }

	public boolean isDownloadWhilePaused() {
		return downloadWhilePaused;
	}

	public int getNumberOfDiskChunks() {
		return numberOfDiskChunks;
	}

	public int getNumberOfDiskFiles() {
		return numberOfDiskFiles;
	}

	public File getDataDir() {
		return dataDir;
	}

    public long getChunkCount() {
        return chunkCount;
    }

	@Override
	public String toString() {
		return "BufferPersistenceDTO{" +
				(buffer == null ? "" : "chunks=" + buffer.length) +
				(nextGet == 0 ? "" : ", nextGet=" + nextGet) +
				(nextAdd == 0 ? "" : ", nextAdd=" + nextAdd) +
				(fill == 0 ? "" : ", fill=" + fill) +
				(nextDownloadPosition == 0 ? "": ", nextDownloadPosition=" + nextDownloadPosition) +
				", stream=" + stream +
				", playing=" + playing +
				", downloadWhilePaused=" + downloadWhilePaused +
				(numberOfDiskChunks == 0 ? "" : ", numberOfDiskChunks=" + numberOfDiskChunks) +
				(numberOfDiskFiles == 0 ? "" : ", numberOfDiskFiles=" + numberOfDiskFiles) +
				(dataDir == null ? "" : ", dataDir=" + dataDir) +
				", chunkCount=" + chunkCount +
				'}';
	}

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Chunk[] buffer;
        private int nextGet;
        private int nextAdd;
        private int fill;

        private long nextDownloadPosition;

        private Stream stream;
        private boolean playing;
        private boolean downloadWhilePaused;

        private int numberOfDiskChunks;
        private int numberOfDiskFiles;
        private File dataDir;
        private long chunkCount;

        private Builder() {
            // no-op
        }

        public Builder buffer(Chunk[] buffer, int nextGet, int nextAdd, int fill) {
            this.buffer = buffer;
            this.nextGet = nextGet;
            this.nextAdd = nextAdd;
            this.fill = fill;

            return this;
        }

        public Builder nextDownloadPosition(long nextDownloadPosition) {
            this.nextDownloadPosition = nextDownloadPosition;

            return this;
        }

        public Builder stream(Stream stream, boolean playing, boolean downloadWhilePaused) {
            this.stream = stream;
            this.playing = playing;
            this.downloadWhilePaused = downloadWhilePaused;

            return this;
        }

        public Builder data(int numberOfDiskChunks, int numberOfDiskFiles, File dataDir) {
            this.numberOfDiskChunks = numberOfDiskChunks;
            this.numberOfDiskFiles = numberOfDiskFiles;
            this.dataDir = dataDir;

            return this;
        }

        public Builder chunkCount(long chunkCount) {
            this.chunkCount = chunkCount;

            return this;
        }

        public BufferPersistenceDTO build() {
            return new BufferPersistenceDTO(buffer, nextGet, nextAdd, fill, nextDownloadPosition, stream,
                    playing, downloadWhilePaused, numberOfDiskChunks, numberOfDiskFiles, dataDir, chunkCount);
        }
    }
}
