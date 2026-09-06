package pl.commercelink.inventory.supplier.acme;

import pl.commercelink.inventory.supplier.api.SupplierOrderException;
import pl.commercelink.inventory.supplier.api.SupplierOrderLine;
import pl.commercelink.inventory.supplier.api.SupplierOrderResult;
import pl.commercelink.inventory.supplier.api.SupplierOrderState;
import pl.commercelink.inventory.supplier.api.SupplierOrderTracking;
import pl.commercelink.inventory.supplier.api.SupplierParcel;
import pl.commercelink.inventory.supplier.api.SupplierQuote;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

final class AcmeTrackingSimulation {

    enum Scenario { SINGLE, PARTS, CANCEL, NODATA }

    static final String SHIP_AFTER_CHECKS_KEY = "trackingShipAfterChecks";
    static final String SCENARIO_KEY = "trackingScenario";
    static final String CARRIER = "DPD";
    static final String TRACKING_URL_PREFIX = "https://tracking.acme.example/";
    private static final int SHORT_REF_LENGTH = 12;

    private static final Map<String, AtomicInteger> CHECKS = new ConcurrentHashMap<>();
    private static final Map<String, SupplierParcel> PARCELS = new ConcurrentHashMap<>();

    private final int shipAfterChecks;
    private final Scenario scenario;

    AcmeTrackingSimulation(Map<String, String> configuration) {
        String rawChecks = valueOrDefault(configuration, SHIP_AFTER_CHECKS_KEY, "2");
        try {
            this.shipAfterChecks = Integer.parseInt(rawChecks);
        } catch (NumberFormatException e) {
            throw new SupplierOrderException(SHIP_AFTER_CHECKS_KEY + " must be a positive integer, got: " + rawChecks);
        }
        if (shipAfterChecks < 1) {
            throw new SupplierOrderException(SHIP_AFTER_CHECKS_KEY + " must be at least 1, got: " + rawChecks);
        }
        String rawScenario = valueOrDefault(configuration, SCENARIO_KEY, "single");
        try {
            this.scenario = Scenario.valueOf(rawScenario.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new SupplierOrderException(SCENARIO_KEY + " must be one of single, parts, cancel, nodata; got: " + rawScenario);
        }
    }

    private static String valueOrDefault(Map<String, String> configuration, String key, String defaultValue) {
        String value = configuration.get(key);
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    SupplierOrderTracking track(String orderKey, SupplierOrderResult placed) {
        int check = CHECKS.computeIfAbsent(orderKey, k -> new AtomicInteger()).incrementAndGet();
        if (check < shipAfterChecks) {
            return new SupplierOrderTracking(SupplierOrderState.PROCESSING, List.of());
        }
        return switch (scenario) {
            case CANCEL -> new SupplierOrderTracking(SupplierOrderState.CANCELLED, List.of());
            case NODATA -> new SupplierOrderTracking(SupplierOrderState.SHIPPED, List.of());
            case SINGLE -> new SupplierOrderTracking(SupplierOrderState.SHIPPED,
                    List.of(parcel(orderKey, "", List.of())));
            case PARTS -> parts(orderKey, placed, check - shipAfterChecks);
        };
    }

    private SupplierOrderTracking parts(String orderKey, SupplierOrderResult placed, int stage) {
        List<SupplierOrderLine> lines = placed.confirmedLines().stream().map(AcmeTrackingSimulation::toLine).toList();
        if (lines.size() < 2) {
            return new SupplierOrderTracking(SupplierOrderState.SHIPPED, List.of(parcel(orderKey, "P1", lines)));
        }
        SupplierParcel first = parcel(orderKey, "P1", lines.subList(0, 1));
        if (stage == 0) {
            return new SupplierOrderTracking(SupplierOrderState.PARTIALLY_SHIPPED, List.of(first));
        }
        SupplierParcel second = parcel(orderKey, "P2", lines.subList(1, lines.size()));
        return new SupplierOrderTracking(SupplierOrderState.SHIPPED, List.of(first, second));
    }

    private static SupplierParcel parcel(String orderKey, String suffix, List<SupplierOrderLine> lines) {
        String trackingNo = trackingPrefix(orderKey) + clientRef(orderKey) + suffix;
        return PARCELS.computeIfAbsent(orderKey + "#" + suffix, k -> new SupplierParcel(
                CARRIER, trackingNo, TRACKING_URL_PREFIX + trackingNo, LocalDateTime.now(), lines));
    }

    private static SupplierOrderLine toLine(SupplierQuote quote) {
        return new SupplierOrderLine(null, quote.ean(), quote.mfn(), quote.availableQuantity());
    }

    private static String trackingPrefix(String orderKey) {
        return orderKey.substring(0, orderKey.indexOf('|')).toUpperCase(Locale.ROOT) + "TRK";
    }

    private static String clientRef(String orderKey) {
        return shortClientRef(orderKey.substring(orderKey.lastIndexOf('|') + 1));
    }

    /**
     * Carriers accept short upper-case alphanumeric numbers only (Furgonetka: 7-34 characters,
     * {@code [A-Z0-9]}) and echo them upper-cased in webhooks, so the simulation emits the first 12 hex
     * digits of the purchase reference in upper case to round-trip byte-for-byte.
     */
    static String shortClientRef(String purchaseRef) {
        String compact = purchaseRef.replace("-", "");
        String truncated = compact.length() <= SHORT_REF_LENGTH ? compact : compact.substring(0, SHORT_REF_LENGTH);
        return truncated.toUpperCase(Locale.ROOT);
    }
}
