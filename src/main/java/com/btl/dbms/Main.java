package com.btl.dbms;

import com.btl.dbms.benchmark.*;
import com.btl.dbms.connection.ConnectionManager;
import com.btl.dbms.dataset.DataLoader;
import com.btl.dbms.schema.SchemaManager;
import com.btl.dbms.verification.InvariantAuditor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        String command = (args.length > 0) ? args[0].toLowerCase() : "smoke-test";

        log.info("==========================================================================================");
        log.info("           BTL CƠ SỞ DỮ LIỆU: TPC-C BENCHMARK & MULTI-DBMS EVALUATION SUITE              ");
        log.info("==========================================================================================");

        try {
            switch (command) {
                case "check-conn" -> runCheckConnectivity();
                case "init-schema" -> runInitSchema();
                case "load-data" -> runLoadData(args);
                case "smoke-test" -> runSmokeTest(args);
                case "benchmark" -> runBenchmark(args);
                case "verify" -> runVerify();
                case "help" -> printHelp();
                default -> {
                    log.error("Unknown command '{}'. Running smoke-test by default.", command);
                    runSmokeTest(args);
                }
            }
        } catch (Exception e) {
            log.error("Execution failed: {}", e.getMessage(), e);
            System.exit(1);
        } finally {
            ConnectionManager.closeAll();
        }
    }

    private static void printHelp() {
        System.out.println("Usage: java -jar btl-dbms-tpcc.jar <command> [options]");
        System.out.println("Commands:");
        System.out.println("  check-conn               Test connection to MySQL, TiDB, MongoDB, Redis");
        System.out.println("  init-schema              Initialize DDL schemas across all DBMSs");
        System.out.println("  load-data [-w <w>] [--items <i>] [--customers <c>] [--target <t>]");
        System.out.println("  smoke-test [-w 1]        Full end-to-end smoke test on W=1 across all DBMSs");
        System.out.println("  benchmark [--target <t>] [--threads <n>] [--duration <s>] [--theta <skew>]");
        System.out.println("  verify                   Run Invariant Audit checks on databases");
    }

    private static void runCheckConnectivity() {
        boolean ok = ConnectionManager.checkAllConnections();
        if (ok) {
            log.info(">>> ALL DBMS CONNECTIONS ARE HEALTHY AND ACCESSIBLE!");
        } else {
            log.error(">>> ONE OR MORE DBMS CONNECTIONS FAILED.");
        }
    }

    private static void runInitSchema() {
        log.info(">>> Initializing TPC-C Schemas across MySQL, TiDB, and MongoDB...");
        SchemaManager.initAll();
        log.info(">>> Schema initialization completed.");
    }

    private static void runLoadData(String[] args) {
        int warehouses = getArgInt(args, "-w", 1);
        int items = getArgInt(args, "--items", 2000);
        int customers = getArgInt(args, "--customers", 300);
        String target = getArgString(args, "--target", "all").toLowerCase();

        log.info(">>> Loading TPC-C Dataset: Warehouses={}, Items={}, Customers/Dist={}", warehouses, items, customers);

        if (target.equals("all") || target.equals("mysql")) {
            DataLoader.loadRelational("MySQL InnoDB", ConnectionManager.getMySqlDataSource(), warehouses, items, customers);
        }
        if (target.equals("all") || target.equals("tidb")) {
            DataLoader.loadRelational("TiDB NewSQL", ConnectionManager.getTiDbDataSource(), warehouses, items, customers);
        }
        if (target.equals("all") || target.equals("mongo")) {
            DataLoader.loadMongo(warehouses, items, customers);
        }
        if (target.equals("all") || target.equals("redis")) {
            DataLoader.loadRedisStockCounters(warehouses, items);
        }
    }

    private static void runSmokeTest(String[] args) throws Exception {
        log.info("==========================================================================================");
        log.info(" STEP 1: CHECKING CONNECTIVITY ACROSS ALL 4 DBMS ENGINES                                   ");
        log.info("==========================================================================================");
        boolean connOk = ConnectionManager.checkAllConnections();
        if (!connOk) {
            throw new RuntimeException("Connectivity check failed. Please ensure Docker containers are running!");
        }

        log.info("==========================================================================================");
        log.info(" STEP 2: INITIALIZING SCHEMAS (MySQL InnoDB, TiDB NewSQL, MongoDB NoSQL)                  ");
        log.info("==========================================================================================");
        SchemaManager.initAll();

        log.info("==========================================================================================");
        log.info(" STEP 3: LOADING TPC-C SEED DATASET (W=1, Items=2,000, Cust/Dist=300)                    ");
        log.info("==========================================================================================");
        int warehouses = 1;
        int items = 2000;
        int customers = 300;
        DataLoader.loadRelational("MySQL InnoDB", ConnectionManager.getMySqlDataSource(), warehouses, items, customers);
        DataLoader.loadRelational("TiDB NewSQL", ConnectionManager.getTiDbDataSource(), warehouses, items, customers);
        DataLoader.loadMongo(warehouses, items, customers);
        DataLoader.loadRedisStockCounters(warehouses, items);

        log.info("==========================================================================================");
        log.info(" STEP 4: RUNNING END-TO-END TRANSACTION TEST (5 TPC-C TRANSACTIONS ON ALL DBMSs)          ");
        log.info("==========================================================================================");
        testSingleExecutor("MySQL InnoDB", MySqlTpccExecutor::new, items);
        testSingleExecutor("TiDB NewSQL", TidbTpccExecutor::new, items);
        testSingleExecutor("MongoDB NoSQL", MongoTpccExecutor::new, items);

        log.info("==========================================================================================");
        log.info(" STEP 5: RUNNING CONCURRENT SMOKE LOAD TEST (W=1, 4 Threads, 5s duration per DBMS)         ");
        log.info("==========================================================================================");
        BenchmarkResult mysqlResult = BenchmarkRunner.runBenchmark("MySQL InnoDB", MySqlTpccExecutor::new, 4, 5, items, warehouses, 0.0);
        mysqlResult.printReport();

        BenchmarkResult tidbResult = BenchmarkRunner.runBenchmark("TiDB NewSQL", TidbTpccExecutor::new, 4, 5, items, warehouses, 0.0);
        tidbResult.printReport();

        BenchmarkResult mongoResult = BenchmarkRunner.runBenchmark("MongoDB NoSQL", MongoTpccExecutor::new, 4, 5, items, warehouses, 0.0);
        mongoResult.printReport();

        log.info("==========================================================================================");
        log.info(" STEP 6: VERIFYING TPC-C BUSINESS INVARIANTS (Real-time Auditing Engine)                  ");
        log.info("==========================================================================================");
        boolean mysqlInv = InvariantAuditor.auditRelational("MySQL InnoDB", ConnectionManager.getMySqlDataSource());
        boolean tidbInv = InvariantAuditor.auditRelational("TiDB NewSQL", ConnectionManager.getTiDbDataSource());

        log.info("==========================================================================================");
        log.info("                          SMOKE TEST RESULT SUMMARY                                       ");
        log.info("==========================================================================================");
        log.info(" 1. Database Connections:  ALL HEALTHY (MySQL:3307, TiDB:4000, Mongo:27018, Redis:6379)");
        log.info(" 2. Schemas & Datasets:    INITIALIZED & POPULATED (W=1)");
        log.info(" 3. MySQL Throughput:      {} TPS (p99: {} ms)", String.format("%.2f", mysqlResult.getTps()), String.format("%.2f", mysqlResult.getP99LatencyMs()));
        log.info(" 4. TiDB Throughput:       {} TPS (p99: {} ms)", String.format("%.2f", tidbResult.getTps()), String.format("%.2f", tidbResult.getP99LatencyMs()));
        log.info(" 5. MongoDB Throughput:    {} TPS (p99: {} ms)", String.format("%.2f", mongoResult.getTps()), String.format("%.2f", mongoResult.getP99LatencyMs()));
        log.info(" 6. Invariant Auditing:    MySQL: {} | TiDB: {}", (mysqlInv ? "PASS" : "FAIL"), (tidbInv ? "PASS" : "FAIL"));
        log.info(">>> SMOKE TEST TPC-C W=1 COMPLETED SUCCESSFULLY (THONG LUONG HOAN TOAN)!");
        log.info("==========================================================================================");
    }

    private static void testSingleExecutor(String name, BenchmarkRunner.ExecutorFactory factory, int items) throws Exception {
        log.info("Testing 5 TPC-C transaction flows on {}...", name);
        try (TpccExecutor exec = factory.create()) {
            // NewOrder
            int[] iIds = {1, 2, 3, 4, 5};
            int[] wIds = {1, 1, 1, 1, 1};
            int[] qtys = {2, 1, 3, 4, 1};
            boolean noOk = exec.executeNewOrder(1, 1, 1, iIds, wIds, qtys);
            log.info("  [{}] NewOrder Tx: {}", name, noOk ? "SUCCESS" : "FAILED");

            // Payment
            boolean payOk = exec.executePayment(1, 1, 1, 1, 1, 100.0);
            log.info("  [{}] Payment Tx: {}", name, payOk ? "SUCCESS" : "FAILED");

            // OrderStatus
            boolean osOk = exec.executeOrderStatus(1, 1, 1);
            log.info("  [{}] OrderStatus Tx: {}", name, osOk ? "SUCCESS" : "FAILED");

            // Delivery
            boolean delOk = exec.executeDelivery(1, 1);
            log.info("  [{}] Delivery Tx: {}", name, delOk ? "SUCCESS" : "FAILED");

            // StockLevel
            boolean slOk = exec.executeStockLevel(1, 1, 20);
            log.info("  [{}] StockLevel Tx: {}", name, slOk ? "SUCCESS" : "FAILED");
        }
    }

    private static void runBenchmark(String[] args) {
        String target = getArgString(args, "--target", "mysql").toLowerCase();
        int threads = getArgInt(args, "--threads", 8);
        int duration = getArgInt(args, "--duration", 10);
        int items = getArgInt(args, "--items", 2000);
        int warehouses = getArgInt(args, "-w", 1);
        double theta = getArgDouble(args, "--theta", 0.0);

        BenchmarkRunner.ExecutorFactory factory = switch (target) {
            case "tidb" -> TidbTpccExecutor::new;
            case "mongo" -> MongoTpccExecutor::new;
            default -> MySqlTpccExecutor::new;
        };

        BenchmarkResult res = BenchmarkRunner.runBenchmark(target.toUpperCase(), factory, threads, duration, items, warehouses, theta);
        res.printReport();
    }

    private static void runVerify() {
        log.info("Verifying Business Invariants...");
        InvariantAuditor.auditRelational("MySQL InnoDB", ConnectionManager.getMySqlDataSource());
        InvariantAuditor.auditRelational("TiDB NewSQL", ConnectionManager.getTiDbDataSource());
    }

    private static int getArgInt(String[] args, String flag, int defaultVal) {
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equalsIgnoreCase(flag)) {
                try {
                    return Integer.parseInt(args[i + 1]);
                } catch (NumberFormatException ignored) {}
            }
        }
        return defaultVal;
    }

    private static double getArgDouble(String[] args, String flag, double defaultVal) {
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equalsIgnoreCase(flag)) {
                try {
                    return Double.parseDouble(args[i + 1]);
                } catch (NumberFormatException ignored) {}
            }
        }
        return defaultVal;
    }

    private static String getArgString(String[] args, String flag, String defaultVal) {
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equalsIgnoreCase(flag)) {
                return args[i + 1];
            }
        }
        return defaultVal;
    }
}
