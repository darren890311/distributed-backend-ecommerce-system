package com.cs6650.leaderfollowerkv.controller;

import com.cs6650.leaderfollowerkv.model.VersionedValue;
import com.cs6650.leaderfollowerkv.service.KVStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Testing endpoints that bypass normal replication logic.
 * These endpoints are used to expose the inconsistency window.
 *
 * WARNING: These should not be used in production!
 */
@RestController
@RequestMapping("/test")
public class TestController {
  private static final Logger logger = LoggerFactory.getLogger(TestController.class);

  @Autowired
  private KVStore kvStore;


  /**
   * Local read endpoint: Returns the value on THIS node only.
   * Does NOT participate in quorum reads or any replication logic.
   *
   * GET /test/local_read?key=name
   *
   * This is used to test the inconsistency window:
   * 1. Write to Leader
   * 2. Immediately local_read from Follower
   * 3. Should return stale data (proves inconsistency!)
   *
   * Returns:
   * - 200 OK: With VersionedValue from this node
   * - 404 Not Found: Key doesn't exist on this node
   */
  @GetMapping("/local_read")
  public ResponseEntity<VersionedValue> localRead(@RequestParam String key) {
    logger.debug("LOCAL_READ request: key='{}'", key);

    // Directly read from local store, no replication logic
    VersionedValue value = kvStore.get(key);

    if (value != null) {
      logger.debug("LOCAL_READ successful: key='{}', version={}",
          key, value.getVersion());
      return ResponseEntity.ok(value);
    } else {
      logger.debug("LOCAL_READ not found: key='{}'", key);
      return ResponseEntity.notFound().build();
    }
  }


  /**
   * Health check endpoint
   *
   * GET /test/health
   *
   * Returns basic info about this node.
   */
  @GetMapping("/health")
  public ResponseEntity<String> health() {
    return ResponseEntity.ok("OK - KV Store is running, " + kvStore.size() + " keys stored");
  }
}