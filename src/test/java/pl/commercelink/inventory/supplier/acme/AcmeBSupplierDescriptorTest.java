package pl.commercelink.inventory.supplier.acme;

import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.supplier.api.SupplierOrderLine;
import pl.commercelink.inventory.supplier.api.SupplierOrderResult;
import pl.commercelink.inventory.supplier.api.SupplierProvider;
import pl.commercelink.inventory.supplier.api.SupplierPurchaseRequest;
import pl.commercelink.inventory.supplier.api.SupplierQuote;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcmeBSupplierDescriptorTest {

    private static final List<SupplierOrderLine> SAMPLE_LINES =
            List.of(new SupplierOrderLine("ACME-5900000000001", "5900000000001", "MFN-CLEAR-01", 1));

    @Test
    void createdProviderSupportsOrdering() {
        // given
        SupplierProvider provider = new AcmeBSupplierDescriptor().create(Map.of());

        // when / then
        assertTrue(provider.supportsOrdering());
    }

    @Test
    void quotesAvailabilityFromTheAcmeBFeedInsteadOfTheAcmeOne() {
        // given
        SupplierProvider provider = new AcmeBSupplierDescriptor().create(Map.of());

        // when
        List<SupplierQuote> quotes = provider.checkAvailability(
                List.of(new SupplierOrderLine("ACME-5900000000006", "5900000000006", "MFN-MIRAGE-01", 1)));

        // then
        assertEquals(35, quotes.getFirst().availableQuantity());
        assertEquals(649.00, quotes.getFirst().netPrice());
    }

    @Test
    void keepsPlacedOrdersSeparateFromAcmeForTheSameClientOrderRef() {
        // given
        String clientOrderRef = UUID.randomUUID().toString();
        SupplierProvider acme = new AcmeSupplierDescriptor().create(Map.of());
        SupplierProvider acmeB = new AcmeBSupplierDescriptor().create(Map.of());

        // when
        SupplierOrderResult acmeOrder = acme.placeOrder(
                new SupplierPurchaseRequest(clientOrderRef, SAMPLE_LINES, "2"));
        SupplierOrderResult acmeBOrder = acmeB.placeOrder(
                new SupplierPurchaseRequest(clientOrderRef, SAMPLE_LINES, "2"));

        // then
        assertEquals("ACME-PO-" + clientOrderRef, acmeOrder.externalOrderId());
        assertEquals("ACMEB-PO-" + clientOrderRef, acmeBOrder.externalOrderId());
    }
}
