package pl.commercelink.inventory.supplier.acme;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AcmeCategoryMapperTest {

    @Test
    void knowsExactlyTheFeedCategoriesOfTheAcmeCatalog() {
        assertEquals(Set.of(
                        "CPU", "GPU", "Memory", "Motherboard", "PSU", "Storage", "Cooler", "Case",
                        "Fan", "Displays", "Keyboards", "Mice", "MousePads", "Headphones",
                        "Microphones", "Webcams", "Speakers", "Laptops"),
                AcmeCategoryMapper.KNOWN_CATEGORIES);
    }

    @Test
    void mapsKnownCategoryToItselfAndUnknownToOther() {
        assertEquals("GPU", AcmeCategoryMapper.from("GPU"));
        assertEquals("Other", AcmeCategoryMapper.from("Frobnicator"));
        assertEquals("Other", AcmeCategoryMapper.from("gpu"));
    }
}
