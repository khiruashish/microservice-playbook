package dev.foodfleet.ch13.wallet;

import dev.foodfleet.ch13.eventstore.NewEvent;
import dev.foodfleet.ch13.eventstore.StoredEvent;
import dev.foodfleet.ch13.eventstore.Upcaster;
import dev.foodfleet.ch13.wallet.WalletEvent.AdjustmentApplied;
import dev.foodfleet.ch13.wallet.WalletEvent.EarningCredited;
import dev.foodfleet.ch13.wallet.WalletEvent.PayoutCompleted;
import dev.foodfleet.ch13.wallet.WalletEvent.PayoutFailed;
import dev.foodfleet.ch13.wallet.WalletEvent.PayoutRequested;
import dev.foodfleet.ch13.wallet.WalletEvent.TipCredited;
import dev.foodfleet.ch13.wallet.WalletEvent.WalletOpened;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Domain events ↔ stored form. The stored form is the long-lived contract: type names and field
 * names in here must never change meaning; new needs get a new schema version and an upcaster.
 */
public final class WalletEventCodec {

    /** Current schema versions. EarningCredited v1 stored rupees as a decimal string. */
    static final int EARNING_CREDITED_VERSION = 2;

    public static final List<Upcaster> UPCASTERS = List.of(new EarningCreditedV1ToV2());

    private WalletEventCodec() {
    }

    public static NewEvent encode(WalletEvent event) {
        Map<String, String> p = new HashMap<>();
        p.put("occurredAt", event.occurredAt().toString());
        return switch (event) {
            case WalletOpened e -> {
                p.put("riderId", e.riderId());
                yield new NewEvent("WalletOpened", 1, p);
            }
            case EarningCredited e -> {
                p.put("orderId", e.orderId());
                p.put("paise", Long.toString(e.paise()));
                yield new NewEvent("EarningCredited", EARNING_CREDITED_VERSION, p);
            }
            case TipCredited e -> {
                p.put("orderId", e.orderId());
                p.put("paise", Long.toString(e.paise()));
                yield new NewEvent("TipCredited", 1, p);
            }
            case AdjustmentApplied e -> {
                p.put("paise", Long.toString(e.paise()));
                p.put("reason", e.reason());
                yield new NewEvent("AdjustmentApplied", 1, p);
            }
            case PayoutRequested e -> {
                p.put("payoutId", e.payoutId());
                p.put("paise", Long.toString(e.paise()));
                yield new NewEvent("PayoutRequested", 1, p);
            }
            case PayoutCompleted e -> {
                p.put("payoutId", e.payoutId());
                yield new NewEvent("PayoutCompleted", 1, p);
            }
            case PayoutFailed e -> {
                p.put("payoutId", e.payoutId());
                p.put("reason", e.reason());
                yield new NewEvent("PayoutFailed", 1, p);
            }
        };
    }

    public static WalletEvent decode(StoredEvent stored) {
        StoredEvent e = Upcaster.upcastFully(stored, UPCASTERS);
        Map<String, String> p = e.payload();
        Instant at = Instant.parse(p.get("occurredAt"));
        return switch (e.type()) {
            case "WalletOpened" -> new WalletOpened(p.get("riderId"), at);
            case "EarningCredited" -> new EarningCredited(p.get("orderId"), Long.parseLong(p.get("paise")), at);
            case "TipCredited" -> new TipCredited(p.get("orderId"), Long.parseLong(p.get("paise")), at);
            case "AdjustmentApplied" -> new AdjustmentApplied(Long.parseLong(p.get("paise")), p.get("reason"), at);
            case "PayoutRequested" -> new PayoutRequested(p.get("payoutId"), Long.parseLong(p.get("paise")), at);
            case "PayoutCompleted" -> new PayoutCompleted(p.get("payoutId"), at);
            case "PayoutFailed" -> new PayoutFailed(p.get("payoutId"), p.get("reason"), at);
            default -> throw new IllegalStateException("Unknown event type " + e.type() + " in " + e.streamId());
        };
    }

    /** 2023 events stored {@code amountRupees: "123.45"}; since 2024 the payload holds integer paise. */
    static final class EarningCreditedV1ToV2 implements Upcaster {

        @Override
        public boolean canUpcast(StoredEvent event) {
            return event.type().equals("EarningCredited") && event.schemaVersion() == 1;
        }

        @Override
        public StoredEvent upcast(StoredEvent event) {
            Map<String, String> p = new HashMap<>(event.payload());
            long paise = new BigDecimal(p.remove("amountRupees")).movePointRight(2).longValueExact();
            p.put("paise", Long.toString(paise));
            return new StoredEvent(event.streamId(), event.streamVersion(), event.globalPosition(), event.type(), 2,
                    p, event.recordedAt());
        }
    }
}
