package com.cs6650.leaderfollowerkv.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * FOLLOWER STARTUP SERVICE
 *
 * This service runs on follower nodes at startup and registers
 * with the leader through the load balancer.
 *
 * FLOW:
 * 1. Follower starts up
 * 2. This service waits for application to be ready
 * 3. Sends POST /internal/register to leader (via load balancer)
 * 4. Leader extracts follower's IP from request
 * 5. Leader stores direct URL for future replication
 *
 * RETRY LOGIC:
 * - Retries registration until successful
 * - Handles case where leader isn't ready yet
 */
@Service
public class FollowerStartupService {

    private static final Logger logger = LoggerFactory.getLogger(FollowerStartupService.class);

    private final RestTemplate restTemplate;

    @Value("${kvstore.role:follower}")
    private String role;

    @Value("${kvstore.leader-url:}")
    private String leaderUrl;

    @Value("${kvstore.follower-id:}")
    private String followerId;

    @Value("${server.port}")
    private int serverPort;

    @Value("${kvstore.registration-retry-interval-ms:5000}")
    private int retryIntervalMs;

    @Value("${kvstore.registration-max-retries:60}")
    private int maxRetries;

    public FollowerStartupService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Called when Spring Boot application is fully started
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if ("leader".equalsIgnoreCase(role)) {
            logger.info("========================================");
            logger.info("THIS NODE IS THE LEADER");
            logger.info("Waiting for followers to register...");
            logger.info("Registration endpoint: POST /internal/register");
            logger.info("========================================");
            return;
        }

        if ("follower".equalsIgnoreCase(role)) {
            logger.info("========================================");
            logger.info("THIS NODE IS A FOLLOWER");
            logger.info("Follower ID: {}", followerId);
            logger.info("Server Port: {}", serverPort);
            logger.info("Leader URL: {}", leaderUrl);
            logger.info("========================================");

            // Start registration in a separate thread to not block startup
            new Thread(this::registerWithLeader, "follower-registration").start();
        }
    }

    /**
     * Register this follower with the leader
     * Retries until successful
     */
    private void registerWithLeader() {
        if (leaderUrl == null || leaderUrl.isEmpty()) {
            logger.error("Leader URL not configured! Set kvstore.leader-url property.");
            return;
        }

        if (followerId == null || followerId.isEmpty()) {
            // Generate a follower ID based on port if not configured
            followerId = "follower-" + serverPort;
            logger.info("Generated follower ID: {}", followerId);
        }

        String registerUrl = leaderUrl + "/internal/register";
        int attempts = 0;

        while (attempts < maxRetries) {
            attempts++;
            try {
                logger.info("Attempting to register with leader (attempt {}/{})", attempts, maxRetries);
                logger.info("Registration URL: {}", registerUrl);

                Map<String, Object> request = new HashMap<>();
                request.put("followerId", followerId);
                request.put("port", serverPort);

                @SuppressWarnings("unchecked")
                Map<String, Object> response = restTemplate.postForObject(
                    registerUrl,
                    request,
                    Map.class
                );

                if (response != null && Boolean.TRUE.equals(response.get("success"))) {
                    logger.info("========================================");
                    logger.info("REGISTRATION SUCCESSFUL!");
                    logger.info("Registered URL: {}", response.get("registeredUrl"));
                    logger.info("Cluster Status: {}", response.get("clusterStatus"));
                    logger.info("========================================");
                    return;
                } else {
                    logger.warn("Registration response indicates failure: {}", response);
                }

            } catch (Exception e) {
                logger.warn("Registration attempt {} failed: {}", attempts, e.getMessage());
            }

            // Wait before retrying
            try {
                logger.info("Retrying in {} ms...", retryIntervalMs);
                Thread.sleep(retryIntervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("Registration thread interrupted");
                return;
            }
        }

        logger.error("========================================");
        logger.error("REGISTRATION FAILED after {} attempts!", maxRetries);
        logger.error("Follower will operate in standalone mode");
        logger.error("========================================");
    }
}
