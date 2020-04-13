package org.dstadler.audio.buffer;

/**
 * Wrapper-interface around a {@link SeekableRingBuffer} which computes some
 * statistics and counts written and read chunks and bytes as well
 * as a moving average of how long it took on average to receive chunks
 * of data.
 *
 * This average is used to estimate how long it takes when the audio
 * data is played at normal speed.
 */
public interface CountingSeekableRingBuffer extends SeekableRingBuffer<Chunk> {

    /**
     * Used to add chunks which do not count as normal
     * traffic, e.g. when pre-filling or when bulk-adding
     * content.
     *
     * @param chunk A chunk of bytes to store
     */
    void addNoStats(Chunk chunk);

    double getChunksWrittenPerSecond();

    double getChunksReadPerSecond();

    double getChunksPerSecond();
}
