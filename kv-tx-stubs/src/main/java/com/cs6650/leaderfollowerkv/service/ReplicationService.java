package com.cs6650.leaderfollowerkv.service;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.cs6650.leaderfollowerkv.model.VersionedValue;
import com.cs6650.leaderfollowerkv.model.ReplicationRequest;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * FIXED ReplicationService with correct quorum implementation
 *
 * Key fixes:
 * 1. W=5: Sequential replication with delays (not parallel)
 * 2. W=1: Truly async (fire and forget)
 * 3. W=3: Correct quorum logic (no hang on failures)
 * 4. R=5: Reads from ALL 5 nodes (not just leader)
 */
@Service
public class ReplicationService {

  private static final Logger logger = LoggerFactory.getLogger(ReplicationService.class);

  private final RestTemplate restTemplate;
  private final KVStore kvStore;

  @Value("${kvstore.role}")
  private String role;

  @Value("${kvstore.followers}")
  private List<String> followerUrls;

  @Value("${kvstore.all-nodes}")
  private List<String> allNodeUrls;

  @Value("${kvstore.write-quorum}")
  private int W;

  @Value("${kvstore.read-quorum}")
  private int R;

  @Value("${kvstore.replication-delay-ms:200}")
  private int REPLICATION_DELAY_MS;

  @Value("${server.port}")
  private int serverPort;

  public ReplicationService(RestTemplate restTemplate, KVStore kvStore) {
    this.restTemplate = restTemplate;
    this.kvStore = kvStore;
  }

  // ========================================================================
  // WRITE REPLICATION STRATEGIES
  // ========================================================================

  /**
   * Main replication method - routes to correct strategy based on W
   *
   */
  public void replicateWrite(String key, String value, long version, long timestamp)
      throws Exception {

    if (W == 5) {
      // Strategy 1: W=5 - Sequential replication to ALL followers
      replicateToAllFollowersSequential(key, value, version, timestamp);

    } else if (W == 1) {
      // Strategy 2: W=1 - Async replication (fire and forget)
      replicateAsync(key, value, version, timestamp);

    } else if (W == 3) {
      // Strategy 3: W=3 - Quorum-based replication
      replicateToQuorum(key, value, version, timestamp);

    } else {
      throw new IllegalStateException("Unsupported W value: " + W);
    }
  }

  /**
   * Strategy 1: W=5 - SEQUENTIAL replication with delays
   *
   */
  private void replicateToAllFollowersSequential(String key, String value, long version,
      long timestamp) throws Exception {

    logger.info("W=5: Starting SEQUENTIAL replication to {} followers", followerUrls.size());
    long startTime = System.currentTimeMillis();

    int successCount = 0;
    List<String> failures = new ArrayList<>();

    //  SEQUENTIAL replication with delays (not parallel!)
    for (String followerUrl : followerUrls) {
      try {
        // CRITICAL: Sleep BEFORE each replication to simulate network delay
        Thread.sleep(REPLICATION_DELAY_MS);

        boolean success = sendReplicationRequest(followerUrl, key, value, version, timestamp);

        if (success) {
          successCount++;
          logger.debug("  ✓ Replicated to {}", followerUrl);
        } else {
          failures.add(followerUrl);
          logger.warn("  ✗ Failed to replicate to {}", followerUrl);
        }

      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new Exception("Replication interrupted");
      } catch (Exception e) {
        failures.add(followerUrl);
        logger.error("  ✗ Exception replicating to {}: {}", followerUrl, e.getMessage());
      }
    }

    long duration = System.currentTimeMillis() - startTime;
    logger.info("W=5: Sequential replication took {}ms", duration);

    // W=5 requires ALL followers to succeed
    if (successCount < followerUrls.size()) {
      throw new Exception(
          String.format("W=5 replication failed: %d/%d succeeded. Failures: %s",
              successCount, followerUrls.size(), failures)
      );
    }

    logger.info("W=5: Successfully replicated to all {} followers", successCount);
  }

  /**
   * Strategy 2: W=1 - Async replication (fire and forget)
   */
  private void replicateAsync(String key, String value, long version, long timestamp) {
    logger.info("W=1: Starting ASYNC replication to {} followers", followerUrls.size());

    // Fire and forget - don't wait for results
    CompletableFuture.runAsync(() -> {
      for (String followerUrl : followerUrls) {
        try {
          Thread.sleep(REPLICATION_DELAY_MS);
          sendReplicationRequest(followerUrl, key, value, version, timestamp);
          logger.debug("  Async replicated to {}", followerUrl);
        } catch (Exception e) {
          logger.warn("  Async replication failed to {}: {}",
              followerUrl, e.getMessage());
        }
      }
    });

    logger.info("W=1: Leader stored, async replication initiated");
  }

