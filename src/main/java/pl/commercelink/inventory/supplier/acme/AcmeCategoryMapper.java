package pl.commercelink.inventory.supplier.acme;

import pl.commercelink.taxonomy.ProductCategory;

import java.util.Map;

class AcmeCategoryMapper {

    private static final Map<String, ProductCategory> MAPPINGS = Map.ofEntries(
            Map.entry("CPU", ProductCategory.CPU),
            Map.entry("GPU", ProductCategory.GPU),
            Map.entry("Memory", ProductCategory.Memory),
            Map.entry("Motherboard", ProductCategory.Motherboard),
            Map.entry("PSU", ProductCategory.PSU),
            Map.entry("Storage", ProductCategory.Storage),
            Map.entry("Cooler", ProductCategory.Cooler),
            Map.entry("Case", ProductCategory.Case),
            Map.entry("Fan", ProductCategory.Fan),
            Map.entry("Displays", ProductCategory.Displays),
            Map.entry("Keyboards", ProductCategory.Keyboards),
            Map.entry("Mice", ProductCategory.Mice),
            Map.entry("MousePads", ProductCategory.MousePads),
            Map.entry("Headphones", ProductCategory.Headphones),
            Map.entry("Microphones", ProductCategory.Microphones),
            Map.entry("Webcams", ProductCategory.Webcams),
            Map.entry("Speakers", ProductCategory.Speakers),
            Map.entry("Laptops", ProductCategory.Laptops)
    );

    static ProductCategory from(String category) {
        return MAPPINGS.getOrDefault(category, ProductCategory.Other);
    }
}
