package com.btl.dbms.verification;

import com.btl.dbms.connection.ConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Automated Invariant Auditing Engine (Bám sát Section 2.6.2 - Đề xuất 3)
 */
public class InvariantAuditor {
    private static final Logger log = LoggerFactory.getLogger(InvariantAuditor.class);

    public static boolean auditRelational(String targetName, DataSource dataSource) {
        log.info("------------------------------------------------------------------------------------------");
        log.info("RUNNING TPC-C REAL-TIME INVARIANT AUDIT: {}", targetName);
        log.info("------------------------------------------------------------------------------------------");

        boolean allPassed = true;

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            // Invariant 1: Warehouse YTD vs Sum of 10 District YTD
            // Equation: SUM_{d_id=1}^{10} district.d_ytd == warehouse.w_ytd
            String sqlInv1 = "SELECT w.w_id, w.w_ytd, ROUND(SUM(d.d_ytd), 2) AS sum_d_ytd " +
                    "FROM warehouse w " +
                    "JOIN district d ON w.w_id = d.d_w_id " +
                    "GROUP BY w.w_id, w.w_ytd";

            boolean inv1Passed = true;
            try (ResultSet rs = stmt.executeQuery(sqlInv1)) {
                while (rs.next()) {
                    int wId = rs.getInt(1);
                    BigDecimal wYtd = rs.getBigDecimal(2);
                    BigDecimal sumDYtd = rs.getBigDecimal(3);
                    if (wYtd == null || sumDYtd == null || wYtd.compareTo(sumDYtd) != 0) {
                        log.error("[FAIL] Invariant 1 Violated on W_ID={}: warehouse.w_ytd ({}) != sum(district.d_ytd) ({})",
                                wId, wYtd, sumDYtd);
                        inv1Passed = false;
                        allPassed = false;
                    }
                }
            }
            if (inv1Passed) {
                log.info("[PASS] Invariant 1 (Warehouse & District Revenue Balance): Validated successfully!");
            }

            // Invariant 2: Next Order ID Consistency
            // Equation: district.d_next_o_id - 1 == MAX(orders.o_id)
            String sqlInv2 = "SELECT d.d_w_id, d.d_id, (d.d_next_o_id - 1) AS expected_o_id, " +
                    "COALESCE(MAX(o.o_id), 0) AS max_orders_o_id " +
                    "FROM district d " +
                    "LEFT JOIN orders o ON d.d_w_id = o.o_w_id AND d.d_id = o.o_d_id " +
                    "GROUP BY d.d_w_id, d.d_id, d.d_next_o_id";

            boolean inv2Passed = true;
            try (ResultSet rs = stmt.executeQuery(sqlInv2)) {
                while (rs.next()) {
                    int wId = rs.getInt(1);
                    int dId = rs.getInt(2);
                    int expected = rs.getInt(3);
                    int maxOrder = rs.getInt(4);
                    if (expected != maxOrder) {
                        log.error("[FAIL] Invariant 2 Violated on W={}, D={}: (d_next_o_id - 1) = {} != max(orders.o_id) = {}",
                                wId, dId, expected, maxOrder);
                        inv2Passed = false;
                        allPassed = false;
                    }
                }
            }
            if (inv2Passed) {
                log.info("[PASS] Invariant 2 (Order ID Monotonic Sequence): Validated successfully!");
            }

            // Invariant 3: Order Lines exist and match Orders
            String sqlInv3 = "SELECT COUNT(*) FROM orders o " +
                    "LEFT JOIN order_line ol ON o.o_w_id = ol.ol_w_id AND o.o_d_id = ol.ol_d_id AND o.o_id = ol.ol_o_id " +
                    "WHERE ol.ol_number IS NULL";
            try (ResultSet rs = stmt.executeQuery(sqlInv3)) {
                if (rs.next()) {
                    int orphaned = rs.getInt(1);
                    if (orphaned > 0) {
                        log.error("[FAIL] Invariant 3 Violated: Found {} orders without order_lines!", orphaned);
                        allPassed = false;
                    } else {
                        log.info("[PASS] Invariant 3 (Referential Integrity Orders <-> OrderLines): Validated successfully!");
                    }
                }
            }

        } catch (Exception e) {
            log.error("Invariant audit failed with exception: {}", e.getMessage(), e);
            allPassed = false;
        }

        log.info("------------------------------------------------------------------------------------------");
        log.info("AUDIT SUMMARY FOR {}: {}", targetName, (allPassed ? "ALL INVARIANTS SATISFIED [PASS]" : "VIOLATIONS DETECTED [FAIL]"));
        log.info("------------------------------------------------------------------------------------------");
        return allPassed;
    }
}

