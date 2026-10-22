package dev.foodfleet.ch08.eta.config;

import java.net.URI;
import java.time.ZonedDateTime;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.accept.ApiVersionDeprecationHandler;
import org.springframework.web.accept.StandardApiVersionDeprecationHandler;
import tools.jackson.databind.MapperFeature;

@Configuration(proxyBeanMethods = false)
public class WebConfiguration {

    public static final String V1_DEPRECATED = "2026-07-01T00:00:00Z";
    public static final String V1_SUNSET = "2026-12-31T00:00:00Z";

    /**
     * API version 1.0 is deprecated: every v1 response carries {@code Deprecation} (RFC 9745),
     * {@code Sunset} (RFC 8594) and a {@code Link} to the migration guide. Spring Boot plugs this
     * bean into its API versioning auto-configuration; controllers stay unaware of it.
     */
    @Bean
    ApiVersionDeprecationHandler apiVersionDeprecationHandler() {
        StandardApiVersionDeprecationHandler handler = new StandardApiVersionDeprecationHandler();
        handler.configureVersion("1.0")
                .setDeprecationDate(ZonedDateTime.parse(V1_DEPRECATED))
                .setSunsetDate(ZonedDateTime.parse(V1_SUNSET))
                .setDeprecationLink(URI.create("https://handbook.foodfleet.dev/apis/eta#migrating-to-v2"));
        return handler;
    }

    /**
     * Jackson 3 sorts properties alphabetically by default. We prefer record-component order in API
     * responses (it reads like the documentation), so we switch that one default back.
     */
    @Bean
    JsonMapperBuilderCustomizer declarationOrderProperties() {
        return builder -> builder.disable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY);
    }
}
