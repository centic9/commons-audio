package org.dstadler.audio.buffer;

import com.google.common.base.Preconditions;

import java.util.Arrays;

/**
 * Simple data object for holding a chunk of audio data
 * together with accompanying information like song-name
 * and the timestamp when this chunk of audio was actually
 * "on air".
 */
public class Chunk {
    public static final int CHUNK_SIZE = 16384;

    private final byte[] data;
    private final String metaData;
    private final long timestamp;

    /**
     * Default constructor only used for serialization
     */
    @SuppressWarnings("unused")
    private Chunk() {
        this.data = null;
        this.metaData = null;
        this.timestamp = 0;
    }

    public Chunk(byte[] data, String metaData, long timestamp) {
        this.data = Preconditions.checkNotNull(data, "Data cannot be null");
        this.metaData= Preconditions.checkNotNull(metaData, "MetaData cannot be null");
        this.timestamp = timestamp;
    }

    public byte[] getData() {
        return data;
    }

    public String getMetaData() {
        return metaData;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public long size() {
        return data.length;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        Chunk chunk = (Chunk) o;

        return Arrays.equals(data, chunk.data);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(data);
    }

    @Override
    public String toString() {
        return "Chunk{" +
                "data=" + data.length + " bytes" +
                ", metaData='" + metaData + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}
