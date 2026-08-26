package pl.commercelink.inventory.supplier.acme;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.supplier.api.SupplierConsignee;
import pl.commercelink.inventory.supplier.api.SupplierDropshipRequest;
import pl.commercelink.inventory.supplier.api.SupplierOrderException;
import pl.commercelink.inventory.supplier.api.SupplierOrderLine;
import pl.commercelink.inventory.supplier.api.SupplierOrderOutcomeUnknownException;
import pl.commercelink.inventory.supplier.api.SupplierOrderRejectedException;
import pl.commercelink.inventory.supplier.api.SupplierOrderResult;
import pl.commercelink.inventory.supplier.api.SupplierDeliveryAddress;
import pl.commercelink.inventory.supplier.api.SupplierPurchaseRequest;
import pl.commercelink.inventory.supplier.api.SupplierQuote;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

class AcmeSupplierProviderTest {

    @AfterEach
    void resetAcmeStateFile() {
        System.setProperty("acme.state.file", "none");
        AcmeSupplierProvider.reloadStateForTests();
    }

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

    @Test
    void findPlacedOrderReturnsResultByClientOrderRef() {
        // given
        AcmeSupplierProvider provider = new AcmeSupplierProvider(Map.of());
        String ref = UUID.randomUUID().toString();
        SupplierPurchaseRequest request = purchase(
                ref, List.of(new SupplierOrderLine("ACME-5900000000001", "5900000000001", "MFN-CLEAR-01", 5)));

        // when
        SupplierOrderResult placed = provider.placeOrder(request);
        Optional<SupplierOrderResult> found = provider.findPlacedOrder(request);

        // then
        assertTrue(found.isPresent());
        assertEquals(placed.externalOrderId(), found.get().externalOrderId());
        assertEquals(placed.totalNet(), found.get().totalNet());
    }

    @Test
    void findPlacedOrderReturnsEmptyForDifferentRef() {
        // given
        AcmeSupplierProvider provider = new AcmeSupplierProvider(Map.of());
        String ref = UUID.randomUUID().toString();
        SupplierPurchaseRequest request = purchase(
                ref, List.of(new SupplierOrderLine("ACME-5900000000001", "5900000000001", "MFN-CLEAR-01", 5)));

        // when
        provider.placeOrder(request);
        String differentRef = UUID.randomUUID().toString();
        SupplierPurchaseRequest differentRequest = purchase(
                differentRef, List.of(new SupplierOrderLine("ACME-5900000000001", "5900000000001", "MFN-CLEAR-01", 5)));
        Optional<SupplierOrderResult> notFound = provider.findPlacedOrder(differentRequest);

        // then
        assertFalse(notFound.isPresent());
    }

    @Test
    void quotesProductsBeyondTheOriginalSampleCatalog() {
        // given
        AcmeSupplierProvider provider = new AcmeSupplierProvider(Map.of());

        // when
        List<SupplierQuote> quotes = provider.checkAvailability(
                List.of(new SupplierOrderLine("ACME-5900000000014", "5900000000014", "B650-TOMAHAWK-WIFI", 1)));

        // then
        assertEquals(20, quotes.getFirst().availableQuantity());
        assertEquals(730.89, quotes.getFirst().netPrice(), 0.01);
    }

    @Test
    void feedHeaderIsNotQuotedAsAProduct() {
        // given
        AcmeSupplierProvider provider = new AcmeSupplierProvider(Map.of());

        // when
        List<SupplierQuote> quotes = provider.checkAvailability(
                List.of(new SupplierOrderLine("ACME-ean", "ean", "mfn", 1)));

        // then
        assertEquals(0, quotes.getFirst().availableQuantity());
    }

    private static SupplierOrderLine simLine(String ean, String mfn) {
        return new SupplierOrderLine("ACME-" + ean, ean, mfn, 1);
    }

    private static final SupplierConsignee CONSIGNEE = new SupplierConsignee(null, "Jan", "Kowalski",
            "ul. Polna 1", "00-001", "Warszawa", "PL", "+48601234567", "jan.kowalski@example.com");

