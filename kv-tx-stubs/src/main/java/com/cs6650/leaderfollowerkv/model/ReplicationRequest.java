package com.cs6650.leaderfollowerkv.model;

/**
 * Message sent from Leader to Followers (or Coordinator to other nodes)
 * to replicate a write operation.
 */
public class ReplicationRequest {

  private String key;        // Which key to update
  private String value;      // New value to store
  private long version;      // Version number assigned by Leader/Coordinator
  private long timestamp;    // When this write occurred


  // Default constructor required for Spring JSON deserialization
  public ReplicationRequest() {
  }

  public ReplicationRequest(String key, String value, long version, long timestamp) {
    this.key = key;
    this.value = value;
    this.version = version;
    this.timestamp = timestamp;
  }


  // Getters and Setters
  public String getKey() {
    return key;
  }

  public void setKey(String key) {
    this.key = key;
  }

  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = value;
  }

  public long getVersion() {
    return version;
  }

  public void setVersion(long version) {
    this.version = version;
  }

  public long getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(long timestamp) {
    this.timestamp = timestamp;
  }


  // Utility method to convert to VersionedValue
  /**
   * Converts this request into a VersionedValue for storage.
   * Useful for Followers when receiving replication messages.
   */
  public VersionedValue toVersionedValue() {
    return new VersionedValue(this.value, this.version, this.timestamp);
  }


  @Override
  public String toString() {
    return "ReplicationRequest{" +
        "key='" + key + '\'' +
        ", value='" + value + '\'' +
        ", version=" + version +
        ", timestamp=" + timestamp +
        '}';
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null || getClass() != obj.getClass()) return false;

    ReplicationRequest that = (ReplicationRequest) obj;
    return version == that.version &&
        timestamp == that.timestamp &&
        (key != null ? key.equals(that.key) : that.key == null) &&
        (value != null ? value.equals(that.value) : that.value == null);
  }

  @Override
  public int hashCode() {
    int result = key != null ? key.hashCode() : 0;
    result = 31 * result + (value != null ? value.hashCode() : 0);
    result = 31 * result + (int) (version ^ (version >>> 32));
    result = 31 * result + (int) (timestamp ^ (timestamp >>> 32));
    return result;
  }
}