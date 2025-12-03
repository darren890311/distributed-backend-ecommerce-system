package com.cs6650.leaderfollowerkv.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Cluster configuration that tells this node:
 * - Its role (Leader or Follower)
 * - Who its teammates are
 * - Replication parameters (W, R, N)
 */
@Configuration
@ConfigurationProperties(prefix = "cluster")
public class ClusterConfig {

  private String role = "leader";                    // "leader" or "follower"
  private List<String> followers = new ArrayList<>(); // URLs of follower nodes
  private String leaderUrl;                          // URL of leader (for followers)
  private int writeQuorum = 5;                       // W value
  private int readQuorum = 1;                        // R value
  private int totalNodes = 5;                        // N value


  // Getters and Setters
  public String getRole() {
    return role;
  }

  public void setRole(String role) {
    this.role = role;
  }

  public List<String> getFollowers() {
    return followers;
  }

  public void setFollowers(List<String> followers) {
    this.followers = followers;
  }

  public String getLeaderUrl() {
    return leaderUrl;
  }

  public void setLeaderUrl(String leaderUrl) {
    this.leaderUrl = leaderUrl;
  }

  public int getWriteQuorum() {
    return writeQuorum;
  }

  public void setWriteQuorum(int writeQuorum) {
    this.writeQuorum = writeQuorum;
  }

  public int getReadQuorum() {
    return readQuorum;
  }

  public void setReadQuorum(int readQuorum) {
    this.readQuorum = readQuorum;
  }

  public int getTotalNodes() {
    return totalNodes;
  }

  public void setTotalNodes(int totalNodes) {
    this.totalNodes = totalNodes;
  }


  // Helper methods

  /**
   * Check if this node is the Leader
   */
  public boolean isLeader() {
    return "leader".equalsIgnoreCase(role);
  }

  /**
   * Check if this node is a Follower
   */
  public boolean isFollower() {
    return "follower".equalsIgnoreCase(role);
  }


}
