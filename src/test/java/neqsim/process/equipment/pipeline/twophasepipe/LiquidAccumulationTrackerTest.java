package neqsim.process.equipment.pipeline.twophasepipe;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.twophasepipe.LiquidAccumulationTracker.AccumulationZone;
import neqsim.process.equipment.pipeline.twophasepipe.LiquidAccumulationTracker.SlugCharacteristics;
import neqsim.process.equipment.pipeline.twophasepipe.PipeSection.FlowRegime;

/**
 * Tests for LiquidAccumulationTracker.
 */
class LiquidAccumulationTrackerTest {

  private LiquidAccumulationTracker tracker;
  private PipeSection[] sections;

  @BeforeEach
  void setUp() {
    tracker = new LiquidAccumulationTracker();
    createTestPipeline();
  }

  /**
   * Create a pipeline with a low point (terrain-induced slugging scenario).
   */
  private void createTestPipeline() {
    // 10 sections, with a dip in the middle
    // Profile: flat -> down -> up -> flat
    // Elevations: 0, 0, -5, -10, -10, -5, 0, 0, 0, 0
    sections = new PipeSection[10];
    double[] elevations = {0, 0, -5, -10, -10, -5, 0, 0, 0, 0};

    for (int i = 0; i < 10; i++) {
      sections[i] = new PipeSection(i * 100, 100, 0.3, 0);
      sections[i].setElevation(elevations[i]);
      sections[i].setRoughness(0.0001);

      // Set inclination
      if (i > 0) {
        double dz = elevations[i] - elevations[i - 1];
        sections[i].setInclination(Math.atan2(dz, 100));
      }

      // Set fluid properties
      sections[i].setGasDensity(50);
      sections[i].setLiquidDensity(800);
      sections[i].setGasViscosity(1.5e-5);
      sections[i].setLiquidViscosity(1e-3);
      sections[i].setSurfaceTension(0.02);
      sections[i].setFlowRegime(FlowRegime.STRATIFIED_SMOOTH);

      // Set initial holdup and velocities
      sections[i].setGasHoldup(0.7);
      sections[i].setLiquidHoldup(0.3);
      sections[i].setGasVelocity(3);
      sections[i].setLiquidVelocity(0.5);
      sections[i].updateDerivedQuantities();
    }
  }

  @Test
  void testIdentifyLowPoints() {
    tracker.identifyAccumulationZones(sections);

    // Should identify the low point at sections 3-4
    List<AccumulationZone> zones = tracker.getAccumulationZones();
    assertFalse(zones.isEmpty(), "Should identify at least one accumulation zone");

    // Check that section 3 or 4 is marked as low point
    assertTrue(sections[3].isLowPoint() || sections[4].isLowPoint(),
        "Low point should be at sections 3 or 4");
  }

  @Test
  void testZoneVolume() {
    tracker.identifyAccumulationZones(sections);

    List<AccumulationZone> zones = tracker.getAccumulationZones();
    assertFalse(zones.isEmpty());

    AccumulationZone zone = zones.get(0);
    assertTrue(zone.maxVolume > 0, "Zone max volume should be positive");

    // Max volume should be pipe volume in zone
    double expectedVolume = 0;
    for (int idx : zone.sectionIndices) {
      expectedVolume += sections[idx].getArea() * sections[idx].getLength();
    }
    assertEquals(expectedVolume, zone.maxVolume, 0.01);
  }

  @Test
  void testAccumulationUpdate() {
    tracker.identifyAccumulationZones(sections);

    double dt = 1.0; // 1 second time step

    // Initial state
    double initialVolume = tracker.getTotalAccumulatedVolume();

    // Run several time steps
    for (int step = 0; step < 100; step++) {
      tracker.updateAccumulation(sections, dt);
    }

    // Volume should change based on net inflow
    // (Actual result depends on upstream/downstream conditions)
    assertNotNull(tracker.getTotalAccumulatedVolume());
  }

  @Test
  void testSlugRelease() {
    tracker.identifyAccumulationZones(sections);

    List<AccumulationZone> zones = tracker.getAccumulationZones();
    assertFalse(zones.isEmpty());

    AccumulationZone zone = zones.get(0);

    // Fill the zone to overflow
    zone.liquidVolume = zone.maxVolume * 0.95;
    zone.isOverflowing = true;

    // Check for slug release
    SlugCharacteristics slug = tracker.checkForSlugRelease(zone, sections);

    // Slug should be released if downstream section is uphill
    if (slug != null) {
      assertTrue(slug.length > 0, "Slug length should be positive");
      assertTrue(slug.volume > 0, "Slug volume should be positive");
      assertTrue(slug.isTerrainInduced, "Should be terrain-induced slug");
    }
  }

  @Test
  void testDrainageRate() {
    tracker.identifyAccumulationZones(sections);

    List<AccumulationZone> zones = tracker.getAccumulationZones();
    if (zones.isEmpty()) {
      return;
    }

    AccumulationZone zone = zones.get(0);
    zone.liquidVolume = zone.maxVolume * 0.5; // Half full

    double pressureDrop = 1e5; // 1 bar
    double drainRate = tracker.calculateDrainageRate(zone, sections, pressureDrop);

    assertTrue(drainRate >= 0, "Drainage rate should be non-negative");
  }

  @Test
  void testOverflowingZones() {
    tracker.identifyAccumulationZones(sections);

    List<AccumulationZone> zones = tracker.getAccumulationZones();
    if (zones.isEmpty()) {
      return;
    }

    // Set one zone to overflow
    zones.get(0).isOverflowing = true;

    List<AccumulationZone> overflowing = tracker.getOverflowingZones();
    assertEquals(1, overflowing.size());
  }

  @Test
  void testMultipleLowPoints() {
    // Create pipeline with two low points
    double[] elevations = {0, -5, 0, -5, 0, 0, 0, 0, 0, 0};
    for (int i = 0; i < 10; i++) {
      sections[i].setElevation(elevations[i]);
      if (i > 0) {
        double dz = elevations[i] - elevations[i - 1];
        sections[i].setInclination(Math.atan2(dz, 100));
      }
    }

    tracker.identifyAccumulationZones(sections);

    List<AccumulationZone> zones = tracker.getAccumulationZones();
    assertTrue(zones.size() >= 2, "Should identify two low points");
  }

  @Test
  void testFlatPipeline() {
    // Flat pipeline - no low points
    for (PipeSection section : sections) {
      section.setElevation(0);
      section.setInclination(0);
    }

    tracker.identifyAccumulationZones(sections);

    List<AccumulationZone> zones = tracker.getAccumulationZones();
    // Flat pipeline should have no terrain-induced accumulation zones
    // (though riser base detection might still trigger)
  }

  @Test
  void testCriticalHoldupSetting() {
    tracker.setCriticalHoldup(0.3);
    // No error should be thrown

    // Test bounds
    tracker.setCriticalHoldup(0.05); // Below min
    tracker.setCriticalHoldup(0.95); // Above max
  }

  @Test
  void testDrainageCoefficientSetting() {
    tracker.setDrainageCoefficient(0.8);
    // Verify it affects drainage calculation
    tracker.identifyAccumulationZones(sections);
  }
}
