package pl.commercelink.inventory.supplier.acme;

import pl.commercelink.inventory.supplier.api.FeedData;
import pl.commercelink.inventory.supplier.api.SupplierOrderException;
import pl.commercelink.inventory.supplier.api.SupplierOrderLine;
import pl.commercelink.inventory.supplier.api.SupplierOrderResult;
import pl.commercelink.inventory.supplier.api.SupplierProvider;
import pl.commercelink.inventory.supplier.api.SupplierPurchaseRequest;
import pl.commercelink.inventory.supplier.api.SupplierQuote;
import pl.commercelink.inventory.supplier.api.support.ResourceDownloadException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

class AcmeSupplierProvider implements SupplierProvider {

    private static final Map<String, SupplierOrderResult> PLACED_ORDERS = new ConcurrentHashMap<>();

    private final Set<String> unavailableEans;
    private final double priceDriftFactor;

    AcmeSupplierProvider(Map<String, String> configuration) {
        String rawEans = trimmedOrDefault(configuration, "orderingUnavailableEans", "");
        this.unavailableEans = Arrays.stream(rawEans.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
        double driftPercent = Double.parseDouble(
                trimmedOrDefault(configuration, "orderingPriceDriftPercent", "0"));
        this.priceDriftFactor = 1 + driftPercent / 100;
    }

    private static String trimmedOrDefault(Map<String, String> configuration, String key, String defaultValue) {
        String value = configuration.get(key);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    @Override
    public Optional<FeedData> download() throws ResourceDownloadException {
        try {
            return Optional.of(FeedData.csv(feedBytes()));
        } catch (RuntimeException e) {
            throw new ResourceDownloadException("Failed to download ACME feed", e);
        }
    }

    @Override
    public boolean supportsOrdering() {
        return true;
    }

    @Override
    public List<SupplierQuote> checkAvailability(List<SupplierOrderLine> lines) {
        Map<String, String[]> feedByEan = feedRowsByEan();
        return lines.stream()
                .map(line -> toQuote(line, feedByEan.get(line.ean())))
                .collect(Collectors.toList());
    }

    @Override
    public SupplierOrderResult placeOrder(SupplierPurchaseRequest request) {
        return PLACED_ORDERS.computeIfAbsent(request.clientOrderRef(), ref -> {
            List<SupplierQuote> quotes = checkAvailability(request.lines());
            Map<String, SupplierQuote> quotesByEan = quotes.stream()
                    .collect(Collectors.toMap(SupplierQuote::ean, Function.identity()));
            for (SupplierOrderLine line : request.lines()) {
                SupplierQuote quote = quotesByEan.get(line.ean());
                if (quote.availableQuantity() < line.quantity()) {
                    throw new SupplierOrderException(
                            "Insufficient availability for EAN " + line.ean()
                                    + ": requested " + line.quantity()
                                    + ", available " + quote.availableQuantity());
                }
            }
            double totalNet = request.lines().stream()
                    .mapToDouble(line -> line.quantity() * quotesByEan.get(line.ean()).netPrice())
                    .sum();
            return new SupplierOrderResult("ACME-PO-" + ref, totalNet, "PLN", quotes);
        });
    }

    private SupplierQuote toQuote(SupplierOrderLine line, String[] feedRow) {
        if (feedRow == null || unavailableEans.contains(line.ean())) {
            return new SupplierQuote(line.ean(), line.mfn(), 0, 0, "PLN");
        }
        double livePrice = Double.parseDouble(feedRow[5]) * priceDriftFactor;
        return new SupplierQuote(feedRow[0], feedRow[1],
                Integer.parseInt(feedRow[7]), livePrice, feedRow[6]);
    }

    private Map<String, String[]> feedRowsByEan() {
        String feed = new String(feedBytes(), StandardCharsets.UTF_8);
        return feed.lines()
                .filter(row -> !row.isBlank())
                .map(row -> row.split(";"))
                .collect(Collectors.toMap(row -> row[0], Function.identity()));
    }

    private byte[] feedBytes() {
        try {
            return getClass().getClassLoader()
                    .getResourceAsStream("acme-products.csv")
                    .readAllBytes();
        } catch (IOException | NullPointerException e) {
            throw new RuntimeException("Failed to load acme-products.csv from resources", e);
        }
    }
}
