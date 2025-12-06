package com.cs6650.leaderfollowerkv.service;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.cs6650.leaderfollowerkv.model.VersionedValue;
import com.cs6650.leaderfollowerkv.model.ReplicationRequest;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * REPLICATION SERVICE with DYNAMIC FOLLOWER REGISTRATION
 *
 * This service handles write replication and read strategies.
 * Now uses FollowerRegistryService for dynamic follower discovery
 * instead of static configuration.
 *
 * Key features:
 * 1. W=N: Sequential replication with delays
 * 2. W=1: Async replication (fire and forget)
 * 3. W=3: Quorum-based replication
 * 4. R=N: Reads from ALL nodes
 * 5. R=1: Local read only
 * 6. DYNAMIC: Uses registered followers instead of static config
 */
@Service
public class ReplicationService {

  private static final Logger logger = LoggerFactory.getLogger(ReplicationService.class);

  private final RestTemplate restTemplate;
  private final KVStore kvStore;

  @Autowired
  private FollowerRegistryService followerRegistry;

  @Value("${kvstore.role:leader}")
  private String role;

  // Static fallback (used if no dynamic registration)
  @Value("${kvstore.followers:}")
  private List<String> staticFollowerUrls;

  @Value("${kvstore.all-nodes:}")
  private List<String> staticAllNodeUrls;

  @Value("${kvstore.write-quorum:1}")
  private int W;

  @Value("${kvstore.read-quorum:1}")
  private int R;

  @Value("${kvstore.replication-delay-ms:100}")
  private int REPLICATION_DELAY_MS;

  @Value("${server.port}")
  private int serverPort;

  public ReplicationService(RestTemplate restTemplate, KVStore kvStore) {
    this.restTemplate = restTemplate;
    this.kvStore = kvStore;
  }

  // ========================================================================
  // DYNAMIC FOLLOWER ACCESS
  // ========================================================================

  /**
   * Get list of follower URLs - prefers dynamic registry, falls back to static
   */
  private List<String> getFollowerUrls() {
    // First try dynamic registry
    List<String> dynamicFollowers = followerRegistry.getFollowerUrls();
    if (!dynamicFollowers.isEmpty()) {
      logger.debug("Using {} dynamically registered followers", dynamicFollowers.size());
      return dynamicFollowers;
    }

    // Fall back to static config
    if (staticFollowerUrls != null && !staticFollowerUrls.isEmpty()) {
      logger.debug("Using {} statically configured followers", staticFollowerUrls.size());
      return staticFollowerUrls;
    }

    logger.warn("No followers available (dynamic or static)");
    return Collections.emptyList();
  }

  /**
   * Get list of all node URLs (leader + followers)
   */
  private List<String> getAllNodeUrls() {
    String leaderUrl = String.format("http://localhost:%d", serverPort);

    // First try dynamic registry
    List<String> dynamicNodes = followerRegistry.getAllNodeUrls(leaderUrl);
    if (dynamicNodes.size() > 1) { // More than just leader
      logger.debug("Using {} dynamically registered nodes", dynamicNodes.size());
      return dynamicNodes;
    }

    // Fall back to static config
    if (staticAllNodeUrls != null && !staticAllNodeUrls.isEmpty()) {
      logger.debug("Using {} statically configured nodes", staticAllNodeUrls.size());
      return staticAllNodeUrls;
    }

    // Just return leader
    return Collections.singletonList(leaderUrl);
  }

  // ========================================================================
  // WRITE REPLICATION STRATEGIES
  // ========================================================================

  /**
   * Main replication method - routes to correct strategy based on W
   */
  public void replicateWrite(String key, String value, long version, long timestamp)
      throws Exception {

    List<String> followers = getFollowerUrls();

    if (followers.isEmpty()) {
      logger.info("No followers registered - skipping replication");
      return;
    }

    if (W >= followers.size() + 1) {
      // W=N - Sequential replication to ALL followers
      replicateToAllFollowersSequential(followers, key, value, version, timestamp);

    } else if (W == 1) {
      // W=1 - Async replication (fire and forget)
      replicateAsync(followers, key, value, version, timestamp);

    } else {
      // W=quorum - Quorum-based replication
      replicateToQuorum(followers, key, value, version, timestamp);
    }
  }

