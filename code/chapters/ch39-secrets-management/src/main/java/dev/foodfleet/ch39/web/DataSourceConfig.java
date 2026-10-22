package dev.foodfleet.ch39.web;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.vault.core.lease.SecretLeaseContainer;

/**
 * order-service's DataSource: URL and pool settings from spring.datasource.*, credentials from OpenBao/Vault through
 * {@link VaultDatabaseCredentials}. No database password exists in configuration, environment or image.
 */
@Configuration(proxyBeanMethods = false)
public class DataSourceConfig {

    @Bean
    VaultDatabaseCredentials databaseCredentials(SecretLeaseContainer secretLeaseContainer,
            @Value("${app.database.credentials-path:database/creds/order-service}") String path) {
        return new VaultDatabaseCredentials(secretLeaseContainer, path);
    }

    @Bean
    HikariDataSource dataSource(DataSourceProperties properties, VaultDatabaseCredentials credentials) {
        HikariDataSource dataSource = properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
        dataSource.setCredentialsProvider(credentials);
        credentials.attach(dataSource);
        return dataSource;
    }
}
