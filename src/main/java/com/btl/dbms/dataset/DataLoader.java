package com.btl.dbms.dataset;

import com.btl.dbms.connection.ConnectionManager;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class DataLoader {
    private static final Logger log = LoggerFactory.getLogger(DataLoader.class);
    private static final int BATCH_SIZE = 1000;

    public static void loadRelational(String targetName, DataSource dataSource, int warehouses, int totalItems, int customersPerDist) {
        long start = System.currentTimeMillis();
        log.info(">>> Starting TPC-C Dataset Generation for {} (Warehouses={}, Items={}, Cust/Dist={})",
                targetName, warehouses, totalItems, customersPerDist);

        try (Connection conn = dataSource.getConnection();
             java.sql.Statement sStmt = conn.createStatement()) {
            try {
                sStmt.execute("SET FOREIGN_KEY_CHECKS = 0");
            } catch (Exception ignored) {}
            conn.setAutoCommit(false);

            // 1. Items
            log.info("[1/9] Loading Item table ({} items)...", totalItems);
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO item (i_id, i_im_id, i_name, i_price, i_data) VALUES (?, ?, ?, ?, ?)")) {
                for (int i = 1; i <= totalItems; i++) {
                    ps.setInt(1, i);
                    ps.setInt(2, TpccDataGenerator.getRandomInt(1, 10000));
                    ps.setString(3, TpccDataGenerator.getRandomString(14, 24));
                    ps.setBigDecimal(4, TpccDataGenerator.getRandomDecimal(1.0, 100.0, 2));
                    ps.setString(5, TpccDataGenerator.getRandomString(26, 50));
                    ps.addBatch();
                    if (i % BATCH_SIZE == 0 || i == totalItems) {
                        ps.executeBatch();
                    }
                }
            }
            conn.commit();

            // 2. Warehouses
            log.info("[2/9] Loading Warehouse table ({} warehouses)...", warehouses);
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO warehouse (w_id, w_name, w_street_1, w_street_2, w_city, w_state, w_zip, w_tax, w_ytd) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                for (int w = 1; w <= warehouses; w++) {
                    ps.setInt(1, w);
                    ps.setString(2, TpccDataGenerator.getRandomString(6, 10));
                    ps.setString(3, TpccDataGenerator.getRandomString(10, 20));
                    ps.setString(4, TpccDataGenerator.getRandomString(10, 20));
                    ps.setString(5, TpccDataGenerator.getRandomString(10, 20));
                    ps.setString(6, "VN");
                    ps.setString(7, TpccDataGenerator.getRandomNumeric(9));
                    ps.setBigDecimal(8, new BigDecimal("0.1000"));
                    ps.setBigDecimal(9, new BigDecimal("300000.00")); // Invariant 1 baseline
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            conn.commit();

            // 3. Stock
            log.info("[3/9] Loading Stock table ({} stock records)...", warehouses * totalItems);
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO stock (s_i_id, s_w_id, s_quantity, s_dist_01, s_dist_02, s_dist_03, s_dist_04, s_dist_05, " +
                            "s_dist_06, s_dist_07, s_dist_08, s_dist_09, s_dist_10, s_ytd, s_order_cnt, s_remote_cnt, s_data) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                int count = 0;
                for (int w = 1; w <= warehouses; w++) {
                    for (int i = 1; i <= totalItems; i++) {
                        ps.setInt(1, i);
                        ps.setInt(2, w);
                        ps.setInt(3, TpccDataGenerator.getRandomInt(10, 100)); // Initial stock
                        for (int d = 4; d <= 13; d++) {
                            ps.setString(d, TpccDataGenerator.getRandomString(24, 24));
                        }
                        ps.setBigDecimal(14, BigDecimal.ZERO);
                        ps.setInt(15, 0);
                        ps.setInt(16, 0);
                        ps.setString(17, TpccDataGenerator.getRandomString(26, 50));
                        ps.addBatch();
                        count++;
                        if (count % BATCH_SIZE == 0) {
                            ps.executeBatch();
                        }
                    }
                }
                ps.executeBatch();
            }
            conn.commit();

            // 4. District
            log.info("[4/9] Loading District table ({} districts)...", warehouses * 10);
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO district (d_id, d_w_id, d_name, d_street_1, d_street_2, d_city, d_state, d_zip, d_tax, d_ytd, d_next_o_id) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                for (int w = 1; w <= warehouses; w++) {
                    for (int d = 1; d <= 10; d++) {
                        ps.setInt(1, d);
                        ps.setInt(2, w);
                        ps.setString(3, TpccDataGenerator.getRandomString(6, 10));
                        ps.setString(4, TpccDataGenerator.getRandomString(10, 20));
                        ps.setString(5, TpccDataGenerator.getRandomString(10, 20));
                        ps.setString(6, TpccDataGenerator.getRandomString(10, 20));
                        ps.setString(7, "VN");
                        ps.setString(8, TpccDataGenerator.getRandomNumeric(9));
                        ps.setBigDecimal(9, new BigDecimal("0.1000"));
                        ps.setBigDecimal(10, new BigDecimal("30000.00")); // Sum for 10 districts = 300,000.00 (Invariant 1)
                        ps.setInt(11, customersPerDist + 1); // d_next_o_id starts after initial orders
                        ps.addBatch();
                    }
                }
                ps.executeBatch();
            }
            conn.commit();

            // 5. Customer & History
            log.info("[5/9] Loading Customer & History ({} customers)...", warehouses * 10 * customersPerDist);
            try (PreparedStatement psCust = conn.prepareStatement(
                    "INSERT INTO customer (c_id, c_d_id, c_w_id, c_first, c_middle, c_last, c_street_1, c_street_2, " +
                            "c_city, c_state, c_zip, c_phone, c_since, c_credit, c_credit_lim, c_discount, c_balance, " +
                            "c_ytd_payment, c_payment_cnt, c_delivery_cnt, c_data) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
                 PreparedStatement psHist = conn.prepareStatement(
                    "INSERT INTO history (h_c_id, h_c_d_id, h_c_w_id, h_d_id, h_w_id, h_date, h_amount, h_data) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {

                int totalCust = 0;
                for (int w = 1; w <= warehouses; w++) {
                    for (int d = 1; d <= 10; d++) {
                        for (int c = 1; c <= customersPerDist; c++) {
                            psCust.setInt(1, c);
                            psCust.setInt(2, d);
                            psCust.setInt(3, w);
                            psCust.setString(4, TpccDataGenerator.getRandomString(8, 16));
                            psCust.setString(5, "OE");
                            psCust.setString(6, TpccDataGenerator.getLastName(c % 1000));
                            psCust.setString(7, TpccDataGenerator.getRandomString(10, 20));
                            psCust.setString(8, TpccDataGenerator.getRandomString(10, 20));
                            psCust.setString(9, TpccDataGenerator.getRandomString(10, 20));
                            psCust.setString(10, "VN");
                            psCust.setString(11, TpccDataGenerator.getRandomNumeric(9));
                            psCust.setString(12, TpccDataGenerator.getRandomNumeric(16));
                            psCust.setTimestamp(13, TpccDataGenerator.getCurrentTimestamp());
                            psCust.setString(14, (c % 10 == 0) ? "BC" : "GC");
                            psCust.setBigDecimal(15, new BigDecimal("50000.00"));
                            psCust.setBigDecimal(16, TpccDataGenerator.getRandomDecimal(0.0, 0.5, 4));
                            psCust.setBigDecimal(17, new BigDecimal("-10.00"));
                            psCust.setBigDecimal(18, new BigDecimal("10.00"));
                            psCust.setInt(19, 1);
                            psCust.setInt(20, 0);
                            psCust.setString(21, TpccDataGenerator.getRandomString(300, 500));
                            psCust.addBatch();

                            psHist.setInt(1, c);
                            psHist.setInt(2, d);
                            psHist.setInt(3, w);
                            psHist.setInt(4, d);
                            psHist.setInt(5, w);
                            psHist.setTimestamp(6, TpccDataGenerator.getCurrentTimestamp());
                            psHist.setBigDecimal(7, new BigDecimal("10.00"));
                            psHist.setString(8, TpccDataGenerator.getRandomString(12, 24));
                            psHist.addBatch();

                            totalCust++;
                            if (totalCust % BATCH_SIZE == 0) {
                                psCust.executeBatch();
                                psHist.executeBatch();
                            }
                        }
                    }
                }
                psCust.executeBatch();
                psHist.executeBatch();
            }
            conn.commit();

            // 6. Orders and New_Order
            log.info("[6/9] Loading Orders and New_Order...");
            Timestamp now = TpccDataGenerator.getCurrentTimestamp();
            try (PreparedStatement psOrder = conn.prepareStatement(
                    "INSERT INTO orders (o_id, o_d_id, o_w_id, o_c_id, o_entry_d, o_carrier_id, o_ol_cnt, o_all_local) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?)");
                 PreparedStatement psNewOrder = conn.prepareStatement(
                    "INSERT INTO new_order (no_o_id, no_d_id, no_w_id) VALUES (?, ?, ?)")) {

                int orderBatchCount = 0;
                for (int w = 1; w <= warehouses; w++) {
                    for (int d = 1; d <= 10; d++) {
                        for (int o = 1; o <= customersPerDist; o++) {
                            int olCnt = 10;
                            boolean isNewOrder = (o > (customersPerDist * 7 / 10)); // Top 30% are new_orders

                            psOrder.setInt(1, o);
                            psOrder.setInt(2, d);
                            psOrder.setInt(3, w);
                            psOrder.setInt(4, o);
                            psOrder.setTimestamp(5, now);
                            if (isNewOrder) {
                                psOrder.setNull(6, java.sql.Types.TINYINT);
                            } else {
                                psOrder.setInt(6, TpccDataGenerator.getRandomInt(1, 10));
                            }
                            psOrder.setInt(7, olCnt);
                            psOrder.setInt(8, 1);
                            psOrder.addBatch();

                            if (isNewOrder) {
                                psNewOrder.setInt(1, o);
                                psNewOrder.setInt(2, d);
                                psNewOrder.setInt(3, w);
                                psNewOrder.addBatch();
                            }

                            orderBatchCount++;
                            if (orderBatchCount % BATCH_SIZE == 0) {
                                psOrder.executeBatch();
                                psNewOrder.executeBatch();
                            }
                        }
                    }
                }
                psOrder.executeBatch();
                psNewOrder.executeBatch();
            }
            conn.commit();

            // 7. Order_Line
            log.info("[7/9] Loading Order_Line...");
            try (PreparedStatement psLine = conn.prepareStatement(
                    "INSERT INTO order_line (ol_o_id, ol_d_id, ol_w_id, ol_number, ol_i_id, ol_supply_w_id, ol_delivery_d, ol_quantity, ol_amount, ol_dist_info) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                int lineBatchCount = 0;
                for (int w = 1; w <= warehouses; w++) {
                    for (int d = 1; d <= 10; d++) {
                        for (int o = 1; o <= customersPerDist; o++) {
                            int olCnt = 10;
                            boolean isNewOrder = (o > (customersPerDist * 7 / 10));

                            for (int line = 1; line <= olCnt; line++) {
                                psLine.setInt(1, o);
                                psLine.setInt(2, d);
                                psLine.setInt(3, w);
                                psLine.setInt(4, line);
                                psLine.setInt(5, TpccDataGenerator.getRandomInt(1, totalItems));
                                psLine.setInt(6, w);
                                psLine.setTimestamp(7, isNewOrder ? null : now);
                                psLine.setInt(8, 5);
                                psLine.setBigDecimal(9, new BigDecimal("49.95"));
                                psLine.setString(10, TpccDataGenerator.getRandomString(24, 24));
                                psLine.addBatch();
                                lineBatchCount++;

                                if (lineBatchCount % BATCH_SIZE == 0) {
                                    psLine.executeBatch();
                                }
                            }
                        }
                    }
                }
                psLine.executeBatch();
            }
            conn.commit();

            try {
                sStmt.execute("SET FOREIGN_KEY_CHECKS = 1");
            } catch (Exception ignored) {}

            log.info(">>> Successfully loaded TPC-C Dataset on {} in {} ms.", targetName, (System.currentTimeMillis() - start));
        } catch (Exception e) {
            log.error("Failed to load dataset on {}: {}", targetName, e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    public static void loadMongo(int warehouses, int totalItems, int customersPerDist) {
        long start = System.currentTimeMillis();
        log.info(">>> Starting TPC-C Dataset Generation for MongoDB (Warehouses={}, Items={}, Cust/Dist={})",
                warehouses, totalItems, customersPerDist);

        MongoDatabase db = ConnectionManager.getMongoDatabase();

        // 1. Items
        log.info("[1/9] Mongo: Loading Items ({})...", totalItems);
        MongoCollection<Document> itemCol = db.getCollection("item");
        itemCol.drop();
        List<Document> items = new ArrayList<>(BATCH_SIZE);
        for (int i = 1; i <= totalItems; i++) {
            items.add(new Document("_id", i)
                    .append("i_id", i)
                    .append("i_im_id", TpccDataGenerator.getRandomInt(1, 10000))
                    .append("i_name", TpccDataGenerator.getRandomString(14, 24))
                    .append("i_price", TpccDataGenerator.getRandomDecimal(1.0, 100.0, 2).doubleValue())
                    .append("i_data", TpccDataGenerator.getRandomString(26, 50)));
            if (items.size() >= BATCH_SIZE || i == totalItems) {
                itemCol.insertMany(items);
                items.clear();
            }
        }

        // 2. Warehouse
        log.info("[2/9] Mongo: Loading Warehouses ({})...", warehouses);
        MongoCollection<Document> wCol = db.getCollection("warehouse");
        wCol.drop();
        List<Document> wDocs = new ArrayList<>();
        for (int w = 1; w <= warehouses; w++) {
            wDocs.add(new Document("_id", w)
                    .append("w_id", w)
                    .append("w_name", TpccDataGenerator.getRandomString(6, 10))
                    .append("w_street_1", TpccDataGenerator.getRandomString(10, 20))
                    .append("w_street_2", TpccDataGenerator.getRandomString(10, 20))
                    .append("w_city", TpccDataGenerator.getRandomString(10, 20))
                    .append("w_state", "VN")
                    .append("w_zip", TpccDataGenerator.getRandomNumeric(9))
                    .append("w_tax", 0.10)
                    .append("w_ytd", 300000.00));
        }
        wCol.insertMany(wDocs);

        // 3. Stock
        log.info("[3/9] Mongo: Loading Stock ({})...", warehouses * totalItems);
        MongoCollection<Document> sCol = db.getCollection("stock");
        sCol.drop();
        List<Document> stockDocs = new ArrayList<>(BATCH_SIZE);
        for (int w = 1; w <= warehouses; w++) {
            for (int i = 1; i <= totalItems; i++) {
                stockDocs.add(new Document("_id", w + "_" + i)
                        .append("s_i_id", i)
                        .append("s_w_id", w)
                        .append("s_quantity", TpccDataGenerator.getRandomInt(10, 100))
                        .append("s_ytd", 0.0)
                        .append("s_order_cnt", 0)
                        .append("s_remote_cnt", 0)
                        .append("s_data", TpccDataGenerator.getRandomString(26, 50)));
                if (stockDocs.size() >= BATCH_SIZE) {
                    sCol.insertMany(stockDocs);
                    stockDocs.clear();
                }
            }
        }
        if (!stockDocs.isEmpty()) {
            sCol.insertMany(stockDocs);
            stockDocs.clear();
        }

        // 4. District
        log.info("[4/9] Mongo: Loading District ({})...", warehouses * 10);
        MongoCollection<Document> dCol = db.getCollection("district");
        dCol.drop();
        List<Document> distDocs = new ArrayList<>();
        for (int w = 1; w <= warehouses; w++) {
            for (int d = 1; d <= 10; d++) {
                distDocs.add(new Document("_id", w + "_" + d)
                        .append("d_id", d)
                        .append("d_w_id", w)
                        .append("d_name", TpccDataGenerator.getRandomString(6, 10))
                        .append("d_tax", 0.10)
                        .append("d_ytd", 30000.00)
                        .append("d_next_o_id", customersPerDist + 1));
            }
        }
        dCol.insertMany(distDocs);

        // 5. Customer
        log.info("[5/9] Mongo: Loading Customer ({})...", warehouses * 10 * customersPerDist);
        MongoCollection<Document> cCol = db.getCollection("customer");
        cCol.drop();
        List<Document> cDocs = new ArrayList<>(BATCH_SIZE);
        for (int w = 1; w <= warehouses; w++) {
            for (int d = 1; d <= 10; d++) {
                for (int c = 1; c <= customersPerDist; c++) {
                    cDocs.add(new Document("_id", w + "_" + d + "_" + c)
                            .append("c_id", c)
                            .append("c_d_id", d)
                            .append("c_w_id", w)
                            .append("c_first", TpccDataGenerator.getRandomString(8, 16))
                            .append("c_last", TpccDataGenerator.getLastName(c % 1000))
                            .append("c_balance", -10.00)
                            .append("c_ytd_payment", 10.00)
                            .append("c_payment_cnt", 1)
                            .append("c_delivery_cnt", 0));
                    if (cDocs.size() >= BATCH_SIZE) {
                        cCol.insertMany(cDocs);
                        cDocs.clear();
                    }
                }
            }
        }
        if (!cDocs.isEmpty()) {
            cCol.insertMany(cDocs);
            cDocs.clear();
        }

        // 6. Orders and Order_Line
        log.info("[6/9] Mongo: Loading Orders...");
        MongoCollection<Document> oCol = db.getCollection("orders");
        oCol.drop();
        MongoCollection<Document> noCol = db.getCollection("new_order");
        noCol.drop();
        MongoCollection<Document> olCol = db.getCollection("order_line");
        olCol.drop();

        List<Document> oDocs = new ArrayList<>(BATCH_SIZE);
        List<Document> noDocs = new ArrayList<>(BATCH_SIZE);
        List<Document> olDocs = new ArrayList<>(BATCH_SIZE);

        for (int w = 1; w <= warehouses; w++) {
            for (int d = 1; d <= 10; d++) {
                for (int o = 1; o <= customersPerDist; o++) {
                    int olCnt = TpccDataGenerator.getRandomInt(5, 15);
                    boolean isNewOrder = (o > (customersPerDist * 7 / 10));

                    oDocs.add(new Document("_id", w + "_" + d + "_" + o)
                            .append("o_id", o)
                            .append("o_d_id", d)
                            .append("o_w_id", w)
                            .append("o_c_id", o)
                            .append("o_carrier_id", isNewOrder ? null : 1)
                            .append("o_ol_cnt", olCnt)
                            .append("o_all_local", 1));

                    if (isNewOrder) {
                        noDocs.add(new Document("_id", w + "_" + d + "_" + o)
                                .append("no_o_id", o)
                                .append("no_d_id", d)
                                .append("no_w_id", w));
                    }

                    for (int line = 1; line <= olCnt; line++) {
                        olDocs.add(new Document("_id", w + "_" + d + "_" + o + "_" + line)
                                .append("ol_o_id", o)
                                .append("ol_d_id", d)
                                .append("ol_w_id", w)
                                .append("ol_number", line)
                                .append("ol_i_id", TpccDataGenerator.getRandomInt(1, totalItems))
                                .append("ol_quantity", 5)
                                .append("ol_amount", 49.95));
                    }

                    if (oDocs.size() >= BATCH_SIZE) {
                        oCol.insertMany(oDocs);
                        oDocs.clear();
                    }
                    if (noDocs.size() >= BATCH_SIZE) {
                        noCol.insertMany(noDocs);
                        noDocs.clear();
                    }
                    if (olDocs.size() >= BATCH_SIZE) {
                        olCol.insertMany(olDocs);
                        olDocs.clear();
                    }
                }
            }
        }
        if (!oDocs.isEmpty()) oCol.insertMany(oDocs);
        if (!noDocs.isEmpty()) noCol.insertMany(noDocs);
        if (!olDocs.isEmpty()) olCol.insertMany(olDocs);

        log.info(">>> Successfully loaded TPC-C Dataset on MongoDB in {} ms.", (System.currentTimeMillis() - start));
    }

    public static void loadRedisStockCounters(int warehouses, int totalItems) {
        log.info(">>> Syncing initial stock to Redis Cache (Flash-Sale buffer)...");
        try (Jedis jedis = ConnectionManager.getJedis()) {
            for (int w = 1; w <= warehouses; w++) {
                for (int i = 1; i <= Math.min(totalItems, 5000); i++) {
                    String key = "stock:" + w + ":" + i;
                    jedis.set(key, "100");
                }
            }
            log.info("Redis initial stock counters populated successfully.");
        } catch (Exception e) {
            log.warn("Redis stock cache sync skipped/notice: {}", e.getMessage());
        }
    }
}
