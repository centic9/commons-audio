package org.dstadler.audio.buffer;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.undercouch.bson4jackson.BsonFactory;
import de.undercouch.bson4jackson.BsonGenerator;
import org.dstadler.commons.logging.jdk.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.logging.Logger;

/**
 * Use BSON and bson4jackson to persist the current buffer to disk
 * and read it in later again
 *
 * See https://github.com/michel-kraemer/bson4jackson and https://michelkraemer.com/binary-json-with-bson4jackson/
 */
public class BufferPersistence {
    private final static Logger log = LoggerFactory.make();

    private static final BsonFactory fac = new BsonFactory();
    static {
        // bson4jackson can omit the initial length and thus stream out data without full intermediate object
        // in memory. However I am not sure if this is used when using ObjectMapper.writeValue(),
        // simple micro benchmarks were inconclusive
        fac.enable(BsonGenerator.Feature.ENABLE_STREAMING);
    }
    private static final ObjectMapper mapper = new ObjectMapper(fac);

    /**
     * write the data out to the given file
     * @param file The file to write to
     * @param dto The data to serialize out, prepared as DTO to shield from implementation details of the buffer-classes
     *
     * @throws IOException If the file cannot be opened for writing.
     */
    public static void writeBufferToDisk(File file, BufferPersistenceDTO dto) throws IOException {
        log.info("Writing " + dto + " to file " + file);
        try (OutputStream stream = new BufferedOutputStream(new FileOutputStream(file))) {
            mapper.writeValue(stream, dto);
        }
        log.fine("Done writing to " + file);
    }

    /**
     * read the data from the given file and
     * construct the buffer as it was at the time of writing.
     *
     * @param file The file to read the data from
     * @return A DTO for creating the buffer-object in the state as it was at the time of writing
     *
     * @throws IOException If the file does not exist or an error occurs while reading from it.
     */
    public static BufferPersistenceDTO readBufferFromDisk(File file) throws IOException {
        log.info("Reading buffer from file " + file);
        try (InputStream stream = new BufferedInputStream(new FileInputStream(file))) {
            final BufferPersistenceDTO dto = mapper.readValue(stream, BufferPersistenceDTO.class);
            log.info("Read " + dto + " from file " + file);
            return dto;
        }
    }

    /**
     * Check if a valid data-file exists.
     *
     * @param file The file to look for.
     *
     * @return True if the file exists and can be read, false otherwise.
     */
    public static boolean hasBufferOnDisk(File file) {
        return file != null && file.exists() && file.isFile() && file.canRead();
    }

	public static ObjectMapper getMapper() {
		return mapper;
	}
}
