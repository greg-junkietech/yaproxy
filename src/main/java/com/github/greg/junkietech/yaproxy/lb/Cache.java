package com.github.greg.junkietech.yaproxy.lb;

import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Cache {

    private final static Logger LOGGER = LoggerFactory.getLogger(Cache.class);

    private final ConcurrentHashMap<String, CacheEntry> entriesByPath = new ConcurrentHashMap<>();

    public void putEntry(CacheEntry ce) {
        entriesByPath.put(ce.getPath(), ce);
        LOGGER.info("cached {}", ce.toString());
    }

    public CacheEntry getEntry(String path) {
        return entriesByPath.get(path);
    }

}
