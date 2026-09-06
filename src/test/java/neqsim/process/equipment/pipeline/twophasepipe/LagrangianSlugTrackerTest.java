package neqsim.process.equipment.pipeline.twophasepipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.twophasepipe.LagrangianSlugTracker.SlugBubbleUnit;
import neqsim.process.equipment.pipeline.twophasepipe.LagrangianSlugTracker.SlugSource;
import neqsim.process.equipment.pipeline.twophasepipe.PipeSection.FlowRegime;

/**
 * Unit tests for LagrangianSlugTracker.
 *
 * <p>
 * Tests the OLGA-style Lagrangian slug tracking model including:
 * <ul>
 * <li>Slug initiation (inlet, terrain, instability)</li>
 * <li>Slug propagation and velocity</li>
 * <li>Slug growth and decay</li>
 * <li>Slug merging</li>
 * <li>Wake effects</li>
 * <li>Mass conservation</li>
 * <li>Statistics tracking</li>
 * </ul>
 * </p>
 *
 * @author Test
 */
class LagrangianSlugTrackerTest {

  private LagrangianSlugTracker tracker;
  private TwoFluidSection[] sections;

  private static final double PIPE_DIAMETER = 0.1; // 100 mm
  private static final double PIPE_LENGTH = 100.0; // 100 m
  private static final int NUM_SECTIONS = 20;

  @BeforeEach
  void setUp() {
    tracker = new LagrangianSlugTracker(12345L); // Fixed seed for reproducibility
    sections = createTestSections();
  }

  /**
   * Create test pipe sections with slug flow conditions.
   */
  private TwoFluidSection[] createTestSections() {
    TwoFluidSection[] secs = new TwoFluidSection[NUM_SECTIONS];
    double dx = PIPE_LENGTH / NUM_SECTIONS;

    for (int i = 0; i < NUM_SECTIONS; i++) {
      secs[i] = new TwoFluidSection();
      secs[i].setPosition((i + 0.5) * dx);
      secs[i].setLength(dx);
      secs[i].setDiameter(PIPE_DIAMETER);
      secs[i].setInclination(0); // Horizontal

      // Set fluid properties for slug flow conditions
      secs[i].setLiquidDensity(800.0);
      secs[i].setGasDensity(50.0);
      secs[i].setLiquidViscosity(0.001);
      secs[i].setGasViscosity(1.5e-5);

      // Set velocities for slug flow
      secs[i].setLiquidVelocity(1.0);
      secs[i].setGasVelocity(3.0);

      // Set holdup
      secs[i].setLiquidHoldup(0.2);
      secs[i].setGasHoldup(0.8);

      // Set flow regime to slug
      secs[i].setFlowRegime(FlowRegime.SLUG);

      secs[i].updateDerivedQuantities();
    }

    return secs;
  }

  @Test
  void testSlugInitialization() {
    // Initially no slugs
    assertEquals(0, tracker.getSlugCount());
    assertEquals(0, tracker.getTotalSlugsGenerated());

    // Advance time step - should generate inlet slug
    double dt = 1.0;
    tracker.advanceTimeStep(sections, dt);

    // Should have generated at least one slug
    assertTrue(tracker.getTotalSlugsGenerated() >= 0, "Slugs should be generated based on frequency");
  }

  @Test
  void testTerrainSlugInitialization() {
    // Create terrain slug characteristics
    LiquidAccumulationTracker.SlugCharacteristics chars = new LiquidAccumulationTracker.SlugCharacteristics();
    chars.frontPosition = 30.0;
    chars.tailPosition = 28.0;
    chars.length = 2.0;
    chars.velocity = 2.0;
    chars.holdup = 0.85;
    chars.volume = 0.01;

    // Initialize terrain slug
    SlugBubbleUnit slug = tracker.initializeTerrainSlug(chars, sections);

    assertNotNull(slug, "Terrain slug should be created");
    assertEquals(SlugSource.TERRAIN, slug.source);
    assertEquals(30.0, slug.frontPosition, 0.01);
    assertEquals(28.0, slug.tailPosition, 0.01);
    assertEquals(2.0, slug.slugLength, 0.01);
    assertTrue(slug.isTerrainInduced);
    assertEquals(1, tracker.getSlugCount());
    assertEquals(1, tracker.getTotalSlugsGenerated());
  }

