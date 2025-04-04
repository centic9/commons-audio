package org.dstadler.audio.fm4;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.HexDump;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.commons.lang3.time.FastDateFormat;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.util.EntityUtils;
import org.dstadler.commons.http.HttpClientWrapper;
import org.dstadler.commons.logging.jdk.LoggerFactory;
import org.dstadler.commons.net.UrlUtils;
import org.dstadler.commons.testing.MockRESTServer;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import static org.dstadler.audio.fm4.FM4.RESPONSE_INVALID_STREAM;
import static org.dstadler.audio.fm4.FM4Stream.FM4_STREAM_URL_BASE;
import static org.junit.jupiter.api.Assertions.*;

public class FM4Test {
    private final static Logger log = LoggerFactory.make();

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final FM4 fm4 = new FM4();

    @Test
    public void testFetch() throws IOException, ParseException {
        List<FM4Stream> fm4Streams = fm4.fetchStreams(7);

        assertNotNull(fm4Streams);
        assertFalse(fm4Streams.isEmpty());

        // check resulting streams are sane
        for (FM4Stream stream : fm4Streams) {
            assertTrue(stream.getDuration() > 60_000);
            assertTrue(stream.getStart() > System.currentTimeMillis() - (35L*24*60*60*1000));
            assertNotNull(stream.getProgramKey());
            assertNotNull(stream.getTitle());
            assertNotNull(stream.getShortTime());
            assertNotNull(DateFormatUtils.ISO_8601_EXTENDED_DATE_FORMAT.parse(stream.getShortTime()));
        }
    }

    @Test
    public void testFetchNoDuplicates() throws IOException {
        List<FM4Stream> fm4Streams = fm4.fetchStreams(14);
        Set<String> seenStreams = new HashSet<>();
        for (FM4Stream stream : fm4Streams) {
            assertTrue(seenStreams.add(stream.getShortTime()),
                    "Had duplicate for " + stream);
        }
    }

    @Test
    public void testFetchTooManyDays() {
        assertThrows(IOException.class,
                () -> fm4.fetchStreams(90));
    }

    @Test
    public void testFetchForDate() throws IOException, ParseException {
        String date = FastDateFormat.getInstance("yyyyMMdd").format(DateUtils.addDays(new Date(), -3));
        List<FM4Stream> fm4Streams = fm4.fetchStreams(date);

        assertNotNull(fm4Streams);
        assertFalse(fm4Streams.isEmpty());

        // check resulting streams are sane
        for (FM4Stream stream : fm4Streams) {
            assertTrue(stream.getDuration() > 60_000);
            assertTrue(stream.getStart() > System.currentTimeMillis() - (35L*24*60*60*1000));
            assertNotNull(stream.getProgramKey());
            assertNotNull(stream.getTitle());
            assertNotNull(stream.getShortTime());
            assertNotNull(DateFormatUtils.ISO_8601_EXTENDED_DATE_FORMAT.parse(stream.getShortTime()));
        }
    }

    @Disabled("Fetches streams for all programs, this runs for some time, so disable for CI")
    @Test
    public void testFetchAllStreams() throws IOException {
        List<FM4Stream> fm4Streams = fm4.fetchStreams(14);

        assertNotNull(fm4Streams);
        assertFalse(fm4Streams.isEmpty());

        // check resulting streams are sane
        for (FM4Stream stream : fm4Streams) {
            assertNotNull(stream.getStreams());
        }
    }

    @Test
    public void testFetchRadioOOE() throws IOException {
        List<FM4Stream> ooeStreams = fm4.fetchStreams(FM4.OOE_API_URL, FM4Stream.OOE_STREAM_URL_BASE);

        assertNotNull(ooeStreams);
        assertFalse(ooeStreams.isEmpty());

        FM4Stream stream = ooeStreams.iterator().next();
        assertNotNull(stream.getProgramKey());
        assertNotNull(stream.getStreams());
        assertNotNull(stream.getShortSummary());
        assertNotNull(stream.getShortTime());
        assertNotNull(stream.getSummary());
        assertNotNull(stream.getTitle());
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

    @Disabled("This starts an actual download and thus should not run for every CI run")
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
            "            \"duration\": 3607000," +
            "            \"start\": \"2024-02-15T03:59:50.000Z\"," +
            "            \"end\": \"2024-02-15T04:59:57.000Z\"" +
            "         }";

    // template for the download-url Stream with replace-url
    private static final String STREAM_JSON = "{\"href\":\"$url\"}";

    @Disabled("This test is not finished, we could not inject the stream-URL properly here...")
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

    @Test
    public void testDownloadStreamInvalidURL() throws JsonProcessingException {
        JsonNode jsonNode = objectMapper.readTree(JSON.replace("$url", "http://invalid/url"));
        assertThrows(UnknownHostException.class, () ->
                fm4.downloadStream(new FM4Stream(jsonNode, FM4_STREAM_URL_BASE), new File("/tmp")));
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
                    log.info(stream.toString(StandardCharsets.UTF_8));

                    final String content = new String(bytes, 0, 31, StandardCharsets.UTF_8);
                    assertTrue(RESPONSE_INVALID_STREAM.contains(content.replace("\n", "").replace("\r", "")),
                            "Fetched " + url + ", had: " + content);
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
