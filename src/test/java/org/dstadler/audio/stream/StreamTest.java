package org.dstadler.audio.stream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.dstadler.audio.player.TempoStrategy;
import org.dstadler.commons.testing.TestHelpers;
import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class StreamTest {
    @Test
    public void test() throws IOException {
        Stream stream = new Stream();
        assertNull(stream.getUrl());
        assertNull(stream.getTempoStrategy());
        assertEquals(1, stream.getTempo(), 0);
        assertNull(stream.getImageUrl());
        assertNull(stream.getName());
        assertNull(stream.getStreamType());
        assertNull(stream.getUser());
        assertNull(stream.getPassword());
        assertNull(stream.getData());
        assertEquals(0, stream.getStartTimestamp());
        assertEquals(0, stream.getDuration());
        TestHelpers.ToStringTest(stream);

        try {
            stream.validate();
            fail("Should throw exception");
        } catch (NullPointerException e) {
            // expected because of missing URL
        }

        stream.setUrl("url1");
        stream.validate();
        TestHelpers.ToStringTest(stream);

        assertEquals("url1", stream.getUrl());
        assertEquals("Always lowercase", "default", stream.getTempoStrategy());
        assertEquals(1, stream.getTempo(), 0);
        assertNull(stream.getImageUrl());
        assertNull(stream.getName());
        assertEquals(Stream.StreamType.live, stream.getStreamType());
        assertNull(stream.getUser());
        TestHelpers.ToStringTest(stream);

        stream.setTempoStrategy("Constant");
        stream.setTempo(1.0f);
        stream.setName("name1");
        stream.setImageUrl("imageUrl");
        stream.setStreamType(Stream.StreamType.download);
        stream.setUser("test");

        assertEquals("url1", stream.getUrl());
        assertEquals("Always lowercase", TempoStrategy.CONSTANT, stream.getTempoStrategy());
        assertEquals(1, stream.getTempo(), 0);
        assertEquals("imageUrl", stream.getImageUrl());
        assertEquals("name1", stream.getName());
        assertEquals(Stream.StreamType.download, stream.getStreamType());
        assertEquals("test", stream.getUser());
        TestHelpers.ToStringTest(stream);

        stream.validate();

        assertEquals("url1", stream.getUrl());
        assertEquals("Always lowercase", TempoStrategy.CONSTANT, stream.getTempoStrategy());
        assertEquals(1, stream.getTempo(), 0);
        assertEquals("imageUrl", stream.getImageUrl());
        assertEquals("name1", stream.getName());
        assertEquals(Stream.StreamType.download, stream.getStreamType());
        assertEquals("test", stream.getUser());
        TestHelpers.ToStringTest(stream);

        stream.setTempoStrategy(TempoStrategy.CONSTANT_PREFIX + "2.3");
        try {
            stream.validate();
            fail("Expect an exception because specifying tempo here is not expected");
        } catch (IllegalStateException e) {
            // not valid
        }

        assertEquals(TempoStrategy.CONSTANT_PREFIX + "2.3", stream.getTempoStrategy());
        TestHelpers.ToStringTest(stream);

        stream.setUser("");
        assertEquals("", stream.getUser());
        assertNull(stream.getPassword());

        stream.setStartTimestamp(1L);
        stream.setDuration(2L);
        assertEquals(1L, stream.getStartTimestamp());
        assertEquals(2L, stream.getDuration());

        stream.setData("123");
        assertEquals("123", stream.getData());

        stream.setStreamType(Stream.StreamType.favourite);
        assertEquals(Stream.StreamType.favourite, stream.getStreamType());
    }

    @Test
    public void testCredentials() throws IOException {
        Stream stream = new Stream();

        stream.setUser("someuser");
        try {
            assertNull(stream.getPassword());

            TestHelpers.ToStringTest(stream);
        } catch (IOException e) {
            Assume.assumeNoException("Credentials file not available, cannot test for password",
                    e);
        }
    }

    @Test
    public void testCredentialsDummyFile() throws IOException {
		final File credentialsFile = new File("credentials.properties");
		Assume.assumeFalse("Can only run this test when no credentials.properties exists before",
				credentialsFile.exists());

        Stream stream = new Stream();
        stream.setUser("someuser");

		// expect a FileNotFoundException
		assertThrows(IOException.class,
				stream::getPassword);

		try {
			FileUtils.writeStringToFile(credentialsFile, "password.someuser=pass1", "UTF-8");

			assertEquals("Should be able to read password now",
					"pass1", stream.getPassword());
		} finally {
			assertTrue(credentialsFile.delete());
		}

		TestHelpers.ToStringTest(stream);
    }

    @Test
    public void testTempoStrategy() {
        Stream stream = new Stream();
        stream.setUrl("http://localhost");

        assertNull(stream.getTempoStrategy());
        assertEquals(1.0f, stream.getTempo(), 0.001f);

        stream.setTempoStrategy("default");
        assertEquals("default", stream.getTempoStrategy());
        assertEquals(1.0f, stream.getTempo(), 0.001f);

        stream.setTempo(2.0f);
        assertEquals(2.0f, stream.getTempo(), 0.001f);
    }

    @Test
    public void testMetaData() {
        Stream stream = new Stream();

        Function<Double, Pair<String, Long>> fun = stream.getMetaDataFun();
        assertNotNull(fun);
        Pair<String, Long> meta = fun.apply(1.0);
        assertEquals(0L, meta.getValue().longValue());

        stream.setStartTimestamp(100);
        stream.setDuration(200);
        meta = fun.apply(0.9);
        assertEquals(280L, meta.getValue().longValue());
    }

    @Test
    public void testEmpty() {
        assertNotNull(Stream.EMPTY);
        assertEquals("", Stream.EMPTY.getName());
    }
}
