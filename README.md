# Vaultik

Vaultik is a concurrent, persistent in-memory key-value storage engine written in Java 21 with a thin Spring Boot REST API. It is intentionally single-node and built for learning storage internals: concurrency, eviction, write-ahead logging, snapshots, recovery, and benchmarking.

Vaultik is not a Redis clone. It avoids clustering, replication, consensus, leader election, and distributed sharding so the implementation can stay focused on local storage-engine mechanics.

## Features

- String keys with arbitrary Java `Serializable` values
- `SET`, `GET`, and `DELETE`
- Optional TTL with lazy expiration on access
- Capacity-based eviction with pluggable policies
- LRU and LFU eviction implementations
- Append-only write-ahead log before every mutation
- Snapshot checkpointing and startup recovery
- Spring Boot REST API with DTOs and global error handling
- Standalone CLI for demos without running the web server
- Interactive database shell for terminal walkthroughs
- WAL inspector and engine report generator for storage introspection
- Custom benchmark harness plus a JMH benchmark class
- Markdown benchmark report generator
- JUnit 5 coverage for storage behavior, recovery, concurrency, persistence, API contracts, and benchmark sanity

## Architecture

```text
                    +--------------------+
HTTP clients  ----> | Spring REST API    |
                    | api/ config/       |
                    +---------+----------+
                              |
                              v
                    +--------------------+
                    | KeyValueStore API  |
                    | core/              |
                    +---------+----------+
                              |
                              v
          +-------------------+-------------------+
          | PersistentKeyValueStore               |
          | storage/                              |
          | - HashMap memory table                |
          | - ReadWriteLock concurrency control   |
          | - TTL checks                          |
          | - Eviction coordination               |
          +----------+----------------+-----------+
                     |                |
                     v                v
           +----------------+   +------------------+
           | EvictionPolicy |   | Persistence      |
           | eviction/      |   | persistence/     |
           | LRU / LFU      |   | WAL / Snapshot   |
           +----------------+   +------------------+
```

## Directory Structure

```text
src/main/java/com/vaultik
  api/          REST controllers, DTOs, error handling
  benchmark/    Benchmark reports, harness, JMH benchmark
  cli/          Command-line demo and benchmark entrypoint
  config/       Spring configuration and properties
  core/         Framework-independent store interfaces and records
  eviction/     Eviction strategy interface, LRU, LFU
  persistence/  WAL and snapshot abstractions/implementations
  storage/      Persistent in-memory engine
  util/         Small I/O helpers
```

## Engineering Decisions

The storage engine is framework-independent. Spring only builds and exposes it over HTTP. This keeps the domain model testable without booting an application context and mirrors how real infrastructure projects separate core engines from adapters.

The primary in-memory structure is a `HashMap<String, StoredValue>`. `StoredValue` is immutable and carries both the value and optional expiration timestamp.

Eviction uses the Strategy Pattern. `EvictionPolicy` defines the contract, while `LruEvictionPolicy` and `LfuEvictionPolicy` encapsulate policy-specific bookkeeping.

## Concurrency Model

Vaultik uses `ReadWriteLock` instead of `synchronized` methods or a single explicit mutex because read-heavy key-value workloads benefit from independently modeling read and write critical sections. Per-key locking is intentionally avoided: it can improve parallel writes, but makes eviction, snapshotting, and recovery invariants harder to explain and verify.

`GET` may acquire the write side of the lock because it can mutate engine metadata: LRU/LFU access state and lazy TTL cleanup. That tradeoff favors correctness and a compact design over maximum read throughput.

## Persistence Model

Every write is appended to the WAL before memory is changed:

```text
SET key=value
  |
  v
append WAL record
  |
  v
update memory table
  |
  v
update eviction metadata
  |
  v
checkpoint snapshot if interval is reached
```

WAL records are length-prefixed serialized objects. During replay, Vaultik stops cleanly at a partial trailing record, which is a common recovery technique for append-only logs.

Snapshots serialize the current memory table. After a successful checkpoint, the WAL is truncated because the snapshot now contains the latest durable state.

## Recovery Process

```text
startup
  |
  v
load snapshot
  |
  v
rebuild eviction metadata
  |
  v
replay WAL records after snapshot
  |
  v
drop expired records
  |
  v
ready
```

## GET Sequence

```text
client -> REST API -> KeyValueStore.get(key)
                            |
                            v
                    acquire lock
                            |
              +-------------+-------------+
              |                           |
        key missing                key present
              |                           |
        record miss              check TTL
                                          |
                                expired? delete + miss
                                          |
                                live? update eviction + hit
```

## REST API

Run the service:

```bash
mvn spring-boot:run
```

Set a value:

```bash
curl -i -X POST http://localhost:8080/set \
  -H 'Content-Type: application/json' \
  -d '{"key":"language","value":"java","ttlSeconds":60}'
```

Get a value:

```bash
curl -i http://localhost:8080/get/language
```

Delete a value:

```bash
curl -i -X DELETE http://localhost:8080/delete/language
```

Stats and health:

```bash
curl http://localhost:8080/stats
curl http://localhost:8080/health
```

## Configuration

```yaml
vaultik:
  capacity: 10000
  eviction-policy: LRU
  wal-path: data/vaultik.wal
  snapshot-path: data/vaultik.snapshot
  snapshot-interval: 1000
```