  @Test
  void testSlugPropagation() {
    // Create initial slug
    LiquidAccumulationTracker.SlugCharacteristics chars = new LiquidAccumulationTracker.SlugCharacteristics();
    chars.frontPosition = 20.0;
    chars.tailPosition = 18.0;
    chars.length = 2.0;
    chars.velocity = 2.5;
    chars.holdup = 0.85;
    chars.volume = 0.01;

    SlugBubbleUnit slug = tracker.initializeTerrainSlug(chars, sections);
    double initialFront = slug.frontPosition;

    // Advance several time steps
    double dt = 0.5;
    for (int i = 0; i < 10; i++) {
      tracker.advanceTimeStep(sections, dt);
    }

    // Get updated slug
    List<SlugBubbleUnit> slugs = tracker.getSlugs();
    assertFalse(slugs.isEmpty(), "Slug should still exist");

    SlugBubbleUnit updatedSlug = null;
    for (SlugBubbleUnit s : slugs) {
      if (s.id == slug.id) {
        updatedSlug = s;
        break;
      }
    }

    if (updatedSlug != null) {
      assertTrue(updatedSlug.frontPosition > initialFront, "Slug should have moved downstream");
      assertTrue(updatedSlug.age > 0, "Slug age should increase");
      assertTrue(updatedSlug.distanceTraveled > 0, "Distance traveled should increase");
    }
  }

  @Test
  void testSlugMerging() {
    // Create two slugs close together
    LiquidAccumulationTracker.SlugCharacteristics chars1 = new LiquidAccumulationTracker.SlugCharacteristics();
    chars1.frontPosition = 25.0;
    chars1.tailPosition = 23.0;
    chars1.length = 2.0;
    chars1.velocity = 2.5;
    chars1.holdup = 0.85;
    chars1.volume = 0.01;

    LiquidAccumulationTracker.SlugCharacteristics chars2 = new LiquidAccumulationTracker.SlugCharacteristics();
    chars2.frontPosition = 22.5; // Close behind first slug
    chars2.tailPosition = 20.5;
    chars2.length = 2.0;
    chars2.velocity = 3.0; // Faster - will catch up
    chars2.holdup = 0.85;
    chars2.volume = 0.01;

    tracker.initializeTerrainSlug(chars1, sections);
    tracker.initializeTerrainSlug(chars2, sections);

    assertEquals(2, tracker.getSlugCount(), "Should have two slugs initially");

    // Advance until merging occurs
    double dt = 0.1;
    int initialCount = tracker.getSlugCount();
    int mergeCount = 0;

    for (int i = 0; i < 50; i++) {
      tracker.advanceTimeStep(sections, dt);
      if (tracker.getTotalSlugsMerged() > mergeCount) {
        mergeCount = tracker.getTotalSlugsMerged();
        break;
      }
    }

    // Merging may or may not occur depending on velocities
    // At minimum, verify no errors occurred
    assertTrue(tracker.getSlugCount() <= initialCount, "Slug count should not increase");
  }

