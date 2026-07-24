package com.vaultik.benchmark;

import com.vaultik.eviction.EvictionPolicyType;

public final class BenchmarkCli {
    private BenchmarkCli() {
    }

    public static void main(String[] args) throws Exception {
        int operations = args.length > 0 ? Integer.parseInt(args[0]) : 50_000;
        int[] threadCounts = {1, Runtime.getRuntime().availableProcessors()};
        BenchmarkRunner runner = new BenchmarkRunner();

        System.out.println("policy pattern threads operations throughput_ops_sec avg_us p95_us p99_us");
        for (EvictionPolicyType policy : EvictionPolicyType.values()) {
            for (AccessPattern pattern : AccessPattern.values()) {
                for (int threads : threadCounts) {
                    BenchmarkReport report = runner.run(policy, pattern, threads, operations);
                    System.out.printf(
                            "%s %s %d %d %.2f %.2f %.2f %.2f%n",
                            report.policy(),
                            report.pattern(),
                            report.threads(),
                            report.operations(),
                            report.throughputOpsPerSecond(),
                            report.averageLatencyMicros(),
                            report.p95LatencyMicros(),
                            report.p99LatencyMicros()
                    );
                }
            }
        }
    }
}
