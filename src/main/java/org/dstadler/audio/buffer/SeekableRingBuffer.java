package org.dstadler.audio.buffer;

/**
 * An interface for a specialized RingBuffer which allows to step
 * forward/backward in the stored elements as long as elements are
 * not yet overwritten by newer data.
 *
 * Implementations should usually ensure thread-safety.
 *
 * Implementations can decide if next() should be a blocking call
 * or should return null on empty buffer.
 */
public interface SeekableRingBuffer<T> extends AutoCloseable {
    /**
     * Add a new chunk into the ring-buffer, possibly overwriting
     * the oldest chunk in the buffer.
     *
     * @param chunk A chunk of bytes to store
     */
    void add(T chunk);

    /**
     * Fetch the next chunk from the ring-buffer and advance the read-position.
     *
     * This call may block until a chunk becomes available, the thread is
     * interrupted or close() is called.
     *
     * @return An array of bytes or null if the buffer was closed.
     */
    T next();

    /**
     * Fetch the next chunk from the buffer if possible, return null otherwise.
     * This does not advance the read-position, so a subsequent peek() or next()
     * will return the same chunk again.
     *
     * This call may block until a chunk becomes available, the thread is
     * interrupted or close() is called.
     *
     * @return A chunk if available or null if the buffer is empty or the buffer
     *      is closed already.
     */
    T peek();

    /**
     * Go forward or backward the given number of chunks.
     * Negative numbers indicate backwards, positive indicate
     * forward.
     * The return value indicates how many chunks could actually be
     * seeked towards the start or end of the buffer.
     *
     * @param nrOfChunks A signed number indicating the number of
     *                   blocks to go forward or backwards in the buffer
     *
     * @return The number of chunks that could be seeked towards the
     *      start or end of the buffer, i.e. return == nrOfChunks indicates
     *      that seeking this many chunks forward or backwards was possible.
     */
    int seek(int nrOfChunks);

    /**
     * @return true if there are no elements in the buffer, false if next() is
     *      able to read an element without blocking or throwing an exception..
     */
    boolean empty();

    /**
     * Check if all slots in the buffer are filled with data, i.e.
     * calling next() as many times as the number of chunks would
     * still not block for more data..
     *
     * @return true if all slots in the buffer are filled.
     */
    boolean full();

    /**
     * Capacity describes how many elements the buffer can contain
     * at maximum.
     *
     * @return The size provided during construction of the buffer
     */
    int capacity();

    /**
     * Returns how many elements are available for reading from the
     * current position in the buffer up to the element added last.
     *
     * @return The current number of elements between tail and head
     */
    int size();

    /**
     * Indicates how many positions are actually filled with data.
     * After the writing position wrapped around, this will always
     * return the same value as capacity().
     *
     * @return The number of elements that are currently stored in the buffer,
     *  the same as capacity() after the write-position wrapped around once.
     */
    int fill();

    /**
     * Sets the buffer to empty, i.e. size(), fill(), bufferedForward() and
     * bufferedBackwards() will return 0 afterwards and next() without
     * add() would block or throw an Exception.
     */
    void reset();

    /**
     * Provides information how many chunks can be fetched or seeked forward in the
     * buffer until a blocking read operation is necessary.
     *
      * @return The number of chunks that are buffered.
     */
    int bufferedForward();

    /**
     * Provides information how many chunks can be seeked backwards in the buffer
     * without a blocking read operation.
     *
     * @return The number of chunks that are buffered.
     */
    int bufferedBackward();

    /**
     * Overridden from {@link AutoCloseable} to remove thrown Exception
     */
    @Override
    void close();
}
