package org.dstadler.audio.fm4;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.dstadler.commons.http.HttpClientWrapper;
import org.dstadler.commons.logging.jdk.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static org.dstadler.audio.fm4.FM4Stream.DATETIME_FORMAT;
import static org.dstadler.audio.fm4.FM4Stream.FM4_STREAM_URL_BASE;

/**
 * Fetch information about which streams are currently
 * available on the FM4 streaming service and allow
 * to download data to local files.
 */
public class FM4 {
    private final static Logger log = LoggerFactory.make();

    public static final String FM4_STREAM_URL = "https://orf-live.ors-shoutcast.at/fm4-q2a";

    public static final String FM4_API_URL = "https://audioapi.orf.at/fm4/json/5.0/broadcasts?_o=fm4.orf.at";
    public static final String OOE_API_URL = "https://audioapi.orf.at/ooe/json/5.0/broadcasts?_o=ooe.orf.at";

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Fetch default list of previous streams, usually returns data for the last 7 days
     * @return A list of {@link FM4Stream}
     * @throws IOException If fetching data via the FM4 REST API fails
     */
    public List<FM4Stream> fetchStreams() throws IOException {
        return fetchStreams(FM4_API_URL, FM4_STREAM_URL_BASE);
    }

    /**
     * Fetch a list of previous streams for a given day.
     *
     * @param date A date in the form of yyyyMMdd, usually data is available for the last 30 days
     * @return A list of {@link FM4Stream}
     * @throws IOException If fetching data via the FM4 REST API fails
     */
    public List<FM4Stream> fetchStreams(String date) throws IOException {
        // https://audioapi.orf.at/fm4/api/json/5.0/broadcasts/20240131?_o=sound.orf.at
        String apiUrl = FM4_API_URL.replace("/broadcasts?", "/broadcasts/" + date + "?");

        JsonNode jsonNode = parsAPI(apiUrl);

        List<FM4Stream> streams = new ArrayList<>();
        for (JsonNode node : jsonNode) {
            streams.add(new FM4Stream(node, FM4_STREAM_URL_BASE));
        }
        return streams;
    }

    /**
     * Fetch default list of previous streams via the given REST API URLs.
     * This usually returns data for the last 7 days
     *
     * @param apiUrl The REST API URL to use
     * @param streamUrlBase The URL to use for downloading streams
     * @return A list of {@link FM4Stream}
     * @throws IOException If fetching data via the FM4 REST API fails
     */
    public List<FM4Stream> fetchStreams(String apiUrl, String streamUrlBase) throws IOException {
        JsonNode jsonNode = parsAPI(apiUrl);

        List<FM4Stream> streams = new ArrayList<>();
        for (JsonNode node : jsonNode) {
            for (JsonNode broadcast : node.get("broadcasts")) {
                streams.add(new FM4Stream(broadcast, streamUrlBase));
            }
        }
        return streams;
    }

    private static JsonNode parsAPI(String apiUrl) throws IOException {
        final String json;
        try {
            // fetch stream
            json = HttpClientWrapper.retrieveData(apiUrl);
        } catch (IOException e) {
            throw new IOException("While reading from: " + apiUrl, e);
        }

        return objectMapper.readTree(json).get("payload");
    }

    /**
     * Fetch streams and filter them by the given programKey and also remove
     * future instances of streams.
     *
     * @param programKey The FM4 program-key, e.g. "4MG"
     * @return A list of {@link FM4Stream}
     * @throws IOException If fetching data via the FM4 REST API fails
     */
    public List<FM4Stream> filterStreams(String programKey) throws IOException {
        List<FM4Stream> streams = fetchStreams();

        return streams.stream().
                // filter out future shows
                filter(fm4Stream -> {
                    try {
                        Date time = DATETIME_FORMAT.parse(fm4Stream.getTime());

                        // exclude future shows
                        return time.before(new Date());
                    } catch (ParseException e) {
                        throw new IllegalStateException(e);
                    }
                }).

                // match by program-key
                filter(fm4Stream -> fm4Stream.getProgramKey().equalsIgnoreCase(programKey)).

                // collect into a list
                collect(Collectors.toList());
    }

    /**
     * Download the given stream to a MP3 file.
     *
     * @param stream The FM4 stream to download
     * @param downloadDir Where to store the resulting file. It will be named after the title of the stream.
     * @throws IOException If fetching data via the FM4 REST API fails
     */
    public void downloadStream(FM4Stream stream, File downloadDir) throws IOException {
        int count = 0;
        for (String url : stream.getStreams()) {
            File destination = new File(downloadDir, stream.getTitle() + " " +
                    stream.getShortTime().replace(":", "_") + "_" + count + ".mp3");

            log.info("Downloading " + stream.getProgramKey() + " - " + stream.getShortTime() + " - " +
                    stream.getTitle() + " to URL: " + url + " to " + destination);

            HttpClientWrapper.downloadFile(url, destination, 120_000);

            count++;
        }
    }
}
