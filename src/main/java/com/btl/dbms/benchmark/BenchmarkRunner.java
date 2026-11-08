package com.btl.dbms.benchmark;

import org.HdrHistogram.Histogram;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class BenchmarkRunner {
    private static final Logger log = LoggerFactory.getLogger(BenchmarkRunner.class);

    public interface ExecutorFactory {
        TpccExecutor create() throws Exception;
    }

    public static BenchmarkResult runBenchmark(String dbName, ExecutorFactory factory, int threads,
                                              int durationSeconds, int totalItems, int warehouses,
                                              double skewTheta) {
        log.info("Starting Benchmark for {} [Threads={}, Duration={}s, Warehouses={}, Items={}, SkewTheta={}]",
                dbName, threads, durationSeconds, warehouses, totalItems, skewTheta);

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        Histogram histogram = new Histogram(3600000000L, 3); // Up to 1 hour in microseconds
        AtomicBoolean running = new AtomicBoolean(true);
        AtomicLong successCount = new AtomicLong(0);
        AtomicLong abortCount = new AtomicLong(0);
        Map<TransactionType, AtomicLong> txBreakdown = new EnumMap<>(TransactionType.class);
        for (TransactionType t : TransactionType.values()) {
            txBreakdown.put(t, new AtomicLong(0));
        }

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            final int threadId = i;
            pool.submit(() -> {
                Random rand = new Random(42 + threadId);
                ZipfianGenerator zipfian = new ZipfianGenerator(totalItems, skewTheta);
                Histogram localHistogram = new Histogram(3600000000L, 3);

                try (TpccExecutor executor = factory.create()) {
                    startLatch.await();

                    while (running.get()) {
                        int roll = rand.nextInt(100) + 1;
                        TransactionType txType;
                        if (roll <= 45) txType = TransactionType.NEW_ORDER;
                        else if (roll <= 88) txType = TransactionType.PAYMENT;
                        else if (roll <= 92) txType = TransactionType.ORDER_STATUS;
                        else if (roll <= 96) txType = TransactionType.DELIVERY;
                        else txType = TransactionType.STOCK_LEVEL;

                        int wId = 1 + rand.nextInt(warehouses);
                        int dId = 1 + rand.nextInt(10);
                        int cId = 1 + rand.nextInt(300); // 300 customers baseline for tests

                        long txStart = System.nanoTime();
                        boolean ok = false;
                        try {
                            switch (txType) {
                                case NEW_ORDER -> {
                                    int olCnt = 5 + rand.nextInt(11);
                                    int[] itemIds = new int[olCnt];
                                    int[] supWIds = new int[olCnt];
                                    int[] quantities = new int[olCnt];
                                    for (int k = 0; k < olCnt; k++) {
                                        itemIds[k] = zipfian.nextItemId();
                                        supWIds[k] = (warehouses > 1 && rand.nextInt(100) == 0) ? (1 + rand.nextInt(warehouses)) : wId;
                                        quantities[k] = 1 + rand.nextInt(10);
                                    }
                                    ok = executor.executeNewOrder(wId, dId, cId, itemIds, supWIds, quantities);
                                }
                                case PAYMENT -> {
                                    int cWId = (warehouses > 1 && rand.nextInt(100) < 15) ? (1 + rand.nextInt(warehouses)) : wId;
                                    double amount = 10.0 + (rand.nextDouble() * 4990.0);
                                    ok = executor.executePayment(wId, dId, cWId, dId, cId, amount);
                                }
                                case ORDER_STATUS -> ok = executor.executeOrderStatus(wId, dId, cId);
                                case DELIVERY -> ok = executor.executeDelivery(wId, 1 + rand.nextInt(10));
                                case STOCK_LEVEL -> ok = executor.executeStockLevel(wId, dId, 10 + rand.nextInt(11));
                            }
                        } catch (Exception e) {
                            ok = false;
                        }

                        long txLatencyMicros = (System.nanoTime() - txStart) / 1000;
                        if (ok) {
                            successCount.incrementAndGet();
                            localHistogram.recordValue(Math.min(txLatencyMicros, 3600000000L));
                        } else {
                            abortCount.incrementAndGet();
                        }
                        txBreakdown.get(txType).incrementAndGet();
                    }
                } catch (Exception e) {
                    log.error("Thread {} aborted with error: {}", threadId, e.getMessage());
                } finally {
                    synchronized (histogram) {
                        histogram.add(localHistogram);
                    }
                    doneLatch.countDown();
                }
            });
        }

        // Release threads and wait for test duration
        long benchmarkStart = System.currentTimeMillis();
        startLatch.countDown();

        try {
            Thread.sleep(durationSeconds * 1000L);
        } catch (InterruptedException ignored) {}

        running.set(false);

        try {
            doneLatch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {}

        pool.shutdownNow();
        long actualDurationMs = System.currentTimeMillis() - benchmarkStart;
        double actualDurationSec = actualDurationMs / 1000.0;

        long totalSuccess = successCount.get();
        long totalAbort = abortCount.get();
        long totalTx = totalSuccess + totalAbort;
        double tps = actualDurationSec > 0 ? (totalSuccess / actualDurationSec) : 0;

        Map<TransactionType, Long> counts = new EnumMap<>(TransactionType.class);
        for (Map.Entry<TransactionType, AtomicLong> entry : txBreakdown.entrySet()) {
            counts.put(entry.getKey(), entry.getValue().get());
        }

        double minMs = histogram.getMinValue() / 1000.0;
        double meanMs = histogram.getMean() / 1000.0;
        double p50Ms = histogram.getValueAtPercentile(50.0) / 1000.0;
        double p90Ms = histogram.getValueAtPercentile(90.0) / 1000.0;
        double p95Ms = histogram.getValueAtPercentile(95.0) / 1000.0;
        double p99Ms = histogram.getValueAtPercentile(99.0) / 1000.0;
        double p999Ms = histogram.getValueAtPercentile(99.9) / 1000.0;
        double maxMs = histogram.getMaxValue() / 1000.0;

        return new BenchmarkResult(dbName, threads, actualDurationSec, totalTx, totalSuccess, totalAbort,
                tps, minMs, meanMs, p50Ms, p90Ms, p95Ms, p99Ms, p999Ms, maxMs, counts);
    }
}

