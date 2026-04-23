package pl.commercelink.inventory.supplier.acme;

import pl.commercelink.inventory.supplier.api.FeedData;
import pl.commercelink.inventory.supplier.api.FeedFormat;
import pl.commercelink.inventory.supplier.api.ShippingCostPolicy;
import pl.commercelink.inventory.supplier.api.ShippingPolicy;
import pl.commercelink.inventory.supplier.api.ShippingTerms;
import pl.commercelink.inventory.supplier.api.SupplierDescriptor;
import pl.commercelink.inventory.supplier.api.SupplierInfo;
import pl.commercelink.inventory.supplier.api.SupplierType;
import pl.commercelink.inventory.supplier.api.support.ResourceDownloadException;

import java.io.IOException;
import java.util.Optional;

public class AcmeSupplierDescriptor implements SupplierDescriptor {

    public static final SupplierInfo SUPPLIER = new SupplierInfo("Acme", SupplierType.Distributor, 5, "PL",
            new ShippingPolicy(new ShippingTerms(1, new ShippingCostPolicy.Free())),
            null);

    @Override
    public Optional<FeedData> download(String secret) throws ResourceDownloadException {
        try {
            byte[] bytes = getClass().getClassLoader()
                    .getResourceAsStream("acme-products.csv")
                    .readAllBytes();
            return Optional.of(FeedData.csv(bytes));
        } catch (IOException | NullPointerException e) {
            throw new ResourceDownloadException("Failed to load acme-products.csv from resources", e);
        }
    }

    @Override
    public FeedFormat feedFormat() {
        return new FeedFormat.Csv(new AcmeCsvRowParser(), ';');
    }

    @Override
    public SupplierInfo supplierInfo() {
        return SUPPLIER;
    }
}