Switch `eviction-policy` between `LRU` and `LFU`.

## CLI Demo

The CLI exercises the storage engine directly, without Spring Boot. This is the fastest way to demonstrate the core system.

```bash
mvn -q exec:java -Dexec.mainClass=com.vaultik.cli.VaultikCli -Dexec.args="--help"
```

Set, get, delete, and inspect stats:

```bash
mvn -q exec:java -Dexec.mainClass=com.vaultik.cli.VaultikCli -Dexec.args="set project vaultik"
mvn -q exec:java -Dexec.mainClass=com.vaultik.cli.VaultikCli -Dexec.args="get project"
mvn -q exec:java -Dexec.mainClass=com.vaultik.cli.VaultikCli -Dexec.args="stats"
mvn -q exec:java -Dexec.mainClass=com.vaultik.cli.VaultikCli -Dexec.args="delete project"
```

Run a quick scripted demo:

```bash
mvn -q exec:java -Dexec.mainClass=com.vaultik.cli.VaultikCli -Dexec.args="demo"
```

Start an interactive database shell:

```bash
mvn -q exec:java -Dexec.mainClass=com.vaultik.cli.VaultikCli -Dexec.args="shell"
```

Example shell session:

```text
vaultik> set user:1 shlok
SET user:1 = shlok
vaultik> get user:1
user:1 = shlok
vaultik> stats
size=1 capacity=10000 hits=1 misses=0 evictions=0 writes=0 deletes=0
vaultik> wal
WAL: data/vaultik.wal
Records: 1
vaultik> exit
```

Use a separate data directory when you want a clean demo:

```bash
mvn -q exec:java -Dexec.mainClass=com.vaultik.cli.VaultikCli -Dexec.args="set language java --data-dir /tmp/vaultik-demo"
mvn -q exec:java -Dexec.mainClass=com.vaultik.cli.VaultikCli -Dexec.args="get language --data-dir /tmp/vaultik-demo"
```

Inspect the write-ahead log:

```bash
mvn -q exec:java -Dexec.mainClass=com.vaultik.cli.VaultikCli -Dexec.args="wal"
```

Generate a storage-engine report:

```bash
mvn -q exec:java -Dexec.mainClass=com.vaultik.cli.VaultikCli -Dexec.args="engine-report --output reports/engine-report.md"
```

A sample engine report is included at [reports/engine-report.md](reports/engine-report.md). It summarizes the memory table, eviction counters, WAL file, snapshot file, and the WAL tail.

## Benchmarks

Vaultik includes a custom benchmark harness that reports throughput, average latency, p95, and p99 latency across:

- single-thread and multi-thread runs
- uniform and hot-key access patterns
- LRU and LFU eviction

The `VaultikJmhBenchmark` class is also included for JMH-based measurement.

Generate a Markdown report:

```bash
mvn -q exec:java -Dexec.mainClass=com.vaultik.cli.VaultikCli -Dexec.args="benchmark-report --operations 10000 --threads 1,4,8 --output reports/benchmark-report.md"
```

A small sample report is included at [reports/benchmark-report.md](reports/benchmark-report.md). Regenerate it on your own machine before using numbers in a resume, presentation, or README badge.

Example report format:

```text
policy pattern threads operations throughput_ops_sec avg_us p95_us p99_us
LRU    UNIFORM 1       50000      42000              21.4   42.7   88.1
LRU    HOT_KEY 8       50000      98000              76.2   180.4  330.9
LFU    UNIFORM 1       50000      39000              23.0   47.6   91.5
LFU    HOT_KEY 8       50000      91000              82.9   194.2  355.0
```

These numbers are illustrative. Real measurements depend on hardware, JVM flags, filesystem behavior, and whether WAL writes are directed to persistent storage.

## Testing

```bash
mvn test
```

Current coverage includes:

- `SET`, `GET`, `DELETE`
- TTL expiration
- LRU and LFU eviction
- WAL replay
- snapshot loading
- concurrent access
- persistence primitives
- benchmark report correctness
- REST status codes and response payloads
- CLI persistence flow
- interactive shell command execution
- WAL inspection
- engine report generation
- benchmark report generation

## Tradeoffs

- Java serialization keeps the project compact, but a production engine would use a stable explicit binary format.
- WAL appends open and close the file per operation for readability. A production build would keep a channel open and tune fsync policy.
- TTL cleanup is lazy. A background sweeper could reduce memory retained by expired cold keys.
- The lock model favors explainability over maximum parallelism.
- Snapshots are full copies. Incremental checkpointing would reduce pause time and write amplification.

## Future Improvements

- Binary codec with checksums and schema versioning
- Configurable fsync durability modes
- Background TTL sweeper
- Compacted WAL segments
- Async snapshot writer
- Metrics integration
- Optional off-heap value storage experiments

## Interview Discussion Points

- Why WAL must be written before mutating memory
- How snapshot + WAL replay bounds recovery time
- Why eviction policies are strategies
- Why `GET` may still mutate engine metadata
- Where `ReadWriteLock` helps and where it does not
- How LFU tie-breaking affects determinism
- What would change for crash consistency, checksums, and fsync
- Why distributed features are explicitly out of scope
