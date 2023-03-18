package org.dstadler.audio.buffer;

import com.google.common.base.Preconditions;
import org.dstadler.commons.metrics.MovingAverage;

import javax.annotation.Nonnull;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Wrapper-interface around a {@link SeekableRingBuffer} which computes some
 * statistics and counts written and read chunks and bytes as well
 * as a moving average of how long it took on average to receive chunks
 * of data.
 *
 * This average is used to estimate how long it takes when the audio
 * data is played at normal speed.
 */
public class CountingSeekableRingBufferImpl implements CountingSeekableRingBuffer {
    private final SeekableRingBuffer<Chunk> delegate;
    private final long start = System.currentTimeMillis();

    protected static final double DEFAULT_CHUNKS_PER_SECOND = 1.5;

    private final AtomicLong bytesWrittenOverall = new AtomicLong();
    private final AtomicLong chunksWrittenOverall = new AtomicLong();

    private final AtomicLong bytesReadOverall = new AtomicLong();
    private final AtomicLong chunksReadOverall = new AtomicLong();

    // record timestamps of the last 300 chunks so we can compute how many we do per second
    private static final int MOVING_WINDOW = 50;
    private final MovingAverage chunksWrittenPerSecond = new MovingAverage(MOVING_WINDOW);
    private final MovingAverage chunksReadPerSecond = new MovingAverage(MOVING_WINDOW);

    public CountingSeekableRingBufferImpl(@Nonnull SeekableRingBuffer<Chunk> delegate) {
        Preconditions.checkNotNull(delegate, "Buffer cannot be null");
        this.delegate = delegate;
    }

    @Override
    public void add(Chunk chunk) {
        delegate.add(chunk);

        bytesWrittenOverall.addAndGet(chunk.size());
        chunksWrittenOverall.addAndGet(1);

        // compute how long it takes until we add a certain number of chunks
        // to allow computation of a moving average written chunks per second
        synchronized (chunksWrittenPerSecond) {
            chunksWrittenPerSecond.add(chunk.getTimestamp());
        }
    }

    /**
     * Used to add chunks which do not count as normal
     * traffic, e.g. when pre-filling or when bulk-adding
     * content.
     *
     * @param chunk A chunk of bytes to store
     */
    @Override
    public void addNoStats(Chunk chunk) {
        delegate.add(chunk);
    }

    @Override
    public Chunk next() {
        Chunk chunk = delegate.next();
        if(chunk == null) {
            return null;
        }

        bytesReadOverall.addAndGet(chunk.size());
        chunksReadOverall.addAndGet(1);

        // compute how long it takes between reads
        synchronized (chunksReadPerSecond) {
            chunksReadPerSecond.add(System.currentTimeMillis());
        }

        return chunk;
    }

    @Override
    public Chunk peek() {
        return delegate.peek();
    }

    @Override
    public int seek(int nrOfChunks) {
        return delegate.seek(nrOfChunks);
    }

    @Override
    public boolean empty() {
        return delegate.empty();
    }

    @Override
    public boolean full() {
        return delegate.full();
    }

    @Override
    public int capacity() {
        return delegate.capacity();
    }

    @Override
    public int size() {
        return delegate.size();
    }

    @Override
    public int fill() {
        return delegate.fill();
    }

    @Override
    public void reset() {
        delegate.reset();
    }

    @Override
    public int bufferedForward() {
        return delegate.bufferedForward();
    }

    @Override
    public int bufferedBackward() {
        return delegate.bufferedBackward();
    }

    @Override
    public void close() {
        delegate.close();
    }

    @Override
    public double getChunksWrittenPerSecond() {
        synchronized (chunksWrittenPerSecond) {
            return computeChunksPerSecond(chunksWrittenPerSecond);
        }
    }

    @Override
    public double getChunksReadPerSecond() {
        synchronized (chunksReadPerSecond) {
            return computeChunksPerSecond(chunksReadPerSecond);
        }
    }

    @Override
    public double getChunksPerSecond() {
        double written = getChunksWrittenPerSecond();
        double read = getChunksReadPerSecond();

        // having less than half a chunk per second is an indication
        // that the values are off for some reason, e.g.
        // having a download and not a live stream
        if((written < 0.5 || written > 5) &&
                (read < 0.5 || read > 5)) {
            return DEFAULT_CHUNKS_PER_SECOND;
        }

		// do not report very small or large values
        if(written < 0.5 || written > 5) {
            return read;
        }
        if(read < 0.5 || read > 5) {
            return written;
        }

        return Math.min(written, read);
    }

    private static double computeChunksPerSecond(MovingAverage chunksPerSecond) {
        if(chunksPerSecond.getFill() == 0) {
            return 0;
        }

        // compute the duration of receiving all the chunks
        // in the sliding window, but exclude time-jumps, e.g
        // where the Laptop was in Sleep/Hibernate
        long durationAdjust = 0;
        long countAdjust = 0;
        long[] window = chunksPerSecond.getWindow();
        long prevValue = window[0];
        for (long value : window) {
            // only count values up to 5 seconds, larger
            // times between chunks are very likely stopped
            // or sleeping applications
            if(value - prevValue >= 5000) {
                durationAdjust += (value - prevValue);
                countAdjust++;
            }
            prevValue = value;
        }

        long start = chunksPerSecond.getFirst();
        long end = chunksPerSecond.getLast();
        double durationInSec = ((double) end - start - durationAdjust)/1000;

        double chunksInWindow = chunksPerSecond.getFill() - countAdjust;

        // we have up to 300 items in the moving average,
        // we get per-second rate by dividing by the number
        // of seconds it took to receive that many
        return chunksInWindow / durationInSec;
    }

    // for testing
    public long getChunksReadOverall() {
        return chunksReadOverall.get();
    }

    private double getPerSecond(AtomicLong bytesWrittenOverall) {
        long time = System.currentTimeMillis() - start;

        // multiply by thousand to convert from milliseconds to seconds
        return ((double) bytesWrittenOverall.get()) * 1000 / time;
    }

    @Override
    public String toString() {
        //long time = System.currentTimeMillis() - start;
        /*", time: " + time + "/" + (double)time / 1000 +*/
        return String.format("Start: %d, " +
            "CPS: %.2f, " +
            "CPSRead: %.2f, " +
            "CPSWritten: %.2f, " +
            "Written: %d bytes/%d chunks, %.2f bytes/s, %.2f chunks/s, " +
            "Read: %d bytes/%d chunks, %.2f bytes/s, %.2f chunks/s, " +
            "%s",
            start,
            getChunksPerSecond(),
            getChunksReadPerSecond(),
            getChunksWrittenPerSecond(),
            bytesWrittenOverall.get(), chunksWrittenOverall.get(), getPerSecond(bytesWrittenOverall), getPerSecond(chunksWrittenOverall),
            bytesReadOverall.get(), chunksReadOverall.get(), getPerSecond(bytesReadOverall), getPerSecond(chunksReadOverall),
            delegate.toString());
    }
}
