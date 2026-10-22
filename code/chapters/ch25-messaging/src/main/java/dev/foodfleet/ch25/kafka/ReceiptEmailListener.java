package dev.foodfleet.ch25.kafka;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.BackOff;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Emails a receipt when an order is delivered. Order between receipts doesn't matter, and one
 * customer's mail server being slow must not delay everyone else's receipt, so failures leave the
 * partition: retry topics after 1 s, 10 s and 60 s, then the dead-letter topic.
 */
@Component
public class ReceiptEmailListener {

    private static final Logger log = LoggerFactory.getLogger(ReceiptEmailListener.class);

    /** Sends the email; throws when the mail provider is unavailable. */
    public interface Mailer {
        void sendReceipt(OrderEvent event);
    }

    private final Mailer mailer;
    private final ProcessedEvents processed;
    private final List<String> deadLettered = new CopyOnWriteArrayList<>();

    public ReceiptEmailListener(Mailer mailer, ProcessedEvents processed) {
        this.mailer = mailer;
        this.processed = processed;
    }

    @RetryableTopic(
            attempts = "4",                                       // 1 try + 3 retries: 1 s, 10 s, 60 s
            backOff = @BackOff(delay = 1_000, multiplier = 10, maxDelay = 60_000),
            exclude = InvalidEventException.class,                // not retryable: straight to the DLT
            retryTopicSuffix = "-receipts-retry",                 // order-events-receipts-retry-1000, ...
            dltTopicSuffix = "-receipts-dlt",                     // not order-events-dlt: that one is the kitchen's
            dltStrategy = DltStrategy.FAIL_ON_ERROR)
    @KafkaListener(id = "receipt-emails", topics = "order-events", groupId = "receipt-emails")
    public void onEvent(OrderEvent event) {
        if (event.type() != OrderEvent.Type.DELIVERED) {
            return;
        }
        if (event.orderId() == null) {
            throw new InvalidEventException("event " + event.eventId() + " has no order id");
        }
        if (processed.firstTime("receipt:" + event.eventId())) {
            try {
                mailer.sendReceipt(event);
            } catch (RuntimeException e) {
                processed.forget("receipt:" + event.eventId());   // not sent: let the retry try again
                throw e;
            }
        }
    }

    /** Messages that exhausted their retries, or were never valid. Alert, inspect, replay. */
    @DltHandler
    public void onDeadLetter(OrderEvent event, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                             @Header(name = KafkaHeaders.EXCEPTION_MESSAGE, required = false) byte[] error) {
        log.error("receipt for {} dead-lettered from {}: {}", event.orderId(), topic,
                error == null ? "?" : new String(error, StandardCharsets.UTF_8));
        deadLettered.add(event.eventId());
    }

    public List<String> deadLettered() {
        return List.copyOf(deadLettered);
    }
}
