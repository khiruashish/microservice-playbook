package dev.foodfleet.ch31.reads;

import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Two connection pools: the primary (all writes, Flyway, and reads that must be fresh) and an asynchronous
 * replica (everything else). The primary DataSource is {@code @Primary}, so Spring Boot's JdbcClient,
 * transactions and Flyway use it; the replica gets its own JdbcClient.
 */
@Configuration
public class DataSources {

    @Bean
    @Primary
    DataSource primaryDataSource(@Value("${app.datasource.primary.url}") String url,
                                 @Value("${app.datasource.primary.username}") String user,
                                 @Value("${app.datasource.primary.password}") String password) {
        return DataSourceBuilder.create().url(url).username(user).password(password).build();
    }

    @Bean
    DataSource replicaDataSource(@Value("${app.datasource.replica.url}") String url,
                                 @Value("${app.datasource.replica.username}") String user,
                                 @Value("${app.datasource.replica.password}") String password) {
        return DataSourceBuilder.create().url(url).username(user).password(password).build();
    }

    @Bean
    @Qualifier("replica")
    JdbcClient replicaJdbcClient(@Qualifier("replicaDataSource") DataSource replica) {
        return JdbcClient.create(replica);
    }
}
