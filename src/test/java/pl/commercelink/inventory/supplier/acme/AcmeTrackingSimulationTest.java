package pl.commercelink.inventory.supplier.acme;

import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.supplier.api.SupplierOrderException;
import pl.commercelink.inventory.supplier.api.SupplierOrderResult;
import pl.commercelink.inventory.supplier.api.SupplierOrderState;
import pl.commercelink.inventory.supplier.api.SupplierOrderTracking;
import pl.commercelink.inventory.supplier.api.SupplierParcel;
import pl.commercelink.inventory.supplier.api.SupplierQuote;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcmeTrackingSimulationTest {

    private static SupplierOrderResult twoLineOrder(String ref) {
        return new SupplierOrderResult("ACME-DS-" + ref, 100.0, "PLN", List.of(
                new SupplierQuote("5900000000001", "MFN-CLEAR-01", 5, 50.0, "PLN"),
                new SupplierQuote("5900000000002", "MFN-VALUE-01", 5, 50.0, "PLN")));
    }

    private static String key(String ref) {
        return "Acme|DS|" + ref;
    }

    @Test
    void reportsProcessingUntilConfiguredCheck() {
        // given
        AcmeTrackingSimulation simulation = new AcmeTrackingSimulation(Map.of("trackingShipAfterChecks", "3"));
        String ref = UUID.randomUUID().toString();

        // when
        SupplierOrderTracking first = simulation.track(key(ref), twoLineOrder(ref));
        SupplierOrderTracking second = simulation.track(key(ref), twoLineOrder(ref));
        SupplierOrderTracking third = simulation.track(key(ref), twoLineOrder(ref));

        // then
        assertEquals(SupplierOrderState.PROCESSING, first.state());
        assertEquals(SupplierOrderState.PROCESSING, second.state());
        assertEquals(SupplierOrderState.SHIPPED, third.state());
    }

    @Test
    void singleScenarioShipsOneParcelWithoutLines() {
        // given
        AcmeTrackingSimulation simulation = new AcmeTrackingSimulation(Map.of("trackingShipAfterChecks", "1"));
        String ref = UUID.randomUUID().toString();

        // when
        SupplierOrderTracking tracking = simulation.track(key(ref), twoLineOrder(ref));

        // then
        assertEquals(SupplierOrderState.SHIPPED, tracking.state());
        assertEquals(1, tracking.parcels().size());
        assertEquals("DPD", tracking.parcels().get(0).carrier());
        assertEquals("ACMETRK" + AcmeTrackingSimulation.shortClientRef(ref), tracking.parcels().get(0).trackingNo());
        assertEquals("https://tracking.acme.example/ACMETRK" + AcmeTrackingSimulation.shortClientRef(ref),
                tracking.parcels().get(0).trackingUrl());
        assertTrue(tracking.parcels().get(0).lines().isEmpty());
        assertTrue(tracking.parcels().get(0).shippedAt() != null);
    }

    @Test
    void partsScenarioShipsInTwoStages() {
        // given
        AcmeTrackingSimulation simulation = new AcmeTrackingSimulation(
                Map.of("trackingShipAfterChecks", "1", "trackingScenario", "parts"));
        String ref = UUID.randomUUID().toString();

        // when
        SupplierOrderTracking stage1 = simulation.track(key(ref), twoLineOrder(ref));
        SupplierOrderTracking stage2 = simulation.track(key(ref), twoLineOrder(ref));

        // then
        assertEquals(SupplierOrderState.PARTIALLY_SHIPPED, stage1.state());
        assertEquals(1, stage1.parcels().size());
        assertEquals("ACMETRK" + AcmeTrackingSimulation.shortClientRef(ref) + "P1",
                stage1.parcels().get(0).trackingNo());
        assertEquals("5900000000001", stage1.parcels().get(0).lines().get(0).ean());
        assertEquals(SupplierOrderState.SHIPPED, stage2.state());
        assertEquals(2, stage2.parcels().size());
        assertEquals(stage1.parcels().get(0), stage2.parcels().get(0));
        assertEquals("ACMETRK" + AcmeTrackingSimulation.shortClientRef(ref) + "P2",
                stage2.parcels().get(1).trackingNo());
        assertEquals("5900000000002", stage2.parcels().get(1).lines().get(0).ean());
    }

    @Test
    void partsScenarioWithSingleLineShipsAtOnce() {
        // given
        AcmeTrackingSimulation simulation = new AcmeTrackingSimulation(
                Map.of("trackingShipAfterChecks", "1", "trackingScenario", "parts"));
        String ref = UUID.randomUUID().toString();
        SupplierOrderResult oneLine = new SupplierOrderResult("ACME-DS-" + ref, 50.0, "PLN",
                List.of(new SupplierQuote("5900000000001", "MFN-CLEAR-01", 5, 50.0, "PLN")));

        // when
        SupplierOrderTracking tracking = simulation.track(key(ref), oneLine);

        // then
        assertEquals(SupplierOrderState.SHIPPED, tracking.state());
        assertEquals(1, tracking.parcels().size());
    }

    @Test
    void cancelScenarioReportsCancelledWithoutParcels() {
        // given
        AcmeTrackingSimulation simulation = new AcmeTrackingSimulation(
                Map.of("trackingShipAfterChecks", "1", "trackingScenario", "cancel"));
        String ref = UUID.randomUUID().toString();

        // when
        SupplierOrderTracking tracking = simulation.track(key(ref), twoLineOrder(ref));

        // then
        assertEquals(SupplierOrderState.CANCELLED, tracking.state());
        assertTrue(tracking.parcels().isEmpty());
    }

    @Test
    void nodataScenarioReportsShippedWithoutParcels() {
        // given
        AcmeTrackingSimulation simulation = new AcmeTrackingSimulation(
                Map.of("trackingShipAfterChecks", "1", "trackingScenario", "nodata"));
        String ref = UUID.randomUUID().toString();

        // when
        SupplierOrderTracking tracking = simulation.track(key(ref), twoLineOrder(ref));

        // then
        assertEquals(SupplierOrderState.SHIPPED, tracking.state());
        assertTrue(tracking.parcels().isEmpty());
    }

    @Test
    void parcelsAreStableAcrossChecks() {
        // given
        AcmeTrackingSimulation simulation = new AcmeTrackingSimulation(Map.of("trackingShipAfterChecks", "1"));
        String ref = UUID.randomUUID().toString();

        // when
        SupplierOrderTracking first = simulation.track(key(ref), twoLineOrder(ref));
        SupplierOrderTracking again = simulation.track(key(ref), twoLineOrder(ref));

        // then
        assertEquals(first.parcels(), again.parcels());
    }

    @Test
    void checksAreCountedPerOrder() {
        // given
        AcmeTrackingSimulation simulation = new AcmeTrackingSimulation(Map.of("trackingShipAfterChecks", "2"));
        String ref1 = UUID.randomUUID().toString();
        String ref2 = UUID.randomUUID().toString();

        // when
        simulation.track(key(ref1), twoLineOrder(ref1));
        SupplierOrderTracking shipped = simulation.track(key(ref1), twoLineOrder(ref1));
        SupplierOrderTracking other = simulation.track(key(ref2), twoLineOrder(ref2));

        // then
        assertEquals(SupplierOrderState.SHIPPED, shipped.state());
        assertEquals(SupplierOrderState.PROCESSING, other.state());
    }

    @Test
    void trackingNumberUsesSupplierPrefixAndShortClientRef() {
        // given
        AcmeTrackingSimulation simulation = new AcmeTrackingSimulation(Map.of("trackingShipAfterChecks", "1"));
        String ref = "c94af5c9-663f-4961-b768-6f34f2039f34";

        // when
        SupplierOrderTracking tracking = simulation.track("AcmeB|" + ref,
                new SupplierOrderResult("ACMEB-PO-" + ref, 1.0, "PLN", List.of()));

        // then
        assertEquals("ACMEBTRKc94af5c9663f", tracking.parcels().get(0).trackingNo());
    }

    @Test
    void trackingNumbersAreAlphanumericAndFitFurgonetkaLimitIncludingPartSuffix() {
        // given: Furgonetka accepts alphanumeric package numbers of 7 to 34 characters, no separators
        AcmeTrackingSimulation simulation = new AcmeTrackingSimulation(
                Map.of("trackingShipAfterChecks", "1", "trackingScenario", "parts"));
        String ref = UUID.randomUUID().toString();

        // when
        SupplierOrderTracking first = simulation.track(key(ref), twoLineOrder(ref));
        SupplierOrderTracking second = simulation.track(key(ref), twoLineOrder(ref));

        // then
        for (SupplierParcel parcel : second.parcels()) {
            assertTrue(parcel.trackingNo().matches("[A-Za-z0-9]{7,34}"),
                    "tracking number not accepted by Furgonetka: " + parcel.trackingNo());
        }
        assertEquals(2, second.parcels().size());
        assertEquals("ACMETRK" + AcmeTrackingSimulation.shortClientRef(ref) + "P1", first.parcels().get(0).trackingNo());
        assertEquals("ACMETRK" + AcmeTrackingSimulation.shortClientRef(ref) + "P2", second.parcels().get(1).trackingNo());
    }

    @Test
    void rejectsUnknownScenarioAndNonPositiveThreshold() {
        // when / then
        assertThrows(SupplierOrderException.class,
                () -> new AcmeTrackingSimulation(Map.of("trackingScenario", "explode")));
        assertThrows(SupplierOrderException.class,
                () -> new AcmeTrackingSimulation(Map.of("trackingShipAfterChecks", "0")));
        assertThrows(SupplierOrderException.class,
                () -> new AcmeTrackingSimulation(Map.of("trackingShipAfterChecks", "abc")));
    }

    @Test
    void defaultsToSingleScenarioAfterTwoChecks() {
        // given
        AcmeTrackingSimulation simulation = new AcmeTrackingSimulation(Map.of());
        String ref = UUID.randomUUID().toString();

        // when
        SupplierOrderTracking first = simulation.track(key(ref), twoLineOrder(ref));
        SupplierOrderTracking second = simulation.track(key(ref), twoLineOrder(ref));

        // then
        assertEquals(SupplierOrderState.PROCESSING, first.state());
        assertEquals(SupplierOrderState.SHIPPED, second.state());
        assertTrue(second.parcels().get(0).lines().isEmpty());
    }
}
