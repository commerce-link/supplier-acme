package pl.commercelink.inventory.supplier.acme;

import pl.commercelink.inventory.supplier.api.SupplierConsignee;
import pl.commercelink.inventory.supplier.api.SupplierDropshipRequest;
import pl.commercelink.inventory.supplier.api.SupplierOrderLine;
import pl.commercelink.inventory.supplier.api.SupplierOrderLookup;
import pl.commercelink.inventory.supplier.api.SupplierOrderResult;
import pl.commercelink.inventory.supplier.api.SupplierProvider;
import pl.commercelink.inventory.supplier.api.testing.SupplierOrderTrackingContractTest;

import java.util.List;
import java.util.Map;
import java.util.UUID;

class AcmeTrackingContractTest extends SupplierOrderTrackingContractTest {

    private static final SupplierConsignee CONSIGNEE = new SupplierConsignee(null, "Jan", "Kowalski",
            "ul. Polna 1", "00-001", "Warszawa", "PL", "+48601234567", "jan.kowalski@example.com");

    // Acme requires a shipping service; this is the default valid choice for tests unrelated to that option.
    private static final Map<String, String> ACME_OPTIONS =
            Map.of(AcmeSupplierProvider.SHIPPING_SERVICE_OPTION, "standard");

    @Override
    protected SupplierProvider trackingProvider() {
        return new AcmeSupplierProvider(Map.of("trackingShipAfterChecks", "2"));
    }

    @Override
    protected List<SupplierOrderLine> sampleLines() {
        return List.of(new SupplierOrderLine("ACME-730143318280", "730143318280", "100-100001973WOF", 1));
    }

    @Override
    protected String uniqueClientOrderRef() {
        return UUID.randomUUID().toString();
    }

    @Override
    protected SupplierOrderResult placeSampleOrder(SupplierProvider provider, String clientOrderRef) {
        return provider.placeDropshipOrder(
                new SupplierDropshipRequest(clientOrderRef, sampleLines(), CONSIGNEE, null, null, ACME_OPTIONS));
    }

    @Override
    protected boolean advanceToShipped(SupplierProvider provider, String clientOrderRef, String externalOrderId) {
        provider.trackOrder(new SupplierOrderLookup(externalOrderId, clientOrderRef));
        return true;
    }
}
