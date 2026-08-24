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

    private static final String SAMPLE_EAN = "5900000000001";

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
        return List.of(new SupplierOrderLine("ACME-" + SAMPLE_EAN, SAMPLE_EAN, "MFN-CLEAR-01", 1));
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
        SupplierProvider baseProvider = new AcmeBSupplierDescriptor().create(Map.of());
        return new SupplierProvider() {
            @Override
            public java.util.Optional<pl.commercelink.inventory.supplier.api.FeedData> download()
                    throws pl.commercelink.inventory.supplier.api.support.ResourceDownloadException {
                return baseProvider.download();
            }

            @Override
            public boolean supportsOrdering() {
                return baseProvider.supportsOrdering();
            }

            @Override
            public boolean requiresDeliveryAddress() {
                return baseProvider.requiresDeliveryAddress();
            }

            @Override
            public List<pl.commercelink.inventory.supplier.api.SupplierDeliveryAddress> deliveryAddresses() {
                return baseProvider.deliveryAddresses();
            }

            @Override
            public List<pl.commercelink.inventory.supplier.api.SupplierQuote> checkAvailability(
                    List<pl.commercelink.inventory.supplier.api.SupplierOrderLine> lines) {
                return baseProvider.checkAvailability(lines);
            }

            @Override
            public pl.commercelink.inventory.supplier.api.SupplierOrderResult placeOrder(
                    SupplierPurchaseRequest request) {
                throw new SupplierOrderOutcomeUnknownException("Simulated transport failure");
            }

            @Override
            public boolean supportsDropshipping() {
                return baseProvider.supportsDropshipping();
            }

            @Override
            public pl.commercelink.inventory.supplier.api.SupplierOrderResult placeDropshipOrder(
                    pl.commercelink.inventory.supplier.api.SupplierDropshipRequest request) {
                return baseProvider.placeDropshipOrder(request);
            }

            @Override
            public java.util.Optional<pl.commercelink.inventory.supplier.api.SupplierOrderResult> findPlacedOrder(
                    SupplierPurchaseRequest request) {
                return baseProvider.findPlacedOrder(request);
            }
        };
    }
}
