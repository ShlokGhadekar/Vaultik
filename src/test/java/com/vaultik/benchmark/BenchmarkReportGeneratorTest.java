package com.vaultik.benchmark;

import com.vaultik.eviction.EvictionPolicyType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class BenchmarkReportGeneratorTest {
    @TempDir
    Path directory;

    @Test
    void writesMarkdownReport() throws Exception {
        BenchmarkRunner runner = new BenchmarkRunner() {
            @Override
            public BenchmarkReport run(EvictionPolicyType policy, AccessPattern pattern, int threads, int operations) {
                return new BenchmarkReport(policy.name(), pattern, threads, operations, 1000.0, 10.0, 20.0, 30.0);
            }
        };
        Path output = directory.resolve("benchmark-report.md");

        var reports = new BenchmarkReportGenerator(runner).generate(output, 100, new int[]{1});

        assertThat(reports).hasSize(4);
        assertThat(Files.readString(output))
                .contains("# Vaultik Benchmark Report")
                .contains("| LRU | UNIFORM | 1 | 100 |")
                .contains("How To Read This");
    }
}
