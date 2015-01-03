package br.edu.utfpr.minerador.preprocessor.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 *
 * @author Rodrigo T. Kuroda
 */
public class ConnectionFactory {

    private final HikariConfig config;
    private final HikariDataSource datasource;

    public ConnectionFactory() {
        config = new HikariConfig();
        config.setMaximumPoolSize(100);
        config.setDataSourceClassName("com.mysql.jdbc.jdbc2.optional.MysqlDataSource");
        config.addDataSourceProperty("serverName", "localhost");
        config.addDataSourceProperty("port", "3306");
        config.addDataSourceProperty("user", "root");
        config.addDataSourceProperty("password", "root");
        config.setConnectionTestQuery("SELECT 1");

        datasource = new HikariDataSource(config);
    }

    public ConnectionFactory(final String databaseName) {
        config = new HikariConfig();
        config.setMaximumPoolSize(100);
        config.setDataSourceClassName("com.mysql.jdbc.jdbc2.optional.MysqlDataSource");
        config.addDataSourceProperty("serverName", "localhost");
        config.addDataSourceProperty("port", "3306");
        if (databaseName != null) {
            config.addDataSourceProperty("databaseName", databaseName);
        }
        config.addDataSourceProperty("user", "root");
        config.addDataSourceProperty("password", "root");
        config.setConnectionTestQuery("SELECT 1");

        datasource = new HikariDataSource(config);
    }

    public Connection getConnection() throws SQLException {
        return datasource.getConnection();
    }

    public HikariConfig getConfig() {
        return config;
    }

    public HikariDataSource getDatasource() {
        return datasource;
    }

    public void close() {
        if (datasource != null) {
            datasource.close();
        }
    }
}
