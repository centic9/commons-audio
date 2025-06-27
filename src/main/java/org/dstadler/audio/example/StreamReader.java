package org.dstadler.audio.example;

import org.apache.commons.io.IOUtils;
import org.apache.hc.client5.http.ClientProtocolException;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.util.Timeout;
import org.dstadler.audio.buffer.Chunk;
import org.dstadler.audio.buffer.SeekableRingBuffer;
import org.dstadler.commons.http5.HttpClientWrapper5;
import org.dstadler.commons.logging.jdk.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.dstadler.audio.buffer.Chunk.CHUNK_SIZE;

/**
 * Utility class to read from an audio stream into a
 * {@link SeekableRingBuffer}, handling the ShoutCast
 * protocol for reading audio data together with metadata
 * if provided by the stream.
 *
 * This is part of the example audio player.
 */
public class StreamReader implements AutoCloseable {
    private final static Logger log = LoggerFactory.make();


    protected final CloseableHttpClient httpClient;
    private final BooleanSupplier shouldStop;

    private String currentMetaData = "";

    public StreamReader(int timeoutMs, BooleanSupplier shouldStop) {
        RequestConfig reqConfig = RequestConfig.custom()
                //.setSocketTimeout(timeoutMs)
                .setConnectTimeout(Timeout.ofMilliseconds(timeoutMs))
                .setConnectionRequestTimeout(Timeout.ofMilliseconds(timeoutMs))
                .build();

        HttpClientBuilder builder = HttpClients.custom();
        // configure the builder for HttpClients
        builder.setDefaultRequestConfig(reqConfig);

        // finally create the HttpClient instance
        this.httpClient = builder.build();
        this.shouldStop = shouldStop;
    }

