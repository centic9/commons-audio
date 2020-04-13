package org.dstadler.audio.download;

import java.io.IOException;

/**
 * Base interface for fetching a range of bytes from a resource.
 *
 * Possible implementations include HTTP based fetching or
 * reading content from local files.
 */
public interface RangeDownload extends AutoCloseable {
    /**
     * Returns the length of the download as returned by the header-field
     * "Content-Length"
     *
     * @return The length of the download in bytes.
     */
    long getLength();

    /**
     * Read a range of bytes from the file.
     *
     * If start+size are beyond the length of the download, an info message
     * is logged and only the remaining bytes are read and returned.
     *
     * @param start The byte-position where reading should start.
     * @param size The number of bytes to read from the file.
     * @return An array of up to "size" bytes, less if the end of the download was reached.
     * @throws IOException If requesting data via HTTP fails
     * @throws IllegalArgumentException If start is larger or equals to the length of the download
     * @throws IllegalStateException If the object is closed already
     */
    byte[] readRange(long start, int size) throws IOException;

    /**
     * Frees up any resources. Using the class
     * after this will usually not work.
     *
     * @throws IOException If closing resources fails
     */
    @Override
    void close() throws IOException;
}
