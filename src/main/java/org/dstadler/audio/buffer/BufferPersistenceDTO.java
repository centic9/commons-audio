package org.dstadler.audio.buffer;

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
 */
public class BufferPersistenceDTO {
    private final Chunk[] buffer;
    private final int nextGet;
    private final int nextAdd;
    private final int fill;

    private final long nextDownloadPosition;

    private final Stream stream;
    private final boolean playing;

    // default constructor for persistence
    @SuppressWarnings("unused")
    private BufferPersistenceDTO() {
        this.buffer = null;
        this.nextGet = 0;
        this.nextAdd = 0;
        this.fill = 0;
        this.stream = null;
        this.nextDownloadPosition = 0;
        this.playing = false;
    }

    public BufferPersistenceDTO(Chunk[] buffer, int nextGet, int nextAdd, int fill, Stream stream, boolean playing) {
        // copy the array to be able to continue adding items to the buffer
        // while the data is written
        this.buffer = ArrayUtils.clone(buffer);
        this.nextGet = nextGet;
        this.nextAdd = nextAdd;
        this.fill = fill;
        this.stream = stream;
        this.nextDownloadPosition = 0;
        this.playing = playing;

    }

    public BufferPersistenceDTO(long nextDownloadPosition, Stream stream, boolean playing) {
        this.buffer = null;
        this.nextGet = 0;
        this.nextAdd = 0;
        this.fill = 0;
        this.stream = stream;
        this.nextDownloadPosition = nextDownloadPosition;
        this.playing = playing;
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

    @Override
    public String toString() {
        return "BufferPersistenceDTO{" +
                "chunks=" + (buffer == null ? "<null>" : buffer.length) +
                ", nextGet=" + nextGet +
                ", nextAdd=" + nextAdd +
                ", fill=" + fill +
                ", nextDownloadPosition=" + nextDownloadPosition +
                ", stream=" + stream +
                ", playing=" + playing +
                '}';
    }
}
