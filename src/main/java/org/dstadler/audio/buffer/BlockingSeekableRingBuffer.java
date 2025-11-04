package org.dstadler.audio.buffer;

import com.google.common.base.Preconditions;
import org.dstadler.audio.stream.Stream;
import org.dstadler.audio.util.RuntimeInterruptedException;
import org.dstadler.commons.util.SuppressForbidden;

import java.io.IOException;

/**
 * Implementation of the {@link SeekableRingBuffer} interface which
 * uses {@link Chunk} as data object to provide byte arrays together
 * with meta-data and timestamps at which the chunk was received.
 *
 * The implementation uses two pointers for next-add-position and
 * next-get-position which cannot use the given number of chunks
 * because of ambiguity in the used ring-buffer algorithm.
 *
 * This is accepted to avoid a more complex implementation which
 * has other limitations and downsides.
 *
 * Seeking currently iterates the number of steps given to use
 * a simple implementation, there may be more efficient ways of
 * implementing this.
 */
public class BlockingSeekableRingBuffer implements SeekableRingBuffer<Chunk>, Persistable {
    private static final byte[] EMPTY = new byte[0];

    private final Chunk[] buffer;

    /**
     * indicates the next position to read,
     * there is no more data to read if nextGet == nextAdd
     * this is always in the range [0, numberOfChunks[
     */
    private int nextGet = 0;

    /**
     * indicates the next position to write,
     * this is always in the range [0, numberOfChunks[
     */
    private int nextAdd = 0;

    /**
     * indicates how many elements in the buffer
     * are populated.
     *
     * This will increase when items are added until
     * it has reached "numberOfChunks - 1" where
     * it stays
     */
    private int fill = 0;

    /**
     * This enables breaking the blocking wait in next(),
     * set via calling close()
     */
    private boolean stop = false;

    public BlockingSeekableRingBuffer(int numberOfChunks) {
        Preconditions.checkArgument(numberOfChunks > 0, "Had chunks: %s", numberOfChunks);

        this.buffer = new Chunk[numberOfChunks];

        // initialize buffer with empty chunks
        for(int i = 0;i < numberOfChunks;i++) {
            this.buffer[i] = new Chunk(EMPTY, "", 0);
        }
    }

    /**
     * Constructor for Serialization only, will construct the buffer in the state
     * that it was serialized, i.e. the same chunks and the same positions
     *
     * @param buffer The array of chunks as serialized out before
     * @param nextGet The position for the next get operation
     * @param nextAdd The position for the next add operation
     * @param fill The current fill value
     */
    private BlockingSeekableRingBuffer(Chunk[] buffer, int nextGet, int nextAdd, int fill) {
        this.buffer = buffer;
        this.nextGet = nextGet;
        this.nextAdd = nextAdd;
        this.fill = fill;
    }

    @SuppressForbidden(reason = "Uses Object.notify() on purpose here")
    @Override
    public synchronized void add(Chunk chunk) {
        Preconditions.checkNotNull(chunk);

        buffer[nextAdd] = chunk;

        nextAdd = (nextAdd + 1) % buffer.length;
        if(nextAdd == nextGet) {
            // we are overwriting the next to read, so we need to move nextGet forward as well
            nextGet = (nextGet + 1) % buffer.length;
        }

        // increase fill until we wrapped around at least once
        // so we know when the buffer is filled up with data
        if(fill != (buffer.length - 1)) {
            fill++;
        }

        notify();
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

        // fetch item before we increase the pointer
        Chunk chunk = buffer[nextGet];

        nextGet = (nextGet + 1) % buffer.length;

        return chunk;
    }

    @Override
    public synchronized Chunk peek() {
        if(empty() || stop) {
            return null;
        }

        return buffer[nextGet];
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
        return stepped;
    }

    private boolean incrementNextGet() {
        if(empty()) {
            // cannot increment further
            return false;
        }

        // advance by one
        nextGet = (nextGet + 1) % buffer.length;
        return true;
    }

    private boolean decrementNextGet() {
        if(full()) {
            // cannot increment further
            return false;
        }

        // decrement by one
        if(nextGet == 0) {
            nextGet = buffer.length - 1;
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
        return ((nextAdd + 1) % buffer.length) == nextGet;
    }

    @Override
    public synchronized int capacity() {
        // minus one because we cannot use all buffer-elements due
        // to head == tail meaning empty and (head - 1) == tail meaning full
        return buffer.length - 1;
    }

    @Override
    public int size() {
        if(nextAdd >= nextGet) {
            return nextAdd - nextGet;
        } else {
            return buffer.length - (nextGet - nextAdd);
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
    }

    @Override
    public synchronized int bufferedForward() {
        if(nextAdd >= nextGet) {
            return nextAdd - nextGet;
        } else if (fill == buffer.length - 1) {
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
        return "BlockingSeekableRingBuffer{" +
                "numberOfChunks=" + (buffer == null ? "<null>" : buffer.length) +
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
    public synchronized BufferPersistenceDTO toPersistence(Stream stream, boolean playing, boolean downloadWhilePaused,
            long chunkCount) {
        return BufferPersistenceDTO.builder().
            buffer(buffer, nextGet, nextAdd, fill).
            stream(stream, playing, downloadWhilePaused).
            chunkCount(chunkCount).
            build();
    }

    public static BlockingSeekableRingBuffer fromPersistence(BufferPersistenceDTO dto) throws IOException {
        if(dto.getBuffer() == null) {
            throw new IOException("Could not read buffer from persistent file, having: " + dto);
        }

        return new BlockingSeekableRingBuffer(dto.getBuffer(), dto.getNextGet(), dto.getNextAdd(), dto.getFill());
    }
}
