import java.util.*;

/** Problem: an event-sourced bank account: commands validate and emit events; state is rebuilt by replaying events (+ snapshots). */
public class EventSourcedAccount {

    sealed interface Event permits Opened, Deposited, Withdrawn { long version(); }
    record Opened(long version, String owner) implements Event { }
    record Deposited(long version, long cents) implements Event { }
    record Withdrawn(long version, long cents) implements Event { }

    /** State is a pure fold over events. No setters: apply() is the only way state changes. */
    record Account(String owner, long balance, long version) {
        static final Account EMPTY = new Account(null, 0, 0);

        Account apply(Event e) {
            return switch (e) {
                case Opened o -> new Account(o.owner(), 0, o.version());
                case Deposited d -> new Account(owner, balance + d.cents(), d.version());
                case Withdrawn w -> new Account(owner, balance - w.cents(), w.version());
            };
        }

        // Commands: validate against current state, return NEW events (never mutate)
        List<Event> deposit(long cents) {
            if (owner == null) throw new IllegalStateException("account not opened");
            if (cents <= 0) throw new IllegalArgumentException("deposit must be positive");
            return List.of(new Deposited(version + 1, cents));
        }

        List<Event> withdraw(long cents) {
            if (cents > balance) throw new IllegalStateException("insufficient funds: balance " + balance);
            return List.of(new Withdrawn(version + 1, cents));
        }
    }

    record Snapshot(Account state) { }

    /** Append-only store with optimistic concurrency on the expected version. */
    static final class EventStore {
        final List<Event> events = new ArrayList<>();
        Snapshot snapshot;
        int replayedLastLoad;

        void append(long expectedVersion, List<Event> newEvents) {
            long current = events.isEmpty() ? 0 : events.get(events.size() - 1).version();
            if (current != expectedVersion) throw new ConcurrentModificationException("expected v" + expectedVersion + " but was v" + current);
            events.addAll(newEvents);
        }

        Account load() {
            Account state = snapshot == null ? Account.EMPTY : snapshot.state();
            replayedLastLoad = 0;
            for (Event e : events) if (e.version() > state.version()) { state = state.apply(e); replayedLastLoad++; }
            return state;
        }

        void takeSnapshot() { snapshot = new Snapshot(load()); }
    }

    public static void main(String[] args) {
        EventStore store = new EventStore();
        store.append(0, List.of(new Opened(1, "ana")));
        for (int i = 0; i < 5; i++) { Account a = store.load(); store.append(a.version(), a.deposit(1_000)); }
        Account a = store.load();
        store.append(a.version(), a.withdraw(1_500));
        System.out.println("events      = " + store.events.size() + " -> " + store.load());

        try { Account x = store.load(); store.append(x.version(), x.withdraw(1_000_000)); }
        catch (IllegalStateException e) { System.out.println("command rejected, no event stored: " + e.getMessage()); }

        Account stale = Account.EMPTY.apply(store.events.get(0));      // a writer holding an old version
        try { store.append(stale.version(), stale.deposit(1)); }
        catch (ConcurrentModificationException e) { System.out.println("concurrent writer rejected: " + e.getMessage()); }

        store.takeSnapshot();
        Account b = store.load();
        store.append(b.version(), b.deposit(250));
        Account afterSnapshot = store.load();
        System.out.println("after snapshot, replayed only " + store.replayedLastLoad + " event(s) -> " + afterSnapshot);

        // Temporal query: balance as of version 4
        Account asOf = Account.EMPTY;
        for (Event e : store.events) if (e.version() <= 4) asOf = asOf.apply(e);
        System.out.println("balance as of v4 = " + asOf.balance() + " (time travel by partial replay)");

        check(afterSnapshot.balance() == 3_750 && afterSnapshot.version() == 8, "final state");
        check(store.replayedLastLoad == 1, "snapshot limits replay");
        check(asOf.balance() == 3_000, "temporal query");
        System.out.println("PASS");
    }

    static void check(boolean ok, String what) { if (!ok) { System.out.println("FAILED: " + what); System.exit(1); } }
}
