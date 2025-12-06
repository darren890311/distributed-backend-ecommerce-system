package com.cs6650.leaderfollowerkv.controller;

import com.cs6650.leaderfollowerkv.model.ReplicationRequest;
import com.cs6650.leaderfollowerkv.model.VersionedValue;
import com.cs6650.leaderfollowerkv.service.KVStore;
import com.cs6650.leaderfollowerkv.service.FollowerRegistryService;
import java.util.Map;
import java.util.HashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Internal API for node-to-node replication and dynamic follower registration.
 *
 * DYNAMIC REGISTRATION FLOW:
 * 1. Leader exposes POST /internal/register endpoint
 * 2. Followers call this endpoint on startup (through load balancer)
 * 3. Leader extracts follower's IP from the request
 * 4. Leader stores follower's direct URL for future replication
 * 5. Replication now bypasses load balancer, going directly to each follower
 */
@RestController
@RequestMapping("/internal")
public class ReplicationController {
  private static final Logger logger = LoggerFactory.getLogger(ReplicationController.class);

  @Autowired
  private KVStore kvStore;

  @Autowired
  private FollowerRegistryService followerRegistry;

  @Value("${kvstore.role:follower}")
  private String role;

  // ========================================================================
  // DYNAMIC FOLLOWER REGISTRATION (NEW)
  // ========================================================================

  /**
   * REGISTER endpoint: Followers register themselves with the Leader
   *
   * POST /internal/register
   * Body: {"followerId": "follower-1", "port": 8081}
   *
   * The Leader extracts the follower's IP from the HTTP request
   * and constructs the direct URL for future replication.
   *
   * This solves the load balancer problem:
   * - Followers register through the load balancer (any route works)
   * - Leader stores direct IP addresses
   * - Replication goes directly to followers, not through LB
   */
  @PostMapping("/register")
  public ResponseEntity<Map<String, Object>> registerFollower(
      @RequestBody Map<String, Object> request,
      HttpServletRequest httpRequest) {

    String followerId = (String) request.get("followerId");
    Integer port = (Integer) request.get("port");

    if (followerId == null || port == null) {
      logger.warn("Invalid registration request: missing followerId or port");
      return ResponseEntity.badRequest().body(Map.of(
          "success", false,
          "error", "Missing followerId or port"
      ));
    }

    // Extract the follower's real IP from the request
    String followerIp = extractClientIp(httpRequest);
    String followerUrl = String.format("http://%s:%d", followerIp, port);

    logger.info("========================================");
    logger.info("FOLLOWER REGISTRATION REQUEST");
    logger.info("Follower ID: {}", followerId);
    logger.info("Follower IP: {}", followerIp);
    logger.info("Follower Port: {}", port);
    logger.info("Constructed URL: {}", followerUrl);
    logger.info("========================================");

    // Only leader accepts registrations
    if (!"leader".equalsIgnoreCase(role)) {
      logger.warn("Registration rejected: This node is not the leader (role={})", role);
      return ResponseEntity.status(403).body(Map.of(
          "success", false,
          "error", "Only leader accepts registrations",
          "thisNodeRole", role
      ));
    }

    // Register the follower
    boolean success = followerRegistry.registerFollower(followerId, followerUrl);

    Map<String, Object> response = new HashMap<>();
    response.put("success", success);
    response.put("followerId", followerId);
    response.put("registeredUrl", followerUrl);
    response.put("clusterStatus", followerRegistry.getClusterStatus());

    return ResponseEntity.ok(response);
  }

  /**
   * UNREGISTER endpoint: For graceful shutdown
   */
  @PostMapping("/unregister")
  public ResponseEntity<Map<String, Object>> unregisterFollower(
      @RequestBody Map<String, Object> request) {

    String followerId = (String) request.get("followerId");

    if (followerId == null) {
      return ResponseEntity.badRequest().body(Map.of(
          "success", false,
          "error", "Missing followerId"
      ));
    }

    followerRegistry.unregisterFollower(followerId);

    return ResponseEntity.ok(Map.of(
        "success", true,
        "unregistered", followerId,
        "clusterStatus", followerRegistry.getClusterStatus()
    ));
  }

  /**
   * CLUSTER STATUS endpoint: Check registration status
   */
  @GetMapping("/cluster-status")
  public ResponseEntity<Map<String, Object>> getClusterStatus() {
    Map<String, Object> status = new HashMap<>();
    status.put("role", role);
    status.put("clusterStatus", followerRegistry.getClusterStatus());
    return ResponseEntity.ok(status);
  }

  /**
   * Extract client IP from request, handling proxies/load balancers
   */
  private String extractClientIp(HttpServletRequest request) {
    // Check for X-Forwarded-For header (set by load balancers)
    String xForwardedFor = request.getHeader("X-Forwarded-For");
    if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
      // Take the first IP if there are multiple
      String ip = xForwardedFor.split(",")[0].trim();
      logger.debug("Using X-Forwarded-For IP: {}", ip);
      return ip;
    }

    // Check for X-Real-IP header
    String xRealIp = request.getHeader("X-Real-IP");
    if (xRealIp != null && !xRealIp.isEmpty()) {
      logger.debug("Using X-Real-IP: {}", xRealIp);
      return xRealIp;
    }

    // Fall back to remote address
    String remoteAddr = request.getRemoteAddr();
    logger.debug("Using RemoteAddr: {}", remoteAddr);
    return remoteAddr;
  }

  // ========================================================================
  // EXISTING REPLICATION ENDPOINTS
  // ========================================================================

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
