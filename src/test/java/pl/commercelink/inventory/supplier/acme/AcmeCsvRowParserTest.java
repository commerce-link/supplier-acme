package pl.commercelink.inventory.supplier.acme;

import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.supplier.api.FeedItem;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcmeCsvRowParserTest {

    private final AcmeCsvRowParser parser = new AcmeCsvRowParser();

    @Test
    void parsesRowIntoFeedItem() {
        // given
        String[] row = {
                "1234567890123", "MFN-1", "TestBrand", "Test Product",
                "CPU", "100.50", "PLN", "5"
        };

        // when
        FeedItem result = parser.parse(row);

        // then
        assertEquals("1234567890123", result.getEan());
        assertEquals("MFN-1", result.getMfn());
        assertEquals("TestBrand", result.getBrand());
        assertEquals("Test Product", result.getName());
        assertEquals("CPU", result.getCategory());
        assertEquals(100.50, result.getNetPrice());
        assertEquals("PLN", result.getCurrency());
        assertEquals(5, result.getQty());
        assertEquals(1, result.getLeadTimeDays());
        assertTrue(result.isSellable());
        assertFalse(result.isInStock());
        assertFalse(result.isInDelivery());
        assertNull(result.getNetWeightInGrams());
        assertNull(result.getGrossWeightInGrams());
    }

    @Test
    void passesFeedCategoryColumnThrough() {
        for (String feedCategory : List.of("CPU", "GPU", "Laptops", "Frobnicator", "Karty graficzne")) {
            // given
            String[] row = {
                    "1234567890123", "MFN-1", "TestBrand", "Test Product",
                    feedCategory, "100.50", "PLN", "5"
            };

            // when
            FeedItem result = parser.parse(row);

            // then
            assertEquals(feedCategory, result.getCategory());
        }
    }

    @Test
    void derivesSkuFromUnifiedEan() {
        // when
        FeedItem result = parser.parse(new String[]{"1234567890123", "MFN-1", "Brand", "Name", "CPU", "100.50", "PLN", "5"});

        // then
        assertEquals("ACME-1234567890123", result.getSku());
    }

    @Test
    void derivesSkuFromUnifiedEanForLeadingZeroEan() {
        // when
        FeedItem result = parser.parse(new String[]{"0123456789012", "MFN-1", "Brand", "Name", "CPU", "100.50", "PLN", "5"});

        // then
        assertEquals("ACME-123456789012", result.getSku());
    }
}
