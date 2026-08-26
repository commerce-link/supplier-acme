package pl.commercelink.inventory.supplier.acme;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.supplier.api.SupplierOrderResult;
import pl.commercelink.inventory.supplier.api.SupplierQuote;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcmeStateStoreTest {

    private String originalStateFileProperty;

    @AfterEach
    void restoreSystemProperty() {
        if (originalStateFileProperty == null) {
            System.clearProperty("acme.state.file");
        } else {
            System.setProperty("acme.state.file", originalStateFileProperty);
        }
    }

    private static SupplierOrderResult sampleOrder() {
        return new SupplierOrderResult("ACME-PO-abc", 1299.00, "PLN",
                List.of(new SupplierQuote("5900000000001", "MFN-CLEAR-01", 20, 1299.00, "PLN")));
    }

    @Test
    void savesAndReloadsOrdersAndAttemptedRefs(@org.junit.jupiter.api.io.TempDir Path tempDir) {
        // given
        Path file = tempDir.resolve("state.json");
        AcmeStateStore writer = new AcmeStateStore(file);
        Map<String, SupplierOrderResult> orders = new HashMap<>();
        orders.put("Acme|order-1", sampleOrder());
        Set<String> attempted = new HashSet<>(Set.of("Acme|order-1", "Acme|order-2"));

        // when
        writer.save(orders, attempted);
        AcmeStateStore reader = new AcmeStateStore(file);
        Map<String, SupplierOrderResult> loadedOrders = new HashMap<>();
        Set<String> loadedAttempted = new HashSet<>();
        reader.load(loadedOrders, loadedAttempted);

        // then
        assertEquals(attempted, loadedAttempted);
        assertEquals(1, loadedOrders.size());
        SupplierOrderResult loaded = loadedOrders.get("Acme|order-1");
        SupplierOrderResult original = orders.get("Acme|order-1");
        assertEquals(original.externalOrderId(), loaded.externalOrderId());
        assertEquals(original.totalNet(), loaded.totalNet());
        assertEquals(original.currency(), loaded.currency());
        assertEquals(original.provisional(), loaded.provisional());
        assertEquals(original.confirmedLines().size(), loaded.confirmedLines().size());
        SupplierQuote originalQuote = original.confirmedLines().get(0);
        SupplierQuote loadedQuote = loaded.confirmedLines().get(0);
        assertEquals(originalQuote.ean(), loadedQuote.ean());
        assertEquals(originalQuote.mfn(), loadedQuote.mfn());
        assertEquals(originalQuote.availableQuantity(), loadedQuote.availableQuantity());
        assertEquals(originalQuote.netPrice(), loadedQuote.netPrice());
        assertEquals(originalQuote.currency(), loadedQuote.currency());
    }

    @Test
    void missingFileLoadsEmpty(@org.junit.jupiter.api.io.TempDir Path tempDir) {
        // given
        Path file = tempDir.resolve("does-not-exist.json");
        AcmeStateStore store = new AcmeStateStore(file);
        Map<String, SupplierOrderResult> orders = new HashMap<>();
        Set<String> attempted = new HashSet<>();

        // when
        store.load(orders, attempted);

        // then
        assertTrue(orders.isEmpty());
        assertTrue(attempted.isEmpty());
    }

    @Test
    void corruptFileLoadsEmpty(@org.junit.jupiter.api.io.TempDir Path tempDir) throws IOException {
        // given
        Path file = tempDir.resolve("corrupt.json");
        Files.writeString(file, "{ this is not valid json ]");
        AcmeStateStore store = new AcmeStateStore(file);
        Map<String, SupplierOrderResult> orders = new HashMap<>();
        Set<String> attempted = new HashSet<>();

        // when
        store.load(orders, attempted);

        // then
        assertTrue(orders.isEmpty());
        assertTrue(attempted.isEmpty());
    }

    @Test
    void noneDisablesPersistence(@org.junit.jupiter.api.io.TempDir Path tempDir) {
        // given
        originalStateFileProperty = System.getProperty("acme.state.file");
        String originalTmpDirProperty = System.getProperty("java.io.tmpdir");
        System.setProperty("java.io.tmpdir", tempDir.toString());
        System.setProperty("acme.state.file", "none");

        try {
            // when
            AcmeStateStore store = AcmeStateStore.fromSystemProperty();
            store.save(Map.of("k", sampleOrder()), Set.of("k"));

            // then
            assertFalse(Files.exists(tempDir.resolve("supplier-acme-state.json")));
        } finally {
            System.setProperty("java.io.tmpdir", originalTmpDirProperty);
        }
    }

    @Test
    void saveFailureDoesNotThrow() throws IOException {
        // given: the target path is itself an existing directory, so the atomic
        // temp-file + move can never succeed.
        Path dir = Files.createTempDirectory("acme-state-dir");
        try {
            AcmeStateStore store = new AcmeStateStore(dir);

            // when / then: must not throw despite the write being impossible.
            store.save(Map.of("k", sampleOrder()), Set.of("k"));
        } finally {
            Files.deleteIfExists(dir);
        }
    }
}
