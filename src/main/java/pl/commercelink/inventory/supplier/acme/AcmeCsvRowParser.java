package pl.commercelink.inventory.supplier.acme;

import pl.commercelink.inventory.supplier.api.CsvRowParser;
import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.inventory.supplier.api.ParsedRow;
import pl.commercelink.inventory.supplier.api.SupplierInfo;
import pl.commercelink.inventory.supplier.api.SupplierProduct;

class AcmeCsvRowParser implements CsvRowParser {

    private final SupplierInfo supplier;

    AcmeCsvRowParser(SupplierInfo supplier) {
        this.supplier = supplier;
    }

    @Override
    public ParsedRow parse(String[] row) {
        String ean = row[0];
        String mfn = row[1];
        String brand = row[2];
        String name = row[3];
        String rawCategory = row[4];
        double netPrice = Double.parseDouble(row[5]);
        String currency = row[6];
        int qty = Integer.parseInt(row[7]);

        return new ParsedRow(
                new InventoryItem(ean, mfn, netPrice, currency, qty,
                        1, supplier.name(), true),
                new SupplierProduct(ean, mfn, brand, name,
                        supplier.accuracyScore(), null, null, rawCategory)
        );
    }
}
