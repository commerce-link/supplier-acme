package pl.commercelink.inventory.supplier.acme;

import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.supplier.api.SupplierConsignee;
import pl.commercelink.inventory.supplier.api.SupplierDropshipRequest;
import pl.commercelink.inventory.supplier.api.SupplierOrderException;
import pl.commercelink.inventory.supplier.api.SupplierOrderLine;
import pl.commercelink.inventory.supplier.api.SupplierOrderLookup;
import pl.commercelink.inventory.supplier.api.SupplierOrderRejectedException;
import pl.commercelink.inventory.supplier.api.SupplierOrderResult;
import pl.commercelink.inventory.supplier.api.SupplierPickupPoint;
import pl.commercelink.inventory.supplier.api.SupplierProvider;
import pl.commercelink.inventory.supplier.api.SupplierPurchaseRequest;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcmeDropshipBehaviourTest {

    private static final String SAMPLE_EAN = "730143318280";
    private static final String ACME_B_EAN = "4711636046213";

    private static final SupplierConsignee CONSIGNEE = new SupplierConsignee(null, "Jan", "Kowalski",
            "ul. Polna 1", "00-001", "Warszawa", "PL", "+48601234567", "jan.kowalski@example.com");

    private static final SupplierPickupPoint LOCKER = new SupplierPickupPoint("InPost", "WAW04A", null, null, null, null);

    // Acme requires a shipping service; this is the default valid choice for tests unrelated to that option.
    private static final Map<String, String> ACME_OPTIONS =
            Map.of(AcmeSupplierProvider.SHIPPING_SERVICE_OPTION, "standard");

    private static List<SupplierOrderLine> sampleLines() {
        return List.of(new SupplierOrderLine("ACME-" + SAMPLE_EAN, SAMPLE_EAN, "100-100001973WOF", 1));
    }

    private static List<SupplierOrderLine> acmeBLines() {
        return List.of(new SupplierOrderLine("ACME-" + ACME_B_EAN, ACME_B_EAN, "90YV0M17-M0NA00", 1));
    }

    @Test
    void acmeSupportsDropshippingAndAcmeBDoesNot() {
        // given
        SupplierProvider acme = new AcmeSupplierDescriptor().create(Map.of());
        SupplierProvider acmeB = new AcmeBSupplierDescriptor().create(Map.of());

        // when / then
        assertTrue(acme.supportsDropshipping());
        assertFalse(acmeB.supportsDropshipping());
        assertThrows(SupplierOrderException.class, () -> acmeB.placeDropshipOrder(
                new SupplierDropshipRequest(UUID.randomUUID().toString(), sampleLines(), CONSIGNEE)));
    }

    @Test
    void dropshipAndRegularOrderWithSameRefAreIndependent() {
        // given
        SupplierProvider acme = new AcmeSupplierDescriptor().create(Map.of());
        String ref = UUID.randomUUID().toString();

        // when
        SupplierOrderResult regular = acme.placeOrder(
                new SupplierPurchaseRequest(ref, sampleLines(), "1", ACME_OPTIONS));
        SupplierOrderResult dropship = acme.placeDropshipOrder(
                new SupplierDropshipRequest(ref, sampleLines(), CONSIGNEE, null, null, ACME_OPTIONS));

        // then
        assertEquals("ACME-PO-" + ref, regular.externalOrderId());
        assertEquals("ACME-DS-" + ref, dropship.externalOrderId());
        assertNotEquals(regular.externalOrderId(), dropship.externalOrderId());
    }

    @Test
    void acmeBSupportsDropshippingWhenEnabledInConfiguration() {
        // given
        SupplierProvider acmeB = new AcmeBSupplierDescriptor().create(Map.of("orderingDropshipEnabled", "1"));

        // when
        SupplierOrderResult result = acmeB.placeDropshipOrder(
                new SupplierDropshipRequest(UUID.randomUUID().toString(), acmeBLines(), CONSIGNEE));

        // then
        assertTrue(acmeB.supportsDropshipping());
        assertTrue(result.externalOrderId().startsWith("ACMEB-DS-"));
    }

    @Test
    void dropshipConfigurationKnobCannotDisableAcme() {
        // when / then
        assertTrue(new AcmeSupplierDescriptor().create(Map.of()).supportsDropshipping());
    }

    @Test
    void acmeDeliversDropshipOrdersToPickupPoints() {
        // given
        SupplierProvider acme = new AcmeSupplierDescriptor().create(Map.of());
        String ref = UUID.randomUUID().toString();

        // when
        SupplierOrderResult result = acme.placeDropshipOrder(
                new SupplierDropshipRequest(ref, sampleLines(), CONSIGNEE, null, LOCKER, ACME_OPTIONS));

        // then
        assertTrue(acme.supportsPickupPointDropship());
        assertEquals("ACME-DS-" + ref, result.externalOrderId());
    }

    @Test
    void pickupPointsCanBeDisabledByConfigurationAndThenRejectSuchOrders() {
        // given
        SupplierProvider acme = new AcmeSupplierDescriptor().create(Map.of("orderingPickupPointsEnabled", "0"));
        String ref = UUID.randomUUID().toString();

        // when / then
        assertFalse(acme.supportsPickupPointDropship());
        assertTrue(acme.supportsDropshipping());
        SupplierOrderRejectedException rejected = assertThrows(SupplierOrderRejectedException.class,
                () -> acme.placeDropshipOrder(
                        new SupplierDropshipRequest(ref, sampleLines(), CONSIGNEE, null, LOCKER, ACME_OPTIONS)));
        assertTrue(rejected.getMessage().toLowerCase().contains("pickup"),
                "Expected the pickup-point guard to reject this order, but got: " + rejected.getMessage());
        assertTrue(acme.trackOrder(new SupplierOrderLookup(null, ref)).isEmpty());
    }

    @Test
    void acmeBWithoutDropshipDoesNotSupportPickupPoints() {
        // when / then
        assertFalse(new AcmeBSupplierDescriptor().create(Map.of()).supportsPickupPointDropship());
    }

    @Test
    void acmeRecordsThePickupPointCodeItWasAskedToDeliverTo() {
        // given
        SupplierProvider acme = new AcmeSupplierDescriptor().create(Map.of());
        String pickupRef = UUID.randomUUID().toString();
        String courierRef = UUID.randomUUID().toString();

        // when
        acme.placeDropshipOrder(
                new SupplierDropshipRequest(pickupRef, sampleLines(), CONSIGNEE, null, LOCKER, ACME_OPTIONS));

        // then
        assertEquals("WAW04A", AcmeSupplierProvider.lastPickupPointCode("Acme").orElseThrow());

        // when a later courier (non-pickup-point) dropship order is placed
        acme.placeDropshipOrder(
                new SupplierDropshipRequest(courierRef, sampleLines(), CONSIGNEE, null, null, ACME_OPTIONS));

        // then the last recorded pickup point code is unchanged
        assertEquals("WAW04A", AcmeSupplierProvider.lastPickupPointCode("Acme").orElseThrow());
    }
}
