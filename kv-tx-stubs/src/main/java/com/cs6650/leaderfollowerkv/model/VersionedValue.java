package com.cs6650.leaderfollowerkv.model;

/**
 * Wraps a value with version metadata for conflict resolution in distributed systems.
 * Higher version numbers indicate more recent data.
 */
public class VersionedValue {

  private String value;      // The actual data
  private long version;      // Monotonically increasing version number (higher = newer)
  private long timestamp;    // Unix timestamp in milliseconds


  // Default constructor required for Spring JSON deserialization
  public VersionedValue() {
  }

  public VersionedValue(String value, long version, long timestamp) {
    this.value = value;
    this.version = version;
    this.timestamp = timestamp;
  }


  // Getters and Setters
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


  // Utility Methods

  /**
   * Returns true if this version is higher (more recent) than the other.
   */
  public boolean isNewerThan(VersionedValue other) {
    return other == null || this.version > other.version;
  }

  /**
   * Returns true if this version is lower (older) than the other.
   */
  public boolean isOlderThan(VersionedValue other) {
    return other != null && this.version < other.version;
  }

  /**
   * Creates a deep copy of this VersionedValue.
   */
  public VersionedValue copy() {
    return new VersionedValue(this.value, this.version, this.timestamp);
  }


  // Standard overrides for debugging and testing

  @Override
  public String toString() {
    return "VersionedValue{" +
        "value='" + value + '\'' +
        ", version=" + version +
        ", timestamp=" + timestamp +
        '}';
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null || getClass() != obj.getClass()) return false;

    VersionedValue that = (VersionedValue) obj;
    return version == that.version &&
        timestamp == that.timestamp &&
        (value != null ? value.equals(that.value) : that.value == null);
  }

  @Override
  public int hashCode() {
    int result = value != null ? value.hashCode() : 0;
    result = 31 * result + (int) (version ^ (version >>> 32));
    result = 31 * result + (int) (timestamp ^ (timestamp >>> 32));
    return result;
  }
}