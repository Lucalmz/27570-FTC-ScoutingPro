package com.bear27570.ftc.scouting.repository;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.sqlobject.SqlObjectPlugin;

public class DatabaseManager {
    private static HikariDataSource dataSource;
    private static Jdbi jdbi;

    public static synchronized void initialize(String dbUrl) {
        if (dataSource == null) {
            // 1. 初始化高性能连接池
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(dbUrl);
            config.setUsername("sa");
            config.setPassword("");
            config.setMaximumPoolSize(25);
            config.setMinimumIdle(5);
            config.setConnectionTimeout(30000);
            dataSource = new HikariDataSource(config);

            // 2. Flyway 自动版本迁移，告别手工 ALTER TABLE
            Flyway flyway = Flyway.configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/migration")
                    .baselineOnMigrate(true)
                    .load();
            flyway.migrate();
            System.out.println("[DB] Flyway executed schema migrations successfully.");

            // 3. 初始化 JDBI 并安装 SQL Object 插件
            jdbi = Jdbi.create(dataSource);
            jdbi.installPlugin(new SqlObjectPlugin());
            System.out.println("[DB] HikariCP & JDBI Initialized successfully.");
        }
    }

    // 暴露 Jdbi 实例供 DAO 层使用
    public static Jdbi getJdbi() {
        if (jdbi == null) throw new IllegalStateException("DatabaseManager is not initialized!");
        return jdbi;
    }

    public static void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            System.out.println("[DB] Connections safely closed.");
        }
    }
}