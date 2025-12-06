package com.cs6650.leaderlesskv.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "cluster")
@Data
public class ClusterConfig {
    private int nodeId;
    private int totalNodes = 5;
    private int writeQuorum = 5;  // W=N (all nodes)
    private int readQuorum = 1;   // R=1 (single node)
    private List<String> peers;   // URLs of all other peer nodes
    private int networkDelayMs = 0;  // Simulated network delay per write
    private int readDelayMs = 50;  // Delay when processing read requests (to show inconsistency)
}