package com.vaultik.config;

import com.vaultik.eviction.EvictionPolicyType;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "vaultik")
public class VaultikProperties {
    private int capacity = 10_000;
    private EvictionPolicyType evictionPolicy = EvictionPolicyType.LRU;
    private String walPath = "data/vaultik.wal";
    private String snapshotPath = "data/vaultik.snapshot";
    private long snapshotInterval = 1_000;

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public EvictionPolicyType getEvictionPolicy() {
        return evictionPolicy;
    }

    public void setEvictionPolicy(EvictionPolicyType evictionPolicy) {
        this.evictionPolicy = evictionPolicy;
    }

    public String getWalPath() {
        return walPath;
    }

    public void setWalPath(String walPath) {
        this.walPath = walPath;
    }

    public String getSnapshotPath() {
        return snapshotPath;
    }

    public void setSnapshotPath(String snapshotPath) {
        this.snapshotPath = snapshotPath;
    }

    public long getSnapshotInterval() {
        return snapshotInterval;
    }

    public void setSnapshotInterval(long snapshotInterval) {
        this.snapshotInterval = snapshotInterval;
    }
}
