package dev.foodfleet.ch15.app.outbox;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Default publisher for this chapter: logs and remembers what was published. */
public class RecordingMessagePublisher implements MessagePublisher {

    private static final Logger log = LoggerFactory.getLogger(RecordingMessagePublisher.class);

    private final List<OutboxMessage> published = new CopyOnWriteArrayList<>();

    @Override
    public void publish(OutboxMessage message) {
        log.info("publish topic={} key={} type={} id={}", message.topic(), message.aggregateId(), message.type(),
                message.id());
        published.add(message);
    }

    public List<OutboxMessage> published() {
        return List.copyOf(published);
    }

    public void clear() {
        published.clear();
    }
}
