package pl.commercelink.inventory.supplier.acme;

import pl.commercelink.inventory.supplier.api.SupplierOrderLine;
import pl.commercelink.inventory.supplier.api.SupplierProvider;
import pl.commercelink.inventory.supplier.api.testing.SupplierDropshipContractTest;

import java.util.List;
import java.util.Map;
import java.util.UUID;

class AcmeDropshipContractTest extends SupplierDropshipContractTest {

    private static final String SAMPLE_EAN = "5900000000001";

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
        return List.of(new SupplierOrderLine("ACME-" + SAMPLE_EAN, SAMPLE_EAN, "MFN-CLEAR-01", 1));
    }

    @Override
    protected String uniqueClientOrderRef() {
        return UUID.randomUUID().toString();
    }
}
