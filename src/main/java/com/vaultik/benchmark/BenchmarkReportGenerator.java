package com.vaultik.benchmark;

import com.vaultik.eviction.EvictionPolicyType;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class BenchmarkReportGenerator {
    private final BenchmarkRunner runner;

    public BenchmarkReportGenerator() {
        this(new BenchmarkRunner());
    }

    BenchmarkReportGenerator(BenchmarkRunner runner) {
        this.runner = runner;
    }

    public List<BenchmarkReport> generate(Path output, int operations, int[] threadCounts)
            throws IOException, InterruptedException {
        List<BenchmarkReport> reports = runBenchmarks(operations, threadCounts);
        writeMarkdown(output, operations, reports);
        return reports;
    }

    public List<BenchmarkReport> runBenchmarks(int operations, int[] threadCounts)
            throws IOException, InterruptedException {
        List<BenchmarkReport> reports = new ArrayList<>();
        for (EvictionPolicyType policy : EvictionPolicyType.values()) {
            for (AccessPattern pattern : AccessPattern.values()) {
                for (int threads : threadCounts) {
                    reports.add(runner.run(policy, pattern, threads, operations));
                }
            }
        }
        return reports;
    }

    public void writeMarkdown(Path output, int operations, List<BenchmarkReport> reports) throws IOException {
        Path parent = output.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(output, markdown(operations, reports));
    }

    public String markdown(int operations, List<BenchmarkReport> reports) {
        StringBuilder builder = new StringBuilder();
        builder.append("# Vaultik Benchmark Report\n\n");
        builder.append("- Generated at: `").append(Instant.now()).append("`\n");
        builder.append("- Java: `").append(System.getProperty("java.version")).append("`\n");
        builder.append("- JVM: `").append(ManagementFactory.getRuntimeMXBean().getVmName()).append("`\n");
        builder.append("- Available processors: `").append(Runtime.getRuntime().availableProcessors()).append("`\n");
        builder.append("- Operations per scenario: `").append(operations).append("`\n\n");
        builder.append("## Results\n\n");
        builder.append("| Policy | Pattern | Threads | Operations | Throughput ops/sec | Avg us | p95 us | p99 us |\n");
        builder.append("| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |\n");
        for (BenchmarkReport report : reports) {
            builder.append("| ")
                    .append(report.policy()).append(" | ")
                    .append(report.pattern()).append(" | ")
                    .append(report.threads()).append(" | ")
                    .append(report.operations()).append(" | ")
                    .append(format(report.throughputOpsPerSecond())).append(" | ")
                    .append(format(report.averageLatencyMicros())).append(" | ")
                    .append(format(report.p95LatencyMicros())).append(" | ")
                    .append(format(report.p99LatencyMicros())).append(" |\n");
        }
        builder.append("\n## How To Read This\n\n");
        builder.append("The benchmark runs a mixed workload with about 90% reads and 10% writes. ");
        builder.append("`UNIFORM` spreads requests across the keyspace. `HOT_KEY` concentrates traffic on a smaller key range. ");
        builder.append("The numbers are best used for comparing policies and access patterns on the same machine, not as universal performance claims.\n\n");
        builder.append("## Demo Command\n\n");
        builder.append("```bash\n");
        builder.append("mvn -q exec:java -Dexec.mainClass=com.vaultik.cli.VaultikCli -Dexec.args=\"benchmark-report --operations ")
                .append(operations)
                .append("\"\n");
        builder.append("```\n");
        return builder.toString();
    }

    private String format(double value) {
        return String.format("%.2f", value);
    }
}
