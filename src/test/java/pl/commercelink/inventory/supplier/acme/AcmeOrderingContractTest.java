package pl.commercelink.inventory.supplier.acme;

import pl.commercelink.inventory.supplier.api.SupplierOrderLine;
import pl.commercelink.inventory.supplier.api.SupplierProvider;
import pl.commercelink.inventory.supplier.api.testing.SupplierOrderingContractTest;

import java.util.List;
import java.util.Map;
import java.util.UUID;

class AcmeOrderingContractTest extends SupplierOrderingContractTest {

    private static final String SAMPLE_EAN = "5900000000001";

    @Override
    protected SupplierProvider providerFullyAvailable() {
        return new AcmeSupplierProvider(Map.of());
    }

    @Override
    protected SupplierProvider providerWithShortage() {
        return new AcmeSupplierProvider(Map.of("orderingUnavailableEans", SAMPLE_EAN));
    }

    @Override
    protected List<SupplierOrderLine> sampleLines() {
        return List.of(new SupplierOrderLine("ACME-" + SAMPLE_EAN, SAMPLE_EAN, "MFN-CLEAR-01", 1));
    }

    @Override
    protected String uniqueClientOrderRef() {
        return UUID.randomUUID().toString();
    }
}
