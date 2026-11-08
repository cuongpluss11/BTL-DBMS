package com.btl.dbms.benchmark;

import com.btl.dbms.connection.ConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.*;

public class MySqlTpccExecutor implements TpccExecutor {
    private static final Logger log = LoggerFactory.getLogger(MySqlTpccExecutor.class);
    private final Connection conn;

    public MySqlTpccExecutor() throws SQLException {
        this.conn = ConnectionManager.getMySqlConnection();
        this.conn.setAutoCommit(false);
    }

    public MySqlTpccExecutor(Connection conn) throws SQLException {
        this.conn = conn;
        this.conn.setAutoCommit(false);
    }

    @Override
    public String getDbName() {
        return "MySQL-InnoDB";
    }

    @Override
    public boolean executeNewOrder(int wId, int dId, int cId, int[] itemIds, int[] supplierWIds, int[] quantities) throws Exception {
        try {
            // 1. Get warehouse tax
            double wTax = 0;
            try (PreparedStatement ps = conn.prepareStatement("SELECT w_tax FROM warehouse WHERE w_id = ?")) {
                ps.setInt(1, wId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) wTax = rs.getDouble(1);
                }
            }

            // 2. Lock & update district next order ID
            int nextOId = 0;
            double dTax = 0;
            try (PreparedStatement ps = conn.prepareStatement("SELECT d_tax, d_next_o_id FROM district WHERE d_w_id = ? AND d_id = ? FOR UPDATE")) {
                ps.setInt(1, wId);
                ps.setInt(2, dId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        dTax = rs.getDouble(1);
                        nextOId = rs.getInt(2);
                    }
                }
            }

            try (PreparedStatement ps = conn.prepareStatement("UPDATE district SET d_next_o_id = d_next_o_id + 1 WHERE d_w_id = ? AND d_id = ?")) {
                ps.setInt(1, wId);
                ps.setInt(2, dId);
                ps.executeUpdate();
            }

