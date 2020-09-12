package org.dstadler.audio.fm4;

import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.commons.lang3.time.FastDateFormat;
import org.junit.Test;

import java.io.IOException;
import java.text.ParseException;
import java.util.List;

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
    }
}
