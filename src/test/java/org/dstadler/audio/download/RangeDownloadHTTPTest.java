package org.dstadler.audio.download;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.util.EntityUtils;
import org.dstadler.commons.http.HttpClientWrapper;
import org.dstadler.commons.http.NanoHTTPD;
import org.dstadler.commons.testing.MemoryLeakVerifier;
import org.dstadler.commons.testing.MockRESTServer;
import org.dstadler.commons.testing.TestHelpers;
import org.junit.After;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class RangeDownloadHTTPTest {
    private static final String SAMPLE_URL = "https://www.dstadler.org/DominikStadler2013.png";
    private static final long EXPECTED_LENGTH = 841641;

    private static final String FM4_URL = "https://loopstreamfm4.apa.at/?channel=fm4&id=2019-12-21_2200_tl_54_7DaysSat20_90331.mp3";

    private final MemoryLeakVerifier verifier = new MemoryLeakVerifier();

    @After
    public void tearDown() {
        verifier.assertGarbageCollected();
    }

    @Test
    public void testLength() throws Exception {
        try (RangeDownload download = new RangeDownloadHTTP(SAMPLE_URL, "", null)) {
            assertEquals(EXPECTED_LENGTH, download.getLength());

            verifier.addObject(download);
        }
    }

    @Test
    public void testLengthClosed() throws IOException {
        RangeDownload download = new RangeDownloadHTTP(SAMPLE_URL, "", null);
        download.close();

        assertEquals(EXPECTED_LENGTH, download.getLength());

        verifier.addObject(download);
    }

    @Test(expected = IllegalStateException.class)
    public void testClosed() throws IOException {
        RangeDownload download = new RangeDownloadHTTP(SAMPLE_URL, "", null);
        download.close();

        verifier.addObject(download);

        download.readRange(0, 1);
    }

    @Test
    public void testReadRangeDstadlerOrg() {
        //noinspection resource
        assertThrows("Expect an exception because the endpoint does not support Accept-Range",
                IllegalStateException.class,
                () -> new RangeDownloadHTTP("https://www.dstadler.org/", "", null));
    }

    @Test
    public void testReadRange() throws Exception {
        try (RangeDownload download = new RangeDownloadHTTP(SAMPLE_URL, "", null)) {
            byte[] bytes = download.readRange(0, 200);
            assertEquals(200, bytes.length);
            assertEquals("PNG", new String(Arrays.copyOfRange(bytes, 1, 4)));

            verifier.addObject(download);
        }
    }

    @Ignore("Depends on an URL that will become unavailable")
    @Test
    public void testFM4() throws Exception {
        try (RangeDownload download = new RangeDownloadHTTP(FM4_URL, "", null)) {
            byte[] bytes = download.readRange(0, 200);
            assertEquals(200, bytes.length);
            byte[] bytes2 = download.readRange(200, 200);
            assertEquals(200, bytes.length);
            assertFalse(Arrays.equals(bytes, bytes2));
            byte[] bytes3 = download.readRange(200, 200);
            assertArrayEquals(bytes2, bytes3);

            //assertEquals("PNG", new String(Arrays.copyOfRange(bytes, 1, 4)));

            verifier.addObject(download);
        }
    }

    @Ignore("Download URL will become unavailable soon")
    @Test
    public void testFM4Download() throws IOException {
        assertNotNull(Thread.currentThread().getContextClassLoader().getResource("log4j.properties"));

        File destination = new File("/tmp/FM4.mp3");
        HttpClientWrapper.downloadFile(
                FM4_URL,
                destination, 60_000);
        assertTrue(destination.exists());
    }

    @Ignore("Download URL will become unavailable soon")
    @Test
    public void testFM4DownloadRange() throws IOException {
        File destination = new File("/tmp/FM4.mp3");
        String url = FM4_URL;
        try (HttpClientWrapper client = new HttpClientWrapper(60_000)) {
            final HttpUriRequest httpGet = new HttpGet(url);

            httpGet.setHeader("Range", "bytes=0-199");

            try (CloseableHttpResponse response = client.getHttpClient().execute(httpGet)) {
                HttpEntity entity = HttpClientWrapper.checkAndFetch(response, url);
                try {
                    FileUtils.copyInputStreamToFile(entity.getContent(), destination);
                } finally {
                    // ensure all content is taken out to free resources
                    EntityUtils.consume(entity);
                }
            }
        }

        assertTrue(destination.exists());
    }

    @Ignore("Download URL will become unavailable soon")
    @Test
    public void testDownloadFM4Hangs() throws IOException {
        String url = FM4_URL;

        final HttpUriRequest httpGet = new HttpGet(url);

        // Range: bytes=0-1023
        //httpGet.setHeader("Range", "bytes=" + start + "-" + (start + size - 1));
        httpGet.setHeader("Range", "bytes=0-199");

        try (HttpClientWrapper client = new HttpClientWrapper(60_000)) {
            try (CloseableHttpResponse response = client.getHttpClient().execute(httpGet)) {
                HttpEntity entity = HttpClientWrapper.checkAndFetch(response, url);
                try {
                    byte[] bytes = new byte[200];
                    IOUtils.read(entity.getContent(), bytes);
                } finally {
                    // ensure all content is taken out to free resources
                    EntityUtils.consume(entity);
                }
            }
        }
    }

    @Test
    public void testToString() throws Exception {
        try (RangeDownload download = new RangeDownloadHTTP(SAMPLE_URL, "", null)) {
            TestHelpers.ToStringTest(download);

            assertTrue("Had: " + download, download.toString().contains(Long.toString(EXPECTED_LENGTH)));

            verifier.addObject(download);
        }
    }

    @Test
    public void testWithUserAndAuthResponse() throws IOException {
        try (MockRESTServer server = new MockRESTServer(() -> {
            NanoHTTPD.Response response = new NanoHTTPD.Response("200", "text/html",
                    "");
            response.addHeader("Accept-Ranges", "bytes");
            response.addHeader("Content-Length", "132");
            return response;
        })) {
            try (RangeDownload download = new RangeDownloadHTTP("http://localhost:" + server.getPort(),
                    "user123", "pwd")) {
                assertEquals(132, download.getLength());

                verifier.addObject(download);
            }
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void readWithStartBeyondLength() throws IOException {
        try (RangeDownload download = new RangeDownloadHTTP(SAMPLE_URL, "", null)) {
            verifier.addObject(download);

            download.readRange(99999999L, 200);
        }
    }

    @Test
    public void readBeyondLength() throws IOException {
        try (RangeDownload download = new RangeDownloadHTTP(SAMPLE_URL, "", null)) {
            verifier.addObject(download);

            byte[] bytes = download.readRange(download.getLength() - 100, 200);
            assertEquals(100, bytes.length);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void readWithInvalidSizeAt0() throws IOException {
        try (RangeDownload download = new RangeDownloadHTTP(SAMPLE_URL, "", null)) {
            verifier.addObject(download);

            download.readRange(0, 0);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void readWithInvalidSizeAt100() throws IOException {
        try (RangeDownload download = new RangeDownloadHTTP(SAMPLE_URL, "", null)) {
            verifier.addObject(download);

            download.readRange(100, 0);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void readWithInvalidSizeAtMinus100() throws IOException {
        try (RangeDownload download = new RangeDownloadHTTP(SAMPLE_URL, "", null)) {
            verifier.addObject(download);

            download.readRange(-100, 10);
        }
    }
}