  /**
   * Strategy: W=N - SEQUENTIAL replication with delays
   */
  private void replicateToAllFollowersSequential(List<String> followers, String key, String value,
      long version, long timestamp) throws Exception {

    logger.info("W={}: Starting SEQUENTIAL replication to {} followers", W, followers.size());
    long startTime = System.currentTimeMillis();

    int successCount = 0;
    List<String> failures = new ArrayList<>();

    for (String followerUrl : followers) {
      try {
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
    logger.info("W={}: Sequential replication took {}ms ({}/{} succeeded)",
        W, duration, successCount, followers.size());

    // W=N requires ALL followers to succeed
    int required = followers.size();
    if (successCount < required) {
      throw new Exception(
          String.format("W=%d replication failed: %d/%d succeeded. Failures: %s",
              W, successCount, required, failures)
      );
    }

    logger.info("W={}: Successfully replicated to all {} followers", W, successCount);
  }

  /**
   * Strategy: W=1 - Async replication (fire and forget)
   */
  private void replicateAsync(List<String> followers, String key, String value,
      long version, long timestamp) {
    logger.info("W=1: Starting ASYNC replication to {} followers", followers.size());

    // Fire and forget - don't wait for results
    CompletableFuture.runAsync(() -> {
      for (String followerUrl : followers) {
        try {
          Thread.sleep(REPLICATION_DELAY_MS);
          sendReplicationRequest(followerUrl, key, value, version, timestamp);
          logger.debug("  Async replicated to {}", followerUrl);
        } catch (Exception e) {
          logger.warn("  Async replication failed to {}: {}", followerUrl, e.getMessage());
        }
      }
    });

    logger.info("W=1: Leader stored, async replication initiated");
  }

  /**
   * Strategy: W=quorum - Quorum-based replication
   */
  private void replicateToQuorum(List<String> followers, String key, String value,
      long version, long timestamp) throws Exception {

    int requiredAcks = W - 1; // W-1 because leader is implicit
    logger.info("W={}: Starting quorum replication ({} ACKs required from {} followers)",
        W, requiredAcks, followers.size());

    AtomicInteger successCount = new AtomicInteger(0);
    AtomicInteger completedCount = new AtomicInteger(0);
    CountDownLatch latch = new CountDownLatch(1);

    for (String followerUrl : followers) {
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

          if (successes >= requiredAcks) {
            latch.countDown();
          }
        }

        if (completed >= followers.size()) {
          latch.countDown();
        }
      });
    }

    boolean signaled = latch.await(10, TimeUnit.SECONDS);

    if (!signaled) {
      throw new TimeoutException("Quorum replication timeout after 10 seconds");
    }

    int finalSuccesses = successCount.get();
    if (finalSuccesses < requiredAcks) {
      throw new Exception(
          String.format("W=%d quorum not met: %d/%d followers succeeded (required: %d)",
              W, finalSuccesses, followers.size(), requiredAcks)
      );
    }