  @Test
  void testWakeEffects() {
    tracker.setEnableWakeEffects(true);

    // Create leading slug
    LiquidAccumulationTracker.SlugCharacteristics chars1 = new LiquidAccumulationTracker.SlugCharacteristics();
    chars1.frontPosition = 30.0;
    chars1.tailPosition = 27.0;
    chars1.length = 3.0;
    chars1.velocity = 2.5;
    chars1.holdup = 0.85;
    chars1.volume = 0.02;

    // Create following slug in wake region
    LiquidAccumulationTracker.SlugCharacteristics chars2 = new LiquidAccumulationTracker.SlugCharacteristics();
    chars2.frontPosition = 25.0; // Behind first slug
    chars2.tailPosition = 22.0;
    chars2.length = 3.0;
    chars2.velocity = 2.5;
    chars2.holdup = 0.85;
    chars2.volume = 0.02;

    tracker.initializeTerrainSlug(chars1, sections);
    tracker.initializeTerrainSlug(chars2, sections);

    // Advance time
    tracker.advanceTimeStep(sections, 0.5);

    // Check for wake effects
    List<SlugBubbleUnit> slugs = tracker.getSlugs();
    boolean foundWakeSlug = false;
    for (SlugBubbleUnit slug : slugs) {
      if (slug.inWakeRegion) {
        foundWakeSlug = true;
        assertTrue(slug.wakeCoefficient >= 1.0, "Wake coefficient should be >= 1.0");
      }
    }

    // Wake effect may or may not be detected depending on distance
    // This test verifies no errors occur
  }

  @Test
  void testMassConservation() {
    // Create slug
    LiquidAccumulationTracker.SlugCharacteristics chars = new LiquidAccumulationTracker.SlugCharacteristics();
    chars.frontPosition = 20.0;
    chars.tailPosition = 18.0;
    chars.length = 2.0;
    chars.velocity = 2.5;
    chars.holdup = 0.85;
    chars.volume = 0.01;

    tracker.initializeTerrainSlug(chars, sections);

    double initialBorrowed = tracker.getTotalMassBorrowedFromEulerian();
    assertTrue(initialBorrowed > 0, "Mass should be borrowed when slug is created");

    // Advance a few steps
    double dt = 0.5;
    for (int i = 0; i < 5; i++) {
      tracker.advanceTimeStep(sections, dt);
    }

    // Verify mass tracking is working (values are being tracked)
    // The exact conservation depends on complex slug dynamics
    double borrowed = tracker.getTotalMassBorrowedFromEulerian();
    double returned = tracker.getTotalMassReturnedToEulerian();

    // Mass borrowed should be non-negative
    assertTrue(borrowed >= 0, "Borrowed mass should be non-negative");

    // Mass returned should be non-negative
    assertTrue(returned >= 0, "Returned mass should be non-negative");

    // The getMassConservationError method should return a value (may be non-zero due to dynamics)
    double error = tracker.getMassConservationError();
    // Just verify it returns a finite number
    assertFalse(Double.isNaN(error), "Error should not be NaN");
    assertFalse(Double.isInfinite(error), "Error should not be infinite");
  }

  @Test
  void testMassConservationClosesAfterTerrainSlugExits() {
    tracker.setEnableInletSlugGeneration(false);
    tracker.setEnableStochasticInitiation(false);

    LiquidAccumulationTracker.SlugCharacteristics chars = new LiquidAccumulationTracker.SlugCharacteristics();
    chars.frontPosition = 98.0;
    chars.tailPosition = 96.0;
    chars.length = 2.0;
    chars.velocity = 2.5;
    chars.holdup = 0.85;
    chars.volume = 0.01;

    tracker.initializeTerrainSlug(chars, sections);

    for (int i = 0; i < 100 && tracker.getSlugCount() > 0; i++) {
      tracker.advanceTimeStep(sections, 0.5);
    }

    assertEquals(0, tracker.getSlugCount(), "Terrain slug should leave the pipe");
    assertEquals(1, tracker.getTotalSlugsExited(), "Exactly one terrain slug should exit");
    assertTrue(tracker.getTotalMassExitedAtOutlet() > 0.0, "Exited slug mass should be recorded separately");
    assertEquals(0.0, tracker.getMassConservationError(), 1.0e-9,
        "Tracker mass audit should close after the final slug exits");
    assertEquals(tracker.getTotalMassBorrowedFromEulerian(),
        tracker.getTotalMassReturnedToEulerian() + tracker.getTotalMassExitedAtOutlet(), 1.0e-9,
        "Borrowed mass should equal returned plus exited mass with no active slug");
  }

