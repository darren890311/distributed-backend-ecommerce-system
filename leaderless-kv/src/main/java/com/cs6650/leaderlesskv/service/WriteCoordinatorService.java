package com.cs6650.leaderlesskv.service;

import com.cs6650.leaderlesskv.config.ClusterConfig;
import com.cs6650.leaderlesskv.model.VersionedValue;
import com.cs6650.leaderlesskv.model.WriteRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Service
public class WriteCoordinatorService {
    private static final Logger logger = LoggerFactory.getLogger(WriteCoordinatorService.class);

    private final ClusterConfig clusterConfig;
    private final RestTemplate restTemplate;

    @Autowired
    public WriteCoordinatorService(ClusterConfig clusterConfig) {
        this.clusterConfig = clusterConfig;
        this.restTemplate = new RestTemplate();
    }

    /**
     * Coordinate write to all peer nodes
     * This node acts as the Write Coordinator for this write request
     * Must wait for all W nodes (W=N) to acknowledge before returning
     */
    public void coordinateWrite(String key, VersionedValue versionedValue) {
        List<String> peers = clusterConfig.getPeers();

        if (peers == null || peers.isEmpty()) {
            logger.warn("No peers configured, write only to local node");
            return;
        }

        logger.info("Coordinating write to {} peers for key={}, version={}",
                   peers.size(), key, versionedValue.getVersion());

        List<CompletableFuture<Boolean>> futures = new ArrayList<>();

        // Launch all writes in parallel to all peers
        for (String peerUrl : peers) {
            // Create async task to write to peer
            // Network delay happens naturally during HTTP call
            CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() ->
                    writeToPeer(peerUrl, key, versionedValue)
            );
            futures.add(future);
        }

        // Wait for ALL writes to complete (W=N requirement)
        try {
            CompletableFuture<Void> allWrites = CompletableFuture.allOf(
                    futures.toArray(new CompletableFuture[0])
            );
            allWrites.get();  // Block until all complete

            // Check if all succeeded
            long successCount = futures.stream()
                    .map(f -> {
                        try {
                            return f.get();
                        } catch (InterruptedException | ExecutionException e) {
                            return false;
                        }
                    })
                    .filter(success -> success)
                    .count();

            logger.info("Write coordination complete: {}/{} peers acknowledged",
                       successCount, peers.size());

            if (successCount < peers.size()) {
                logger.error("Write failed: only {}/{} peers acknowledged (W=N requires all)",
                            successCount, peers.size());
                throw new RuntimeException("Write coordination failed - not all peers acknowledged");
            }

        } catch (InterruptedException | ExecutionException e) {
            logger.error("Write coordination failed", e);
            throw new RuntimeException("Write coordination failed", e);
        }
    }

    /**
     * Send write request to a single peer node
     */
    private boolean writeToPeer(String peerUrl, String key, VersionedValue versionedValue) {
        try {
            String url = peerUrl + "/api/kv/peer-write";

            WriteRequest request = new WriteRequest(
                    key,
                    versionedValue.getValue(),
                    versionedValue.getVersion(),
                    versionedValue.getTimestamp()
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<WriteRequest> entity = new HttpEntity<>(request, headers);

            restTemplate.postForEntity(url, entity, Void.class);

            logger.debug("Successfully wrote to peer: {}, key={}, version={}",
                        peerUrl, key, versionedValue.getVersion());
            return true;

        } catch (Exception e) {
            logger.error("Failed to write to peer: {}, key={}, error={}",
                        peerUrl, key, e.getMessage());
            return false;
        }
    }
}