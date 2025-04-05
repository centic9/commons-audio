package org.dstadler.audio.fm4;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.commons.lang3.time.FastDateFormat;
import org.dstadler.commons.testing.TestHelpers;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.text.ParseException;
import java.util.List;
import java.util.Set;

import static org.dstadler.audio.fm4.FM4Stream.DATETIME_FORMAT;
import static org.dstadler.audio.fm4.FM4Stream.FM4_STREAM_URL_BASE;
import static org.junit.jupiter.api.Assertions.*;

public class FM4StreamTest {
    public static long MIN_START_TIME;
    static {
        try {
            MIN_START_TIME = FastDateFormat.getInstance("yyyy-MM-dd").parse("2019-12-23").getTime();
        } catch (ParseException e) {
            throw new IllegalStateException(e);
        }
    }

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final FM4 fm4 = new FM4();

    @Test
    public void testFM4Stream() throws IOException, ParseException {
        List<FM4Stream> fm4Streams = fm4.fetchStreams(7);

        FM4Stream fm4Stream = fm4Streams.get(0);
        assertNotNull(fm4Stream.getTitle());
        assertNotNull(fm4Stream.getTime());
        assertNotNull(DATETIME_FORMAT.parse(fm4Stream.getTime()));
        assertNotNull(fm4Stream.getTimeForREST());

        assertTrue(fm4Stream.getTimeForREST().matches("\\d{4}-\\d{2}-\\d{2}"),
                "Failed for " + fm4Stream.getTimeForREST());

        String time = DateFormatUtils.ISO_8601_EXTENDED_DATE_FORMAT.format(
                FM4Stream.DATETIME_FORMAT.parse(fm4Stream.getTime()));
        assertEquals(time, fm4Stream.getTimeForREST());

        assertNotNull(fm4Stream.getProgramKey());
        assertNotNull(fm4Stream.getSubtitle());

        assertNotNull(fm4Stream.getShortSummary());
        assertTrue(fm4Stream.getShortSummary().contains(fm4Stream.getProgramKey()));
        assertTrue(fm4Stream.getShortSummary().contains(fm4Stream.getTitle()));

        assertNotNull(fm4Stream.getSummary());
        assertTrue(fm4Stream.getSummary().contains(fm4Stream.getProgramKey()));
        assertTrue(fm4Stream.getSummary().contains(fm4Stream.getShortTime()));
        assertTrue(fm4Stream.getSummary().contains(fm4Stream.getTitle()));

        assertNotNull(fm4Stream.getShortTime());
        assertTrue(fm4Stream.getShortTime().matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}"),
                "Failed for " + fm4Stream.getShortTime());
        assertNotNull(DateFormatUtils.ISO_8601_EXTENDED_DATE_FORMAT.parse(fm4Stream.getShortTime()));
        assertTrue(fm4Stream.getDuration() > 0,
                "Duration should be set, but had: " + fm4Stream.getDuration());
        assertTrue(fm4Stream.getDuration() < 24*60*60*1000,
                "Duration should be below one day, but had: " + fm4Stream.getDuration());
        assertTrue(fm4Stream.getStart() > 0,
                "Start should be set, but had: " + fm4Stream.getStart());
        assertTrue(fm4Stream.getStart() > MIN_START_TIME,
                "Start should be after 2019, but had: " + fm4Stream.getStart());

        assertNotNull(fm4Stream.toString());

        TestHelpers.ToStringTest(fm4Stream);
    }

    @Test
    public void testStreams() throws IOException {
        FM4 fm4 = new FM4();
        List<FM4Stream> fm4Streams = fm4.fetchStreams(7);

        Assumptions.assumeFalse(fm4Streams.isEmpty(), "Should get some streams");

        Set<String> streams = fm4Streams.get(0).getStreams();

        assertNotNull(streams);
        assertFalse(streams.isEmpty());
        assertNotNull(streams.iterator().next());
    }

    @Test
    public void testStreamsMoreDays() throws IOException {
        FM4 fm4 = new FM4();
        List<FM4Stream> fm4Streams7 = fm4.fetchStreams(7);
        List<FM4Stream> fm4Streams14 = fm4.fetchStreams(14);

        assertTrue(fm4Streams14.size() > fm4Streams7.size());
    }

    @Test
    public void testEquals() {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("programKey", "ABC");
        node.put("title", "");
        node.put("subtitle", "");
        node.put("href", "");
        node.put("start", "2024-05-23T22:43:22.034Z");
        node.put("duration", Long.MAX_VALUE);
        node.put("end", 1L);

        FM4Stream fm4 = new FM4Stream(node, FM4_STREAM_URL_BASE);
        FM4Stream equal = new FM4Stream(node, FM4_STREAM_URL_BASE);

        node.put("programKey", "XYZ");
        FM4Stream notEquals = new FM4Stream(node, FM4_STREAM_URL_BASE);

        TestHelpers.EqualsTest(fm4, equal, notEquals);

        node.put("programKey", "ABC");
        node.put("start", "2023-04-01T22:43:22.034Z");
        notEquals = new FM4Stream(node, FM4_STREAM_URL_BASE);
        TestHelpers.EqualsTest(fm4, equal, notEquals);

        TestHelpers.HashCodeTest(fm4, equal);

        assertNotNull(fm4.toString());
        assertNotNull(notEquals.toString());

        TestHelpers.ToStringTest(fm4);
        TestHelpers.ToStringTest(notEquals);
    }
}