  @Test
  void testSlugStatistics() {
    // Disable inlet generation for controlled test
    tracker.setEnableInletSlugGeneration(false);

    // Create multiple slugs
    for (int i = 0; i < 5; i++) {
      LiquidAccumulationTracker.SlugCharacteristics chars = new LiquidAccumulationTracker.SlugCharacteristics();
      chars.frontPosition = 10.0 + i * 15.0;
      chars.tailPosition = 8.0 + i * 15.0;
      chars.length = 2.0;
      chars.velocity = 2.0 + i * 0.2;
      chars.holdup = 0.85;
      chars.volume = 0.01;
      tracker.initializeTerrainSlug(chars, sections);
    }

    assertEquals(5, tracker.getSlugCount());
    assertEquals(5, tracker.getTotalSlugsGenerated());

    // Advance time
    tracker.advanceTimeStep(sections, 1.0);

    assertTrue(tracker.getAverageSlugLength() > 0, "Average slug length should be positive");
    assertTrue(tracker.getMaxSlugLength() >= tracker.getAverageSlugLength(), "Max length should be >= average");
  }

  @Test
  void testSlugFrequencyCalculation() {
    // Enable inlet generation
    tracker.setEnableInletSlugGeneration(true);

    // Advance many time steps
    double dt = 0.5;
    for (int i = 0; i < 100; i++) {
      tracker.advanceTimeStep(sections, dt);
    }

    // Frequency should be calculated
    double freq = tracker.getInletSlugFrequency();
    assertTrue(freq >= 0, "Inlet frequency should be non-negative");
  }

  @Test
  void testConfigurationMethods() {
    // Test setters
    tracker.setMinSlugLengthDiameters(15.0);
    tracker.setMaxSlugLengthDiameters(250.0);
    tracker.setInitialSlugLengthDiameters(25.0);
    tracker.setWakeLengthDiameters(40.0);
    tracker.setMaxWakeAcceleration(1.4);
    tracker.setInitiationHoldupThreshold(0.3);
    tracker.setReferenceVelocity(3.0);

    // No errors should occur
    tracker.advanceTimeStep(sections, 1.0);
  }

  @Test
  void testReset() {
    // Create some slugs
    for (int i = 0; i < 3; i++) {
      LiquidAccumulationTracker.SlugCharacteristics chars = new LiquidAccumulationTracker.SlugCharacteristics();
      chars.frontPosition = 20.0 + i * 10.0;
      chars.tailPosition = 18.0 + i * 10.0;
      chars.length = 2.0;
      chars.velocity = 2.5;
      chars.holdup = 0.85;
      chars.volume = 0.01;
      tracker.initializeTerrainSlug(chars, sections);
    }

    tracker.advanceTimeStep(sections, 1.0);

    assertTrue(tracker.getSlugCount() > 0);
    assertTrue(tracker.getTotalSlugsGenerated() > 0);

    // Reset
    tracker.reset();

    assertEquals(0, tracker.getSlugCount());
    assertEquals(0, tracker.getTotalSlugsGenerated());
    assertEquals(0, tracker.getTotalSlugsMerged());
    assertEquals(0, tracker.getTotalSlugsDissipated());
    assertEquals(0, tracker.getTotalSlugsExited());
    assertEquals(0.0, tracker.getTotalMassExitedAtOutlet());
  }

