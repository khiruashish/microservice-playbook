package dev.foodfleet.ch09.payment.adapter.out.persistence;

import dev.foodfleet.ch09.payment.application.port.out.PaymentRepository;
import dev.foodfleet.ch09.payment.support.InMemoryPaymentRepository;

class InMemoryPaymentRepositoryTest extends PaymentRepositoryContract {

    @Override
    protected PaymentRepository createRepository() {
        return new InMemoryPaymentRepository();
    }
}
