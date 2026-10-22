package dev.foodfleet.ch04.zones.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import dev.foodfleet.ch04.zones.zone.ServiceabilityService;
import dev.foodfleet.ch04.zones.zone.ServiceabilityService.Serviceability;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(ServiceabilityController.class)
class ServiceabilityControllerTest {

    @MockitoBean
    ServiceabilityService service;

    private final MockMvcTester mvc;

    ServiceabilityControllerTest(@Autowired MockMvcTester mvc) {
        this.mvc = mvc;
    }

    @Test
    void returnsServiceability() {
        given(service.check("560034"))
                .willReturn(new Serviceability("560034", "Koramangala", "Bengaluru", true, 2_900L));

        assertThat(mvc.get().uri("/api/v1/serviceability/{pincode}", "560034"))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"pincode": "560034", "areaName": "Koramangala", "serviceable": true, "deliveryFeePaise": 2900}
                        """);
    }

    @Test
    void malformedPincodeIs400() {
        assertThat(mvc.get().uri("/api/v1/serviceability/{pincode}", "56003")).hasStatus(HttpStatus.BAD_REQUEST);
    }
}
