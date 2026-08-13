package pl.commercelink.inventory.supplier.acme;

import pl.commercelink.inventory.supplier.api.FeedFormat;
import pl.commercelink.inventory.supplier.api.ShippingCostPolicy;
import pl.commercelink.inventory.supplier.api.ShippingPolicy;
import pl.commercelink.inventory.supplier.api.ShippingTerms;
import pl.commercelink.inventory.supplier.api.SupplierProvider;
import pl.commercelink.inventory.supplier.api.SupplierProviderDescriptor;
import pl.commercelink.inventory.supplier.api.SupplierInfo;
import pl.commercelink.inventory.supplier.api.SupplierType;
import pl.commercelink.provider.api.ProviderField;

import java.util.List;
import java.util.Map;

public class AcmeSupplierDescriptor implements SupplierProviderDescriptor {

    public static final SupplierInfo SUPPLIER = new SupplierInfo("Acme", SupplierType.Distributor, 5, "PL",
            new ShippingPolicy(new ShippingTerms(1, new ShippingCostPolicy.Free())),
            null);

    @Override
    public SupplierProvider create(Map<String, String> configuration) {
        return new AcmeSupplierProvider(configuration);
    }

    @Override
    public List<ProviderField> configurationFields() {
        return List.of(
                new ProviderField("orderingUnavailableEans", "Symulacja: EAN-y niedostępne u dostawcy",
                        ProviderField.FieldType.TEXT, false,
                        "np. 5900000000001,5900000000003 — po przecinku, zawsze kwotowane jako brak"),
                new ProviderField("orderingPriceDriftPercent", "Symulacja: odchylenie ceny live od feedu (%)",
                        ProviderField.FieldType.NUMBER, false,
                        "np. 5 = ceny przy zamówieniu o 5% wyższe niż w feedzie"));
    }

    @Override
    public FeedFormat feedFormat() {
        return new FeedFormat.Csv(new AcmeCsvRowParser(SUPPLIER), ';');
    }

    @Override
    public SupplierInfo supplierInfo() {
        return SUPPLIER;
    }
}
