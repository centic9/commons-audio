package org.dstadler.audio.download;

import com.google.common.base.Preconditions;
import org.dstadler.commons.logging.jdk.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.logging.Logger;

/**
 * Implementation of the RangeDownload interface for reading
 * data from local files
 */
public class RangeDownloadFile implements RangeDownload {
    private final static Logger log = LoggerFactory.make();

    private final File file;
    private boolean closed = false;

    public RangeDownloadFile(File file) {
        this.file = file;
    }

    @Override
    public long getLength() {
        return file.length();
    }

    @Override
    public byte[] readRange(long start, int size) throws IOException {
        if(closed) {
            throw new IllegalStateException("Already closed");
        }
        long length = getLength();

        // don't try to read beyond the end
        Preconditions.checkArgument(start <= length,
                "Tried to start reading beyond the end of the file. " +
                        "Size of stream: %s, position to read: %s, size to read: %s",
                length, start, size);

        if(start + size > length) {
            log.info("Reducing number of bytes to read for " + file + " at position " + start +
                    " from " + size + " bytes to " + (length - start) +
                    " bytes because it would exceed the length of the stream of " + length + " bytes");
            size = (int)(length - start);
        }

        Preconditions.checkArgument(start >= 0,
                "Had an invalid download-start %s for size %s and length: %s",
                start, size, length);

        Preconditions.checkArgument(size >= 1,
                "Had an invalid download-size %s for start %s and length: %s",
                size, start, length);

        byte[] bytes = new byte[size];
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            raf.seek(start);

            raf.read(bytes);
        }

        return bytes;
    }

    @Override
    public void close() {
        // just remember closed state for throwing exception on reading
        closed = true;
    }

    @Override
    public String toString() {
        return "RangeDownloadHTTP{" +
                "file='" + file + '\'' +
                ", length=" + getLength() +
                '}';
    }
}
