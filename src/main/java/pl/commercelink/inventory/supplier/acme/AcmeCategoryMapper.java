package pl.commercelink.inventory.supplier.acme;

import pl.commercelink.inventory.supplier.api.Taxonomy;

import java.util.Set;

class AcmeCategoryMapper {

    static final Set<String> KNOWN_CATEGORIES = Set.of(
            "CPU", "GPU", "Memory", "Motherboard", "PSU", "Storage", "Cooler", "Case",
            "Fan", "Displays", "Keyboards", "Mice", "MousePads", "Headphones",
            "Microphones", "Webcams", "Speakers", "Laptops");

    static String from(String category) {
        return KNOWN_CATEGORIES.contains(category) ? category : Taxonomy.OTHER;
    }
}
