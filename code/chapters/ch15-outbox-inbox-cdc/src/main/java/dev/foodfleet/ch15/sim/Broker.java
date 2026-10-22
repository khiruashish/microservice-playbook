package dev.foodfleet.ch15.sim;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A message broker topic with one partition (so one total order), Kafka-style consumer groups and
 * committed offsets. Publishing can fail in the two ways that matter:
 * <ul>
 *   <li>{@code broker.unavailable}: the message is not stored and the publisher gets an error;</li>
 *   <li>{@code broker.ack-lost}: the message IS stored but the acknowledgement never arrives, so the
 *       publisher also gets an error and will usually send it again.</li>
 * </ul>
 * Consumers get at-least-once delivery: a consumer that crashes before committing its offset
 * receives the same messages again.
 */
public final class Broker {

    public static final class PublishFailed extends Exception {
        public PublishFailed(String reason) {
            super(reason, null, false, false);
        }
    }

    private final List<Message> log = new ArrayList<>();
    private final Map<String, Long> committedOffsets = new HashMap<>();
    private final Faults faults;

    public Broker(Faults faults) {
        this.faults = faults;
    }

    public void publish(Message message) throws PublishFailed {
        if (faults.fails("broker.unavailable")) {
            throw new PublishFailed("broker unavailable");
        }
        log.add(message);
        if (faults.fails("broker.ack-lost")) {
            throw new PublishFailed("timeout waiting for ack");
        }
    }

    public List<Message> log() {
        return List.copyOf(log);
    }

    public Subscription subscribe(String group) {
        return new Subscription(group);
    }

    /** One consumer instance in a group: an in-memory position plus the group's committed offset. */
    public final class Subscription {
        private final String group;
        private long position;

        private Subscription(String group) {
            this.group = group;
            this.position = committedOffsets.getOrDefault(group, 0L);
        }

        /** The next message, or null if the consumer has caught up. */
        public Message poll() {
            return position < log.size() ? log.get((int) position++) : null;
        }

        public void commit() {
            committedOffsets.put(group, position);
        }

        /** After a crash, a new instance starts from the group's last committed offset. */
        public void restart() {
            position = committedOffsets.getOrDefault(group, 0L);
        }

        public boolean caughtUp() {
            return position >= log.size();
        }
    }
}
