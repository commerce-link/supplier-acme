package pl.commercelink.inventory.supplier.acme;

import pl.commercelink.inventory.supplier.api.CsvRowParser;
import pl.commercelink.inventory.supplier.api.FeedItem;

import static pl.commercelink.taxonomy.UnifiedProductIdentifiers.unifyEan;

class AcmeCsvRowParser implements CsvRowParser {

    @Override
    public FeedItem parse(String[] row) {
        String ean = row[0];
        return new AcmeFeedItem(ean, row[1], row[2], row[3], row[4],
                Double.parseDouble(row[5]), row[6], Integer.parseInt(row[7]), "ACME-" + unifyEan(ean));
    }

    private record AcmeFeedItem(String ean, String mfn, String brand, String name, String category,
                                double netPrice, String currency, int qty, String sku) implements FeedItem {

        @Override
        public String getEan() {
            return ean;
        }

        @Override
        public String getMfn() {
            return mfn;
        }

        @Override
        public String getBrand() {
            return brand;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getCategory() {
            return category;
        }

        @Override
        public double getNetPrice() {
            return netPrice;
        }

        @Override
        public String getCurrency() {
            return currency;
        }

        @Override
        public int getQty() {
            return qty;
        }

        @Override
        public int getLeadTimeDays() {
            return 1;
        }

        @Override
        public String getSku() {
            return sku;
        }
    }
}
