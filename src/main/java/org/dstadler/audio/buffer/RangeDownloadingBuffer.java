package org.dstadler.audio.buffer;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import org.apache.commons.lang3.tuple.Pair;
import org.dstadler.audio.download.RangeDownload;
import org.dstadler.audio.download.RangeDownloadFile;
import org.dstadler.audio.download.RangeDownloadHTTP;
import org.dstadler.audio.stream.Stream;
import org.dstadler.commons.logging.jdk.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Initial implementation of a special buffer-implementation for downloading chunks from files
 * via HTTP Range-requests
 *
 * The buffer represents the whole file, ranges from the file are downloaded in batches and filled up
 * with more whenever necessary.
 *
 * Pre-filling in a separate thread can be added later.
 */
public class RangeDownloadingBuffer implements SeekableRingBuffer<Chunk>, Persistable {
    private final static Logger log = LoggerFactory.make();

    @VisibleForTesting
    int RETRY_SLEEP_TIME = 5000;

    private final int bufferedChunks;
    private final int chunkSize;
    private final Function<Double, Pair<String, Long>> metaDataFun;

    private final RangeDownload download;
    private final BlockingSeekableRingBuffer buffer;

    private long nextDownloadPos = 0;

    /**
     * Create a buffer for downloading a audio-stream via the given URL.
     *
     * @param url The URL to fetch data from. Can be a local file-name or a file:// url.
     * @param user The user to use for authentication, can be empty or null to not use authentication
     * @param pwd The password to use for authenticatoin, can be null if no authentication is necessary
     * @param bufferedChunks The number of "chunks" to buffer. See {@link Chunk}
     * @param chunkSize The size of single "chunks", a common value is provided via {@link Chunk#CHUNK_SIZE}
     * @param metaDataFun Allows to provide a callback which is invoked for providing additional metdata, it
     *                    is presented with the percentage of the position in the download and should return
     *                    with a pair consisting of a generic metadata-string (e.g. a song title or artist)
     *                    and a timestamp in milliseconds since the epoch.
     *                    The function can return null via "p -&gt; null" to not provide any metadata.
     *
     * @throws IOException if reading information for the given url fails.
     */
    public RangeDownloadingBuffer(String url, String user, String pwd, int bufferedChunks, int chunkSize,
                                  Function<Double, Pair<String, Long>> metaDataFun) throws IOException {
        if(url.startsWith("file://")) {
            try {
                this.download = new RangeDownloadFile(new File(new URL(url).toURI()));
            } catch (URISyntaxException e) {
                throw new IOException(e);
            }
        } else if(url.startsWith("/") || (url.startsWith("\\"))) {
                this.download = new RangeDownloadFile(new File(url));
        } else {
            this.download = new RangeDownloadHTTP(url, user, pwd);
        }

        // make the buffer-capacity considerably larger to not fail on multi-threaded access
        // which might add more chunks than expected sometimes due to "expected" race-conditions
        this.buffer = new BlockingSeekableRingBuffer(bufferedChunks*2);

        this.bufferedChunks = bufferedChunks;
        this.chunkSize = chunkSize;
        this.metaDataFun = metaDataFun;
    }

    /**
     * Ensure that the buffer is filled with chunks up to it's capacity.
     *
     * Depending on parameters downloading can be skipped to not
     * fetch data too eagerly.
     *
     * Also a maximum number of chunks can be specified so that not
     * too many chunks are fetched at once which could block playback
     * for too long if the buffer is nearly exhausted.
     *
     * @param min If -1, fill the buffer whenever the local
     *            buffer can take more, otherwise only if we need
     *            to download at least this many chunks
     * @param max If -1, fill the buffer completely, otherwise
     *            download up to max chunks into the buffer.
     * @return The number of chunks downloaded
     * @throws IOException If downloading fails
     */
    public int fillupBuffer(int min, int max) throws IOException {
        int retries = 0;
        while(true) {
            try {
                return downloadChunksSync(min, max);
            } catch (IOException e) {
                retries++;
                if(retries >= 10) {
                    throw e;
                }

                log.warning(String.format("Retry %,d: Failed to download %,d bytes, chunkSize: %,d, bufferedChunks: %,d, " +
                                "min: %,d, max: %,d from position %,d: length: %,d: %s",
                        retries, chunkSize * buffer.size(), chunkSize, bufferedChunks, min, max, nextDownloadPos,
                        download.getLength(), e));

                try {
                    //noinspection BusyWait
                    Thread.sleep(RETRY_SLEEP_TIME);
                } catch (InterruptedException ex) {
                    log.log(Level.WARNING, "Sleeping was interrupted", ex);
                }
            }
        }
    }

