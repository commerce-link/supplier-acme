package pl.commercelink.inventory.supplier.acme;

import pl.commercelink.inventory.supplier.api.SupplierOrderLine;
import pl.commercelink.inventory.supplier.api.SupplierOrderOutcomeUnknownException;
import pl.commercelink.inventory.supplier.api.SupplierProvider;
import pl.commercelink.inventory.supplier.api.SupplierPurchaseRequest;
import pl.commercelink.inventory.supplier.api.testing.SupplierOrderingContractTest;

import java.util.List;
import java.util.Map;
import java.util.UUID;

class AcmeBOrderingContractTest extends SupplierOrderingContractTest {

    private static final String SAMPLE_EAN = "4711636046213";

    @Override
    protected SupplierProvider providerFullyAvailable() {
        return new AcmeBSupplierDescriptor().create(Map.of());
    }

    @Override
    protected SupplierProvider providerWithShortage() {
        return new AcmeBSupplierDescriptor().create(Map.of("orderingUnavailableEans", SAMPLE_EAN));
    }

    @Override
    protected List<SupplierOrderLine> sampleLines() {
        return List.of(new SupplierOrderLine("ACME-" + SAMPLE_EAN, SAMPLE_EAN, "90YV0M17-M0NA00", 1));
    }

    @Override
    protected String deliveryAddressId() {
        return "2";
    }

    @Override
    protected String uniqueClientOrderRef() {
        return UUID.randomUUID().toString();
    }

    @Override
    protected SupplierProvider providerRejectingOrders() {
        return new AcmeBSupplierDescriptor().create(Map.of("orderingUnavailableEans", SAMPLE_EAN));
    }

    @Override
    protected SupplierProvider providerWithPlacementTransportFailure() {
        return new AcmeSupplierProvider(Map.of(), AcmeBSupplierDescriptor.SUPPLIER, "acmeb-products.csv") {
            @Override
            public pl.commercelink.inventory.supplier.api.SupplierOrderResult placeOrder(
                    SupplierPurchaseRequest request) {
                throw new SupplierOrderOutcomeUnknownException("simulated transport failure during placement");
            }
        };
    }
}
