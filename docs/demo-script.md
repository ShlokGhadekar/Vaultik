# Vaultik Demo Script

Use this as a short walkthrough for interviews, project reviews, or a GitHub video demo.

## 1. Open With The Pitch

Vaultik is a single-node in-memory key-value storage engine in Java. The interesting parts are not the REST endpoints; the interesting parts are WAL durability, snapshot recovery, TTL, LRU/LFU eviction, thread safety, and benchmarks.

## 2. Run Tests

```bash
mvn test
```

Talking point: the tests cover storage behavior, TTL, eviction, WAL replay, snapshot loading, concurrent access, API contracts, CLI behavior, and benchmark report generation.

## 3. Show The CLI

```bash
mvn -q exec:java -Dexec.mainClass=com.vaultik.cli.VaultikCli -Dexec.args="demo --data-dir /tmp/vaultik-demo"
```

Then show persistence across invocations:

```bash
mvn -q exec:java -Dexec.mainClass=com.vaultik.cli.VaultikCli -Dexec.args="set language java --data-dir /tmp/vaultik-demo"
mvn -q exec:java -Dexec.mainClass=com.vaultik.cli.VaultikCli -Dexec.args="get language --data-dir /tmp/vaultik-demo"
```

Talking point: each CLI command opens the engine, recovers from snapshot/WAL, performs the operation, and closes cleanly.

## 4. Show The Interactive Shell

```bash
mvn -q exec:java -Dexec.mainClass=com.vaultik.cli.VaultikCli -Dexec.args="shell --data-dir /tmp/vaultik-demo"
```

Inside the shell:

```text
vaultik> set user:1 shlok
vaultik> get user:1
vaultik> stats
vaultik> wal
vaultik> engine-report --output reports/engine-report.md
vaultik> exit
```

Talking point: the shell makes Vaultik feel like a tiny database console. The `wal` command shows the append-only durability log directly.

## 5. Generate An Engine Report

```bash
mvn -q exec:java -Dexec.mainClass=com.vaultik.cli.VaultikCli -Dexec.args="engine-report --data-dir /tmp/vaultik-demo --output reports/engine-report.md"
```

Talking point: the report gives a static view of the database internals: memory table size, eviction policy, counters, WAL file, snapshot file, and recent WAL records.

## 6. Show The REST API

```bash
mvn spring-boot:run
```

In another terminal:

```bash
curl -i -X POST http://localhost:8080/set \
  -H 'Content-Type: application/json' \
  -d '{"key":"language","value":"java","ttlSeconds":60}'

curl -i http://localhost:8080/get/language
curl -i http://localhost:8080/stats
```

Talking point: Spring is only an adapter. The storage engine does not depend on Spring.

## 7. Generate A Benchmark Report

```bash
mvn -q exec:java -Dexec.mainClass=com.vaultik.cli.VaultikCli -Dexec.args="benchmark-report --operations 10000 --threads 1,4,8 --output reports/benchmark-report.md"
```

Talking point: the report compares LRU and LFU under uniform and hot-key workloads, with throughput, average latency, p95, and p99 latency.

## 8. Explain The Recovery Path

```text
startup -> load snapshot -> replay WAL -> purge expired entries -> ready
```

Talking point: writes append to the WAL before memory changes. Snapshots cap replay time by compacting the current in-memory state.

## 9. Close With Tradeoffs

- Java serialization is simple but not ideal for production compatibility.
- Full snapshots are easy to reason about but can pause on larger datasets.
- `ReadWriteLock` is clear and interview-friendly, though `GET` can still need write access for TTL cleanup and eviction metadata.
- This is single-node by design; no replication, consensus, or distributed sharding.
