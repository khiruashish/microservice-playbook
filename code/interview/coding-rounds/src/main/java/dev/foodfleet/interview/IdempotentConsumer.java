package dev.foodfleet.interview;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * An idempotent consumer: a message's effect and the record that it was processed are written in the same
 * transaction, so a redelivered message (at-least-once delivery, a crash before the offset commit) is recognised and
 * skipped, and a failure part-way leaves neither. The "database" here is a map with all-or-nothing transactions; in a
 * service it is the service's own database, with an inbox table keyed by message id (chapter 15, ADR-015).
 */
public final class IdempotentConsumer {

    public record Message(String id, String orderId, long amountPaise) {
    }

    /** A tiny database: tables of balances and processed ids, changed only inside {@link #transact}. */
    public static final class Database {
        private final Map<String, Long> balances = new HashMap<>();
        private final Set<String> inbox = new HashSet<>();

        public final class Tx {
            private final Map<String, Long> balances = new HashMap<>(Database.this.balances);
            private final Set<String> inbox = new HashSet<>(Database.this.inbox);

            public boolean claim(String messageId) {
                return inbox.add(messageId);     // INSERT ... ON CONFLICT DO NOTHING, returning whether it inserted
            }

            public void add(String account, long paise) {
                balances.merge(account, paise, Long::sum);
            }
        }

        /** Runs {@code work} as one transaction: committed if it returns, discarded if it throws. Serialised. */
        public synchronized void transact(Consumer<Tx> work) {
            Tx tx = new Tx();
            work.accept(tx);
            balances.clear();
            balances.putAll(tx.balances);
            inbox.clear();
            inbox.addAll(tx.inbox);
        }

        public synchronized long balance(String account) {
            return balances.getOrDefault(account, 0L);
        }
    }

    private final Database db;
    private final Consumer<Message> sideEffectCheck;

    /** @param sideEffectCheck runs inside the transaction before commit; tests use it to inject failures */
    public IdempotentConsumer(Database db, Consumer<Message> sideEffectCheck) {
        this.db = db;
        this.sideEffectCheck = sideEffectCheck;
    }

    /** @return true if this delivery applied the effect, false if the message had been processed before */
    public boolean handle(Message m) {
        boolean[] applied = {false};
        db.transact(tx -> {
            if (!tx.claim(m.id())) {
                return;                          // duplicate: already processed, nothing to do
            }
            tx.add(m.orderId(), m.amountPaise());
            sideEffectCheck.accept(m);           // a crash here rolls back the claim too, so redelivery retries it
            applied[0] = true;
        });
        return applied[0];
    }
}
