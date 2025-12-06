package com.cs6650.leaderlesskv.controller;

import com.cs6650.leaderlesskv.config.ClusterConfig;
import com.cs6650.leaderlesskv.model.VersionedValue;
import com.cs6650.leaderlesskv.model.WriteRequest;
import com.cs6650.leaderlesskv.service.KVStore;
import com.cs6650.leaderlesskv.service.WriteCoordinatorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class KVController {
    private static final Logger logger = LoggerFactory.getLogger(KVController.class);

    private final KVStore kvStore;
    private final WriteCoordinatorService writeCoordinator;
    private final ClusterConfig clusterConfig;

    @Autowired
    public KVController(KVStore kvStore,
                       WriteCoordinatorService writeCoordinator,
                       ClusterConfig clusterConfig) {
        this.kvStore = kvStore;
        this.writeCoordinator = writeCoordinator;
        this.clusterConfig = clusterConfig;
    }

    /**
     * Client write request - This node becomes the Write Coordinator
     * Must coordinate writes to all N nodes before returning success
     */
    @PostMapping("/api/kv/set")
    public ResponseEntity<VersionedValue> set(@RequestParam String key,
                                              @RequestParam String value) {
        logger.info("Received client WRITE request: key={}, value={} (acting as coordinator)",
                   key, value);

        try {
            // 1. Write to local storage with new version
            VersionedValue versionedValue = kvStore.set(key, value);

            // 2. Coordinate write to all peer nodes (W=N)
            // This will block until all peers acknowledge
            writeCoordinator.coordinateWrite(key, versionedValue);

            // 3. Return success only after all nodes have acknowledged
            logger.info("Write coordination successful: key={}, version={}",
                       key, versionedValue.getVersion());

            return ResponseEntity.status(HttpStatus.CREATED).body(versionedValue);

        } catch (Exception e) {
            logger.error("Write coordination failed: key={}, error={}", key, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Client read request - Returns local value (R=1)
     * Load balancer distributes requests across nodes using round-robin
     * Node sleeps before reading to demonstrate inconsistency window
     */
    @GetMapping("/api/kv/get")
    public ResponseEntity<VersionedValue> get(@RequestParam String key) {
        logger.info("Received client READ request: key={} (node {}, will return local value)",
                   key, clusterConfig.getNodeId());

        try {
            // Sleep to simulate read delay and increase inconsistency window
            int readDelay = clusterConfig.getReadDelayMs();
            if (readDelay > 0) {
                logger.debug("Sleeping for {}ms before read", readDelay);
                Thread.sleep(readDelay);
            }

            // Read from local storage only (R=1)
            VersionedValue value = kvStore.get(key);

            if (value != null) {
                logger.info("Returning local value: key={}, version={}, value={}",
                           key, value.getVersion(), value.getValue());
                return ResponseEntity.ok(value);
            } else {
                logger.warn("Key not found in local storage: {}", key);
                return ResponseEntity.notFound().build();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Read interrupted for key={}", key);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Read failed for key={}: {}", key, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Internal peer-write endpoint
     * Called by Write Coordinator to replicate data to this peer
     */
    @PostMapping("/api/kv/peer-write")
    public ResponseEntity<Void> peerWrite(@RequestBody WriteRequest request) {
        logger.info("Received PEER-WRITE: key={}, version={}, from coordinator",
                   request.getKey(), request.getVersion());

        try {
            kvStore.setWithVersion(
                    request.getKey(),
                    request.getValue(),
                    request.getVersion(),
                    request.getTimestamp()
            );

            return ResponseEntity.ok().build();

        } catch (Exception e) {
            logger.error("Peer write failed: key={}, error={}", request.getKey(), e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Test endpoint - Read local value directly (for testing)
     */
    @GetMapping("/test/local_read")
    public ResponseEntity<VersionedValue> localRead(@RequestParam String key) {
        logger.debug("Test local read: key={}", key);

        VersionedValue value = kvStore.get(key);

        if (value != null) {
            return ResponseEntity.ok(value);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/test/health")
    public ResponseEntity<String> health() {
        String response = String.format(
                "OK - Leaderless KV Store Node %d is running, %d keys stored (W=%d, R=%d)",
                clusterConfig.getNodeId(),
                kvStore.size(),
                clusterConfig.getWriteQuorum(),
                clusterConfig.getReadQuorum()
        );
        return ResponseEntity.ok(response);
    }

    // ========================================================================
    // SIMULATED ACID TRANSACTION STUBS
    // These endpoints print a message but don't implement 2PC.
    // They demonstrate where transactions should begin/end/abort.
    // ========================================================================

    /**
     * BEGIN TRANSACTION endpoint - STUB
     * Called at the start of a transaction (e.g., before modifying cart)
     */
    @PostMapping("/api/kv/transaction/begin")
    public ResponseEntity<Void> beginTransaction() {
        logger.info("--- [KV DB] BEGIN TRANSACTION (Simulated - 2PC not implemented) ---");
        return ResponseEntity.ok().build();
    }

    /**
     * END TRANSACTION endpoint - STUB
     * Called when transaction completes successfully (commit point)
     */
    @PostMapping("/api/kv/transaction/end")
    public ResponseEntity<Void> endTransaction() {
        logger.info("--- [KV DB] END TRANSACTION / COMMIT (Simulated - 2PC not implemented) ---");
        return ResponseEntity.ok().build();
    }

    /**
     * ABORT TRANSACTION endpoint - STUB
     * Called when transaction fails and needs rollback
     */
    @PostMapping("/api/kv/transaction/abort")
    public ResponseEntity<Void> abortTransaction() {
        logger.warn("--- [KV DB] ABORT TRANSACTION / ROLLBACK (Simulated - 2PC not implemented) ---");
        return ResponseEntity.ok().build();
    }
}