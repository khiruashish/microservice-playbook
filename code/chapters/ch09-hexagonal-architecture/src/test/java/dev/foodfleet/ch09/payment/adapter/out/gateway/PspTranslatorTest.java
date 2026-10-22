package dev.foodfleet.ch09.payment.adapter.out.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.foodfleet.ch09.payment.adapter.out.gateway.PspClient.PspPayment;
import dev.foodfleet.ch09.payment.application.port.out.PaymentGateway.Approved;
import dev.foodfleet.ch09.payment.application.port.out.PaymentGateway.Declined;
import dev.foodfleet.ch09.payment.application.port.out.PaymentGateway.GatewayUnavailableException;
import org.junit.jupiter.api.Test;

class PspTranslatorTest {

    @Test
    void authorizedBecomesApprovedWithTheProvidersReference() {
        assertThat(PspTranslator.toAuthorization(new PspPayment("pk_123", "authorized", null)))
                .isEqualTo(new Approved("pk_123"));
    }

    @Test
    void providerErrorCodesBecomeCustomerLanguage() {
        assertThat(PspTranslator.toAuthorization(new PspPayment("pk_1", "failed", "UPI_COLLECT_EXPIRED")))
                .isEqualTo(new Declined("The UPI request expired before it was approved"));
        assertThat(PspTranslator.toAuthorization(new PspPayment("pk_2", "failed", "SOMETHING_NEW")))
                .isEqualTo(new Declined("Declined by the payment provider"));
    }

    @Test
    void anUndecidedOrUnknownStatusIsNeverTreatedAsApproved() {
        assertThatThrownBy(() -> PspTranslator.toAuthorization(new PspPayment("pk_3", "created", null)))
                .isInstanceOf(GatewayUnavailableException.class);
        assertThatThrownBy(() -> PspTranslator.toAuthorization(new PspPayment("pk_4", "authorised", null)))
                .isInstanceOf(GatewayUnavailableException.class);
    }
}
