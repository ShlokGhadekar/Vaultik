package com.vaultik.cli;

import com.vaultik.benchmark.BenchmarkReportGenerator;
import com.vaultik.core.KeyValueStore;
import com.vaultik.core.StoreConfiguration;
import com.vaultik.core.StoreStats;
import com.vaultik.core.StoredValue;
import com.vaultik.eviction.EvictionPolicyFactory;
import com.vaultik.eviction.EvictionPolicyType;
import com.vaultik.persistence.WalRecord;
import com.vaultik.persistence.FileSnapshotStore;
import com.vaultik.persistence.FileWriteAheadLog;
import com.vaultik.storage.PersistentKeyValueStore;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Small operational CLI for demoing Vaultik without running Spring Boot.
 */
public final class VaultikCli {
    private static final int DEFAULT_CAPACITY = 10_000;
    private static final long DEFAULT_SNAPSHOT_INTERVAL = 1_000;

    private VaultikCli() {
    }

    public static void main(String[] args) throws Exception {
        int exitCode = run(args, System.out, System.err, System.in);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    static int run(String[] args, PrintStream out, PrintStream err) throws Exception {
        return run(args, out, err, System.in);
    }

    static int run(String[] args, PrintStream out, PrintStream err, InputStream input) throws Exception {
        if (args.length == 0 || "--help".equals(args[0]) || "-h".equals(args[0])) {
            printHelp(out);
            return 0;
        }

        CliOptions options = CliOptions.parse(args);
        return switch (options.command()) {
            case "set" -> set(options, out);
            case "get" -> get(options, out);
            case "delete" -> delete(options, out);
            case "stats" -> stats(options, out);
            case "demo" -> demo(options, out);
            case "shell" -> shell(options, out, err, input);
            case "wal" -> wal(options, out);
            case "engine-report" -> engineReport(options, out);
            case "benchmark-report" -> benchmarkReport(options, out);
            default -> {
                err.println("Unknown command: " + options.command());
                printHelp(err);
                yield 2;
            }
        };
    }

    private static int set(CliOptions options, PrintStream out) throws IOException {
        requireArguments(options, 2, "set <key> <value>");
        KeyValueStore store = openStore(options);
        String key = options.argument(0);
        String value = options.argument(1);
        Long ttlSeconds = options.longFlag("--ttl");
        if (ttlSeconds == null) {
            store.set(key, value);
        } else {
            store.set(key, value, Duration.ofSeconds(ttlSeconds));
        }
        out.printf("SET %s = %s%n", key, value);
        return 0;
    }

    private static int get(CliOptions options, PrintStream out) throws IOException {
        requireArguments(options, 1, "get <key>");
        KeyValueStore store = openStore(options);
        String key = options.argument(0);
        return store.get(key)
                .map(value -> {
                    out.printf("%s = %s%n", key, value);
                    return 0;
                })
                .orElseGet(() -> {
                    out.println("MISS " + key);
                    return 1;
                });
    }

    private static int delete(CliOptions options, PrintStream out) throws IOException {
        requireArguments(options, 1, "delete <key>");
        KeyValueStore store = openStore(options);
        String key = options.argument(0);
        boolean removed = store.delete(key);
        out.println((removed ? "DELETED " : "MISSING ") + key);
        return removed ? 0 : 1;
    }

    private static int stats(CliOptions options, PrintStream out) throws IOException {
        KeyValueStore store = openStore(options);
        printStats(store.stats(), out);
        return 0;
    }

    private static int demo(CliOptions options, PrintStream out) throws IOException {
        KeyValueStore store = openStore(options);
        out.println("Vaultik demo");
        store.set("language", "java");
        store.set("engine", "single-node");
        store.set("temporary", "expires-fast", Duration.ofSeconds(1));
        out.println("SET language, engine, temporary");
        out.println("GET language -> " + store.get("language").orElse("<missing>"));
        store.delete("engine");
        out.println("DELETE engine");
        printStats(store.stats(), out);
        out.println("Run `wal` to inspect the write-ahead log, or `get language` to demonstrate recovery.");
        return 0;
    }

    private static int shell(CliOptions options, PrintStream out, PrintStream err, InputStream input) throws Exception {
        out.println("Vaultik shell. Type help for commands, exit to quit.");
        BufferedReader reader = new BufferedReader(new InputStreamReader(input));
        String line;
        while (true) {
            out.print("vaultik> ");
            line = reader.readLine();
            if (line == null) {
                out.println();
                return 0;
            }
            String trimmed = line.trim();
            if (trimmed.isBlank()) {
                continue;
            }
            if ("exit".equalsIgnoreCase(trimmed) || "quit".equalsIgnoreCase(trimmed)) {
                return 0;
            }
            if ("help".equalsIgnoreCase(trimmed)) {
                printHelp(out);
                continue;
            }
            String[] commandArgs = mergeShellOptions(tokenize(trimmed), options.globalOptions());
            try {
                run(commandArgs, out, err, input);
            } catch (Exception e) {
                err.println("ERROR " + e.getMessage());
            }
        }
    }

    private static int wal(CliOptions options, PrintStream out) throws IOException {
        Path walPath = dataDirectory(options).resolve("vaultik.wal");
        List<WalRecord> records = new FileWriteAheadLog(walPath).readAll();
        out.printf("WAL: %s%n", walPath);
        out.printf("Records: %d%n", records.size());
        out.println("| # | Operation | Key | Value | Expires At |");
        out.println("|---:|---|---|---|---|");
        for (int i = 0; i < records.size(); i++) {
            WalRecord record = records.get(i);
            out.printf("| %d | %s | %s | %s | %s |%n",
                    i + 1,
                    record.operation(),
                    record.key(),
                    record.value() == null ? "-" : record.value(),
                    record.expiresAt() == null ? "never" : record.expiresAt());
        }
        return 0;
    }

    private static int engineReport(CliOptions options, PrintStream out) throws IOException {
        Path output = Path.of(options.stringFlag("--output", "reports/engine-report.md"));
        String markdown = buildEngineReport(options);
        Path parent = output.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(output, markdown);
        out.printf("Generated %s%n", output);
        return 0;
    }

    private static int benchmarkReport(CliOptions options, PrintStream out) throws Exception {
        int operations = options.intFlag("--operations", 10_000);
        int[] threads = options.intListFlag("--threads", new int[]{1, Runtime.getRuntime().availableProcessors()});
        Path output = Path.of(options.stringFlag("--output", "reports/benchmark-report.md"));

        var generator = new BenchmarkReportGenerator();
        var reports = generator.generate(output, operations, threads);
        out.printf("Generated %s with %d benchmark rows.%n", output, reports.size());
        return 0;
    }

    private static String buildEngineReport(CliOptions options) throws IOException {
        Path dataDirectory = dataDirectory(options);
        Path walPath = dataDirectory.resolve("vaultik.wal");
        Path snapshotPath = dataDirectory.resolve("vaultik.snapshot");
        EvictionPolicyType policy = policy(options);
        KeyValueStore store = openStore(options);
        StoreStats stats = store.stats();
        List<WalRecord> records = new FileWriteAheadLog(walPath).readAll();
        Map<String, StoredValue> snapshotEntries = new FileSnapshotStore(snapshotPath).load();

        StringBuilder builder = new StringBuilder();
        builder.append("# Vaultik Engine Report\n\n");
        builder.append("## Memory Table\n\n");
        builder.append("- Size: `").append(stats.size()).append("`\n");
        builder.append("- Capacity: `").append(stats.capacity()).append("`\n\n");
        builder.append("## Eviction\n\n");
        builder.append("- Policy: `").append(policy).append("`\n");
        builder.append("- Evictions: `").append(stats.evictions()).append("`\n\n");
        builder.append("## Persistence\n\n");
        builder.append("- WAL path: `").append(walPath).append("`\n");
        builder.append("- WAL records: `").append(records.size()).append("`\n");
        builder.append("- WAL size bytes: `").append(fileSize(walPath)).append("`\n");
        builder.append("- Snapshot path: `").append(snapshotPath).append("`\n");
        builder.append("- Snapshot entries: `").append(snapshotEntries.size()).append("`\n");
        builder.append("- Snapshot size bytes: `").append(fileSize(snapshotPath)).append("`\n\n");
        builder.append("## Counters\n\n");
        builder.append("| Hits | Misses | Writes | Deletes |\n");
        builder.append("| ---: | ---: | ---: | ---: |\n");
        builder.append("| ").append(stats.hits())
                .append(" | ").append(stats.misses())
                .append(" | ").append(stats.writes())
                .append(" | ").append(stats.deletes())
                .append(" |\n\n");
        builder.append("## WAL Tail\n\n");
        builder.append("| # | Operation | Key | Value | Expires At |\n");
        builder.append("| ---: | --- | --- | --- | --- |\n");
        int start = Math.max(0, records.size() - 20);
        for (int i = start; i < records.size(); i++) {
            WalRecord record = records.get(i);
            builder.append("| ").append(i + 1)
                    .append(" | ").append(record.operation())
                    .append(" | ").append(record.key())
                    .append(" | ").append(record.value() == null ? "-" : record.value())
                    .append(" | ").append(record.expiresAt() == null ? "never" : record.expiresAt())
                    .append(" |\n");
        }
        return builder.toString();
    }

    private static KeyValueStore openStore(CliOptions options) throws IOException {
        Path dataDirectory = dataDirectory(options);
        EvictionPolicyType policy = policy(options);
        int capacity = options.intFlag("--capacity", DEFAULT_CAPACITY);
        long snapshotInterval = options.longFlag("--snapshot-interval", DEFAULT_SNAPSHOT_INTERVAL);
        StoreConfiguration configuration = new StoreConfiguration(
                capacity,
                dataDirectory.resolve("vaultik.wal"),
                dataDirectory.resolve("vaultik.snapshot"),
                snapshotInterval
        );
        return new PersistentKeyValueStore(
                configuration,
                EvictionPolicyFactory.create(policy),
                new FileWriteAheadLog(configuration.walPath()),
                new FileSnapshotStore(configuration.snapshotPath())
        );
    }

    private static Path dataDirectory(CliOptions options) {
        return Path.of(options.stringFlag("--data-dir", "data"));
    }

    private static EvictionPolicyType policy(CliOptions options) {
        return EvictionPolicyType.valueOf(options.stringFlag("--policy", "LRU").toUpperCase(Locale.ROOT));
    }

    private static long fileSize(Path path) throws IOException {
        return Files.exists(path) ? Files.size(path) : 0;
    }

    private static String[] tokenize(String line) {
        return Arrays.stream(line.split("\\s+"))
                .filter(token -> !token.isBlank())
                .toArray(String[]::new);
    }

    private static String[] mergeShellOptions(String[] commandArgs, String[] globalOptions) {
        String[] merged = Arrays.copyOf(commandArgs, commandArgs.length + globalOptions.length);
        System.arraycopy(globalOptions, 0, merged, commandArgs.length, globalOptions.length);
        return merged;
    }

    private static void printStats(StoreStats stats, PrintStream out) {
        out.printf("size=%d capacity=%d hits=%d misses=%d evictions=%d writes=%d deletes=%d%n",
                stats.size(),
                stats.capacity(),
                stats.hits(),
                stats.misses(),
                stats.evictions(),
                stats.writes(),
                stats.deletes());
    }

    private static void requireArguments(CliOptions options, int count, String usage) {
        if (options.arguments().length < count) {
            throw new IllegalArgumentException("Usage: " + usage);
        }
    }

    private static void printHelp(PrintStream out) {
        out.println("""
                Vaultik CLI

                Commands:
                  set <key> <value> [--ttl seconds]      Store a string value
                  get <key>                              Read a value
                  delete <key>                           Delete a value
                  stats                                  Print engine counters
                  demo                                   Run a short persistence demo
                  shell                                  Start an interactive database shell
                  wal                                    Inspect the write-ahead log
                  engine-report                          Generate reports/engine-report.md
                  benchmark-report                       Generate reports/benchmark-report.md

                Options:
                  --data-dir <path>                      Default: data
                  --policy <LRU|LFU>                     Default: LRU
                  --capacity <number>                    Default: 10000
                  --snapshot-interval <number>           Default: 1000
                  --operations <number>                  Benchmark report only, default: 10000
                  --threads <csv>                        Benchmark report only, example: 1,4,8
                  --output <path>                        Benchmark report only
                """);
    }

    private record CliOptions(String command, String[] arguments, String[] raw) {
        static CliOptions parse(String[] args) {
            String command = args[0];
            String[] commandArguments = Arrays.stream(args, 1, args.length)
                    .filter(argument -> !argument.startsWith("--"))
                    .toArray(String[]::new);
            return new CliOptions(command, commandArguments, args);
        }

        String argument(int index) {
            return arguments[index];
        }

        String[] globalOptions() {
            return Arrays.stream(raw, 1, raw.length)
                    .filter(argument -> argument.startsWith("--") || isFlagValue(argument))
                    .toArray(String[]::new);
        }

        String stringFlag(String name, String defaultValue) {
            int index = indexOf(name);
            return index >= 0 && index + 1 < raw.length ? raw[index + 1] : defaultValue;
        }

        Integer intFlag(String name, int defaultValue) {
            return Math.toIntExact(longFlag(name, (long) defaultValue));
        }

        Long longFlag(String name) {
            int index = indexOf(name);
            return index >= 0 && index + 1 < raw.length ? Long.parseLong(raw[index + 1]) : null;
        }

        Long longFlag(String name, long defaultValue) {
            Long value = longFlag(name);
            return value == null ? defaultValue : value;
        }

        int[] intListFlag(String name, int[] defaultValue) {
            int index = indexOf(name);
            if (index < 0 || index + 1 >= raw.length) {
                return defaultValue;
            }
            return Arrays.stream(raw[index + 1].split(","))
                    .map(String::trim)
                    .filter(value -> !value.isBlank())
                    .mapToInt(Integer::parseInt)
                    .toArray();
        }

        private int indexOf(String flag) {
            for (int i = 0; i < raw.length; i++) {
                if (flag.equals(raw[i])) {
                    return i;
                }
            }
            return -1;
        }

        private boolean isFlagValue(String argument) {
            for (int i = 1; i < raw.length; i++) {
                if (argument.equals(raw[i]) && i > 0 && raw[i - 1].startsWith("--")) {
                    return true;
                }
            }
            return false;
        }
    }
}
