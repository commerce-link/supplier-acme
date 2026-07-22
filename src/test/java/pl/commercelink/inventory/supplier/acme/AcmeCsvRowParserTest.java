package pl.commercelink.inventory.supplier.acme;

import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.supplier.api.ParsedRow;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class AcmeCsvRowParserTest {

    private final AcmeCsvRowParser parser = new AcmeCsvRowParser(AcmeSupplierDescriptor.SUPPLIER);

    @Test
    void parsesRowIntoItemAndUncategorizedTaxonomy() {
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
        assertEquals("TestBrand", result.taxonomy().brand());
        assertEquals("Test Product", result.taxonomy().name());
        assertEquals(5, result.taxonomy().dataAccuracyScore());
        assertNull(result.taxonomy().netWeightInGrams());
        assertNull(result.taxonomy().grossWeightInGrams());
        assertNull(result.taxonomy().category());
        assertFalse(result.taxonomy().isProcessable());
    }

    @Test
    void ignoresFeedCategoryColumnEvenWhenItMatchesKnownCategory() {
        for (String feedCategory : List.of("CPU", "GPU", "Laptops", "Frobnicator")) {
            // given
            String[] row = {
                    "1234567890123", "MFN-1", "TestBrand", "Test Product",
                    feedCategory, "100.50", "PLN", "5"
            };

            // when
            ParsedRow result = parser.parse(row);

            // then
            assertNull(result.taxonomy().category());
            assertFalse(result.taxonomy().isProcessable());
        }
    }

    @Test
    void parsePassesRawFeedCategoryAndLeavesCategoryNull() {
        // given
        String[] row = {
                "1234567890123", "MFN-1", "TestBrand", "Test Product",
                "Karty graficzne", "100.50", "PLN", "5"
        };

        // when
        ParsedRow result = parser.parse(row);

        // then
        assertNull(result.taxonomy().category());
        assertEquals("Karty graficzne", result.taxonomy().rawCategory());
    }
}
