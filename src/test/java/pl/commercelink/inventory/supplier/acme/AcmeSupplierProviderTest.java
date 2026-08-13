package pl.commercelink.inventory.supplier.acme;

import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.supplier.api.SupplierOrderException;
import pl.commercelink.inventory.supplier.api.SupplierOrderLine;
import pl.commercelink.inventory.supplier.api.SupplierOrderResult;
import pl.commercelink.inventory.supplier.api.SupplierDeliveryAddress;
import pl.commercelink.inventory.supplier.api.SupplierPurchaseRequest;
import pl.commercelink.inventory.supplier.api.SupplierQuote;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcmeSupplierProviderTest {

    private static SupplierPurchaseRequest purchase(String clientOrderRef, List<SupplierOrderLine> lines) {
        return new SupplierPurchaseRequest(clientOrderRef, lines, "2");
    }


    @Test
    void supportsOrdering() {
        // given
        AcmeSupplierProvider provider = new AcmeSupplierProvider(Map.of());

        // when / then
        assertTrue(provider.supportsOrdering());
    }

    @Test
    void returnsAvailabilityAndPriceFromFeed() {
        // given
        AcmeSupplierProvider provider = new AcmeSupplierProvider(Map.of());

        // when
        List<SupplierQuote> quotes = provider.checkAvailability(
                List.of(new SupplierOrderLine("ACME-5900000000001", "5900000000001", "MFN-CLEAR-01", 5)));

        // then
        assertEquals(1, quotes.size());
        assertEquals(20, quotes.get(0).availableQuantity());
        assertEquals(1299.00, quotes.get(0).netPrice());
        assertEquals("PLN", quotes.get(0).currency());
    }

    @Test
    void reportsConfiguredEansAsUnavailable() {
        // given
        AcmeSupplierProvider provider = new AcmeSupplierProvider(
                Map.of("orderingUnavailableEans", "5900000000001,5900000000002"));

        // when
        List<SupplierQuote> quotes = provider.checkAvailability(
                List.of(new SupplierOrderLine("ACME-5900000000001", "5900000000001", "MFN-CLEAR-01", 1)));

        // then
        assertEquals(0, quotes.get(0).availableQuantity());
    }

    @Test
    void blankConfigurationValuesAreTreatedAsDefaults() {
        // given
        AcmeSupplierProvider provider = new AcmeSupplierProvider(
                Map.of("orderingPriceDriftPercent", " ", "orderingUnavailableEans", " "));

        // when
        List<SupplierQuote> quotes = provider.checkAvailability(
                List.of(new SupplierOrderLine("ACME-5900000000001", "5900000000001", "MFN-CLEAR-01", 5)));

        // then
        assertEquals(20, quotes.get(0).availableQuantity());
        assertEquals(1299.00, quotes.get(0).netPrice());
    }

    @Test
    void appliesConfiguredPriceDrift() {
        // given
        AcmeSupplierProvider provider = new AcmeSupplierProvider(
                Map.of("orderingPriceDriftPercent", "10"));

        // when
        List<SupplierQuote> quotes = provider.checkAvailability(
                List.of(new SupplierOrderLine("ACME-5900000000003", "5900000000003", "MFN-TWIN-01", 1)));

        // then
        assertEquals(504.9, quotes.get(0).netPrice(), 0.01);
    }

    @Test
    void unknownEanIsUnavailable() {
        // given
        AcmeSupplierProvider provider = new AcmeSupplierProvider(Map.of());

        // when
        List<SupplierQuote> quotes = provider.checkAvailability(
                List.of(new SupplierOrderLine("ACME-0000000000000", "0000000000000", "MFN-NOPE", 1)));

        // then
        assertEquals(0, quotes.get(0).availableQuantity());
    }

    @Test
    void listsSeveralDeliveryAddresses() {
        // given
        AcmeSupplierProvider provider = new AcmeSupplierProvider(Map.of());

        // when
        List<SupplierDeliveryAddress> addresses = provider.deliveryAddresses();

        // then
        assertTrue(provider.requiresDeliveryAddress());
        assertEquals(4, addresses.size());
        assertEquals("ul. Zakopiańska 58, 30-418 Kraków, PL", addresses.get(1).label());
    }

    @Test
    void rejectsOrderForAnAddressTheAccountDoesNotHave() {
        // given
        AcmeSupplierProvider provider = new AcmeSupplierProvider(Map.of());
        SupplierPurchaseRequest request = new SupplierPurchaseRequest(UUID.randomUUID().toString(),
                List.of(new SupplierOrderLine("ACME-5900000000001", "5900000000001", "MFN-CLEAR-01", 1)), "99");

        // when / then
        assertThrows(SupplierOrderException.class, () -> provider.placeOrder(request));
    }

    @Test
    void placesOrderWhenFullyAvailable() {
        // given
        AcmeSupplierProvider provider = new AcmeSupplierProvider(Map.of());
        String ref = UUID.randomUUID().toString();

        // when
        SupplierOrderResult result = provider.placeOrder(purchase(
                ref, List.of(new SupplierOrderLine("ACME-5900000000001", "5900000000001", "MFN-CLEAR-01", 5))));

        // then
        assertEquals("ACME-PO-" + ref, result.externalOrderId());
        assertEquals(5 * 1299.00, result.totalNet(), 0.01);
        assertEquals("PLN", result.currency());
    }

    @Test
    void rejectsOrderWhenAnyLineExceedsAvailability() {
        // given
        AcmeSupplierProvider provider = new AcmeSupplierProvider(Map.of());
        SupplierPurchaseRequest request = purchase(
                UUID.randomUUID().toString(),
                List.of(new SupplierOrderLine("ACME-5900000000001", "5900000000001", "MFN-CLEAR-01", 5),
                        new SupplierOrderLine("ACME-5900000000002", "5900000000002", "MFN-VALUE-01", 999)));

        // when / then
        assertThrows(SupplierOrderException.class, () -> provider.placeOrder(request));
    }

    @Test
    void placeOrderIsIdempotentOnClientOrderRef() {
        // given
        AcmeSupplierProvider provider = new AcmeSupplierProvider(Map.of());
        String ref = UUID.randomUUID().toString();
        SupplierPurchaseRequest request = purchase(
                ref, List.of(new SupplierOrderLine("ACME-5900000000001", "5900000000001", "MFN-CLEAR-01", 5)));

        // when
        SupplierOrderResult first = provider.placeOrder(request);
        SupplierOrderResult second = provider.placeOrder(request);

        // then
        assertEquals(first.externalOrderId(), second.externalOrderId());
        assertEquals(first.totalNet(), second.totalNet());
    }

    @Test
    void retryWithSameRefSucceedsAfterFailedPlaceOrder() {
        // given
        String ref = UUID.randomUUID().toString();
        AcmeSupplierProvider blocked = new AcmeSupplierProvider(
                Map.of("orderingUnavailableEans", "5900000000001"));
        SupplierPurchaseRequest request = purchase(
                ref, List.of(new SupplierOrderLine("ACME-5900000000001", "5900000000001", "MFN-CLEAR-01", 1)));
        assertThrows(SupplierOrderException.class, () -> blocked.placeOrder(request));

        // when
        AcmeSupplierProvider restocked = new AcmeSupplierProvider(Map.of());
        SupplierOrderResult result = restocked.placeOrder(request);

        // then
        assertEquals("ACME-PO-" + ref, result.externalOrderId());
    }

    @Test
    void quotesZeroForLineWithoutSku() {
        AcmeSupplierProvider provider = new AcmeSupplierProvider(Map.of());
        List<SupplierQuote> quotes = provider.checkAvailability(
                List.of(new SupplierOrderLine(null, "5900000000001", "MFN-CLEAR-01", 1)));
        assertEquals(0, quotes.getFirst().availableQuantity());
        assertEquals("5900000000001", quotes.getFirst().ean());
    }

    @Test
    void refusesOrderForLineWithoutSku() {
        AcmeSupplierProvider provider = new AcmeSupplierProvider(Map.of());
        SupplierOrderException e = assertThrows(SupplierOrderException.class, () -> provider.placeOrder(
                purchase(UUID.randomUUID().toString(),
                        List.of(new SupplierOrderLine(null, "5900000000001", "MFN-CLEAR-01", 1)))));
        assertTrue(e.getMessage().contains("5900000000001"));
    }
}
