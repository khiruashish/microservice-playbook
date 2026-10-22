package dev.foodfleet.ch09.payment;

import static org.assertj.core.api.Assertions.assertThat;

import dev.foodfleet.ch09.payment.adapter.in.events.KitchenRejectedOrder;
import dev.foodfleet.ch09.payment.application.port.in.AuthorizePayment;
import dev.foodfleet.ch09.payment.application.port.in.GetPayment;
import dev.foodfleet.ch09.payment.application.port.in.PaymentView;
import dev.foodfleet.ch09.payment.domain.Money;
import dev.foodfleet.ch09.payment.domain.PaymentId;
import dev.foodfleet.ch09.payment.domain.PaymentMethod;
import dev.foodfleet.ch09.payment.domain.PaymentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;

/** The assembled service: real composition root, JDBC adapter on H2, fake gateway, event adapter. */
@SpringBootTest
class PaymentServiceApplicationTest {

    @Autowired
    AuthorizePayment authorize;

    @Autowired
    GetPayment get;

    @Autowired
    ApplicationEventPublisher events;

    @Test
    void kitchenRejectionReleasesTheCustomersMoney() {
        PaymentView view = authorize.authorize(new AuthorizePayment.Command("o-42", Money.ofPaise(45_000),
                PaymentMethod.upi("ravi@okaxis"), "it-key-1"));
        assertThat(view.status()).isEqualTo(PaymentStatus.AUTHORIZED);

        events.publishEvent(new KitchenRejectedOrder("o-42", "out of biryani rice"));

        assertThat(get.get(new PaymentId(view.paymentId())).status()).isEqualTo(PaymentStatus.VOIDED);
    }
}