  /**
   * Strategy 3: W=3 - CORRECT quorum implementation
   *
   * FIX #1: Changed int version → long version
   */
  private void replicateToQuorum(String key, String value, long version, long timestamp)
      throws Exception {

    int requiredAcks = W - 1; // W-1 because leader is implicit
    logger.info("W={}: Starting quorum replication ({} ACKs required from {} followers)",
        W, requiredAcks, followerUrls.size());

    //  FIXED: Use atomic counters, not just latch
    AtomicInteger successCount = new AtomicInteger(0);
    AtomicInteger completedCount = new AtomicInteger(0);
    CountDownLatch latch = new CountDownLatch(1); // Signal quorum OR all done

    // Send to ALL followers concurrently
    for (String followerUrl : followerUrls) {
      CompletableFuture.supplyAsync(() -> {
        try {
          Thread.sleep(REPLICATION_DELAY_MS);
          return sendReplicationRequest(followerUrl, key, value, version, timestamp);
        } catch (Exception e) {
          logger.error("Replication to {} failed: {}", followerUrl, e.getMessage());
          return false;
        }
      }).thenAccept(success -> {
        int completed = completedCount.incrementAndGet();

        if (success) {
          int successes = successCount.incrementAndGet();
          logger.debug("  ✓ Quorum progress: {}/{} successes", successes, requiredAcks);

          //  Quorum achieved!
          if (successes >= requiredAcks) {
            latch.countDown();
          }
        }

        //  All attempts completed - signal even if quorum not met
        if (completed >= followerUrls.size()) {
          latch.countDown();
        }
      });
    }

    //  FIXED: Wait with timeout
    boolean signaled = latch.await(10, TimeUnit.SECONDS);

    if (!signaled) {
      throw new TimeoutException("Quorum replication timeout after 10 seconds");
    }

    //  FIXED: Verify quorum was actually achieved
    int finalSuccesses = successCount.get();
    if (finalSuccesses < requiredAcks) {
      throw new Exception(
          String.format("W=%d quorum not met: %d/%d followers succeeded (required: %d)",
              W, finalSuccesses, followerUrls.size(), requiredAcks)
      );
    }

    logger.info("W={}: Quorum achieved ({}/{} followers)",
        W, finalSuccesses, followerUrls.size());
  }

  // ========================================================================
  // READ STRATEGIES
  // ========================================================================

  /**
   * Main read method - routes to correct strategy based on R
   */
  public VersionedValue handleRead(String key) throws Exception {

    if (R == 1) {
      // Strategy 1: R=1 - Read from local node only
      return kvStore.get(key);

    } else if (R == 5) {
      // Strategy 2: R=5 - Read from ALL 5 nodes
      return readFromAllNodes(key);

    } else if (R == 3) {
      // Strategy 3: R=3 - Read from quorum
      return readFromQuorum(key);

    } else {
      throw new IllegalStateException("Unsupported R value: " + R);
    }
  }

  /**
   * Strategy 2: R=5 - Read from ALL 5 nodes (Leader + 4 Followers)
   *
   * FIX #3: Changed comparingInt → comparingLong
   */
  private VersionedValue readFromAllNodes(String key) throws Exception {
    logger.info("R=5: Reading from ALL {} nodes", allNodeUrls.size());

    ConcurrentLinkedQueue<VersionedValue> results = new ConcurrentLinkedQueue<>();
    CountDownLatch latch = new CountDownLatch(allNodeUrls.size());

    // Read from ALL nodes concurrently
    for (String nodeUrl : allNodeUrls) {
      CompletableFuture.supplyAsync(() -> {
        try {
          // If this is current node, read locally
          if (isCurrentNode(nodeUrl)) {
            return kvStore.get(key);
          } else {
            return sendReadRequest(nodeUrl, key);
          }
        } catch (Exception e) {
          logger.error("Read from {} failed: {}", nodeUrl, e.getMessage());
          return null;
        }
      }).thenAccept(result -> {
        if (result != null) {
          results.add(result);
        }
        latch.countDown();
      });
    }

    // Wait for all reads
    boolean completed = latch.await(5, TimeUnit.SECONDS);

    if (!completed) {
      logger.warn("R=5: Read timeout, got {}/{} responses",
          results.size(), allNodeUrls.size());
    }

    if (results.isEmpty()) {
      return null; // Key not found anywhere
    }

    // Return most recent version
    // FIX #3: Changed comparingInt → comparingLong
    VersionedValue mostRecent = results.stream()
        .max(Comparator.comparingLong(VersionedValue::getVersion))
        .orElse(null);

    logger.info("R=5: Read from {}/{} nodes, most recent version: {}",
        results.size(), allNodeUrls.size(),
        mostRecent != null ? mostRecent.getVersion() : "null");

    return mostRecent;
  }

