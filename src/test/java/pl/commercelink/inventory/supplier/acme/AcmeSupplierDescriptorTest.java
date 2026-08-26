package pl.commercelink.inventory.supplier.acme;

import org.junit.jupiter.api.Test;
import pl.commercelink.provider.api.ProviderField;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AcmeSupplierDescriptorTest {

    @Test
    void configurationFieldsIncludeTheScenarioOverride() {
        // given / when
        List<ProviderField> fields = new AcmeSupplierDescriptor().configurationFields();

        // then
        assertTrue(fields.stream().anyMatch(field -> "orderingScenarioOverride".equals(field.key())));
    }
}
