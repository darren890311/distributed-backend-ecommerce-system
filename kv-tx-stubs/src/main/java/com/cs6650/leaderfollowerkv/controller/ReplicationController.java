package com.cs6650.leaderfollowerkv.controller;

import com.cs6650.leaderfollowerkv.model.ReplicationRequest;
import com.cs6650.leaderfollowerkv.model.VersionedValue;
import com.cs6650.leaderfollowerkv.service.KVStore;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Internal API for node-to-node replication.
 * Only other nodes in the cluster should call these endpoints.
 */
@RestController
@RequestMapping("/internal")
public class ReplicationController {
  private static final Logger logger = LoggerFactory.getLogger(ReplicationController.class);

  @Autowired
  private KVStore kvStore;


  /**
   * Replicate endpoint: Receive write updates from Leader (or Write Coordinator)
   *
   * POST /internal/replicate
   * Body: {"key": "name", "value": "Alice", "version": 1, "timestamp": 1699824000}
   *
   * This endpoint is called by:
   * - Leader → Followers (in Leader-Follower mode)
   * - Write Coordinator → Other nodes (in Leaderless mode)
   *
   * Returns:
   * - 200 OK: Successfully replicated
   * - 500 Internal Server Error: Replication failed
   */
  @PostMapping("/replicate")
  public ResponseEntity<Map<String,Object>> replicate(@RequestBody ReplicationRequest request) {
    logger.info("REPLICATE received: key='{}', value='{}', version={}",
        request.getKey(), request.getValue(), request.getVersion());

    try {
      // Simulate network/disk delay (as required by assignment)
      // Follower sleeps 100ms before processing
      Thread.sleep(100);

      // Store the replicated data with the version from Leader
      kvStore.setWithVersion(
          request.getKey(),
          request.getValue(),
          request.getVersion(),
          request.getTimestamp()
      );

      logger.debug("REPLICATE successful: key='{}', version={}",
          request.getKey(), request.getVersion());

      return ResponseEntity.ok(Map.of("success", true));

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      logger.error("REPLICATE interrupted: key='{}'", request.getKey(), e);
      return ResponseEntity.status(500).body(Map.of("success", false, "error", "Interrupted"));
    } catch (Exception e) {
      logger.error("REPLICATE failed: key='{}'", request.getKey(), e);
      return ResponseEntity.status(500).body(Map.of("success", false, "error", e.getMessage()));
    }
  }


  /**
   * Read endpoint for quorum reads (R > 1)
   *
   * GET /internal/read?key=name
   *
   * Called by Leader when R > 1 to collect values from multiple nodes.
   *
   * Returns:
   * - 200 OK: With VersionedValue in body
   * - 404 Not Found: Key doesn't exist on this node
   */
  @GetMapping("/read")
  public ResponseEntity<VersionedValue> read(@RequestParam String key) {
    logger.debug("INTERNAL_READ request: key='{}'", key);

    try {
      // Simulate read delay (as required by assignment)
      // Follower sleeps 50ms before responding to read
      Thread.sleep(50);

      VersionedValue value = kvStore.get(key);

      if (value != null) {
        logger.debug("INTERNAL_READ successful: key='{}', version={}",
            key, value.getVersion());
        return ResponseEntity.ok(value);
      } else {
        logger.debug("INTERNAL_READ not found: key='{}'", key);
        return ResponseEntity.notFound().build();
      }

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      logger.error("INTERNAL_READ interrupted: key='{}'", key, e);
      return ResponseEntity.status(500).build();
    } catch (Exception e) {
      logger.error("INTERNAL_READ failed: key='{}'", key, e);
      return ResponseEntity.status(500).build();
    }
  }
}