package pl.commercelink.inventory.supplier.acme;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AcmeScenarioTest {

    @Test
    void mapsSimMfnsToScenarios() {
        assertEquals(Optional.of(AcmeScenario.OK), AcmeScenario.fromMfn("SIM-OK"));
        assertEquals(Optional.of(AcmeScenario.UNKNOWN_PLACED), AcmeScenario.fromMfn("SIM-UNKNOWN-PLACED"));
        assertEquals(Optional.of(AcmeScenario.UNKNOWN_LOST), AcmeScenario.fromMfn("SIM-UNKNOWN-LOST"));
        assertEquals(Optional.of(AcmeScenario.REJECTED), AcmeScenario.fromMfn("sim-rejected"));
        assertEquals(Optional.of(AcmeScenario.BLANK_ID), AcmeScenario.fromMfn("SIM-BLANK-ID"));
        assertEquals(Optional.empty(), AcmeScenario.fromMfn("100-100001973WOF"));
    }

    @Test
    void mapsOverrideValues() {
        assertEquals(Optional.of(AcmeScenario.UNKNOWN_PLACED), AcmeScenario.fromOverride("UNKNOWN_PLACED"));
        assertEquals(Optional.of(AcmeScenario.REJECTED), AcmeScenario.fromOverride(" rejected "));
        assertEquals(Optional.empty(), AcmeScenario.fromOverride(""));
        assertEquals(Optional.empty(), AcmeScenario.fromOverride("nope"));
    }
}
