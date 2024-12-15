package org.dstadler.audio.download;

import com.google.common.base.Preconditions;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.util.EntityUtils;
import org.dstadler.commons.http.HttpClientWrapper;
import org.dstadler.commons.logging.jdk.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Provides functionality to download ranges from
 * URLs via the HTTP "Range requests" feature.
 *
 * See <a href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Range_requests">https://developer.mozilla.org/en-US/docs/Web/HTTP/Range_requests</a>
 * for the underlying part of the HTTP protocol.
 *
 * After constructing the object with an URL, you
 * can query for the length of the file via getLength()
 * and download ranges of the file via readRange().
 */
public class RangeDownloadHTTP implements RangeDownload {
    private final static Logger log = LoggerFactory.make();

    private static final int TIMEOUT_MS = 60_000;

    private final String url;
    private final HttpClientWrapper httpClient;

    private final long length;

    /**
     * Construct a range-download with the URL and optional user/password
     * for basic authentication.
     *
     * @param url The URL to download
     * @param user The username to use for basic authentication, use "" for no user.
     * @param pwd The password to use for basic authentication, use null for no password.
     * @throws IOException If the URL does not point to a valid downloadable file or
     *          another error occurs while accessing the URL.
     * @throws IllegalStateException If the web-server does not support the range-protocol for downloading
     *          specific parts of the file.
     */
    public RangeDownloadHTTP(String url, String user, String pwd) throws IOException {
        this.url = url;

        if(StringUtils.isEmpty(user)) {
            this.httpClient = new HttpClientWrapper(TIMEOUT_MS);
        } else {
            this.httpClient = new HttpClientWrapper(user, pwd, TIMEOUT_MS);
        }

        // initialize the length and verify that the range-download will work
        final HttpUriRequest httpHead = new HttpHead(url);
        try (CloseableHttpResponse response = httpClient.getHttpClient().execute(httpHead)) {
            HttpEntity entity = HttpClientWrapper.checkAndFetch(response, url);
            try {
                String headers = Arrays.toString(response.getAllHeaders());
                Preconditions.checkState(response.getFirstHeader("Accept-Ranges") != null,
                        "Need a HTTP response for 'Accept-Ranges' for %s, but got: %s",
                        url, headers);
                Preconditions.checkState("bytes".equals(response.getFirstHeader("Accept-Ranges").getValue()) ||
                                response.getFirstHeader("Accept-Ranges").getValue().matches("\\d+-\\d+"),
                        "Only 'bytes' or 'n-n' is supported for HTTP header 'Accept-Ranges' for %s, but got: %s",
                        url, headers);

                Preconditions.checkState(response.getFirstHeader("Content-Length") != null,
                        "Need a HTTP header-response for 'Content-Length' for %s, but got: %s",
                        url, headers);

                length = Long.parseLong(response.getFirstHeader("Content-Length").getValue());
            } finally {
                // ensure all content is taken out to free resources
                EntityUtils.consume(entity);
            }

            // for some reason the connection to the URL might be stale now,
            // forcing to close the connections seems to help
            //noinspection deprecation
            httpClient.getHttpClient().getConnectionManager().closeIdleConnections(0, TimeUnit.SECONDS);
        }

        log.info("Prepared download of %s, length: %,d".formatted(url, length));
    }

    @Override
    public long getLength() {
        return length;
    }

    @Override
    public byte[] readRange(long start, int size) throws IOException {
        Preconditions.checkArgument(start <= length,
                "Tried to start reading beyond the end of the stream. " +
                        "Size of stream: %s, position to read: %s, size to read: %s",
                length, start, size);

        if(start + size > length) {
            log.info("Reducing number of bytes to read for %s at position %,d from %,d bytes to %,d bytes because of length of stream %,d".formatted(
                    url, start, size, length - start, length));
            size = (int)(length - start);
        }

        final HttpUriRequest httpGet = new HttpGet(url);

        Preconditions.checkArgument(start >= 0,
                "Had an invalid download-start %s for size %s and length: %s",
                start, size, length);

        long end = start + size - 1;
        Preconditions.checkArgument(size >= 1,
                "Had an invalid download-range %s-%s for start %s and size %s, length: %s",
                start, end, start, size, length);

        // Range: bytes=0-1023
        httpGet.setHeader("Range", "bytes=" + start + "-" + end);

        try (CloseableHttpResponse response = httpClient.getHttpClient().execute(httpGet)) {
            HttpEntity entity = HttpClientWrapper.checkAndFetch(response, url);
            try {
                // The FM4 server returns a text/html response if the show was removed after 7 days
                // we should detect this and stop the download in this case
                if (entity.getContentType().getValue().startsWith("text/html")) {
                    // returning empty signals that no more data can be loaded
                    return new byte[0];
                }

                byte[] bytes = new byte[size];
                IOUtils.read(entity.getContent(), bytes);
                return bytes;
            } finally {
                // ensure all content is taken out to free resources
                EntityUtils.consume(entity);
            }
        }
    }

    @Override
    public void close() throws IOException {
        httpClient.close();
    }

    @Override
    public String toString() {
        return "RangeDownloadHTTP{" +
                "url='" + url + '\'' +
                ", length=" + length +
                '}';
    }
}