    @Test
    void unknownPlacedStoresOrderThenThrowsOutcomeUnknown() {
        // given
        AcmeSupplierProvider provider = new AcmeSupplierProvider(Map.of());
        String ref = UUID.randomUUID().toString();
        SupplierPurchaseRequest request = purchase(ref,
                List.of(simLine("5900000000902", "SIM-UNKNOWN-PLACED")));

        // when / then
        SupplierOrderOutcomeUnknownException ex = assertThrows(SupplierOrderOutcomeUnknownException.class,
                () -> provider.placeOrder(request));
        assertEquals("Acme: simulated timeout after the order was accepted", ex.getMessage());

        Optional<SupplierOrderResult> found = provider.findPlacedOrder(request);
        assertTrue(found.isPresent());
        assertEquals("ACME-PO-" + ref, found.get().externalOrderId());

        SupplierOrderResult replay = provider.placeOrder(request);
        assertEquals(found.get().externalOrderId(), replay.externalOrderId());
        assertEquals(found.get().totalNet(), replay.totalNet());
    }

    @Test
    void unknownLostThrowsFirstThenPlacesOnRetry() {
        // given
        AcmeSupplierProvider provider = new AcmeSupplierProvider(Map.of());
        String ref = UUID.randomUUID().toString();
        SupplierPurchaseRequest request = purchase(ref,
                List.of(simLine("5900000000903", "SIM-UNKNOWN-LOST")));

        // when / then
        SupplierOrderOutcomeUnknownException ex = assertThrows(SupplierOrderOutcomeUnknownException.class,
                () -> provider.placeOrder(request));
        assertEquals("Acme: simulated HTTP 502 before the order was registered", ex.getMessage());
        assertFalse(provider.findPlacedOrder(request).isPresent());

        SupplierOrderResult result = provider.placeOrder(request);
        assertEquals("ACME-PO-" + ref, result.externalOrderId());
    }

    @Test
    void rejectedThrowsRejectedException() {
        // given
        AcmeSupplierProvider provider = new AcmeSupplierProvider(Map.of());
        String ref = UUID.randomUUID().toString();
        SupplierPurchaseRequest request = purchase(ref,
                List.of(simLine("5900000000904", "SIM-REJECTED")));

        // when / then
        SupplierOrderRejectedException ex = assertThrows(SupplierOrderRejectedException.class,
                () -> provider.placeOrder(request));
        assertEquals("Acme: simulated rejection, insufficient stock", ex.getMessage());
        assertFalse(provider.findPlacedOrder(request).isPresent());

        assertThrows(SupplierOrderRejectedException.class, () -> provider.placeOrder(request));
        assertFalse(provider.findPlacedOrder(request).isPresent());
    }

    @Test
    void blankIdReturnsBlankExternalOrderIdFirstThenPlacesOnRetry() {
        // given
        AcmeSupplierProvider provider = new AcmeSupplierProvider(Map.of());
        String ref = UUID.randomUUID().toString();
        SupplierPurchaseRequest request = purchase(ref,
                List.of(simLine("5900000000905", "SIM-BLANK-ID")));

        // when / then
        SupplierOrderResult first = provider.placeOrder(request);
        assertEquals("", first.externalOrderId());
        assertFalse(provider.findPlacedOrder(request).isPresent());

        SupplierOrderResult second = provider.placeOrder(request);
        assertEquals("ACME-PO-" + ref, second.externalOrderId());
    }

    @Test
    void simOkBehavesNormally() {
        // given
        AcmeSupplierProvider provider = new AcmeSupplierProvider(Map.of());
        String ref = UUID.randomUUID().toString();

        // when
        SupplierOrderResult result = provider.placeOrder(purchase(ref,
                List.of(simLine("5900000000901", "SIM-OK"))));

        // then
        assertEquals("ACME-PO-" + ref, result.externalOrderId());
        assertEquals(10.00, result.totalNet(), 0.01);
    }

    @Test
    void overrideWinsOverSku() {
        // given
        AcmeSupplierProvider provider = new AcmeSupplierProvider(
                Map.of("orderingScenarioOverride", "REJECTED"));
        String ref = UUID.randomUUID().toString();
        SupplierPurchaseRequest request = purchase(ref,
                List.of(new SupplierOrderLine("ACME-5900000000001", "5900000000001", "MFN-CLEAR-01", 1)));

        // when / then
        assertThrows(SupplierOrderRejectedException.class, () -> provider.placeOrder(request));
    }

