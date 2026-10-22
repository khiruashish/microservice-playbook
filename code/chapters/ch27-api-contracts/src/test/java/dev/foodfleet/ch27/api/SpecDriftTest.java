package dev.foodfleet.ch27.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.yaml.snakeyaml.Yaml;

/**
 * The published OpenAPI document and the code must describe the same operations: an endpoint
 * added without updating the contract, or a documented one that no longer exists, fails the build.
 */
@WebMvcTest(OrderController.class)
class SpecDriftTest {

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping mappings;

    @Test
    void everyDocumentedOperationIsImplementedAndViceVersa() throws Exception {
        Set<String> documented = new TreeSet<>();
        try (InputStream in = getClass().getResourceAsStream("/static/contracts/order-api.yaml")) {
            Map<String, Object> spec = new Yaml().load(in);
            @SuppressWarnings("unchecked")
            Map<String, Map<String, Object>> paths = (Map<String, Map<String, Object>>) spec.get("paths");
            paths.forEach((path, operations) -> operations.keySet().forEach(method ->
                    documented.add(method.toUpperCase() + " " + path)));
        }
        Set<String> implemented = new TreeSet<>();
        mappings.getHandlerMethods().forEach((info, handler) -> {
            if (handler.getBeanType() == OrderController.class) {
                info.getMethodsCondition().getMethods().forEach(method ->
                        info.getPatternValues().forEach(pattern -> implemented.add(method.name() + " " + pattern)));
            }
        });

        assertThat(implemented).isEqualTo(documented);
    }
}