            // 3. Get customer info
            double cDiscount = 0;
            try (PreparedStatement ps = conn.prepareStatement("SELECT c_discount FROM customer WHERE c_w_id = ? AND c_d_id = ? AND c_id = ?")) {
                ps.setInt(1, wId);
                ps.setInt(2, dId);
                ps.setInt(3, cId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) cDiscount = rs.getDouble(1);
                }
            }

            // 4. Insert orders & new_order
            int allLocal = 1;
            for (int sw : supplierWIds) {
                if (sw != wId) allLocal = 0;
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO orders (o_id, o_d_id, o_w_id, o_c_id, o_entry_d, o_ol_cnt, o_all_local) VALUES (?, ?, ?, ?, NOW(), ?, ?)")) {
                ps.setInt(1, nextOId);
                ps.setInt(2, dId);
                ps.setInt(3, wId);
                ps.setInt(4, cId);
                ps.setInt(5, itemIds.length);
                ps.setInt(6, allLocal);
                ps.executeUpdate();
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO new_order (no_o_id, no_d_id, no_w_id) VALUES (?, ?, ?)")) {
                ps.setInt(1, nextOId);
                ps.setInt(2, dId);
                ps.setInt(3, wId);
                ps.executeUpdate();
            }

            // 5. Update stock and insert order lines
            for (int i = 0; i < itemIds.length; i++) {
                int itemId = itemIds[i];
                int supWId = supplierWIds[i];
                int qty = quantities[i];

                double price = 0;
                try (PreparedStatement ps = conn.prepareStatement("SELECT i_price FROM item WHERE i_id = ?")) {
                    ps.setInt(1, itemId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) price = rs.getDouble(1);
                    }
                }

                // Lock stock row (contention point!)
                int sQty = 0;
                try (PreparedStatement ps = conn.prepareStatement("SELECT s_quantity FROM stock WHERE s_w_id = ? AND s_i_id = ? FOR UPDATE")) {
                    ps.setInt(1, supWId);
                    ps.setInt(2, itemId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) sQty = rs.getInt(1);
                    }
                }

                int newQty = (sQty >= qty + 10) ? (sQty - qty) : (sQty - qty + 91);
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE stock SET s_quantity = ?, s_ytd = s_ytd + ?, s_order_cnt = s_order_cnt + 1 WHERE s_w_id = ? AND s_i_id = ?")) {
                    ps.setInt(1, newQty);
                    ps.setDouble(2, qty * price);
                    ps.setInt(3, supWId);
                    ps.setInt(4, itemId);
                    ps.executeUpdate();
                }

                double amount = qty * price * (1 - cDiscount) * (1 + wTax + dTax);
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO order_line (ol_o_id, ol_d_id, ol_w_id, ol_number, ol_i_id, ol_supply_w_id, ol_delivery_d, ol_quantity, ol_amount, ol_dist_info) " +
                                "VALUES (?, ?, ?, ?, ?, ?, NULL, ?, ?, 'default_info')")) {
                    ps.setInt(1, nextOId);
                    ps.setInt(2, dId);
                    ps.setInt(3, wId);
                    ps.setInt(4, i + 1);
                    ps.setInt(5, itemId);
                    ps.setInt(6, supWId);
                    ps.setInt(7, qty);
                    ps.setBigDecimal(8, BigDecimal.valueOf(amount));
                    ps.executeUpdate();
                }
            }

            conn.commit();
            return true;
        } catch (Exception e) {
            safeRollback();
            throw e;
        }
    }

    @Override
    public boolean executePayment(int wId, int dId, int cWId, int cDId, int cId, double amount) throws Exception {
        try {
            // 1. Update warehouse YTD
            try (PreparedStatement ps = conn.prepareStatement("UPDATE warehouse SET w_ytd = w_ytd + ? WHERE w_id = ?")) {
                ps.setDouble(1, amount);
                ps.setInt(2, wId);
                ps.executeUpdate();
            }

            // 2. Update district YTD
            try (PreparedStatement ps = conn.prepareStatement("UPDATE district SET d_ytd = d_ytd + ? WHERE d_w_id = ? AND d_id = ?")) {
                ps.setDouble(1, amount);
                ps.setInt(2, wId);
                ps.setInt(3, dId);
                ps.executeUpdate();
            }

            // 3. Update customer balance
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE customer SET c_balance = c_balance - ?, c_ytd_payment = c_ytd_payment + ?, c_payment_cnt = c_payment_cnt + 1 " +
                            "WHERE c_w_id = ? AND c_d_id = ? AND c_id = ?")) {
                ps.setDouble(1, amount);
                ps.setDouble(2, amount);
                ps.setInt(3, cWId);
                ps.setInt(4, cDId);
                ps.setInt(5, cId);
                ps.executeUpdate();
            }

            // 4. Insert history
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO history (h_c_id, h_c_d_id, h_c_w_id, h_d_id, h_w_id, h_date, h_amount, h_data) " +
                            "VALUES (?, ?, ?, ?, ?, NOW(), ?, 'PaymentTx')")) {
                ps.setInt(1, cId);
                ps.setInt(2, cDId);
                ps.setInt(3, cWId);
                ps.setInt(4, dId);
                ps.setInt(5, wId);
                ps.setDouble(6, amount);
                ps.executeUpdate();
            }

            conn.commit();
            return true;
        } catch (Exception e) {
            safeRollback();
            throw e;
        }
    }

    @Override
    public boolean executeOrderStatus(int wId, int dId, int cId) throws Exception {
        try {
            // Customer read
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT c_first, c_middle, c_last, c_balance FROM customer WHERE c_w_id = ? AND c_d_id = ? AND c_id = ?")) {
                ps.setInt(1, wId);
                ps.setInt(2, dId);
                ps.setInt(3, cId);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                }
            }

            // Last order for customer
            int lastOId = 0;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT o_id, o_entry_d, o_carrier_id FROM orders WHERE o_w_id = ? AND o_d_id = ? AND o_c_id = ? ORDER BY o_id DESC LIMIT 1")) {
                ps.setInt(1, wId);
                ps.setInt(2, dId);
                ps.setInt(3, cId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) lastOId = rs.getInt(1);
                }
            }

            if (lastOId > 0) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT ol_i_id, ol_supply_w_id, ol_quantity, ol_amount, ol_delivery_d FROM order_line WHERE ol_w_id = ? AND ol_d_id = ? AND ol_o_id = ?")) {
                    ps.setInt(1, wId);
                    ps.setInt(2, dId);
                    ps.setInt(3, lastOId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {}
                    }
                }
            }

            conn.commit();
            return true;
        } catch (Exception e) {
            safeRollback();
            throw e;
        }
    }

    @Override
    public boolean executeDelivery(int wId, int carrierId) throws Exception {
        try {
            for (int dId = 1; dId <= 10; dId++) {
                int noOId = -1;
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT no_o_id FROM new_order WHERE no_w_id = ? AND no_d_id = ? ORDER BY no_o_id ASC LIMIT 1 FOR UPDATE")) {
                    ps.setInt(1, wId);
                    ps.setInt(2, dId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) noOId = rs.getInt(1);
                    }
                }

                if (noOId != -1) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "DELETE FROM new_order WHERE no_w_id = ? AND no_d_id = ? AND no_o_id = ?")) {
                        ps.setInt(1, wId);
                        ps.setInt(2, dId);
                        ps.setInt(3, noOId);
                        ps.executeUpdate();
                    }

                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE orders SET o_carrier_id = ? WHERE o_w_id = ? AND o_d_id = ? AND o_id = ?")) {
                        ps.setInt(1, carrierId);
                        ps.setInt(2, wId);
                        ps.setInt(3, dId);
                        ps.setInt(4, noOId);
                        ps.executeUpdate();
                    }

                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE order_line SET ol_delivery_d = NOW() WHERE ol_w_id = ? AND ol_d_id = ? AND ol_o_id = ?")) {
                        ps.setInt(1, wId);
                        ps.setInt(2, dId);
                        ps.setInt(3, noOId);
                        ps.executeUpdate();
                    }

                    double totalAmount = 0;
                    try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT SUM(ol_amount) FROM order_line WHERE ol_w_id = ? AND ol_d_id = ? AND ol_o_id = ?")) {
                        ps.setInt(1, wId);
                        ps.setInt(2, dId);
                        ps.setInt(3, noOId);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) totalAmount = rs.getDouble(1);
                        }
                    }

                    int cId = 0;
                    try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT o_c_id FROM orders WHERE o_w_id = ? AND o_d_id = ? AND o_id = ?")) {
                        ps.setInt(1, wId);
                        ps.setInt(2, dId);
                        ps.setInt(3, noOId);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) cId = rs.getInt(1);
                        }
                    }

                    // Update customer balance
                    if (cId > 0) {
                        try (PreparedStatement ps = conn.prepareStatement(
                                "UPDATE customer SET c_balance = c_balance + ?, c_delivery_cnt = c_delivery_cnt + 1 " +
                                        "WHERE c_w_id = ? AND c_d_id = ? AND c_id = ?")) {
                            ps.setDouble(1, totalAmount);
                            ps.setInt(2, wId);
                            ps.setInt(3, dId);
                            ps.setInt(4, cId);
                            ps.executeUpdate();
                        }
                    }
                }
            }

            conn.commit();
            return true;
        } catch (Exception e) {
            safeRollback();
            throw e;
        }
    }

    @Override
    public boolean executeStockLevel(int wId, int dId, int threshold) throws Exception {
        try {
            int nextOId = 0;
            try (PreparedStatement ps = conn.prepareStatement("SELECT d_next_o_id FROM district WHERE d_w_id = ? AND d_id = ?")) {
                ps.setInt(1, wId);
                ps.setInt(2, dId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) nextOId = rs.getInt(1);
                }
            }

            int count = 0;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(DISTINCT s_i_id) FROM order_line JOIN stock ON s_i_id = ol_i_id AND s_w_id = ol_w_id " +
                            "WHERE ol_w_id = ? AND ol_d_id = ? AND ol_o_id BETWEEN (? - 20) AND (? - 1) AND s_quantity < ?")) {
                ps.setInt(1, wId);
                ps.setInt(2, dId);
                ps.setInt(3, nextOId);
                ps.setInt(4, nextOId);
                ps.setInt(5, threshold);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) count = rs.getInt(1);
                }
            }

            conn.commit();
            return true;
        } catch (Exception e) {
            safeRollback();
            throw e;
        }
    }

    private void safeRollback() {
        try {
            if (conn != null && !conn.isClosed() && !conn.getAutoCommit()) {
                conn.rollback();
            }
        } catch (Exception ignored) {}
    }

    @Override
    public void close() throws Exception {
        if (conn != null && !conn.isClosed()) {
            conn.close();
        }
    }
}
