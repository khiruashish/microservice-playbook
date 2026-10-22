package dev.foodfleet.ch13.wallet;

import dev.foodfleet.ch13.eventstore.EventStore.ConcurrencyException;
import java.util.List;
import java.util.function.Function;

/**
 * The command side: load, decide, append. If another command on the same wallet appended first,
 * reload and decide again: the rules are re-checked against the newer state, so two payouts racing
 * for the same money can never both succeed.
 */
public final class WalletService {

    static final int MAX_ATTEMPTS = 3;

    private final WalletRepository wallets;

    public WalletService(WalletRepository wallets) {
        this.wallets = wallets;
    }

    public RiderWallet execute(String riderId, Function<RiderWallet, List<WalletEvent>> command) {
        for (int attempt = 1; ; attempt++) {
            RiderWallet wallet = wallets.load(riderId);
            List<WalletEvent> events = command.apply(wallet);
            try {
                return wallets.save(riderId, wallet, events);
            } catch (ConcurrencyException conflict) {
                if (attempt == MAX_ATTEMPTS) {
                    throw conflict;
                }
            }
        }
    }
}
