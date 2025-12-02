package com.cs6650.productservice.kvclient;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VersionedValue {
  private String value;
  private long version;
  private long timestamp;
}