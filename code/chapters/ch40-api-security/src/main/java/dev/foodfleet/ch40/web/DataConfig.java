package dev.foodfleet.ch40.web;

import dev.foodfleet.ch40.tenancy.TenantTransactions;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class DataConfig {

    @Bean
    TenantTransactions tenantTransactions(DataSource dataSource) {
        return new TenantTransactions(dataSource);
    }
}