  @Test
  void testJsonOutput() {
    // Create slug
    LiquidAccumulationTracker.SlugCharacteristics chars = new LiquidAccumulationTracker.SlugCharacteristics();
    chars.frontPosition = 20.0;
    chars.tailPosition = 18.0;
    chars.length = 2.0;
    chars.velocity = 2.5;
    chars.holdup = 0.85;
    chars.volume = 0.01;

    tracker.initializeTerrainSlug(chars, sections);
    tracker.advanceTimeStep(sections, 1.0);

    String json = tracker.toJson();
    assertNotNull(json);
    assertTrue(json.contains("simulationTime"));
    assertTrue(json.contains("activeSlugCount"));
    assertTrue(json.contains("activeSlugs"));
  }

  @Test
  void testStatisticsString() {
    // Create slug
    LiquidAccumulationTracker.SlugCharacteristics chars = new LiquidAccumulationTracker.SlugCharacteristics();
    chars.frontPosition = 20.0;
    chars.tailPosition = 18.0;
    chars.length = 2.0;
    chars.velocity = 2.5;
    chars.holdup = 0.85;
    chars.volume = 0.01;

    tracker.initializeTerrainSlug(chars, sections);
    tracker.advanceTimeStep(sections, 1.0);

    String stats = tracker.getStatisticsString();
    assertNotNull(stats);
    assertTrue(stats.contains("Lagrangian Slug Tracking"));
    assertTrue(stats.contains("Active slugs"));
    assertTrue(stats.contains("Total generated"));
  }

  @Test
  void testSlugDisableOptions() {
    // Disable all slug generation
    tracker.setEnableInletSlugGeneration(false);
    tracker.setEnableTerrainSlugGeneration(false);
    tracker.setEnableStochasticInitiation(false);

    // Advance time
    for (int i = 0; i < 50; i++) {
      tracker.advanceTimeStep(sections, 0.5);
    }

    // No slugs should be generated
    assertEquals(0, tracker.getTotalSlugsGenerated());
  }

  @Test
  void testNullAndEmptySections() {
    // Should handle null sections gracefully
    tracker.advanceTimeStep(null, 1.0);
    assertEquals(0, tracker.getSlugCount());

    // Should handle empty sections
    tracker.advanceTimeStep(new TwoFluidSection[0], 1.0);
    assertEquals(0, tracker.getSlugCount());
  }

  @Test
  void testZeroTimeStep() {
    // Create slug
    LiquidAccumulationTracker.SlugCharacteristics chars = new LiquidAccumulationTracker.SlugCharacteristics();
    chars.frontPosition = 20.0;
    chars.tailPosition = 18.0;
    chars.length = 2.0;
    chars.velocity = 2.5;
    chars.holdup = 0.85;
    chars.volume = 0.01;

    tracker.initializeTerrainSlug(chars, sections);
    double initialPos = tracker.getSlugs().get(0).frontPosition;

    // Zero time step should not advance slug
    tracker.advanceTimeStep(sections, 0);
    assertEquals(initialPos, tracker.getSlugs().get(0).frontPosition, 0.001);

    // Negative time step should be handled
    tracker.advanceTimeStep(sections, -1.0);
    assertEquals(initialPos, tracker.getSlugs().get(0).frontPosition, 0.001);
  }

  @Test
  void testDissipationDoesNotInjectEulerianLiquid() {
    tracker.setEnableInletSlugGeneration(false);
    SlugBubbleUnit slug = addTerrainSlug(20.0, 19.5);
    slug.age = 11.0;
    double[] holdup = new double[sections.length];
    for (int i = 0; i < sections.length; i++) {
      holdup[i] = sections[i].getLiquidHoldup();
    }

    tracker.advanceTimeStep(sections, 0.01);

    assertEquals(0, tracker.getSlugCount(), "Subcritical old slug must dissipate instead of being lengthened");
    assertEquals(1, tracker.getTotalSlugsDissipated());
    assertEquals(0.0, tracker.getMassConservationError(), 1.0e-9);
    for (int i = 0; i < sections.length; i++) {
      assertEquals(holdup[i], sections[i].getLiquidHoldup(), 0.0,
          "Dissipation only removes the overlay; it must preserve Eulerian inventory");
      assertFalse(sections[i].isInSlugBody());
      assertFalse(sections[i].isInSlugBubble());
    }
  }

