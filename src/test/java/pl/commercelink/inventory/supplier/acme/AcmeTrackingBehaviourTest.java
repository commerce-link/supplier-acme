package pl.commercelink.inventory.supplier.acme;

import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.supplier.api.SupplierConsignee;
import pl.commercelink.inventory.supplier.api.SupplierDropshipRequest;
import pl.commercelink.inventory.supplier.api.SupplierOrderException;
import pl.commercelink.inventory.supplier.api.SupplierOrderLine;
import pl.commercelink.inventory.supplier.api.SupplierOrderLookup;
import pl.commercelink.inventory.supplier.api.SupplierOrderResult;
import pl.commercelink.inventory.supplier.api.SupplierOrderState;
import pl.commercelink.inventory.supplier.api.SupplierOrderTracking;
import pl.commercelink.inventory.supplier.api.SupplierProvider;
import pl.commercelink.inventory.supplier.api.SupplierPurchaseRequest;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcmeTrackingBehaviourTest {

    private static final SupplierConsignee CONSIGNEE = new SupplierConsignee(null, "Jan", "Kowalski",
            "ul. Polna 1", "00-001", "Warszawa", "PL", "+48601234567", "jan.kowalski@example.com");

    // Acme requires a shipping service; this is the default valid choice for tests unrelated to that option.
    private static final Map<String, String> ACME_OPTIONS =
            Map.of(AcmeSupplierProvider.SHIPPING_SERVICE_OPTION, "standard");

    private static List<SupplierOrderLine> twoLines() {
        return List.of(
                new SupplierOrderLine("ACME-5900000000001", "5900000000001", "MFN-CLEAR-01", 1),
                new SupplierOrderLine("ACME-5900000000002", "5900000000002", "MFN-VALUE-01", 1));
    }

    private static SupplierProvider acme(String... knobs) {
        Map<String, String> configuration = new java.util.HashMap<>();
        for (int i = 0; i < knobs.length; i += 2) {
            configuration.put(knobs[i], knobs[i + 1]);
        }
        return new AcmeSupplierDescriptor().create(configuration);
    }

    @Test
    void acmeAndAcmeBSupportTracking() {
        // when / then
        assertTrue(new AcmeSupplierDescriptor().create(Map.of()).supportsOrderTracking());
        assertTrue(new AcmeBSupplierDescriptor().create(Map.of()).supportsOrderTracking());
    }

    @Test
    void dropshipOrderIsFoundByExternalOrderId() {
        // given
        SupplierProvider acme = acme("trackingShipAfterChecks", "1");
        String ref = UUID.randomUUID().toString();
        SupplierOrderResult placed = acme.placeDropshipOrder(
                new SupplierDropshipRequest(ref, twoLines(), CONSIGNEE, null, null, ACME_OPTIONS));

        // when
        Optional<SupplierOrderTracking> tracking = acme.trackOrder(new SupplierOrderLookup(placed.externalOrderId(), null));

        // then
        assertTrue(tracking.isPresent());
        assertEquals(SupplierOrderState.SHIPPED, tracking.get().state());
        assertEquals("ACME-TRK-" + AcmeTrackingSimulation.shortClientRef(ref), tracking.get().parcels().get(0).trackingNo());
    }

    @Test
    void dropshipOrderIsFoundByClientRefWhenExternalIdUnknown() {
        // given
        SupplierProvider acme = acme("trackingShipAfterChecks", "1");
        String ref = UUID.randomUUID().toString();
        acme.placeDropshipOrder(new SupplierDropshipRequest(ref, twoLines(), CONSIGNEE, null, null, ACME_OPTIONS));

        // when
        Optional<SupplierOrderTracking> tracking = acme.trackOrder(new SupplierOrderLookup("MANUAL-123", ref));

        // then
        assertTrue(tracking.isPresent());
    }

    @Test
    void regularOrderIsTrackedToo() {
        // given
        SupplierProvider acme = acme("trackingShipAfterChecks", "1");
        String ref = UUID.randomUUID().toString();
        SupplierOrderResult placed = acme.placeOrder(
                new SupplierPurchaseRequest(ref, twoLines(), "1", ACME_OPTIONS));

        // when
        Optional<SupplierOrderTracking> tracking = acme.trackOrder(new SupplierOrderLookup(placed.externalOrderId(), null));

        // then
        assertTrue(tracking.isPresent());
        assertEquals("ACME-TRK-" + AcmeTrackingSimulation.shortClientRef(ref), tracking.get().parcels().get(0).trackingNo());
    }

    @Test
    void unknownOrderIsEmpty() {
        // when / then
        assertTrue(acme().trackOrder(new SupplierOrderLookup("ACME-DS-nope", "nope")).isEmpty());
    }

    @Test
    void knobsAreReadPerProviderInstanceButChecksAreSharedPerOrder() {
        // given
        String ref = UUID.randomUUID().toString();
        SupplierProvider slow = acme("trackingShipAfterChecks", "3");
        SupplierProvider fast = acme("trackingShipAfterChecks", "1");
        SupplierOrderResult placed = slow.placeDropshipOrder(
                new SupplierDropshipRequest(ref, twoLines(), CONSIGNEE, null, null, ACME_OPTIONS));
        SupplierOrderLookup lookup = new SupplierOrderLookup(placed.externalOrderId(), ref);

        // when
        SupplierOrderTracking first = slow.trackOrder(lookup).orElseThrow();
        SupplierOrderTracking second = fast.trackOrder(lookup).orElseThrow();

        // then
        assertEquals(SupplierOrderState.PROCESSING, first.state());
        assertEquals(SupplierOrderState.SHIPPED, second.state());
    }

    @Test
    void invalidScenarioFailsAtProviderCreation() {
        // when / then
        assertThrows(SupplierOrderException.class, () -> acme("trackingScenario", "bogus"));
    }
}
