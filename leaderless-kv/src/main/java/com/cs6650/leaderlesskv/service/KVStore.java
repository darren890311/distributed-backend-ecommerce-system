package com.cs6650.leaderlesskv.service;

import com.cs6650.leaderlesskv.model.VersionedValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class KVStore {
    private static final Logger logger = LoggerFactory.getLogger(KVStore.class);

    private final ConcurrentHashMap<String, VersionedValue> store = new ConcurrentHashMap<>();
    private final AtomicLong versionCounter = new AtomicLong(0);

    /**
     * Store a key-value pair with a new version (for coordinator-originated writes)
     */
    public VersionedValue set(String key, String value) {
        long version = versionCounter.incrementAndGet();
        long timestamp = System.currentTimeMillis();

        VersionedValue versionedValue = new VersionedValue(value, version, timestamp);
        store.put(key, versionedValue);

        logger.debug("SET (new version): key={}, value={}, version={}, timestamp={}",
                    key, value, version, timestamp);

        return versionedValue;
    }

    /**
     * Store a key-value pair with specified version (for peer replication)
     */
    public void setWithVersion(String key, String value, Long version, Long timestamp) {
        VersionedValue versionedValue = new VersionedValue(value, version, timestamp);

        // Update local version counter if received version is higher
        versionCounter.updateAndGet(current -> Math.max(current, version));

        store.put(key, versionedValue);

        logger.debug("SET (replicated): key={}, value={}, version={}, timestamp={}",
                    key, value, version, timestamp);
    }

    /**
     * Get the value for a key (returns local value, R=1)
     */
    public VersionedValue get(String key) {
        VersionedValue value = store.get(key);

        if (value != null) {
            logger.debug("GET: key={}, value={}, version={}", key, value.getValue(), value.getVersion());
        } else {
            logger.debug("GET: key={} not found", key);
        }

        return value;
    }

    /**
     * Get the current version for a key
     */
    public Long getVersion(String key) {
        VersionedValue value = store.get(key);
        return value != null ? value.getVersion() : null;
    }

    /**
     * Get the number of keys stored
     */
    public int size() {
        return store.size();
    }
}
