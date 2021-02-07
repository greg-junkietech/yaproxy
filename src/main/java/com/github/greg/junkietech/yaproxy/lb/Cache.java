package com.github.greg.junkietech.yaproxy.lb;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Cache {

    private final static Logger LOGGER = LoggerFactory.getLogger(Cache.class);

    private final ConcurrentHashMap<String, CacheEntry> entriesByPath;

    public Cache() {
        entriesByPath = new ConcurrentHashMap<>();
    }

    public void putEntry(CacheEntry ce) {
        entriesByPath.put(ce.getPath(), ce);
        LOGGER.info("cached {}", ce.toString());
    }

    public CacheEntry getEntry(String path) {
        return entriesByPath.get(path);
    }

    public int invalidateExpiredEntries() {
        AtomicInteger ai = new AtomicInteger();
        LocalDateTime now = LocalDateTime.now();
        entriesByPath.forEach((key, ce) -> {
            if (ce.getExpiration().isBefore(now)) {
                entriesByPath.remove(key);
                ai.incrementAndGet();
                LOGGER.info("invalidated {} from cache", ce.toString());
            }
        });
        return ai.get();
    }

}
