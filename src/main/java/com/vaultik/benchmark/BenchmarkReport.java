package com.vaultik.benchmark;

public record BenchmarkReport(
        String policy,
        AccessPattern pattern,
        int threads,
        int operations,
        double throughputOpsPerSecond,
        double averageLatencyMicros,
        double p95LatencyMicros,
        double p99LatencyMicros
) {
}
