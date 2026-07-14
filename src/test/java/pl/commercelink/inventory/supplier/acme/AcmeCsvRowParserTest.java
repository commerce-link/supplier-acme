package pl.commercelink.inventory.supplier.acme;

import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.supplier.api.ParsedRow;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcmeCsvRowParserTest {

    private static final List<String> KNOWN_CATEGORIES = List.of(
            "CPU", "GPU", "Memory", "Motherboard", "PSU", "Storage", "Cooler", "Case",
            "Fan", "Displays", "Keyboards", "Mice", "MousePads", "Headphones",
            "Microphones", "Webcams", "Speakers", "Laptops");

    private final AcmeCsvRowParser parser = new AcmeCsvRowParser(AcmeSupplierDescriptor.SUPPLIER);

    @Test
    void parsesRowIntoItemAndTaxonomy() {
        String[] row = {
                "1234567890123", "MFN-1", "TestBrand", "Test Product",
                "CPU", "100.50", "PLN", "5"
        };

        ParsedRow result = parser.parse(row);

        assertEquals("1234567890123", result.item().ean());
        assertEquals("MFN-1", result.item().mfn());
        assertEquals(100.50, result.item().netPrice());
        assertEquals("PLN", result.item().currency());
        assertEquals(5, result.item().qty());
        assertEquals("Acme", result.item().supplier());
        assertEquals("TestBrand", result.taxonomy().brand());
        assertEquals("Test Product", result.taxonomy().name());
        assertEquals("CPU", result.taxonomy().category());
        assertEquals(5, result.taxonomy().dataAccuracyScore());
        assertNull(result.taxonomy().netWeightInGrams());
        assertNull(result.taxonomy().grossWeightInGrams());
        assertTrue(result.taxonomy().isProcessable());
    }

    @Test
    void mapsEveryKnownFeedCategoryToItself() {
        for (String category : KNOWN_CATEGORIES) {
            String[] row = {
                    "1234567890123", "MFN-1", "TestBrand", "Test Product",
                    category, "100.50", "PLN", "5"
            };

            ParsedRow result = parser.parse(row);

            assertEquals(category, result.taxonomy().category());
            assertTrue(result.taxonomy().isProcessable());
        }
    }

    @Test
    void mapsUnknownFeedCategoryToOtherWhichIsNotProcessable() {
        String[] row = {
                "1234567890123", "MFN-1", "TestBrand", "Test Product",
                "Frobnicator", "100.50", "PLN", "5"
        };

        ParsedRow result = parser.parse(row);

        assertEquals("Other", result.taxonomy().category());
        assertFalse(result.taxonomy().isProcessable());
    }
}
