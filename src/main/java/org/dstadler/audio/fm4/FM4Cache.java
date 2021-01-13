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
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
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

    // configure cache-implementation so that we keep all matching instances of "FM4Stream"
    // per programKey
    private final Cache<String, List<FM4Stream>> fm4Cache = CacheBuilder.newBuilder()
            // refreshing is done via a scheduled task
            .concurrencyLevel(2)
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .build();

    // we use a thread-pool with one entry to periodically refresh the cache
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(
            new BasicThreadFactory.Builder()
                    .daemon(true)
                    .namingPattern("FM4Cache-")
                    .uncaughtExceptionHandler((t, e) ->
                            log.log(Level.WARNING, "Had unexpected exception", e))
                    .build());

    private final FM4 fm4;

    /**
     *
     * @param fm4 An instance of the FM4 access helper.
     *            This is passed in to facilitate testing.
     */
    public FM4Cache(FM4 fm4) {
        this.fm4 = fm4;
        executor.scheduleAtFixedRate(this::refresh, 0, 5, TimeUnit.MINUTES);
    }

    /**
     * Retrieve all found streams for the given programKey.
     *
     * Multiple streams are usually available for shows which are broadcast
     * more than once per week.
     *
     * @param programKey The FM4-programKey to look for
     * @return The resulting list of FM4Stream instances, or null if no entry is found.
     */
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

    /**
     * Look for the given URL in all cached FM4Stream instances
     * and return the "next" one depending on broadcast-timestamp
     *
     * @param stream The FM4Stream to look for.
     * @return If the url is found, the time-wise next stream is
     *      returned, null is returned if the url is not found or
     *      there is no "next" stream, e.g. if the url is from a
     *      show which is currently broadcast.
     */
    public FM4Stream getNext(FM4Stream stream) {
        // get a Map of all FM4Streams sorted by start-time
        SortedMap<Long, FM4Stream> streams = getStreamsSortedByTime();

        long foundTime = getTimeOfStream(stream);

        // no matching URL found
        if(foundTime == 0) {
            return null;
        }

        // use foundTime + 1 to not include the current show itself in the result
        SortedMap<Long, FM4Stream> streamsAfter = streams.tailMap(foundTime + 1);

        // if this was the last stream the list will be empty
        if(streamsAfter.isEmpty()) {
            return null;
        }

        // we found a stream
        return streamsAfter.values().iterator().next();
    }

    /**
     * Look for the given URL in all cached FM4Stream instances
     * and return the "next" one depending on broadcast-timestamp
     *
     * @param stream The FM4Stream to look for.
     * @return If the url is found, the time-wise next stream is
     *      returned, null is returned if the url is not found or
     *      there is no "next" stream, e.g. if the url is from a
     *      show which is currently broadcast.
     */
    public FM4Stream getPrevious(FM4Stream stream) {
        // get a Map of all FM4Streams sorted by start-time
        SortedMap<Long, FM4Stream> streams = getStreamsSortedByTime();

        long foundTime = getTimeOfStream(stream);

        // no matching URL found
        if(foundTime == 0) {
            return null;
        }

        // use foundTime - 1 to not include the current show itself in the result
        SortedMap<Long, FM4Stream> streamsAfter = streams.headMap(foundTime - 1);

        // if this was the last stream the list will be empty
        if(streamsAfter.isEmpty()) {
            return null;
        }

        // we found a stream
        return streamsAfter.values().iterator().next();
    }

    private SortedMap<Long, FM4Stream> getStreamsSortedByTime() {
        SortedMap<Long, FM4Stream> streams = new TreeMap<>();
        for (List<FM4Stream> fm4Streams : fm4Cache.asMap().values()) {
            for (FM4Stream fm4Stream : fm4Streams) {
                streams.put(fm4Stream.getStart(), fm4Stream);
            }
        }
        return streams;
    }

    private long getTimeOfStream(FM4Stream stream) {
        long foundTime = 0;
        for (List<FM4Stream> fm4Streams : fm4Cache.asMap().values()) {
            for (FM4Stream fm4Stream : fm4Streams) {
                // check if this stream contains the URL
                if(fm4Stream.equals(stream)) {
                    log.info("Found current stream: '" + stream.getShortSummary() + "' with start: " + fm4Stream.getStart());
                    foundTime = fm4Stream.getStart();
                }
            }
        }
        return foundTime;
    }

    /**
     * Look for the given URL in all cached FM4Stream instances
     * and return the "next" one depending on broadcast-timestamp
     *
     * @param url The url to look for.
     * @return If the url is found, the time-wise next stream is
     *      returned, null is returned if the url is not found or
     *      there is no "next" stream, e.g. if the url is from a
     *      show which is currently broadcast.
     */
    public FM4Stream getNextByStreamURL(String url) throws IOException {
        // get a Map of all FM4Streams sorted by start-time
        SortedMap<Long, FM4Stream> streams = new TreeMap<>();
        long foundTime = 0;
        for (List<FM4Stream> fm4Streams : fm4Cache.asMap().values()) {
            for (FM4Stream fm4Stream : fm4Streams) {
                streams.put(fm4Stream.getStart(), fm4Stream);

                // check if this stream contains the URL
                for (String streamUrl : fm4Stream.getStreams()) {
                    if(streamUrl.equals(url)) {
                        log.info("Found url " + url + " for stream '" + fm4Stream.getShortSummary() + "' with start: " + fm4Stream.getStart());
                        foundTime = fm4Stream.getStart();
                    }
                }
            }
        }

        // no matching URL found
        if(foundTime == 0) {
            return null;
        }

        // use foundTime + 1 to not include the current show itself in the result
        SortedMap<Long, FM4Stream> streamsAfter = streams.tailMap(foundTime + 1);

        // if this was the last stream the list will be empty
        if(streamsAfter.isEmpty()) {
            return null;
        }

        // we found a stream
        return streamsAfter.values().iterator().next();
    }

    /**
     * Refresh the contents of the cache by re-adding all
     * shows found via the FM4 instance provided during
     * construction.
     *
     * Expiry is done by the cache so that non-refreshed
     * items are removed from the cache after some time.
     */
    public void refresh() {
        try {
            // first get all streams by programKey
            Multimap<String, FM4Stream> streams = ArrayListMultimap.create();
            for (FM4Stream stream : fm4.fetchStreams()) {
                streams.put(stream.getProgramKey(), stream);
            }

            // then store a list tof items per programKey in the cache
            for (String programKey : streams.keySet()) {
                fm4Cache.put(programKey, new ArrayList<>(streams.get(programKey)));
            }
        } catch (IOException e) {
            log.log(Level.WARNING, "Failed to read FM4 streams", e);
        }
    }

    @Override
    public void close() {
        shutdownAndAwaitTermination(executor);
    }

    /**
     * shutdown the service
     *
     * Note: This can be replaced by ExecutorUtil.shutdownAndAwaitTermination()
     * from commons-dost as soon as we have upgraded to a newer version.
     */
    public static void shutdownAndAwaitTermination(ExecutorService executor) {
        // Disable new tasks from being submitted
        executor.shutdown();
        try {
            // Wait a while for existing tasks to terminate
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                // Some jobs did not finish yet => cancel currently executing tasks
                executor.shutdownNow();

                // Wait again for tasks to respond to being cancelled
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    log.info("Executor did not shutdown cleanly in the given timeout of 10 seconds before cancelling current jobs and 10 seconds after cancelling jobs");
                }
            }
        } catch (@SuppressWarnings("unused") InterruptedException ie) {
            // (Re-)Cancel if current thread also interrupted
            executor.shutdownNow();

            // Preserve interrupt status
            Thread.currentThread().interrupt();
        }
    }
}