    // only synchronize the actual reading and adding to the buffer and adjusting nextDownloadPos
    // to not hold the lock while sleeping during retries
    private synchronized int downloadChunksSync(int min, int max) throws IOException {
        int toDownload = bufferedChunks - buffer.size();

        // nothing to download because enough is buffered already?
        // this can be negative if we seek backwards
        if(toDownload <= 0) {
            return 0;
        }

        // nothing to download because we are at the end of the stream?
        if(nextDownloadPos >= download.getLength()) {
            return 0;
        }

        // only download up to the given number of chunks
        if(max != -1) {
            toDownload = Math.min(toDownload, max);
        }

        // download nothing if toDownload is below "min"
        if(min != -1 && toDownload < min) {
            return 0;
        }

        Preconditions.checkState(toDownload > 0,
                "Invalid value for toDownload: %s, having %s chunks and buffer %s",
                toDownload, bufferedChunks, buffer.size());

        if(log.isLoggable(Level.FINE)) {
            log.fine(String.format("Downloading %,d chunks at download-position %,d from %s",
                    toDownload, nextDownloadPos, download));
        }

        byte[] bytes = download.readRange(nextDownloadPos, (int) Math.min(chunkSize * toDownload, download.getLength() - nextDownloadPos));

        int count = 0;
        for (; count < toDownload && count * chunkSize < bytes.length; count++) {
            Pair<String, Long> metaData = getMetadata(nextDownloadPos + count * chunkSize);
            buffer.add(new Chunk(Arrays.copyOfRange(bytes, count * chunkSize,
                    Math.min(bytes.length, (count + 1) * chunkSize)),
                    metaData == null ? "" : metaData.getKey(),
                    metaData == null ? 0L : metaData.getValue()));
        }

        // advance the download-position by the exact number of bytes that
        // were actually read
        nextDownloadPos += bytes.length;

        return count;
    }

    private Pair<String, Long> getMetadata(long pos) {
        return metaDataFun.apply(((double)pos)/download.getLength());
    }

    @Override
    public void add(Chunk chunk) {
        throw new UnsupportedOperationException("This implementation does not support adding chunks, " +
                "it only downloads from '" + download + "'");
    }

    @Override
    public Chunk next() {
        // buffer.empty() indicates that we should fetch more data
        // empty() indicates that we cannot fetch more data any more
        if(buffer.empty() && !empty()) {
            try {
                log.info(String.format("Filling buffer for next() with download-position at %,d, length %,d, buffer: %s",
                        nextDownloadPos, download.getLength(), buffer));
                int chunks = fillupBuffer(-1, 10);
                log.info(String.format("Downloaded %,d chunks, now at download-position %,d, length %,d, buffer: %s",
                        chunks, nextDownloadPos, download.getLength(), buffer));
            } catch (IOException e) {
                throw new IllegalStateException("Failed to fill-up buffer", e);
            }
        }

        // if we cannot read any more data we seem to have exhausted this stream
        if(empty()) {
            close();
        }

        return buffer.next();
    }

    @Override
    public Chunk peek() {
        // buffer.empty() indicates that we should fetch more data
        // empty() indicates that we cannot fetch more data any more
        if(buffer.empty() && !empty()) {
            try {
                log.info(String.format("Filling buffer for peek() with download-position at %,d, length %,d, buffer: %s",
                        nextDownloadPos, download.getLength(), buffer));
                int chunks = fillupBuffer(-1, 10);
                log.info(String.format("Downloaded %,d chunks, now at download-position %,d, length %,d, buffer: %s",
                        chunks, nextDownloadPos, download.getLength(), buffer));
            } catch (IOException e) {
                throw new IllegalStateException("Failed to fill-up buffer", e);
            }
        }

        // if we cannot read any more data we seem to have exhausted this stream
        if(empty()) {
            close();
        }

        return buffer.peek();
    }

