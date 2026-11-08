package com.btl.dbms.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.Properties;

public class DbConfig {
    private static final Logger log = LoggerFactory.getLogger(DbConfig.class);
    private static final Properties props = new Properties();

    static {
        try (InputStream is = DbConfig.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (is != null) {
                props.load(is);
            } else {
                log.warn("application.properties not found on classpath, using defaults.");
            }
        } catch (Exception e) {
            log.error("Failed to load application.properties", e);
        }
    }

    public static String get(String key, String defaultValue) {
        String envKey = key.toUpperCase().replace('.', '_');
        String envVal = System.getenv(envKey);
        if (envVal != null && !envVal.isBlank()) {
            return envVal;
        }
        return props.getProperty(key, defaultValue);
    }

    public static int getInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(get(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    // MySQL
    public static String getMySqlUrl() {
        return get("mysql.url", "jdbc:mysql://localhost:3307/tpcc?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&rewriteBatchedStatements=true");
    }

    public static String getMySqlUser() {
        return get("mysql.username", "root");
    }

    public static String getMySqlPassword() {
        return get("mysql.password", "rootpassword");
    }

    public static int getMySqlPoolSize() {
        return getInt("mysql.pool.size", 20);
    }

    // TiDB
    public static String getTiDbUrl() {
        return get("tidb.url", "jdbc:mysql://localhost:4000/tpcc?useSSL=false&serverTimezone=UTC&rewriteBatchedStatements=true");
    }

    public static String getTiDbUser() {
        return get("tidb.username", "root");
    }

    public static String getTiDbPassword() {
        return get("tidb.password", "");
    }

    public static int getTiDbPoolSize() {
        return getInt("tidb.pool.size", 20);
    }

    // MongoDB
    public static String getMongoUri() {
        return get("mongo.uri", "mongodb://localhost:27018/?replicaSet=rs0&directConnection=true");
    }

    public static String getMongoDatabase() {
        return get("mongo.database", "tpcc");
    }

    // Redis
    public static String getRedisHost() {
        return get("redis.host", "localhost");
    }

    public static int getRedisPort() {
        return getInt("redis.port", 6379);
    }

    // TPC-C Scale Defaults
    public static int getDefaultWarehouses() {
        return getInt("tpcc.warehouses", 1);
    }

    public static int getDefaultItems() {
        return getInt("tpcc.scale.items", 100000);
    }

    public static int getDefaultCustomersPerDistrict() {
        return getInt("tpcc.scale.customers", 3000);
    }
}

