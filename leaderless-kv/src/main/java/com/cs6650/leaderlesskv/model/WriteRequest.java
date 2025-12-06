package com.cs6650.leaderlesskv.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class WriteRequest {
    private String key;
    private String value;
    private Long version;
    private Long timestamp;
}
