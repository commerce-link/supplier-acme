package pl.commercelink.inventory.supplier.acme;

import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.supplier.api.SupplierConsignee;
import pl.commercelink.inventory.supplier.api.SupplierDropshipRequest;
import pl.commercelink.inventory.supplier.api.SupplierOrderException;
import pl.commercelink.inventory.supplier.api.SupplierOrderLine;
import pl.commercelink.inventory.supplier.api.SupplierOrderResult;
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

    private static final String SAMPLE_EAN = "5900000000001";

    private static final SupplierConsignee CONSIGNEE = new SupplierConsignee(null, "Jan", "Kowalski",
            "ul. Polna 1", "00-001", "Warszawa", "PL", "+48601234567", "jan.kowalski@example.com");

    private static List<SupplierOrderLine> sampleLines() {
        return List.of(new SupplierOrderLine("ACME-" + SAMPLE_EAN, SAMPLE_EAN, "MFN-CLEAR-01", 1));
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
                new SupplierPurchaseRequest(ref, sampleLines(), "1"));
        SupplierOrderResult dropship = acme.placeDropshipOrder(
                new SupplierDropshipRequest(ref, sampleLines(), CONSIGNEE));

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
                new SupplierDropshipRequest(UUID.randomUUID().toString(), sampleLines(), CONSIGNEE));

        // then
        assertTrue(acmeB.supportsDropshipping());
        assertTrue(result.externalOrderId().startsWith("ACMEB-DS-"));
    }

    @Test
    void dropshipConfigurationKnobCannotDisableAcme() {
        // when / then
        assertTrue(new AcmeSupplierDescriptor().create(Map.of()).supportsDropshipping());
    }
}
