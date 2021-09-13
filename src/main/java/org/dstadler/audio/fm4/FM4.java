package org.dstadler.audio.fm4;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.time.DateFormatUtils;
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

import static org.dstadler.audio.fm4.FM4Stream.FM4_STREAM_URL_BASE;

public class FM4 {
    private final static Logger log = LoggerFactory.make();

    // Broken: http://mp3stream1.apasf.apa.at:8000
    public static final String FM4_STREAM_URL = "https://orf-live.ors-shoutcast.at/fm4-q2a";

    public static final String FM4_API_URL = "https://audioapi.orf.at/fm4/json/4.0/broadcasts?_o=fm4.orf.at";
    public static final String OOE_API_URL = "https://audioapi.orf.at/ooe/json/4.0/broadcasts?_o=ooe.orf.at";

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public List<FM4Stream> fetchStreams() throws IOException {
        return fetchStreams(FM4_API_URL, FM4_STREAM_URL_BASE);
    }

    public List<FM4Stream> fetchStreams(String apiUrl, String streamUrlBase) throws IOException {
        final String json;
        try {
            // fetch stream
            json = HttpClientWrapper.retrieveData(apiUrl);
        } catch (IOException e) {
            throw new IOException("While reading from: " + apiUrl, e);
        }

        JsonNode jsonNode = objectMapper.readTree(json);

        /*{
      "dateOffset" : -3600000,
      "broadcasts" : [
         {
            ...

         */

        List<FM4Stream> streams = new ArrayList<>();
        for (JsonNode node : jsonNode) {
            for (JsonNode broadcast : node.get("broadcasts")) {
                streams.add(new FM4Stream(broadcast, streamUrlBase));
            }
        }
        return streams;
    }

    public List<FM4Stream> filterStreams(String programKey) throws IOException {
        List<FM4Stream> streams = fetchStreams();

        return streams.stream().
                // filter out future shows
                filter(fm4Stream -> {
                    try {
                        Date time = DateFormatUtils.ISO_8601_EXTENDED_DATETIME_TIME_ZONE_FORMAT.parse(fm4Stream.getTime());

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
