package org.dstadler.audio.fm4;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.dstadler.commons.logging.jdk.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Simple cache for FM4 shows to avoid delays when switching
 * between shows.
 *
 * Automatically refreshes itself in the background.
 *
 * The cache-data is not held in static members, so keep the instance
 * available where it is used.
 *
 * The cache is refreshed automatically every 5 minutes.
 */
public class FM4Cache implements AutoCloseable {
    private final static Logger log = LoggerFactory.make();

    // cache-implementation configured
    private final Cache<String, List<FM4Stream>> fm4Cache = CacheBuilder.newBuilder()
            // refreshing is done via a scheduled task
            .concurrencyLevel(2)
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .build();

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(
            new BasicThreadFactory.Builder()
                    .daemon(true)
                    .namingPattern("FM4Cache-")
                    .uncaughtExceptionHandler((t, e) ->
                            log.log(Level.WARNING, "Had unexpected exception", e))
                    .build());

    public FM4Cache() {
        executor.scheduleAtFixedRate(this::refresh, 0, 5, TimeUnit.MINUTES);
    }

    public List<FM4Stream> get(String programKey) {
        return fm4Cache.getIfPresent(programKey);
    }

    public long size() {
        return fm4Cache.size();
    }

    public Collection<FM4Stream> allStreams() {
        List<FM4Stream> streams = new ArrayList<>();
        for (List<FM4Stream> list : fm4Cache.asMap().values()) {
            streams.addAll(list);
        }
        return streams;
    }

    public void refresh() {
        FM4 fm4 = new FM4();

        try {
            // first get all streams by programKey
            Multimap<String, FM4Stream> streams = ArrayListMultimap.create();
            for (FM4Stream stream : fm4.fetchStreams()) {
                streams.put(stream.getProgramKey(), stream);
            }

            // then store a lis tof items per programKey in the cache
            for (String programKey : streams.keySet()) {
                fm4Cache.put(programKey, new ArrayList<>(streams.get(programKey)));
            }
        } catch (IOException e) {
            log.log(Level.WARNING, "Failed to read FM4 streams", e);
        }
    }

    @Override
    public void close() {
        executor.shutdown();
    }
}