    @Test
    void firstSimLineDecidesWhenSeveralPresent() {
        // given
        AcmeSupplierProvider providerRejected = new AcmeSupplierProvider(Map.of());
        String refRejected = UUID.randomUUID().toString();

        // when / then
        assertThrows(SupplierOrderRejectedException.class, () -> providerRejected.placeOrder(purchase(refRejected,
                List.of(simLine("5900000000904", "SIM-REJECTED"), simLine("5900000000901", "SIM-OK")))));

        AcmeSupplierProvider providerOk = new AcmeSupplierProvider(Map.of());
        String refOk = UUID.randomUUID().toString();
        SupplierOrderResult result = providerOk.placeOrder(purchase(refOk,
                List.of(simLine("5900000000901", "SIM-OK"), simLine("5900000000904", "SIM-REJECTED"))));
        assertEquals("ACME-PO-" + refOk, result.externalOrderId());
    }

    @Test
    void dropshipHonoursScenarios() {
        // given
        AcmeSupplierProvider provider = new AcmeSupplierProvider(Map.of());
        String ref = UUID.randomUUID().toString();
        SupplierDropshipRequest request = new SupplierDropshipRequest(ref,
                List.of(simLine("5900000000902", "SIM-UNKNOWN-PLACED")), CONSIGNEE);

        // when / then
        assertThrows(SupplierOrderOutcomeUnknownException.class, () -> provider.placeDropshipOrder(request));

        SupplierOrderResult replay = provider.placeDropshipOrder(request);
        assertEquals("ACME-DS-" + ref, replay.externalOrderId());
    }

    @Test
    void restoresPlacedOrdersAfterSimulatedRestart() throws IOException {
        // given
        Path tempFile = Files.createTempFile("acme-state-test", ".json");
        Files.deleteIfExists(tempFile);
        System.setProperty("acme.state.file", tempFile.toString());
        AcmeSupplierProvider.reloadStateForTests();
        try {
            AcmeSupplierProvider provider = new AcmeSupplierProvider(Map.of());
            String ref = UUID.randomUUID().toString();
            SupplierPurchaseRequest request = purchase(
                    ref, List.of(new SupplierOrderLine("ACME-5900000000001", "5900000000001", "MFN-CLEAR-01", 5)));
            SupplierOrderResult placed = provider.placeOrder(request);

            // when: simulate a restart by clearing the in-memory state and reloading from disk
            AcmeSupplierProvider.reloadStateForTests();
            Optional<SupplierOrderResult> found = provider.findPlacedOrder(request);

            // then
            assertTrue(found.isPresent());
            assertEquals(placed.externalOrderId(), found.get().externalOrderId());
            assertEquals(placed.totalNet(), found.get().totalNet());
            assertEquals(placed.currency(), found.get().currency());
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    void concurrentFirstAttemptsForSameKeyFulfilExactlyOnce() throws Exception {
        // given
        AcmeSupplierProvider provider = new AcmeSupplierProvider(Map.of());
        String ref = UUID.randomUUID().toString();
        SupplierPurchaseRequest request = purchase(ref, List.of(simLine("5900000000901", "SIM-OK")));

        int threadCount = 2;
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        try {
            List<Future<SupplierOrderResult>> futures = IntStream.range(0, threadCount)
                    .mapToObj(i -> pool.submit(() -> {
                        ready.countDown();
                        start.await();
                        return provider.placeOrder(request);
                    }))
                    .toList();

            // when
            ready.await();
            start.countDown();
            List<SupplierOrderResult> results = new ArrayList<>();
            for (Future<SupplierOrderResult> future : futures) {
                results.add(future.get());
            }

            // then: both callers observe the exact same stored result instance, proving
            // fulfil() ran exactly once for the two concurrent first attempts.
            assertSame(results.get(0), results.get(1));
            assertEquals("ACME-PO-" + ref, results.get(0).externalOrderId());
        } finally {
            pool.shutdownNow();
        }
    }
}
