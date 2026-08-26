package pl.commercelink.inventory.supplier.acme;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import pl.commercelink.inventory.supplier.api.SupplierOrderResult;
import pl.commercelink.inventory.supplier.api.SupplierQuote;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Best-effort persistence for {@code AcmeSupplierProvider}'s in-memory placed-order and
 * attempted-ref state, so a restart replays the same idempotent outcomes instead of
 * re-running the simulated scenarios. Persistence is never allowed to fail a purchase:
 * {@link #load} swallows a missing or corrupt file and starts empty, and {@link #save}
 * swallows any I/O failure.
 */
final class AcmeStateStore {

    private static final String SYSTEM_PROPERTY = "acme.state.file";
    private static final String NONE = "none";

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private final Path file;

    AcmeStateStore(Path file) {
        this.file = file;
    }

    static AcmeStateStore fromSystemProperty() {
        String defaultPath = System.getProperty("java.io.tmpdir") + "/supplier-acme-state.json";
        String configured = System.getProperty(SYSTEM_PROPERTY, defaultPath);
        if (NONE.equalsIgnoreCase(configured.trim())) {
            return new AcmeStateStore(null);
        }
        return new AcmeStateStore(Path.of(configured));
    }

    void load(Map<String, SupplierOrderResult> orders, Set<String> attempted) {
        if (file == null) {
            return;
        }
        try {
            if (!Files.isRegularFile(file)) {
                return;
            }
            byte[] bytes = Files.readAllBytes(file);
            StateDocument document = MAPPER.readValue(bytes, StateDocument.class);
            if (document.orders() != null) {
                document.orders().forEach((key, value) -> orders.put(key, value.toResult()));
            }
            if (document.attempted() != null) {
                attempted.addAll(document.attempted());
            }
        } catch (IOException | RuntimeException e) {
            // Missing or corrupt state file: start empty rather than fail startup.
        }
    }

    void save(Map<String, SupplierOrderResult> orders, Set<String> attempted) {
        if (file == null) {
            return;
        }
        try {
            Map<String, OrderRecord> orderRecords = new LinkedHashMap<>();
            orders.forEach((key, value) -> orderRecords.put(key, OrderRecord.from(value)));
            StateDocument document = new StateDocument(orderRecords, new LinkedHashSet<>(attempted));
            byte[] bytes = MAPPER.writeValueAsBytes(document);

            Path absoluteFile = file.toAbsolutePath();
            Path parent = absoluteFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path tempFile = Files.createTempFile(parent, "supplier-acme-state", ".tmp");
            try {
                Files.write(tempFile, bytes);
                Files.move(tempFile, absoluteFile, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(tempFile);
            }
        } catch (IOException | RuntimeException e) {
            // Best effort: a persistence failure must never fail a purchase.
        }
    }

    private record StateDocument(Map<String, OrderRecord> orders, Set<String> attempted) {
    }

    private record OrderRecord(String externalOrderId, double totalNet, String currency,
                                List<QuoteRecord> confirmedLines, boolean provisional) {

        static OrderRecord from(SupplierOrderResult result) {
            List<QuoteRecord> lines = result.confirmedLines() == null
                    ? List.of()
                    : result.confirmedLines().stream().map(QuoteRecord::from).collect(Collectors.toList());
            return new OrderRecord(result.externalOrderId(), result.totalNet(), result.currency(), lines,
                    result.provisional());
        }

        SupplierOrderResult toResult() {
            List<SupplierQuote> quotes = confirmedLines == null
                    ? List.of()
                    : confirmedLines.stream().map(QuoteRecord::toQuote).collect(Collectors.toList());
            return new SupplierOrderResult(externalOrderId, totalNet, currency, quotes, provisional);
        }
    }

    private record QuoteRecord(String ean, String mfn, int availableQuantity, double netPrice, String currency) {

        static QuoteRecord from(SupplierQuote quote) {
            return new QuoteRecord(quote.ean(), quote.mfn(), quote.availableQuantity(), quote.netPrice(),
                    quote.currency());
        }

        SupplierQuote toQuote() {
            return new SupplierQuote(ean, mfn, availableQuantity, netPrice, currency);
        }
    }
}
