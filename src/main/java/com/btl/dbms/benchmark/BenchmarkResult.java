package com.btl.dbms.benchmark;

import java.util.Map;

public class BenchmarkResult {
    private final String dbName;
    private final int threads;
    private final double durationSeconds;
    private final long totalTx;
    private final long successTx;
    private final long abortTx;
    private final double tps;
    private final double minLatencyMs;
    private final double meanLatencyMs;
    private final double p50LatencyMs;
    private final double p90LatencyMs;
    private final double p95LatencyMs;
    private final double p99LatencyMs;
    private final double p999LatencyMs;
    private final double maxLatencyMs;
    private final Map<TransactionType, Long> txCounts;

    public BenchmarkResult(String dbName, int threads, double durationSeconds, long totalTx, long successTx, long abortTx,
                           double tps, double minLatencyMs, double meanLatencyMs, double p50LatencyMs, double p90LatencyMs,
                           double p95LatencyMs, double p99LatencyMs, double p999LatencyMs, double maxLatencyMs,
                           Map<TransactionType, Long> txCounts) {
        this.dbName = dbName;
        this.threads = threads;
        this.durationSeconds = durationSeconds;
        this.totalTx = totalTx;
        this.successTx = successTx;
        this.abortTx = abortTx;
        this.tps = tps;
        this.minLatencyMs = minLatencyMs;
        this.meanLatencyMs = meanLatencyMs;
        this.p50LatencyMs = p50LatencyMs;
        this.p90LatencyMs = p90LatencyMs;
        this.p95LatencyMs = p95LatencyMs;
        this.p99LatencyMs = p99LatencyMs;
        this.p999LatencyMs = p999LatencyMs;
        this.maxLatencyMs = maxLatencyMs;
        this.txCounts = txCounts;
    }

    public void printReport() {
        System.out.println("==========================================================================================");
        System.out.printf(" BENCHMARK REPORT: %s (Threads: %d, Duration: %.1fs)\n", dbName, threads, durationSeconds);
        System.out.println("==========================================================================================");
        System.out.printf(" Throughput:          %,.2f TPS\n", tps);
        System.out.printf(" Total Transactions:  %,d (Success: %,d, Abort/Fail: %,d - %.2f%%)\n",
                totalTx, successTx, abortTx, (totalTx > 0 ? (abortTx * 100.0 / totalTx) : 0.0));
        System.out.println("------------------------------------------------------------------------------------------");
        System.out.println(" Latency Percentiles (ms):");
        System.out.printf("   Min:    %8.2f ms    |  Mean:   %8.2f ms\n", minLatencyMs, meanLatencyMs);
        System.out.printf("   p50:    %8.2f ms    |  p90:    %8.2f ms\n", p50LatencyMs, p90LatencyMs);
        System.out.printf("   p95:    %8.2f ms    |  p99:    %8.2f ms\n", p95LatencyMs, p99LatencyMs);
        System.out.printf("   p99.9:  %8.2f ms    |  Max:    %8.2f ms\n", p999LatencyMs, maxLatencyMs);
        System.out.println("------------------------------------------------------------------------------------------");
        System.out.println(" Transaction Breakdown:");
        for (Map.Entry<TransactionType, Long> entry : txCounts.entrySet()) {
            double pct = totalTx > 0 ? (entry.getValue() * 100.0 / totalTx) : 0;
            System.out.printf("   %-15s: %,8d (%.1f%%)\n", entry.getKey(), entry.getValue(), pct);
        }
        System.out.println("==========================================================================================");
    }

    public String getDbName() { return dbName; }
    public double getTps() { return tps; }
    public long getSuccessTx() { return successTx; }
    public long getAbortTx() { return abortTx; }
    public double getP99LatencyMs() { return p99LatencyMs; }
}