  /**
   * Strategy 3: R=3 - Read from quorum
   */
  private VersionedValue readFromQuorum(String key) throws Exception {
    logger.info("R={}: Reading from quorum", R);

    // Select only R nodes
    List<String> selectedNodes = selectRandomNodes(R);
    logger.info("R={}: Selected {} random nodes for quorum read", R, selectedNodes.size());  // ← FIX #3

    ConcurrentLinkedQueue<VersionedValue> results = new ConcurrentLinkedQueue<>();
    AtomicInteger completedCount = new AtomicInteger(0);
    CountDownLatch latch = new CountDownLatch(1);

    // Read from selected nodes only
    for (String nodeUrl : selectedNodes) {
      CompletableFuture.supplyAsync(() -> {
        try {
          if (isCurrentNode(nodeUrl)) {
            return kvStore.get(key);
          } else {
            return sendReadRequest(nodeUrl, key);
          }
        } catch (Exception e) {
          return null;
        }
      }).thenAccept(result -> {
        int completed = completedCount.incrementAndGet();

        if (result != null) {
          results.add(result);

          // Quorum achieved
          if (results.size() >= R) {
            latch.countDown();
          }
        }

        // FIX #1: All selected nodes completed
        if (completed >= selectedNodes.size()) {  // ← FIXED!
          latch.countDown();
        }
      });
    }

    // Wait for quorum
    boolean signaled = latch.await(5, TimeUnit.SECONDS);

    if (!signaled || results.size() < R) {
      throw new Exception(
          String.format("R=%d quorum not met: %d/%d nodes responded",
              R, results.size(), R)
      );
    }

    // Return most recent
    VersionedValue mostRecent = results.stream()
        .max(Comparator.comparingLong(VersionedValue::getVersion))
        .orElse(null);

    // FIX #2: Log selected nodes size
    logger.info("R={}: Quorum achieved ({}/{} nodes), version: {}",
        R, results.size(), selectedNodes.size(),  // ← FIXED!
        mostRecent != null ? mostRecent.getVersion() : "null");

    return mostRecent;
  }


  // ========================================================================
  // HELPER METHODS
  // ========================================================================

  /**
   * NEW: Helper method to select N random nodes from all available nodes
   */
  private List<String> selectRandomNodes(int count) {
    if (count > allNodeUrls.size()) {
      logger.warn("Requested {} nodes but only {} available", count, allNodeUrls.size());
      count = allNodeUrls.size();
    }

    List<String> nodes = new ArrayList<>(allNodeUrls);
    Collections.shuffle(nodes);
    return nodes.subList(0, count);
  }

  /**
   * Send replication request to a follower (legacy method)
   */
  private boolean sendReplicationRequest(String followerUrl, String key, String value,
      long version, long timestamp) {
    try {
      String url = String.format("%s/internal/replicate", followerUrl);

      ReplicationRequest request = new ReplicationRequest(key, value, version, timestamp);

      Map<String, Object> response = restTemplate.postForObject(
          url, request, Map.class);

      return response != null && Boolean.TRUE.equals(response.get("success"));

    } catch (Exception e) {
      logger.error("Failed to replicate to {}: {}", followerUrl, e.getMessage());
      return false;
    }
  }

  /**
   * Send read request to a node
   */
  private VersionedValue sendReadRequest(String nodeUrl, String key) {
    try {
      String url = String.format("%s/internal/read?key=%s", nodeUrl, key);

      Map<String, Object> response = restTemplate.getForObject(url, Map.class);

      if (response == null) {
        return null;
      }

      // FIX #2: Properly cast version to long
      return new VersionedValue(
          (String) response.get("value"),
          ((Number) response.get("version")).longValue(),  // ✅ FIXED
          ((Number) response.get("timestamp")).longValue()
      );

    } catch (Exception e) {
      logger.debug("Read from {} failed: {}", nodeUrl, e.getMessage());
      return null;
    }
  }

  /**
   * Check if URL refers to current node
   */
  private boolean isCurrentNode(String nodeUrl) {
    // Check if the nodeUrl matches current server port
    return nodeUrl.contains(":" + serverPort);
  }

  // Getters
  public int getW() { return W; }
  public int getR() { return R; }
  public boolean isLeader() { return "leader".equalsIgnoreCase(role); }
}