    logger.info("W={}: Quorum achieved ({}/{} followers)", W, finalSuccesses, followers.size());
  }

  // ========================================================================
  // READ STRATEGIES
  // ========================================================================

  /**
   * Main read method - routes to correct strategy based on R
   */
  public VersionedValue handleRead(String key) throws Exception {

    if (R == 1) {
      return kvStore.get(key);

    } else {
      List<String> allNodes = getAllNodeUrls();

      if (R >= allNodes.size()) {
        return readFromAllNodes(allNodes, key);
      } else {
        return readFromQuorum(allNodes, key);
      }
    }
  }

  /**
   * Strategy: R=N - Read from ALL nodes
   */
  private VersionedValue readFromAllNodes(List<String> allNodes, String key) throws Exception {
    logger.info("R={}: Reading from ALL {} nodes", R, allNodes.size());

    ConcurrentLinkedQueue<VersionedValue> results = new ConcurrentLinkedQueue<>();
    CountDownLatch latch = new CountDownLatch(allNodes.size());

    for (String nodeUrl : allNodes) {
      CompletableFuture.supplyAsync(() -> {
        try {
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

    boolean completed = latch.await(5, TimeUnit.SECONDS);

    if (!completed) {
      logger.warn("R={}: Read timeout, got {}/{} responses", R, results.size(), allNodes.size());
    }

    if (results.isEmpty()) {
      return null;
    }

    VersionedValue mostRecent = results.stream()
        .max(Comparator.comparingLong(VersionedValue::getVersion))
        .orElse(null);

    logger.info("R={}: Read from {}/{} nodes, most recent version: {}",
        R, results.size(), allNodes.size(),
        mostRecent != null ? mostRecent.getVersion() : "null");

    return mostRecent;
  }

  /**
   * Strategy: R=quorum - Read from quorum of nodes
   */
  private VersionedValue readFromQuorum(List<String> allNodes, String key) throws Exception {
    logger.info("R={}: Reading from quorum", R);

    List<String> selectedNodes = selectRandomNodes(allNodes, R);

    ConcurrentLinkedQueue<VersionedValue> results = new ConcurrentLinkedQueue<>();
    AtomicInteger completedCount = new AtomicInteger(0);
    CountDownLatch latch = new CountDownLatch(1);

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

          if (results.size() >= R) {
            latch.countDown();
          }
        }

        if (completed >= selectedNodes.size()) {
          latch.countDown();
        }
      });
    }

    boolean signaled = latch.await(5, TimeUnit.SECONDS);

    if (!signaled || results.size() < R) {
      throw new Exception(
          String.format("R=%d quorum not met: %d/%d nodes responded", R, results.size(), R)
      );
    }

    VersionedValue mostRecent = results.stream()
        .max(Comparator.comparingLong(VersionedValue::getVersion))
        .orElse(null);

    logger.info("R={}: Quorum achieved ({}/{} nodes), version: {}",
        R, results.size(), selectedNodes.size(),
        mostRecent != null ? mostRecent.getVersion() : "null");

    return mostRecent;
  }

  // ========================================================================
  // HELPER METHODS
  // ========================================================================

  private List<String> selectRandomNodes(List<String> nodes, int count) {
    if (count > nodes.size()) {
      count = nodes.size();
    }
    List<String> shuffled = new ArrayList<>(nodes);
    Collections.shuffle(shuffled);
    return shuffled.subList(0, count);
  }

  private boolean sendReplicationRequest(String followerUrl, String key, String value,
      long version, long timestamp) {
    try {
      String url = String.format("%s/internal/replicate", followerUrl);
      ReplicationRequest request = new ReplicationRequest(key, value, version, timestamp);

      @SuppressWarnings("unchecked")
      Map<String, Object> response = restTemplate.postForObject(url, request, Map.class);

      return response != null && Boolean.TRUE.equals(response.get("success"));

    } catch (Exception e) {
      logger.error("Failed to replicate to {}: {}", followerUrl, e.getMessage());
      return false;
    }
  }

  private VersionedValue sendReadRequest(String nodeUrl, String key) {
    try {
      String url = String.format("%s/internal/read?key=%s", nodeUrl, key);

      @SuppressWarnings("unchecked")
      Map<String, Object> response = restTemplate.getForObject(url, Map.class);

      if (response == null) {
        return null;
      }

      return new VersionedValue(
          (String) response.get("value"),
          ((Number) response.get("version")).longValue(),
          ((Number) response.get("timestamp")).longValue()
      );

    } catch (Exception e) {
      logger.debug("Read from {} failed: {}", nodeUrl, e.getMessage());
      return null;
    }
  }

  private boolean isCurrentNode(String nodeUrl) {
    return nodeUrl.contains(":" + serverPort) || nodeUrl.contains("localhost:" + serverPort);
  }

  // Getters
  public int getW() { return W; }
  public int getR() { return R; }
  public boolean isLeader() { return "leader".equalsIgnoreCase(role); }
}
