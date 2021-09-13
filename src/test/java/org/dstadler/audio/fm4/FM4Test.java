package org.dstadler.audio.fm4;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.HexDump;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.util.EntityUtils;
import org.dstadler.commons.http.HttpClientWrapper;
import org.dstadler.commons.net.UrlUtils;
import org.dstadler.commons.testing.MockRESTServer;
import org.junit.Ignore;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.dstadler.audio.fm4.FM4Stream.FM4_STREAM_URL_BASE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class FM4Test {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final FM4 fm4 = new FM4();

    @Test
    public void testFetch() throws IOException {
        List<FM4Stream> fm4Streams = fm4.fetchStreams();

        assertNotNull(fm4Streams);
        assertFalse(fm4Streams.isEmpty());
    }

    @Test
    public void testFilter() throws IOException {
        List<FM4Stream> fm4Streams = fm4.filterStreams("4MO");

        assertNotNull(fm4Streams);
        assertFalse(fm4Streams.isEmpty());
    }

    @Test
    public void testFilterEmpty() throws IOException {
        List<FM4Stream> fm4Streams = fm4.filterStreams("NOT_FOUND");

        assertNotNull(fm4Streams);
        assertTrue(fm4Streams.isEmpty());
    }

    @Ignore("This starts an actual download and thus should not run for every CI run")
    @Test
    public void testDownloadStream() throws IOException {
        List<FM4Stream> fm4Streams = fm4.filterStreams("4MO");
        assertNotNull(fm4Streams);
        assertFalse(fm4Streams.isEmpty());

        fm4.downloadStream(fm4Streams.get(0), new File("/tmp"));
    }

    // template for the Stream with replace-url
    private static final String JSON = "{" +
            "            \"programKey\" : \"4GP\"," +
            "            \"subtitle\" : \"<p>Die wöchentliche Radioshow von Gilles Peterson.<br/>FM4-News um 17 Uhr und 18 Uhr (englisch)</p>\"," +
            "            \"program\" : \"4GP\"," +
            "            \"title\" : \"Worldwide Show\"," +
            "            \"href\" : \"$url\"," +
            "            \"start\" : 1575820794000," +
            "            \"end\" : 1575828040000," +
            "            \"startISO\" : \"2019-12-08T16:59:54+01:00\"" +
            "         }";

    // template for the download-url Stream with replace-url
    private static final String STREAM_JSON = "{\"href\":\"$url\"}";

    @Ignore("This test is not finished, we could not inject the stream-URL properly here...")
    @Test
    public void testDownloadStreamOtherURL() throws IOException {
        // Mock the URL-stream download content on a local REST Server
        try (MockRESTServer server = new MockRESTServer("200", "application/json", STREAM_JSON.replace("$url", "https://file-examples.com/wp-content/uploads/2017/11/file_example_MP3_700KB.mp3"))) {

            // inject the local URL as location of the stream-details with download URL
            JsonNode jsonNode = objectMapper.readTree(JSON.replace("$url", "http://localhost:" + server.getPort()));

            // finally download the small file
            File tempDir = File.createTempFile("FM4Test", ".dir");
            try {
                assertTrue(tempDir.delete());
                fm4.downloadStream(new FM4Stream(jsonNode, FM4_STREAM_URL_BASE), tempDir);
            } finally {
                FileUtils.deleteDirectory(tempDir);
            }
        }
    }

    @Test(expected = UnknownHostException.class)
    public void testDownloadStreamInvalidURL() throws IOException {
        JsonNode jsonNode = objectMapper.readTree(JSON.replace("$url", "http://invalid/url"));
        fm4.downloadStream(new FM4Stream(jsonNode, FM4_STREAM_URL_BASE), new File("/tmp"));
    }

    @Test
    public void testFM4StreamURL() {
        assertNull(UrlUtils.getAccessError(FM4.FM4_STREAM_URL, true, false, 1_000));
    }

    /**
     * Verify that the FM4 stream server continues to respond this strange error message
     * instead of a valid HTTP status code when an invalid stream-url is used.
     *
     * If this changes, we should adjust the handling of this error message in some
     * places.
     */
    @Test
    public void testNotFoundIsHTTP200PlusMessage() throws IOException {
        try (HttpClientWrapper httpClient = new HttpClientWrapper(5_000)) {
            String url = "https://loopstreamfm4.apa.at/?channel=fm4&id=2021-01-09_1659_tl_54_7DaysSat14_111346.mp3";
            final HttpUriRequest httpGet = new HttpGet(url);

            // Range: bytes=0-40
            httpGet.setHeader("Range", "bytes=0-40");

            try (CloseableHttpResponse response = httpClient.getHttpClient().execute(httpGet)) {
                HttpEntity entity = HttpClientWrapper.checkAndFetch(response, url);
                try {
                    byte[] bytes = new byte[41];
                    IOUtils.read(entity.getContent(), bytes);
                    ByteArrayOutputStream stream = new ByteArrayOutputStream();
                    HexDump.dump(bytes, 0, stream, 0);
                    System.out.println(stream.toString("UTF-8"));

                    assertEquals("Could not find any loopchannels",
                            new String(bytes, 0, 31, StandardCharsets.UTF_8));
                } finally {
                    // ensure all content is taken out to free resources
                    EntityUtils.consume(entity);
                }
            } catch (SocketTimeoutException e) {
                // sometimes the server reports with timeout
            }
        }
    }
}
