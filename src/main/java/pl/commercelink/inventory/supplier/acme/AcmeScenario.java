package pl.commercelink.inventory.supplier.acme;

import java.util.Locale;
import java.util.Optional;

enum AcmeScenario {
    OK("SIM-OK"),
    UNKNOWN_PLACED("SIM-UNKNOWN-PLACED"),
    UNKNOWN_LOST("SIM-UNKNOWN-LOST"),
    REJECTED("SIM-REJECTED"),
    BLANK_ID("SIM-BLANK-ID");

    private final String mfn;

    AcmeScenario(String mfn) {
        this.mfn = mfn;
    }

    String mfn() {
        return mfn;
    }

    static Optional<AcmeScenario> fromMfn(String candidate) {
        if (candidate == null) return Optional.empty();
        String normalized = candidate.trim().toUpperCase(Locale.ROOT);
        for (AcmeScenario scenario : values()) {
            if (scenario.mfn.equals(normalized)) return Optional.of(scenario);
        }
        return Optional.empty();
    }

    static Optional<AcmeScenario> fromOverride(String value) {
        if (value == null || value.isBlank()) return Optional.empty();
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        for (AcmeScenario scenario : values()) {
            if (scenario.name().equals(normalized)) return Optional.of(scenario);
        }
        return Optional.empty();
    }
}
