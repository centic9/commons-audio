package org.dstadler.audio.fm4;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.commons.lang3.time.FastDateFormat;
import org.dstadler.commons.http.HttpClientWrapper;
import org.dstadler.commons.logging.jdk.LoggerFactory;

import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Object holding information about one stream which
 * is available from the FM4 online stream service.
 */
public class FM4Stream {
    private final static Logger log = LoggerFactory.make();

    public static final String FM4_STREAM_URL_BASE = "https://loopstreamfm4.apa.at/?channel=fm4&id=";
    public static final String OOE_STREAM_URL_BASE = "https://loopstreamoe2o.apa.at/?channel=oe2o&id=";

    public static final FastDateFormat DATETIME_FORMAT = FastDateFormat.getInstance("yyyy-MM-dd'T'HH:mm:ss.SSSZZ");

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final String programKey;
    private final String title;
    private final String subtitle;
    private final String href;
    private final String time;
    private final long start;
    private final long duration;

    private final String streamUrlBase;

    public FM4Stream(JsonNode node, String streamUrlBase) {
        programKey = node.get("programKey").asText();
        title = node.get("title").asText().trim();
        subtitle = node.get("subtitle").asText();
        href = node.get("href").asText();
        time = node.get("start").asText();
        try {
            start = DATETIME_FORMAT.parse(time).getTime();
        } catch (ParseException e) {
            throw new IllegalStateException(e);
        }
        duration = node.get("duration").asLong();

        this.streamUrlBase = streamUrlBase;
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
        // return the date-part
        return DateFormatUtils.ISO_8601_EXTENDED_DATE_FORMAT.format(
                DATETIME_FORMAT.parse(time));
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
        // return time-string up to minutes
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
        JsonNode jsonNode = objectMapper.readTree(json).get("payload");

        List<String> streams = new ArrayList<>();
        for (JsonNode stream : jsonNode.get("streams")) {
            streams.add(streamUrlBase + stream.get("loopStreamId").asText());
        }

        log.info("Found " + streams.size() + " streams: " + streams);

        return streams;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        FM4Stream fm4Stream = (FM4Stream) o;

        if (start != fm4Stream.start) return false;
        return programKey.equals(fm4Stream.programKey);
    }

    @Override
    public int hashCode() {
        int result = programKey.hashCode();
        result = 31 * result + (int) (start ^ (start >>> 32));
        return result;
    }

    @Override
    public String toString() {
        return "FM4Stream{" +
                "programKey='" + programKey + '\'' +
                ", title='" + title + '\'' +
                ", href='" + href + '\'' +
                ", time='" + time + '\'' +
                ", start=" + start +
                ", duration=" + duration +
                '}';
    }
}
