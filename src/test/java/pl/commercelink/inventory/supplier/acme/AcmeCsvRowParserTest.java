package pl.commercelink.inventory.supplier.acme;

import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.supplier.api.ParsedRow;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AcmeCsvRowParserTest {

    private final AcmeCsvRowParser parser = new AcmeCsvRowParser(AcmeSupplierDescriptor.SUPPLIER);

    @Test
    void parsesRowIntoItemAndProduct() {
        // given
        String[] row = {
                "1234567890123", "MFN-1", "TestBrand", "Test Product",
                "CPU", "100.50", "PLN", "5"
        };

        // when
        ParsedRow result = parser.parse(row);

        // then
        assertEquals("1234567890123", result.item().ean());
        assertEquals("MFN-1", result.item().mfn());
        assertEquals(100.50, result.item().netPrice());
        assertEquals("PLN", result.item().currency());
        assertEquals(5, result.item().qty());
        assertEquals("Acme", result.item().supplier());
        assertEquals("TestBrand", result.product().brand());
        assertEquals("Test Product", result.product().name());
        assertEquals(5, result.product().dataAccuracyScore());
        assertNull(result.product().netWeightInGrams());
        assertNull(result.product().grossWeightInGrams());
        assertEquals("CPU", result.product().rawCategory());
    }

    @Test
    void passesFeedCategoryColumnThroughAsRawCategory() {
        for (String feedCategory : List.of("CPU", "GPU", "Laptops", "Frobnicator")) {
            // given
            String[] row = {
                    "1234567890123", "MFN-1", "TestBrand", "Test Product",
                    feedCategory, "100.50", "PLN", "5"
            };

            // when
            ParsedRow result = parser.parse(row);

            // then
            assertEquals(feedCategory, result.product().rawCategory());
        }
    }

    @Test
    void parsePassesRawFeedCategory() {
        // given
        String[] row = {
                "1234567890123", "MFN-1", "TestBrand", "Test Product",
                "Karty graficzne", "100.50", "PLN", "5"
        };

        // when
        ParsedRow result = parser.parse(row);

        // then
        assertEquals("Karty graficzne", result.product().rawCategory());
    }

    @Test
    void derivesSkuFromUnifiedEan() {
        ParsedRow result = parser.parse(new String[]{"1234567890123", "MFN-1", "Brand", "Name", "CPU", "100.50", "PLN", "5"});
        assertEquals("ACME-1234567890123", result.item().sku());
    }

    @Test
    void derivesSkuFromUnifiedEanForLeadingZeroEan() {
        ParsedRow result = parser.parse(new String[]{"0123456789012", "MFN-1", "Brand", "Name", "CPU", "100.50", "PLN", "5"});
        assertEquals("ACME-" + result.item().ean(), result.item().sku());
    }
}