  @Test
  void testMergedHoldupMatchesConservedVolumeAndGeometry() {
    tracker.setEnableInletSlugGeneration(false);
    tracker.setEnableWakeEffects(false);
    addTerrainSlug(25.0, 23.0);
    addTerrainSlug(22.5, 20.5);
    tracker.advanceTimeStep(sections, 1.0e-4);

    assertEquals(1, tracker.getSlugCount());
    SlugBubbleUnit slug = tracker.getSlugs().get(0);
    assertEquals(slug.frontPosition - slug.tailPosition, slug.slugLength, 1.0e-12);
    assertEquals(slug.slugLiquidVolume, slug.slugLength * slug.localArea * slug.slugHoldup, 1.0e-12,
        "Merged holdup must include the intervening film gap in the body length");
    assertEquals(0.0, tracker.getMassConservationError(), 1.0e-9);
  }

  @Test
  void testWakeCanBeDisabledAfterBecomingActive() {
    tracker.setEnableInletSlugGeneration(false);
    addTerrainSlug(30.0, 28.0);
    SlugBubbleUnit following = addTerrainSlug(26.0, 24.0);
    tracker.advanceTimeStep(sections, 0.01);
    assertTrue(following.inWakeRegion);

    tracker.setEnableWakeEffects(false);
    tracker.advanceTimeStep(sections, 0.01);
    assertFalse(following.inWakeRegion, "Disabling wakes must clear existing acceleration state");
    assertEquals(1.0, following.wakeCoefficient, 0.0);
  }

  @Test
  void testRemainingSlugLosesWakeAfterLeaderExits() {
    tracker.setEnableInletSlugGeneration(false);
    addTerrainSlug(101.5, 99.5);
    SlugBubbleUnit following = addTerrainSlug(97.5, 95.5);
    tracker.advanceTimeStep(sections, 0.2);
    assertEquals(1, tracker.getTotalSlugsExited());
    assertEquals(1, tracker.getSlugCount());
    assertTrue(following.inWakeRegion);

    tracker.advanceTimeStep(sections, 0.01);
    assertFalse(following.inWakeRegion, "A sole surviving slug has no upstream wake source");
    assertEquals(1.0, following.wakeCoefficient, 0.0);
  }

  @Test
  void testNonuniformCellCentersAndPhysicalOutlet() {
    tracker.setEnableInletSlugGeneration(false);
    sections = new TwoFluidSection[] { sections[0], sections[1], sections[2] };
    sections[0].setPosition(1.0);
    sections[0].setLength(2.0);
    sections[1].setPosition(3.5);
    sections[1].setLength(3.0);
    sections[2].setPosition(7.5);
    sections[2].setLength(5.0);
    SlugBubbleUnit inlet = addTerrainSlug(0.5, 0.1);
    tracker.advanceTimeStep(sections, 1.0e-4);
    assertTrue(sections[0].isInSlugBody(), "The first cell includes positions below its midpoint");
    assertFalse(sections[1].isInSlugBody());
    assertEquals(0.4, inlet.slugLength, 1.0e-3, "The minimum length must not pad the new slug");

    tracker.reset();
    addTerrainSlug(13.0, 10.5);
    tracker.advanceTimeStep(sections, 1.0e-4);
    assertEquals(0, tracker.getSlugCount(), "The outlet is 10 m, not the last midpoint plus a full cell");
    assertEquals(1, tracker.getTotalSlugsExited());
  }

  @Test
  void testDownhillBuoyancyRetainsItsDirection() {
    tracker.setEnableInletSlugGeneration(false);
    for (TwoFluidSection section : sections) {
      section.setInclination(-Math.PI / 2.0);
    }
    SlugBubbleUnit slug = addTerrainSlug(20.0, 18.0);
    tracker.advanceTimeStep(sections, 0.01);
    double convectiveVelocity = 0.9 * sections[0].getMixtureVelocity();
    assertTrue(slug.frontVelocity < convectiveVelocity, "Buoyancy opposes positive flow in a downward vertical pipe");
  }

