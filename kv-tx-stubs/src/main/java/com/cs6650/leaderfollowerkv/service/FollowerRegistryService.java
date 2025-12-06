package com.cs6650.leaderfollowerkv.service;

import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DYNAMIC FOLLOWER REGISTRATION SERVICE
 *
 * This service manages the dynamic registration of follower nodes.
 * Instead of hardcoding follower IPs in config files, followers register
 * themselves with the leader at startup.
 *
 * FLOW (as described by professor):
 * 1. Leader starts first and exposes /internal/register endpoint
 * 2. Followers start and call POST /internal/register (through load balancer initially)
 * 3. Leader stores the follower's IP from the request
 * 4. Leader can then send replication requests DIRECTLY to follower IPs
 *    (bypassing the load balancer for replication)
 *
 * This solves the problem of:
 * - Load balancer randomly distributing replication requests
 * - Dynamic IPs in cloud environments (ECS Fargate)
 */
@Service
public class FollowerRegistryService {

    private static final Logger logger = LoggerFactory.getLogger(FollowerRegistryService.class);

    // Thread-safe map of follower ID -> follower URL
    private final Map<String, FollowerInfo> registeredFollowers = new ConcurrentHashMap<>();

    // Expected number of followers (configured via environment)
    private int expectedFollowerCount = 4; // Default: 5 nodes - 1 leader = 4 followers

    /**
     * Register a follower node
     *
     * @param followerId Unique identifier for the follower (e.g., "follower-1")
     * @param followerUrl Direct URL to reach the follower (e.g., "http://172.31.x.x:8081")
     * @return true if registration successful
     */
    public boolean registerFollower(String followerId, String followerUrl) {
        logger.info("=== FOLLOWER REGISTRATION ===");
        logger.info("Follower '{}' registering with URL: {}", followerId, followerUrl);

        FollowerInfo info = new FollowerInfo(followerId, followerUrl, System.currentTimeMillis());
        registeredFollowers.put(followerId, info);

        logger.info("Registered followers: {}/{}", registeredFollowers.size(), expectedFollowerCount);
        logAllFollowers();

        return true;
    }

    /**
     * Unregister a follower (for graceful shutdown or failure detection)
     */
    public void unregisterFollower(String followerId) {
        FollowerInfo removed = registeredFollowers.remove(followerId);
        if (removed != null) {
            logger.info("Follower '{}' unregistered", followerId);
        }
    }

    /**
     * Get list of all registered follower URLs
     * Used by ReplicationService for sending replication requests
     */
    public List<String> getFollowerUrls() {
        List<String> urls = new ArrayList<>();
        for (FollowerInfo info : registeredFollowers.values()) {
            urls.add(info.url);
        }
        return urls;
    }

    /**
     * Get all node URLs (leader + followers)
     * Used for R=N reads
     */
    public List<String> getAllNodeUrls(String leaderUrl) {
        List<String> allUrls = new ArrayList<>();
        allUrls.add(leaderUrl);
        allUrls.addAll(getFollowerUrls());
        return allUrls;
    }

    /**
     * Check if we have all expected followers registered
     */
    public boolean isClusterReady() {
        return registeredFollowers.size() >= expectedFollowerCount;
    }

    /**
     * Get current registration status
     */
    public int getRegisteredCount() {
        return registeredFollowers.size();
    }

    public int getExpectedCount() {
        return expectedFollowerCount;
    }

    public void setExpectedFollowerCount(int count) {
        this.expectedFollowerCount = count;
        logger.info("Expected follower count set to: {}", count);
    }

    /**
     * Check if a specific follower is registered
     */
    public boolean isFollowerRegistered(String followerId) {
        return registeredFollowers.containsKey(followerId);
    }

    /**
     * Get follower info by ID
     */
    public FollowerInfo getFollower(String followerId) {
        return registeredFollowers.get(followerId);
    }

    /**
     * Log all registered followers (for debugging)
     */
    private void logAllFollowers() {
        logger.info("Current registered followers:");
        for (Map.Entry<String, FollowerInfo> entry : registeredFollowers.entrySet()) {
            logger.info("  - {}: {}", entry.getKey(), entry.getValue().url);
        }
    }

    /**
     * Get cluster status as a map (for API responses)
     */
    public Map<String, Object> getClusterStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("expectedFollowers", expectedFollowerCount);
        status.put("registeredFollowers", registeredFollowers.size());
        status.put("clusterReady", isClusterReady());
        status.put("followers", registeredFollowers);
        return status;
    }

    /**
     * Inner class to hold follower information
     */
    public static class FollowerInfo {
        public final String id;
        public final String url;
        public final long registeredAt;
        public long lastHeartbeat;

        public FollowerInfo(String id, String url, long registeredAt) {
            this.id = id;
            this.url = url;
            this.registeredAt = registeredAt;
            this.lastHeartbeat = registeredAt;
        }

        public void updateHeartbeat() {
            this.lastHeartbeat = System.currentTimeMillis();
        }
    }
}
