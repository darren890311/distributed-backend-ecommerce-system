package com.cs6650.leaderfollowerkv.service;

import com.cs6650.leaderfollowerkv.model.VersionedValue;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory key-value store with versioning support.
 * Thread-safe for concurrent reads and writes.
 */
@Service
public class KVStore {
  private static final Logger logger = LoggerFactory.getLogger(KVStore.class);

  // Thread-safe storage - multiple threads can access simultaneously
  private final ConcurrentHashMap<String, VersionedValue> store = new ConcurrentHashMap<>();

  // Atomic version counter - guarantees unique, increasing version numbers
  private final AtomicLong versionCounter = new AtomicLong(0);


  /**
   * Set a key-value pair with auto-generated version.
   * Used by Leader when receiving new write requests from clients.
   *
   * @param key The key (cannot be empty)
   * @param value The value to store (can be empty string)
   * @return The VersionedValue that was stored
   */
  public VersionedValue set(String key, String value) {
    // Generate new version atomically
    long version = versionCounter.incrementAndGet();
    long timestamp = System.currentTimeMillis();

    VersionedValue versionedValue = new VersionedValue(value, version, timestamp);
    store.put(key, versionedValue);

    logger.debug("SET: key='{}', value='{}', version={}", key, value, version);
    return versionedValue;
  }


  /**
   * Set a key-value pair with explicit version.
   * Used by Followers when receiving replication updates from Leader.
   *
   * @param key The key
   * @param value The value to store
   * @param version The version number from Leader
   * @param timestamp The timestamp from Leader
   * @return The VersionedValue that was stored
   */
  public VersionedValue setWithVersion(String key, String value, long version, long timestamp) {
    VersionedValue versionedValue = new VersionedValue(value, version, timestamp);
    store.put(key, versionedValue);

    // Update our version counter if we received a higher version
    // This ensures consistency if this node later becomes Leader
    versionCounter.updateAndGet(current -> Math.max(current, version));

    logger.debug("SET_WITH_VERSION: key='{}', value='{}', version={}", key, value, version);
    return versionedValue;
  }


  /**
   * Get the value for a key.
   *
   * @param key The key to look up
   * @return The VersionedValue if found, null if key doesn't exist
   */
  public VersionedValue get(String key) {
    VersionedValue value = store.get(key);

    if (value != null) {
      logger.debug("GET: key='{}', value='{}', version={}",
          key, value.getValue(), value.getVersion());
    } else {
      logger.debug("GET: key='{}' not found", key);
    }

    return value;
  }


  /**
   * Check if a key exists in the store.
   *
   * @param key The key to check
   * @return true if key exists, false otherwise
   */
  public boolean containsKey(String key) {
    return store.containsKey(key);
  }


  /**
   * Get the current version counter value.
   * Useful for debugging and testing.
   *
   * @return Current version number
   */
  public long getCurrentVersion() {
    return versionCounter.get();
  }


  /**
   * Get the total number of keys stored.
   * Useful for monitoring and debugging.
   *
   * @return Number of key-value pairs
   */
  public int size() {
    return store.size();
  }


  /**
   * Clear all data from the store.
   * WARNING: Only use for testing! Data cannot be recovered.
   */
  public void clear() {
    store.clear();
    versionCounter.set(0);
    logger.warn("CLEAR: All data removed from store");
  }
}