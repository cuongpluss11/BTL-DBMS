package com.btl.dbms.benchmark;

import java.util.Random;

/**
 * Zipfian Skew Generator (similar to YCSB)
 * Used to concentrate transactions onto a small subset of hot items (Flash-Sale simulation).
 * theta = 0.0 -> Uniform distribution
 * theta >= 0.8 -> Heavy skew / Flash-sale contention hotspot
 */
public class ZipfianGenerator {
    private final int items;
    private final double theta;
    private final double alpha;
    private final double zetan;
    private final double eta;
    private final Random random = new Random();

    public ZipfianGenerator(int items, double theta) {
        this.items = items;
        this.theta = theta;
        if (theta > 0.0) {
            this.alpha = 1.0 / (1.0 - theta);
            this.zetan = zeta(items, theta);
            this.eta = (1.0 - Math.pow(2.0 / items, 1.0 - theta)) / (1.0 - zeta(2, theta) / zetan);
        } else {
            this.alpha = 0;
            this.zetan = 0;
            this.eta = 0;
        }
    }

    private double zeta(int n, double theta) {
        double sum = 0;
        for (int i = 1; i <= n; i++) {
            sum += Math.pow(1.0 / i, theta);
        }
        return sum;
    }

    public int nextItemId() {
        if (theta <= 0.001) {
            return 1 + random.nextInt(items);
        }
        double u = random.nextDouble();
        double uz = u * zetan;
        if (uz < 1.0) {
            return 1;
        }
        if (uz < 1.0 + Math.pow(0.5, theta)) {
            return 2;
        }
        int item = 1 + (int) (items * Math.pow(eta * u - eta + 1.0, alpha));
        return Math.min(Math.max(item, 1), items);
    }
}

