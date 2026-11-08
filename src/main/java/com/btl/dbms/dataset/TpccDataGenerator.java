package com.btl.dbms.dataset;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.util.Random;

public class TpccDataGenerator {
    private static final Random random = new Random(42);

    private static final String[] LAST_NAMES = {
        "BAR", "OUGHT", "ABLE", "PRI", "PRES", "ESE", "ANTI", "CALLY", "ATION", "EING"
    };

    public static String getLastName(int num) {
        return LAST_NAMES[num / 100] + LAST_NAMES[(num / 10) % 10] + LAST_NAMES[num % 10];
    }

    public static String getRandomString(int minLen, int maxLen) {
        int len = minLen + random.nextInt(maxLen - minLen + 1);
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            char c = (char) ('a' + random.nextInt(26));
            sb.append(c);
        }
        return sb.toString();
    }

    public static String getRandomNumeric(int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }

    public static BigDecimal getRandomDecimal(double min, double max, int scale) {
        double val = min + (random.nextDouble() * (max - min));
        return BigDecimal.valueOf(val).setScale(scale, RoundingMode.HALF_UP);
    }

    public static int getRandomInt(int min, int max) {
        return min + random.nextInt(max - min + 1);
    }

    public static Timestamp getCurrentTimestamp() {
        return new Timestamp(System.currentTimeMillis());
    }
}

