package org.dstadler.audio.fm4;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.dstadler.commons.http.HttpClientWrapper;
import org.dstadler.commons.logging.jdk.LoggerFactory;

import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class FM4Stream {
    private final static Logger log = LoggerFactory.make();

    private static final String STREAM_URL_BASE = "https://loopstream01.apa.at/?channel=fm4&id=";

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private String programKey;
    private String title;
    private String subtitle;
    private String href;
    private String time;
    private long start;
    private long duration;

    public FM4Stream(JsonNode node) {
        programKey = node.get("programKey").asText();
        title = node.get("title").asText().trim();
        subtitle = node.get("subtitle").asText();
        href = node.get("href").asText();
        time = node.get("startISO").asText();
        start = node.get("start").asLong();
        duration = node.get("end").asLong() - start;
    }

    public String getProgramKey() {
        return programKey;
    }

    public String getTitle() {
        return title;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public String getTime() {
        return time;
    }

    public String getTimeForREST() throws ParseException {
        return DateFormatUtils.ISO_8601_EXTENDED_DATE_FORMAT.format(DateFormatUtils.ISO_8601_EXTENDED_DATETIME_TIME_ZONE_FORMAT.parse(time));
    }

    /**
     * Returns the duration of this stream in seconds.
     *
     * @return Duration in millisseconds
     */
    public long getDuration() {
        return duration;
    }

    /**
     * Return the start timestamp.
     *
     * @return Start timestamp in milliseconds.
     */
    public long getStart() {
        return start;
    }

    public String getShortTime() {
        return getTime().substring(0, 16).
                replace("T", " ");
    }

    public String getShortSummary() {
        return getProgramKey() + " " + getTitle();
    }

    public String getSummary() throws IOException {
        return getProgramKey() +
                " - " + getShortTime() +
                " - " + getTitle() +
                " - " + getStreams();
    }

    public List<String> getStreams() throws IOException {
        log.info("Fetching streams for " + programKey + ": " + href);
        String json = HttpClientWrapper.retrieveData(href);
        JsonNode jsonNode = objectMapper.readTree(json);

        List<String> streams = new ArrayList<>();
        for (JsonNode stream : jsonNode.get("streams")) {
            streams.add(STREAM_URL_BASE + stream.get("loopStreamId").asText());
        }

        log.info("Found " + streams.size() + " streams: " + streams);

        return streams;
    }

    /*
         {
            "isOnDemand" : true,
            "programKey" : "4GP",
            "subtitle" : "<p>Die wöchentliche Radioshow von Gilles Peterson.<br/>FM4-News um 17 Uhr und 18 Uhr (englisch)</p>",
            "id" : 15652,
            "niceTime" : 1575820800000,
            "endISO" : "2019-12-08T19:00:40+01:00",
            "broadcastDay" : 20191208,
            "scheduledStartOffset" : -3600000,
            "niceTimeISO" : "2019-12-08T17:00:00+01:00",
            "scheduledStart" : 1575820800000,
            "niceTimeOffset" : -3600000,
            "program" : "4GP",
            "scheduledEnd" : 1575828000000,
            "title" : "Worldwide Show",
            "endOffset" : -3600000,
            "scheduledStartISO" : "2019-12-08T17:00:00+01:00",
            "scheduledEndISO" : "2019-12-08T19:00:00+01:00",
            "href" : "https://audioapi.orf.at/fm4/api/json/4.0/broadcast/4GP/20191208",
            "station" : "fm4",
            "startOffset" : -3600000,
            "start" : 1575820794000,
            "scheduledEndOffset" : -3600000,
            "ressort" : null,
            "end" : 1575828040000,
            "state" : "C",
            "startISO" : "2019-12-08T16:59:54+01:00",
            "isAdFree" : false,
            "entity" : "Broadcast",
            "isGeoProtected" : false
         },

     */
}
