package org.dstadler.audio.buffer;

import org.dstadler.audio.stream.Stream;

/**
 * Small interface for buffers which support writing out the
 * contents to a file for persisting state and buffered data
 * across restarts.
 */
public interface Persistable {
    /**
     * Convert the current buffer and the given Stream into a
     * {@link BufferPersistenceDTO} for writing to disk.
     *
     * @param stream The currently playing stream/file/download
     * @param playing If the stream is currently playing
	 * @param downloadWhilePaused If the stream should continue to be
	 *                          	downloaded while playing is paused
     * @return A populated instance of {@link BufferPersistenceDTO}
     */
    BufferPersistenceDTO toPersistence(Stream stream, boolean playing, boolean downloadWhilePaused);
}
