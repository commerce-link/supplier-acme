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

public class AcmeBSupplierDescriptor implements SupplierProviderDescriptor {

    public static final SupplierInfo SUPPLIER = new SupplierInfo("AcmeB", SupplierType.Retailer, 3, "PL",
            new ShippingPolicy(new ShippingTerms(3, new ShippingCostPolicy.FlatRate(500, 14.99))),
            null);

    private static final String FEED_RESOURCE = "acmeb-products.csv";

    @Override
    public SupplierProvider create(Map<String, String> configuration) {
        return new AcmeSupplierProvider(configuration, SUPPLIER, FEED_RESOURCE);
    }

    @Override
    public List<ProviderField> configurationFields() {
        return List.of(
                new ProviderField("orderingUnavailableEans", "Symulacja: EAN-y niedostępne u dostawcy",
                        ProviderField.FieldType.TEXT, false,
                        "np. 5900000000001,5900000000003 — po przecinku, zawsze kwotowane jako brak"),
                new ProviderField("orderingPriceDriftPercent", "Symulacja: odchylenie ceny live od feedu (%)",
                        ProviderField.FieldType.NUMBER, false,
                        "np. 5 = ceny przy zamówieniu o 5% wyższe niż w feedzie"),
                new ProviderField("orderingDropshipEnabled", "Symulacja: dropshipping (1 = włączony)",
                        ProviderField.FieldType.NUMBER, false,
                        "domyślnie wyłączony — AcmeB służy też jako dostawca bez dropshippingu"));
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
