# Performance

<!-- MEASUREMENTS_START -->
Measured in [performance CI](https://github.com/hamza2829/order-quality-lab/actions/runs/41856024222) on commit `73ce0b335163`.

| Tool | Order POST requests/s | Order POST p95 ms | Order POST error rate | Orders |
|---|---:|---:|---:|---:|
| k6 baseline | 62.43 | 10.65 | 0.000% | 3127 |
| k6 validation | 64.02 | 6.60 | 0.000% | 3206 |
| JMeter | 138.67 | 6.00 | 0.000% | 6924 |

Baseline p95 was 10.65 ms. The derived k6 validation budget was 50 ms. JMeter's overall sample error rate, including reads, was 0.000%.
<!-- MEASUREMENTS_END -->

The flow is one POST to place an order, one GET to read it and 100 ms of pacing. Each order contains one unit of the same synthetic product priced at EUR 12.50. Stock is reset to one billion units before each tool. A rejected-order probe runs outside the k6 samples. End to end functional tests cover rejected and multi-line orders.

k6 ramps from zero to 2, 8 and 16 users over 10, 15 and 15 seconds, then ramps to zero over 10 seconds. A 40-request JVM warmup precedes the baseline. The baseline measures p95; validation uses `max(50, ceil(2 * baseline_p95_ms / 10) * 10)` milliseconds as its p95 budget. Both runs require an order error rate below 1 percent and checks above 99 percent. The 50 ms minimum avoids treating timer noise as a service regression. The budget is relative to this runner, not a product SLO or a historical regression threshold.

JMeter uses 16 threads, a 10-second ramp and a 50-second scheduled duration. It performs the same create-and-read flow with 100 ms pacing per flow. Every create expects 201; every read expects 200 and PLACED. Its overall sample error rate must remain below 1 percent. The k6 p95 gate is not applied to JMeter because the load shapes differ.

Reported requests per second, p95 and error rate refer to the order POST only, excluding stock seeding, probes, warmup and reads. k6 supplies its own counter rate and percentile. JMeter throughput is POST count divided by the interval from the first POST start to the last POST finish; p95 is the nearest-rank percentile of POST elapsed times. Errors include failed status assertions. JMeter read failures are also checked through its overall sample error rate. k6 checks the reads separately.

The services each have a one-CPU limit and a 512 MiB memory limit. H2, the load generator and both JVMs share one GitHub-hosted machine. These are short synthetic tests. They do not measure production capacity, distributed latency, durability, long-term memory growth or recovery after a restart. Sequential tool runs also benefit from different JVM warmup. Raw runner details and service logs accompany every result.
