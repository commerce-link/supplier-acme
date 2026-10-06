package pl.commercelink.inventory.supplier.acme;

import pl.commercelink.inventory.supplier.api.SupplierOrderLine;
import pl.commercelink.inventory.supplier.api.SupplierPickupPoint;
import pl.commercelink.inventory.supplier.api.SupplierProvider;
import pl.commercelink.inventory.supplier.api.testing.SupplierDropshipContractTest;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

class AcmeDropshipContractTest extends SupplierDropshipContractTest {

    private static final String SAMPLE_EAN = "730143318280";

    @Override
    protected SupplierProvider dropshipProvider() {
        return new AcmeSupplierProvider(Map.of());
    }

    @Override
    protected SupplierProvider dropshipProviderWithShortage() {
        return new AcmeSupplierProvider(Map.of("orderingUnavailableEans", SAMPLE_EAN));
    }

    @Override
    protected List<SupplierOrderLine> sampleLines() {
        return List.of(new SupplierOrderLine("ACME-" + SAMPLE_EAN, SAMPLE_EAN, "100-100001973WOF", 1));
    }

    @Override
    protected String uniqueClientOrderRef() {
        return UUID.randomUUID().toString();
    }

    @Override
    protected Optional<SupplierProvider> dropshipProviderRejectingOrders() {
        // A shortage is Acme's only definite rejection.
        return Optional.of(dropshipProviderWithShortage());
    }

    @Override
    protected Optional<SupplierPickupPoint> samplePickupPoint() {
        return Optional.of(new SupplierPickupPoint("InPost", "WAW04A", null, null, null, null));
    }

    @Override
    protected Optional<SupplierProvider> dropshipProviderWithoutPickupPoints() {
        return Optional.of(new AcmeSupplierProvider(Map.of("orderingPickupPointsEnabled", "0")));
    }

    @Override
    protected Optional<String> remotePickupPointCode() {
        return AcmeSupplierProvider.lastPickupPointCode(AcmeSupplierDescriptor.SUPPLIER.name());
    }
}