    /**
     * Start reading data from the given stream/file and write received data in the buffer. For live, writing data as
     * soon as it is received. For download mode, reading is delayed until the buffer is more than half filled. I.e. it
     * will read quickly in the beginning and then keep the buffer half-filled.
     *
     * NOTE: This is a blocking call which will continue to run either until maxChunks are read or {@link #close()}
     * is called.
     *
     * Usually you will call this in a separate reading-thread.
     *
     * @param strUrl The URL to read from, should return either a MP3 live stream (StreamType.live) or a downloadable
     *               MP3 file (StreamType.download)
     * @param buffer The buffer to use for providing resulting data
     * @throws IOException If reading fails, usually also when the stream is closed on purpose via {@link #close()}
     */
    public void connectAndRead(String strUrl, SeekableRingBuffer<Chunk> buffer) throws IOException {
        log.info("Start reading data from " + strUrl + " into buffer: " + buffer);
        HttpGet httpGet = buildHTTPHeader(strUrl);

        while(!shouldStop.getAsBoolean()) {
            try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
                HttpEntity entity = HttpClientWrapper5.checkAndFetch(response, strUrl);
                try {
                    long chunks = 0;
                    InputStream content = entity.getContent();
                    while (!shouldStop.getAsBoolean()) {
                        final byte[] bytes = readResponse(response, content);

                        buffer.add(new Chunk(bytes, getCurrentMetaData(), System.currentTimeMillis()));
                        chunks++;
                        if (chunks % 200 == 0) {
                            log.info("Read " + bytes.length + " bytes from " + strUrl + ", having buffer: " + buffer);
                        }

                        log.info("Having " + buffer.bufferedBackward() + " and " + buffer.bufferedForward() + ": " + buffer);
                    }

                    // close the response here to ensure we stop reading more data in the "consume" below
                    response.close();
                } finally {
                    // ensure all content is taken out to free resources
                    try {
                        EntityUtils.consume(entity);
                    } catch (IOException e) {
                        log.info("Had exception while consuming content: " + e);
                    }
                }
            } catch (ClientProtocolException e) {
                log.warning("Had an invalid url '" + strUrl + "', delaying a bit before retrying: " + e +
                        ", buffer: " + buffer);
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ex) {
                    log.log(Level.WARNING, "Waiting was interrupted", e);
                }
            } catch (IOException e) {
                // do not log a full stacktrace as it is expected
                // that connection will fail if we are stopping
                if (shouldStop.getAsBoolean()) {
                    log.info("Exception on shutdown: " + e);
                } else {
                    throw e;
                }
            }
        }
    }

    protected HttpGet buildHTTPHeader(String strUrl) {
        HttpGet httpGet = new HttpGet(strUrl);
        try {
            httpGet.setVersion(ProtocolVersion.parse("HTTP/1.0"));
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
        httpGet.addHeader("User-Agent", "Wget/1.17.1 (linux-gnu)");
        httpGet.addHeader("Accept", "*/*");
        httpGet.addHeader("Accept-Encoding", "identity");
        httpGet.addHeader("Icy-MetaData", "1");

        return httpGet;
    }


    protected byte[] readResponse(CloseableHttpResponse response, InputStream content) throws IOException {
        // read data-chunk, for now we allocate a new byte[] every time, if churn becomes a problem we can
        // change this to only re-allocate on size-change as usually always 16384 bytes are used here
        final Header icyMetaint = response.getFirstHeader("icy-metaint");
        final byte[] bytes;
        if(icyMetaint != null) {
            int bytesToRead = Integer.parseInt(icyMetaint.getValue());
            bytes = IOUtils.readFully(content, bytesToRead);

            // read/skip metadata
            readMetadata(content);
        } else {
            bytes = new byte[CHUNK_SIZE];
            IOUtils.read(content, bytes);
        }
        return bytes;
    }

    protected void readMetadata(InputStream content) throws IOException {
        byte headerByte = (byte) content.read();
        int icyBytes = headerByte * 16;
        if (icyBytes > 0) {
            readMetadata(content, icyBytes);
        }
    }

    protected void readMetadata(InputStream content, int bytesToRead) throws IOException {
        byte[] metaData = IOUtils.readFully(content, bytesToRead);

        int firstZero = 0;
        while((firstZero < metaData.length) && (metaData[firstZero] != 0)) {
            firstZero++;
        }

        log.info("Had metadata: " + new String(metaData, 0, firstZero));

        String title = getMetadata(Arrays.copyOfRange(metaData, 0, firstZero)).get("StreamTitle");
        if(title != null) {
            currentMetaData = title;
        }
    }

    /**
     * Process metadata bytes
     * @param meta array of bytes holding the metadata
     * @return a map containing the extracted metadata
     */
    protected Map<String, String> getMetadata(byte[] meta) {
        String[] newMeta = new String(meta, StandardCharsets.UTF_8).split(";");
        Map<String, String> metaMap = new HashMap<>();
        for (String tag : newMeta) {
            int index = tag.indexOf("=");
            if (index >= 0) {
                metaMap.put(tag.substring(0, index).trim(), dequote(tag.substring(index + 1)).trim());
            }
        }
        return metaMap;
    }

    /**
     * Remove quotes, semicolons, and backslashes from the beginning and end of the string
     * @param str string to be dequoted
     * @return str with quotes, semicolons, and backslashes from its beginning and end
     */
    protected String dequote(String str) {
        String newStr = str.trim();
        if(newStr.isEmpty()) {
            return newStr;
        }

        if ((newStr.charAt(0) == '\"' && newStr.charAt(newStr.length() - 1) == '\"')
                || (newStr.charAt(0) == '\'' && newStr.charAt(newStr.length() - 1) == '\'')) {
            newStr = newStr.substring(1);
        }
        if (newStr.charAt(newStr.length() - 1) == ';' || newStr.charAt(newStr.length() - 1) == '\'') {
            newStr = newStr.substring(0, newStr.length() - 1);
        }
        return newStr;
    }

    public String getCurrentMetaData() {
        return currentMetaData;
    }

    @Override
    public void close() {
        try {
            // also close the httpClient to quickly stop any socket-connection
            // that is currently blocking the thread
            httpClient.close();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
