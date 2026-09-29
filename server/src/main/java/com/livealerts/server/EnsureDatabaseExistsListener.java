package com.livealerts.server;

import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

/**
 * MSSQL's official image, unlike e.g. Postgres's {@code POSTGRES_DB}, does not auto-create an
 * application database — only the built-in {@code master} exists on first start. This runs
 * before the Spring context (and its eager JPA datasource connection, which would otherwise
 * fail with "Cannot open database") is built: it connects to {@code master} and creates the app
 * database if missing, so the whole schema — database included — really is "created
 * automatically on first start" with no manual step or extra init container.
 */
class EnsureDatabaseExistsListener implements ApplicationListener<ApplicationEnvironmentPreparedEvent>, Ordered {

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        Environment env = event.getEnvironment();
        String host = env.getProperty("DB_HOST", "localhost");
        String port = env.getProperty("DB_PORT", "1433");
        String database = env.getProperty("DB_NAME", "LiveAlerts");
        String user = env.getProperty("DB_USER", "sa");
        String password = env.getProperty("DB_PASSWORD", "LiveAlerts!2026");

        String masterUrl = "jdbc:sqlserver://" + host + ":" + port
                + ";databaseName=master;encrypt=true;trustServerCertificate=true";

        try (Connection connection = DriverManager.getConnection(masterUrl, user, password);
             Statement statement = connection.createStatement()) {
            statement.execute("IF DB_ID('" + database + "') IS NULL CREATE DATABASE [" + database + "]");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to ensure database '" + database + "' exists", e);
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
