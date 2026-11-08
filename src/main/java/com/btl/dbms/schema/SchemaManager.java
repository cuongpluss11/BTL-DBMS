package com.btl.dbms.schema;

import com.btl.dbms.connection.ConnectionManager;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class SchemaManager {
    private static final Logger log = LoggerFactory.getLogger(SchemaManager.class);

    public static void initMySqlSchema() {
        log.info("Initializing MySQL TPC-C Schema (schema-mysql.sql)...");
        executeSqlScript("schema/schema-mysql.sql", ConnectionManager.getMySqlDataSource());
        log.info("MySQL TPC-C Schema initialized successfully.");
    }

    public static void initTiDbSchema() {
        log.info("Initializing TiDB TPC-C Schema (schema-tidb.sql)...");
        executeSqlScript("schema/schema-tidb.sql", ConnectionManager.getTiDbDataSource());
        log.info("TiDB TPC-C Schema initialized successfully.");
    }

    public static void initMongoSchema() {
        log.info("Initializing MongoDB TPC-C Collections & Indexes...");
        MongoDatabase db = ConnectionManager.getMongoDatabase();

        String[] collections = {"warehouse", "district", "customer", "history", "orders", "new_order", "item", "stock", "order_line"};
        for (String col : collections) {
            boolean exists = false;
            for (String name : db.listCollectionNames()) {
                if (name.equalsIgnoreCase(col)) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                db.createCollection(col);
                log.info("Created collection '{}'", col);
            }
        }

        // Indexes for high-performance lookup matching Section 2.1.2 & 2.2
        try {
            db.getCollection("customer").createIndex(Indexes.ascending("c_w_id", "c_d_id", "c_last"));
            db.getCollection("customer").createIndex(Indexes.ascending("c_w_id", "c_d_id", "c_id"), new IndexOptions().unique(true));
            db.getCollection("stock").createIndex(Indexes.ascending("s_w_id", "s_i_id"), new IndexOptions().unique(true));
            db.getCollection("orders").createIndex(Indexes.ascending("o_w_id", "o_d_id", "o_id"), new IndexOptions().unique(true));
            db.getCollection("orders").createIndex(Indexes.ascending("o_w_id", "o_d_id", "o_c_id"));
            db.getCollection("new_order").createIndex(Indexes.ascending("no_w_id", "no_d_id", "no_o_id"), new IndexOptions().unique(true));
            db.getCollection("order_line").createIndex(Indexes.ascending("ol_w_id", "ol_d_id", "ol_o_id", "ol_number"), new IndexOptions().unique(true));
            log.info("MongoDB indexes verified/created successfully.");
        } catch (Exception e) {
            log.warn("MongoDB index creation notice: {}", e.getMessage());
        }
    }

    public static void initAll() {
        initMySqlSchema();
        initTiDbSchema();
        initMongoSchema();
    }

    private static void executeSqlScript(String resourcePath, javax.sql.DataSource dataSource) {
        try (InputStream is = SchemaManager.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new RuntimeException("Resource not found: " + resourcePath);
            }
            List<String> statements = parseSqlStatements(is);
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                for (String sql : statements) {
                    if (!sql.isBlank()) {
                        stmt.execute(sql);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to execute SQL script {}: {}", resourcePath, e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    private static List<String> parseSqlStatements(InputStream is) throws Exception {
        List<String> statements = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.startsWith("--") || trimmed.startsWith("#") || trimmed.isEmpty()) {
                    continue;
                }
                sb.append(line).append("\n");
                if (trimmed.endsWith(";")) {
                    String stmt = sb.toString().trim();
                    // Remove trailing semicolon
                    if (stmt.endsWith(";")) {
                        stmt = stmt.substring(0, stmt.length() - 1);
                    }
                    if (!stmt.isBlank()) {
                        statements.add(stmt);
                    }
                    sb.setLength(0);
                }
            }
        }
        if (sb.length() > 0 && !sb.toString().trim().isBlank()) {
            statements.add(sb.toString().trim());
        }
        return statements;
    }
}

