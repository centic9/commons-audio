package org.dstadler.audio.buffer;

import de.undercouch.bson4jackson.BsonFactory;
import de.undercouch.bson4jackson.BsonGenerator;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Bson4JacksonTest {
    public static class DTO {
        // don't make this final!
        @SuppressWarnings("FieldMayBeFinal")
        private String dataDir;

        private DTO() {
            this.dataDir = null;
        }

        private DTO(String dataDir) {
            this.dataDir = dataDir;
        }
        public String getDataDir() {
            return dataDir;
        }

        @Override
        public String toString() {
            return "DTO{" +
                    (dataDir == null ? "" : ", dataDir=" + dataDir) +
                    '}';
        }

        public static DTO.Builder builder() {
            return new DTO.Builder();
        }

        public static class Builder {
            private String dataDir;

            private Builder() {
                // no-op
            }

            public DTO.Builder data(String dataDir) {
                this.dataDir = dataDir;

                return this;
            }

            public DTO build() {
                return new DTO(dataDir);
            }
        }
    }

    @Test
    void testBSON() throws IOException {
        final BsonFactory fac = new BsonFactory();
        // bson4jackson can omit the initial length and thus stream out data without full intermediate object
        // in memory. However I am not sure if this is used when using ObjectMapper.writeValue(),
        // simple micro benchmarks were inconclusive
        fac.enable(BsonGenerator.Feature.ENABLE_STREAMING);
        final ObjectMapper mapper = JsonMapper.builder(fac).build();

        File file = File.createTempFile("BufferPersistence", ".bson");
        assertTrue(file.delete());
        try (OutputStream stream = new BufferedOutputStream(new FileOutputStream(file))) {
            DTO dto = DTO.builder().data("value1").build();

            mapper.writeValue(stream, dto);
        }

        try (InputStream stream = new BufferedInputStream(new FileInputStream(file))) {
            DTO dto = mapper.readValue(stream, DTO.class);
            assertEquals("value1", dto.getDataDir());
        }
    }
}
