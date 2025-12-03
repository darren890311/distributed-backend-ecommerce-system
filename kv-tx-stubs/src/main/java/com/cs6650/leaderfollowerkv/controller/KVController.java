package com.cs6650.leaderfollowerkv.controller;

import com.cs6650.leaderfollowerkv.service.KVStore;
import com.cs6650.leaderfollowerkv.service.ReplicationService;
import com.cs6650.leaderfollowerkv.model.VersionedValue;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Main KV API Controller
 *
 * Endpoints:
 * - POST /api/kv/set - Write (Leader only)
 * - GET /api/kv/get - Read (any node)
 * - POST /api/kv/transaction/begin - BEGIN Tx STUB (NEW)
 * - POST /api/kv/transaction/end - END Tx STUB (NEW)
 * - POST /api/kv/transaction/abort - ABORT Tx STUB (NEW)
 */
@RestController
@RequestMapping("/api/kv")
public class KVController {

  private static final Logger logger = LoggerFactory.getLogger(KVController.class);

  private final KVStore kvStore;
  private final ReplicationService replicationService;

  public KVController(KVStore kvStore, ReplicationService replicationService) {
    this.kvStore = kvStore;
    this.replicationService = replicationService;
  }

  /**
   * SET endpoint - Only Leader accepts writes
   */
  @PostMapping("/set")
  public ResponseEntity<Map<String, Object>> set(
      @RequestParam String key,
      @RequestParam String value) {

    // Only Leader accepts writes
    if (!replicationService.isLeader()) {
      logger.warn("Write rejected: This is a follower, not leader");
      return ResponseEntity
          .status(HttpStatus.FORBIDDEN)
          .body(Map.of("error", "Writes must go to Leader"));
    }

    try {
      // Store locally with version
      VersionedValue vv = kvStore.set(key, value);

      logger.info("Leader stored: key={}, version={}", key, vv.getVersion());

      // Replicate based on W strategy
      replicationService.replicateWrite(key, value, vv.getVersion(), vv.getTimestamp());

      // Return success
      Map<String, Object> response = new HashMap<>();
      response.put("success", true);
      response.put("key", key);
      response.put("version", vv.getVersion());
      response.put("timestamp", vv.getTimestamp());

      return ResponseEntity.ok(response);

    } catch (Exception e) {
      logger.error("Write failed: key={}, error={}", key, e.getMessage());
      return ResponseEntity
          .status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(Map.of("error", e.getMessage()));
    }
  }

  /**
   * GET endpoint - Can read from any node
   */
  @GetMapping("/get")
  public ResponseEntity<Map<String, Object>> get(@RequestParam String key) {

    try {
      // Read based on R strategy
      VersionedValue result = replicationService.handleRead(key);

      if (result == null) {
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(Map.of("error", "Key not found"));
      }

      Map<String, Object> response = new HashMap<>();
      response.put("key", key);
      response.put("value", result.getValue());
      response.put("version", result.getVersion());
      response.put("timestamp", result.getTimestamp());

      return ResponseEntity.ok(response);

    } catch (Exception e) {
      logger.error("Read failed: key={}, error={}", key, e.getMessage());
      return ResponseEntity
          .status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(Map.of("error", e.getMessage()));
    }
  }

  /**
   * BEGIN TRANSACTION endpoint - STUB
   */
  @PostMapping("/transaction/begin")
  public ResponseEntity<Void> beginTransaction() {
    logger.info("--- [KV DB] RECEIVED BEGIN TRANSACTION (Simulated) ---");
    return ResponseEntity.ok().build();
  }

  /**
   * END TRANSACTION endpoint - STUB
   */
  @PostMapping("/transaction/end")
  public ResponseEntity<Void> endTransaction() {
    logger.info("--- [KV DB] RECEIVED END TRANSACTION (Simulated) ---");
    return ResponseEntity.ok().build();
  }

  /**
   * ABORT TRANSACTION endpoint - STUB
   */
  @PostMapping("/transaction/abort")
  public ResponseEntity<Void> abortTransaction() {
    logger.warn("--- [KV DB] RECEIVED ABORT TRANSACTION (Simulated) ---");
    return ResponseEntity.ok().build();
  }
  @GetMapping("/test/local_read")
  public ResponseEntity<Map<String, Object>> localRead(@RequestParam String key) {

    VersionedValue vv = kvStore.get(key);

    if (vv == null) {
      return ResponseEntity
          .status(HttpStatus.NOT_FOUND)
          .body(Map.of("error", "Key not found locally"));
    }

    Map<String, Object> response = new HashMap<>();
    response.put("key", key);
    response.put("value", vv.getValue());
    response.put("version", vv.getVersion());
    response.put("timestamp", vv.getTimestamp());
    response.put("warning", "This is a local read - may be stale!");

    return ResponseEntity.ok(response);
  }
}