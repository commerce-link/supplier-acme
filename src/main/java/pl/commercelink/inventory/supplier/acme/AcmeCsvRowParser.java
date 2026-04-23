package pl.commercelink.inventory.supplier.acme;

import pl.commercelink.inventory.supplier.api.CsvRowParser;
import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.inventory.supplier.api.ParsedRow;
import pl.commercelink.inventory.supplier.api.Taxonomy;
import pl.commercelink.taxonomy.ProductCategory;

class AcmeCsvRowParser implements CsvRowParser {

    @Override
    public ParsedRow parse(String[] row) {
        String ean = row[0];
        String mfn = row[1];
        String brand = row[2];
        String name = row[3];
        String category = row[4];
        double netPrice = Double.parseDouble(row[5]);
        String currency = row[6];
        int qty = Integer.parseInt(row[7]);

        ProductCategory productCategory = AcmeCategoryMapper.from(category);

        return new ParsedRow(
                new InventoryItem(ean, mfn, netPrice, currency, qty,
                        1, AcmeSupplierDescriptor.SUPPLIER.name(), true),
                new Taxonomy(ean, mfn, brand, name, productCategory,
                        AcmeSupplierDescriptor.SUPPLIER.accuracyScore())
        );
    }
}
