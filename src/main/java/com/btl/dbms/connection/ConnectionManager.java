package com.btl.dbms.connection;

import com.btl.dbms.config.DbConfig;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class ConnectionManager {
    private static final Logger log = LoggerFactory.getLogger(ConnectionManager.class);

    private static volatile HikariDataSource mySqlDataSource;
    private static volatile HikariDataSource tiDbDataSource;
    private static volatile MongoClient mongoClient;
    private static volatile JedisPool jedisPool;

    // MySQL Connection Pool
    public static HikariDataSource getMySqlDataSource() {
        if (mySqlDataSource == null) {
            synchronized (ConnectionManager.class) {
                if (mySqlDataSource == null) {
                    HikariConfig config = new HikariConfig();
                    config.setPoolName("MySQL-InnoDB-Pool");
                    config.setJdbcUrl(DbConfig.getMySqlUrl());
                    config.setUsername(DbConfig.getMySqlUser());
                    config.setPassword(DbConfig.getMySqlPassword());
                    config.setMaximumPoolSize(DbConfig.getMySqlPoolSize());
                    config.setMinimumIdle(2);
                    config.setConnectionTimeout(10000);
                    config.setIdleTimeout(60000);
                    config.addDataSourceProperty("cachePrepStmts", "true");
                    config.addDataSourceProperty("prepStmtCacheSize", "250");
                    config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
                    mySqlDataSource = new HikariDataSource(config);
                    log.info("Initialized MySQL HikariCP pool at {}", DbConfig.getMySqlUrl());
                }
            }
        }
        return mySqlDataSource;
    }

    public static Connection getMySqlConnection() throws SQLException {
        return getMySqlDataSource().getConnection();
    }

    // TiDB Connection Pool
    public static HikariDataSource getTiDbDataSource() {
        if (tiDbDataSource == null) {
            synchronized (ConnectionManager.class) {
                if (tiDbDataSource == null) {
                    HikariConfig config = new HikariConfig();
                    config.setPoolName("TiDB-Distributed-Pool");
                    config.setConnectionInitSql("SET tidb_txn_mode = 'pessimistic'");
                    config.setJdbcUrl(DbConfig.getTiDbUrl());
                    config.setUsername(DbConfig.getTiDbUser());
                    config.setPassword(DbConfig.getTiDbPassword());
                    config.setMaximumPoolSize(DbConfig.getTiDbPoolSize());
                    config.setMinimumIdle(2);
                    config.setConnectionTimeout(10000);
                    config.setIdleTimeout(60000);
                    config.addDataSourceProperty("cachePrepStmts", "true");
                    config.addDataSourceProperty("prepStmtCacheSize", "250");
                    config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
                    tiDbDataSource = new HikariDataSource(config);
                    log.info("Initialized TiDB HikariCP pool at {}", DbConfig.getTiDbUrl());
                }
            }
        }
        return tiDbDataSource;
    }

    public static Connection getTiDbConnection() throws SQLException {
        return getTiDbDataSource().getConnection();
    }

    // MongoDB Client
    public static MongoClient getMongoClient() {
        if (mongoClient == null) {
            synchronized (ConnectionManager.class) {
                if (mongoClient == null) {
                    mongoClient = MongoClients.create(DbConfig.getMongoUri());
                    log.info("Initialized MongoDB Client at {}", DbConfig.getMongoUri());
                }
            }
        }
        return mongoClient;
    }

    public static MongoDatabase getMongoDatabase() {
        return getMongoClient().getDatabase(DbConfig.getMongoDatabase());
    }

    // Redis Pool
    public static JedisPool getJedisPool() {
        if (jedisPool == null) {
            synchronized (ConnectionManager.class) {
                if (jedisPool == null) {
                    JedisPoolConfig poolConfig = new JedisPoolConfig();
                    poolConfig.setMaxTotal(30);
                    poolConfig.setMaxIdle(10);
                    poolConfig.setMinIdle(2);
                    jedisPool = new JedisPool(poolConfig, DbConfig.getRedisHost(), DbConfig.getRedisPort(), 3000);
                    log.info("Initialized Redis JedisPool at {}:{}", DbConfig.getRedisHost(), DbConfig.getRedisPort());
                }
            }
        }
        return jedisPool;
    }

    public static Jedis getJedis() {
        return getJedisPool().getResource();
    }

    // Smoke Connectivity Check
    public static boolean checkAllConnections() {
        boolean allOk = true;

        log.info("=================================================================");
        log.info("CHECKING DBMS CONNECTIVITY AND STATUS");
        log.info("=================================================================");

        // 1. Check MySQL
        try (Connection conn = getMySqlConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT VERSION()")) {
            if (rs.next()) {
                log.info("[OK] MySQL InnoDB: Connected successfully! Version: {}", rs.getString(1));
            }
        } catch (Exception e) {
            log.error("[FAIL] MySQL InnoDB connection failed: {}", e.getMessage());
            allOk = false;
        }

        // 2. Check TiDB
        try (Connection conn = getTiDbConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT tidb_version()")) {
            if (rs.next()) {
                String fullVer = rs.getString(1);
                String firstLine = fullVer.split("\n")[0];
                log.info("[OK] TiDB NewSQL: Connected successfully! {}", firstLine);
            }
        } catch (Exception e) {
            log.error("[FAIL] TiDB NewSQL connection failed: {}", e.getMessage());
            allOk = false;
        }

        // 3. Check MongoDB
        try {
            Document ping = getMongoDatabase().runCommand(new Document("ping", 1));
            Document rsStatus = null;
            try {
                rsStatus = getMongoClient().getDatabase("admin").runCommand(new Document("replSetGetStatus", 1));
            } catch (Exception ignored) {}
            String rsName = rsStatus != null ? rsStatus.getString("set") : "standalone";
            log.info("[OK] MongoDB NoSQL: Connected successfully! Ping: {}, ReplicaSet: {}", ping.getDouble("ok"), rsName);
        } catch (Exception e) {
            log.error("[FAIL] MongoDB NoSQL connection failed: {}", e.getMessage());
            allOk = false;
        }

        // 4. Check Redis
        try (Jedis jedis = getJedis()) {
            String pong = jedis.ping();
            log.info("[OK] Redis In-Memory: Connected successfully! PING -> {}", pong);
        } catch (Exception e) {
            log.error("[FAIL] Redis connection failed: {}", e.getMessage());
            allOk = false;
        }

        log.info("=================================================================");
        return allOk;
    }

    public static void closeAll() {
        if (mySqlDataSource != null) {
            mySqlDataSource.close();
            mySqlDataSource = null;
        }
        if (tiDbDataSource != null) {
            tiDbDataSource.close();
            tiDbDataSource = null;
        }
        if (mongoClient != null) {
            mongoClient.close();
            mongoClient = null;
        }
        if (jedisPool != null) {
            jedisPool.close();
            jedisPool = null;
        }
        log.info("All connection pools closed.");
    }
}
