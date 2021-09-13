package org.dstadler.audio.fm4;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.commons.lang3.time.FastDateFormat;
import org.dstadler.commons.testing.TestHelpers;
import org.junit.Assume;
import org.junit.Test;

import java.io.IOException;
import java.text.ParseException;
import java.util.List;

import static org.dstadler.audio.fm4.FM4Stream.FM4_STREAM_URL_BASE;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

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
        List<FM4Stream> fm4Streams = fm4.fetchStreams();

        FM4Stream fm4Stream = fm4Streams.get(0);
        assertNotNull(fm4Stream.getTitle());
        assertNotNull(fm4Stream.getTime());
        assertNotNull(DateFormatUtils.ISO_8601_EXTENDED_DATETIME_TIME_ZONE_FORMAT.parse(fm4Stream.getTime()));
        assertNotNull(fm4Stream.getTimeForREST());
        assertNotNull(fm4Stream.getProgramKey());
        assertNotNull(fm4Stream.getSubtitle());
        assertNotNull(fm4Stream.getShortSummary());
        assertNotNull(fm4Stream.getSummary());
        assertNotNull(fm4Stream.getShortTime());
        assertNotNull(DateFormatUtils.ISO_8601_EXTENDED_DATE_FORMAT.parse(fm4Stream.getShortTime()));
        assertTrue("Duration should be set, but had: " + fm4Stream.getDuration(),
                fm4Stream.getDuration() > 0);
        assertTrue("Duration should be below one day, but had: " + fm4Stream.getDuration(),
                fm4Stream.getDuration() < 24*60*60*1000);
        assertTrue("Start should be set, but had: " + fm4Stream.getStart(),
                fm4Stream.getStart() > 0);
        assertTrue("Start should be after 2019, but had: " + fm4Stream.getStart(),
                fm4Stream.getStart() > MIN_START_TIME);

        assertNotNull(fm4Stream.toString());
    }

    @Test
    public void testStreams() throws IOException {
        FM4 fm4 = new FM4();
        List<FM4Stream> fm4Streams = fm4.fetchStreams();

        Assume.assumeFalse("Should get some streams", fm4Streams.isEmpty());

        List<String> streams = fm4Streams.get(0).getStreams();

        assertNotNull(streams);
        assertFalse(streams.isEmpty());
        assertNotNull(streams.get(0));
    }

    @Test
    public void testEquals() {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("programKey", "ABC");
        node.put("title", "");
        node.put("subtitle", "");
        node.put("href", "");
        node.put("startISO", "");
        node.put("start", 1L);
        node.put("end", 1L);

        FM4Stream fm4 = new FM4Stream(node, FM4_STREAM_URL_BASE);
        FM4Stream equal = new FM4Stream(node, FM4_STREAM_URL_BASE);

        node.put("programKey", "XYZ");
        FM4Stream notEquals = new FM4Stream(node, FM4_STREAM_URL_BASE);

        TestHelpers.EqualsTest(fm4, equal, notEquals);

        node.put("programKey", "ABC");
        node.put("start", 2L);
        notEquals = new FM4Stream(node, FM4_STREAM_URL_BASE);
        TestHelpers.EqualsTest(fm4, equal, notEquals);

        TestHelpers.HashCodeTest(fm4, equal);

        assertNotNull(fm4.toString());
        assertNotNull(notEquals.toString());
    }
}
