package org.dstadler.audio.stream;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.Preconditions;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.dstadler.audio.player.TempoStrategy;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;
import java.util.function.Function;

/**
 * DTO for a single stream that can be played
 * together with additional information/configuration
 * like images, type of stream, ...
 */
public class Stream {
    public enum StreamType {
        live, download
    }

    public static final Stream EMPTY = new Stream();
    static {
        EMPTY.setName("");
    }

    private String name;
    private String url;
    private String imageUrl;
    private String tempoStrategy;
    private float tempo = 1;
    private StreamType streamType;
    private String user;
    private long startTimestamp;
    private long duration;
    private String data;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getTempoStrategy() {
        return tempoStrategy == null ? null : tempoStrategy.toLowerCase();
    }

    public void setTempoStrategy(String tempoStrategy) {
        this.tempoStrategy = tempoStrategy;
    }

    public float getTempo() {
        return tempo;
    }

    public void setTempo(float tempo) {
        this.tempo = tempo;
    }

    public StreamType getStreamType() {
        return streamType;
    }

    public void setStreamType(StreamType streamType) {
        this.streamType = streamType;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    @JsonIgnore
    public String getPassword() throws IOException {
        if(StringUtils.isEmpty(getUser())) {
            return null;
        }

        Properties properties = new Properties();
        try (FileReader reader = new FileReader(new File("credentials.properties"))) {
            properties.load(reader);
            return properties.getProperty("password." + getUser());
        }
    }

    public void setStartTimestamp(long startTimestamp) {
        this.startTimestamp = startTimestamp;
    }

    public long getStartTimestamp() {
        return startTimestamp;
    }

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public String getData() {
        return data;
    }

    /**
     * Allows to attach some arbitrary data to a stream, e.g.
     * to determine which radio-show was playing.
     *
     * @param data some application-specific data as string.
     */
    public void setData(String data) {
        this.data = data;
    }

    /**
     * Verifies if the Stream is valid and throws an exception if not.
     *
     * @throws NullPointerException if the stream has no URL
     * @throws IllegalStateException if the tempo-strategy is not valid
     */
    public void validate() {
        // only URL is required
        Preconditions.checkNotNull(url, "Need an URL but did not have one for %s", this);

        // others are defaulted
        if(streamType == null) {
            streamType = StreamType.live;
        }
        if(tempoStrategy == null) {
            tempoStrategy = TempoStrategy.DEFAULT;
        } else {
            TempoStrategy.validate(tempoStrategy.toLowerCase());
        }
    }

    @JsonIgnore
    public Function<Double, Pair<String, Long>> getMetaDataFun() {
        return percentage -> Pair.of("",
                getStartTimestamp() + (long)(percentage*getDuration()));
    }

    @Override
    public String toString() {
        return "Stream: name='" + name + '\'' +
                ", url='" + url + '\'' +
                ", imageUrl='" + imageUrl + '\'' +
                ", tempoStrategy=" + tempoStrategy +
                ", tempo=" + tempo +
                ", streamType=" + streamType +
                ", user=" + user +
                ", data=" + data;
    }
}