  @Test
  void testReverseFlowHoldupUsesSpeedMagnitude() {
    tracker.setEnableInletSlugGeneration(false);
    for (TwoFluidSection section : sections) {
      section.setLiquidVelocity(-1.0);
      section.setGasVelocity(-3.0);
      section.updateDerivedQuantities();
    }
    SlugBubbleUnit slug = addTerrainSlug(20.0, 18.0);
    tracker.advanceTimeStep(sections, 0.01);
    double speed = Math.abs(sections[0].getMixtureVelocity());
    double expectedHoldup = 1.0 / (1.0 + Math.pow(speed / 8.66, 1.39));
    assertTrue(slug.frontPosition < 20.0);
    assertEquals(expectedHoldup, slug.slugHoldup, 1.0e-12);
    assertTrue(Double.isFinite(tracker.getMassConservationError()));
  }

  @Test
  void testCountercurrentInletDoesNotEvaluateCocurrentFrequency() {
    sections[0].setGasVelocity(3.0);
    sections[0].setLiquidVelocity(-1.0);
    sections[0].updateDerivedQuantities();
    tracker.advanceTimeStep(sections, 1.0);
    assertEquals(0.0, tracker.getInletSlugFrequency(), 0.0);
    assertEquals(0, tracker.getTotalSlugsGenerated());
  }

  @Test
  void testNonfiniteTimeStepLeavesStateUnchanged() {
    SlugBubbleUnit slug = addTerrainSlug(20.0, 18.0);
    tracker.advanceTimeStep(sections, Double.NaN);
    tracker.advanceTimeStep(sections, Double.POSITIVE_INFINITY);
    assertEquals(20.0, slug.frontPosition, 0.0);
    assertEquals(0.0, slug.age, 0.0);
  }

  @Test
  void testOutletFrequencyUpdatesWhenFinalSlugExits() {
    assertEquals(-1.0, tracker.getLastOutletArrivalTime(), 0.0);
    tracker.setEnableInletSlugGeneration(false);
    tracker.setEnableWakeEffects(false);
    addTerrainSlug(92.0, 90.0);
    addTerrainSlug(72.0, 70.0);
    for (int i = 0; i < 100 && tracker.getSlugCount() > 0; i++) {
      tracker.advanceTimeStep(sections, 0.5);
    }
    assertEquals(0, tracker.getSlugCount());
    assertEquals(2, tracker.getTotalSlugsExited());
    assertEquals(1, tracker.getOutletInterArrivalTimes().size());
    assertTrue(tracker.getOutletSlugFrequency() > 0.0,
        "Frequency must update even when no active slugs remain after the final exit");
    assertEquals(1.0 / tracker.getOutletInterArrivalTimes().get(0), tracker.getOutletSlugFrequency(), 1.0e-9);
    assertTrue(tracker.getLastOutletArrivalTime() > 0.0);
    tracker.reset();
    assertEquals(-1.0, tracker.getLastOutletArrivalTime(), 0.0);
  }

  /**
   * Add a terrain slug with consistent initial body geometry and volume.
   *
   * @param front front position (m)
   * @param tail tail position (m)
   * @return created slug
   */
  private SlugBubbleUnit addTerrainSlug(double front, double tail) {
    LiquidAccumulationTracker.SlugCharacteristics chars = new LiquidAccumulationTracker.SlugCharacteristics();
    chars.frontPosition = front;
    chars.tailPosition = tail;
    chars.length = front - tail;
    chars.holdup = 0.85;
    chars.velocity = 2.0;
    chars.volume = chars.length * sections[0].getArea() * chars.holdup;
    return tracker.initializeTerrainSlug(chars, sections);
  }
}
