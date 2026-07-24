package com.vaultik.benchmark;

import com.vaultik.eviction.EvictionPolicyType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BenchmarkRunnerTest {
    @Test
    void producesLatencyAndThroughputReport() throws Exception {
        BenchmarkReport report = new BenchmarkRunner()
                .run(EvictionPolicyType.LRU, AccessPattern.UNIFORM, 2, 100);

        assertThat(report.throughputOpsPerSecond()).isPositive();
        assertThat(report.averageLatencyMicros()).isPositive();
        assertThat(report.p95LatencyMicros()).isGreaterThanOrEqualTo(report.averageLatencyMicros() * 0.1);
        assertThat(report.p99LatencyMicros()).isGreaterThanOrEqualTo(report.p95LatencyMicros());
    }
}
