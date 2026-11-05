package com.example.library.util;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.Properties;

public final class Database {
    private static final Properties CONFIG = load();
    private Database() {}
    private static Properties load() {
        Properties config = new Properties();
        String file = System.getProperty("library.config", System.getenv("LIBRARY_DB_CONFIG"));
        if (file == null) file = "config/db.local.properties";
        Path path = Path.of(file);
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                config.load(reader);
                // Some PowerShell versions write a BOM when using UTF8.
                String bomKey = "\uFEFFdb.url";
                if (config.containsKey(bomKey)) config.setProperty("db.url", config.getProperty(bomKey));
            } catch (IOException e) { throw new IllegalStateException("Cannot read DB configuration", e); }
        }
        return config;
    }
    private static String setting(String env, String key, String fallback) {
        return System.getenv(env) != null ? System.getenv(env) : CONFIG.getProperty(key, fallback);
    }
    public static Connection open() throws SQLException {
        try { Class.forName("com.mysql.cj.jdbc.Driver"); }
        catch (ClassNotFoundException e) { throw new SQLException("MySQL JDBC driver unavailable", e); }
        return DriverManager.getConnection(setting("LIBRARY_DB_URL", "db.url", "jdbc:mysql://localhost:3306/libraryweb2026?characterEncoding=UTF-8&serverTimezone=Asia/Shanghai"),
                setting("LIBRARY_DB_USER", "db.user", "root"), setting("LIBRARY_DB_PASSWORD", "db.password", ""));
    }
    @FunctionalInterface public interface Work<T> { T run(Connection connection) throws Exception; }
    public static <T> T read(Work<T> work) throws Exception {
        try (Connection connection = open()) { return work.run(connection); }
    }
    public static <T> T transaction(Work<T> work) throws Exception {
        try (Connection connection = open()) {
            connection.setAutoCommit(false);
            try { T result = work.run(connection); connection.commit(); return result; }
            catch (Exception e) { connection.rollback(); throw e; }
        }
    }
}