    @Override
    public synchronized int seek(int nrOfChunks) {
        if(nrOfChunks == 0) {
            return 0;
        }

        // do not seek outside over the end of the file
        if(nrOfChunks > 0 &&
                nextDownloadPos + ((long)nrOfChunks-buffer.size())*chunkSize > download.getLength()) {
            nrOfChunks = (int) Math.ceil((((double)(download.getLength()-nextDownloadPos))/chunkSize));
            seekInternal(download.getLength());
            return nrOfChunks;
        }

        // do not seek before the start of the buffer
        if(nrOfChunks < 0 &&
                nextDownloadPos/chunkSize - buffer.size() + nrOfChunks < 0) {
            nrOfChunks = (int) ((-1)*(nextDownloadPos/chunkSize - buffer.size()));
            // seek to the start of the buffer
            seekInternal(0);
            return nrOfChunks;
        }

        // check if we can seek forward inside the currently buffered data
        if(nrOfChunks > 0 && nrOfChunks <= buffer.bufferedForward()) {
            return buffer.seek(nrOfChunks);
        }

        // check if we can seek backwards inside the currently buffered data
        if(nrOfChunks < 0 && (-1*nrOfChunks) <= buffer.bufferedBackward()) {
            return buffer.seek(nrOfChunks);
        }

        // otherwise we need to reposition, clear the buffer and read data
        seekInternal(nextDownloadPos + (((long)nrOfChunks - buffer.size()) * chunkSize));

        return nrOfChunks;
    }

    private void seekInternal(long newPosition) {
        nextDownloadPos = newPosition;
        buffer.reset();
    }

    @Override
    public synchronized boolean empty() {
        // only report the buffer as empty if we have downloaded everything
        // and there is no data left in the buffer to read
        return nextDownloadPos >= download.getLength() && buffer.empty();
    }

    @Override
    public boolean full() {
        return true;
    }

    @Override
    public int capacity() {
        return (int) Math.ceil(((double)download.getLength())/chunkSize);
    }

    /**
     * This method is not synchronized on-purpose
     * as we accept the potential mis-reporting of
     * size in favour of always returning quickly
     * here and not blocking if download is currently
     * underway.
     */
    @Override
    public int size() {
        // in this case we always report how many chunks we can still read
        // up to the end of the file
        return (int) Math.ceil(((double)download.getLength() - nextDownloadPos)/chunkSize) +
                // plus the number of chunks available in the buffer
                buffer.size();
    }

    @Override
    public int fill() {
        return capacity();
    }

    @Override
    public synchronized void reset() {
        nextDownloadPos = 0;
        buffer.reset();
    }

    @Override
    public int bufferedForward() {
        return buffer.bufferedForward();
    }

    @Override
    public int bufferedBackward() {
        return buffer.bufferedBackward();
    }

    @Override
    public void close() {
        buffer.close();

        try {
            download.close();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public String toString() {
        return "RangeDownloadingBuffer{" +
                "bufferedChunks=" + bufferedChunks +
                ", chunkSize=" + chunkSize +
                ", download=" + download +
                ", buffer=" + buffer +
                ", nextDownloadPos=" + nextDownloadPos +
                ", percentage=" + (((double)nextDownloadPos)/download.getLength()) +
                ", metaData=" + getMetadata(nextDownloadPos) +
                '}';
    }

    @Override
    public synchronized BufferPersistenceDTO toPersistence(Stream stream, boolean playing) {
        // set persisted position where we should start reading based on the
        // current download-position minus the data that is stored in the buffer and thus
        // also needs to be downloaded again
        long startPosition = nextDownloadPos - buffer.size() * chunkSize;

        if(startPosition < 0) {
            log.warning(String.format("Found invalid startPosition: %,d with next download-position at %,d and buffer-size of %,d, resetting to 0",
                    startPosition, nextDownloadPos, buffer.size() * chunkSize));
            startPosition = 0;
        }

        log.fine("Persisting stream: " + stream + " at " + nextDownloadPos + "/" + startPosition);

        return new BufferPersistenceDTO(startPosition, stream, playing);
    }

    public static RangeDownloadingBuffer fromPersistence(BufferPersistenceDTO dto, int bufferedChunks, int chunkSize) throws IOException {
        log.info("Loading stream: " + dto.getStream() + " at " + dto.getNextDownloadPosition() + " from persistence");

        RangeDownloadingBuffer buffer = new RangeDownloadingBuffer(dto.getStream().getUrl(),
                dto.getStream().getUser(), dto.getStream().getPassword(),
                bufferedChunks, chunkSize, dto.getStream().getMetaDataFun());
        buffer.nextDownloadPos = dto.getNextDownloadPosition();
        return buffer;
    }
}
