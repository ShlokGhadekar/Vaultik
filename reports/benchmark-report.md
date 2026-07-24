# Vaultik Benchmark Report

- Generated at: `2026-07-13T15:29:53.422827Z`
- Java: `21.0.11`
- JVM: `OpenJDK 64-Bit Server VM`
- Available processors: `8`
- Operations per scenario: `10000`

## Results

| Policy | Pattern | Threads | Operations | Throughput ops/sec | Avg us | p95 us | p99 us |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |
| LRU | UNIFORM | 1 | 10000 | 208333.33 | 4.46 | 34.79 | 49.29 |
| LRU | UNIFORM | 4 | 10000 | 215287.57 | 18.31 | 125.50 | 228.13 |
| LRU | UNIFORM | 8 | 10000 | 245100.79 | 32.19 | 315.67 | 469.08 |
| LRU | HOT_KEY | 1 | 10000 | 247568.41 | 3.90 | 35.96 | 48.92 |
| LRU | HOT_KEY | 4 | 10000 | 191000.08 | 20.65 | 49.71 | 680.29 |
| LRU | HOT_KEY | 8 | 10000 | 200118.91 | 39.46 | 47.63 | 1548.42 |
| LFU | UNIFORM | 1 | 10000 | 214608.58 | 4.56 | 39.08 | 50.17 |
| LFU | UNIFORM | 4 | 10000 | 193923.57 | 20.01 | 46.63 | 653.79 |
| LFU | UNIFORM | 8 | 10000 | 198532.51 | 39.86 | 47.63 | 1957.63 |
| LFU | HOT_KEY | 1 | 10000 | 227317.29 | 4.32 | 41.54 | 49.08 |
| LFU | HOT_KEY | 4 | 10000 | 213671.22 | 18.51 | 43.58 | 60.58 |
| LFU | HOT_KEY | 8 | 10000 | 209488.98 | 37.70 | 44.67 | 61.67 |

## How To Read This

The benchmark runs a mixed workload with about 90% reads and 10% writes. `UNIFORM` spreads requests across the keyspace. `HOT_KEY` concentrates traffic on a smaller key range. The numbers are best used for comparing policies and access patterns on the same machine, not as universal performance claims.

## Demo Command

```bash
mvn -q exec:java -Dexec.mainClass=com.vaultik.cli.VaultikCli -Dexec.args="benchmark-report --operations 10000"
```
