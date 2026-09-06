package neqsim.process.equipment.pipeline.twophasepipe;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.twophasepipe.LagrangianSlugTracker.SlugBubbleUnit;
import neqsim.process.equipment.pipeline.twophasepipe.LiquidAccumulationTracker.SlugCharacteristics;

/**
 * Liquid-jump, inventory ownership, and pure trial-speed contracts for conservative slug tracking.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class LagrangianSlugConservativeCouplingTest {
  @Test
  void testFrontAndTailContinuityDetermineLengthGrowth() {
    TwoFluidSection[] sections = sections();
    LagrangianSlugTracker tracker = tracker();
    SlugBubbleUnit slug = initialize(tracker, sections, 20.0, 30.0);
    double bodyMass = sections[2].getOilMassPerLength() + sections[2].getWaterMassPerLength();
    double[] ahead = sections[3].getStateVector();
    double[] behind = sections[1].getStateVector();
    double maximumSpeed = tracker.getMaximumInterfaceSpeed(sections);

    tracker.advanceTimeStep(sections, 0.01);

    double tailBodyFlux = slug.tailVelocity * (bodyMass - behind[1] - behind[2]) + behind[4] + behind[5];
    double expectedFront = (tailBodyFlux - ahead[4] - ahead[5]) / (bodyMass - ahead[1] - ahead[2]);
    assertEquals(expectedFront, slug.frontVelocity, 1.0e-10);
    assertEquals(bodyMass * (slug.frontVelocity - slug.tailVelocity), slug.pickupRate - slug.sheddingRate, 1.0e-10);
    assertEquals(10.0 + 0.01 * (slug.frontVelocity - slug.tailVelocity), slug.slugLength, 1.0e-12);
    assertEquals(Math.max(Math.abs(slug.frontVelocity), Math.abs(slug.tailVelocity)), maximumSpeed, 1.0e-10);
  }

  @Test
  void testTrackerDoesNotBecomeASecondInventoryOwner() {
    TwoFluidSection[] sections = sections();
    double[][] original = new double[sections.length][];
    double[] totalMass = new double[3];
    for (int i = 0; i < sections.length; i++) {
      original[i] = sections[i].getStateVector();
      for (int phase = 0; phase < 3; phase++) {
        totalMass[phase] += original[i][phase] * sections[i].getLength();
      }
    }
    LagrangianSlugTracker tracker = tracker();
    SlugBubbleUnit slug = initialize(tracker, sections, 20.0, 30.0);
    tracker.advanceTimeStep(sections, 0.01);
    for (int i = 0; i < sections.length; i++) {
      assertArrayEquals(original[i], sections[i].getStateVector(), 0.0);
    }
    for (int phase = 0; phase < 3; phase++) {
      assertTrue(slug.phaseMassKg[phase] >= 0.0 && slug.phaseMassKg[phase] <= totalMass[phase]);
      assertTrue(Double.isFinite(slug.phaseMomentumKgMPerSecond[phase]));
    }
    assertTrue(slug.phaseMassKg[1] > 0.0 && slug.phaseMassKg[2] > 0.0);
    assertTrue(Double.isFinite(slug.totalEnergyJ));
  }

  @Test
  void testInterfaceSpeedQueriesDoNotAdvanceTrialState() {
    TwoFluidSection[] sections = sections();
    LagrangianSlugTracker tracker = tracker();
    SlugBubbleUnit slug = initialize(tracker, sections, 20.0, 30.0);
    double expected = tracker.getMaximumInterfaceSpeed(sections);
    for (int i = 0; i < 5; i++) {
      assertEquals(expected, tracker.getMaximumInterfaceSpeed(sections), 0.0);
    }
    assertEquals(30.0, slug.frontPosition, 0.0);
    assertEquals(20.0, slug.tailPosition, 0.0);
    assertEquals(0.0, slug.age, 0.0);
    assertEquals(1, tracker.getTotalSlugsGenerated());
  }

  @Test
  void testReferenceVelocityDoesNotDriveStoppedEqualDensityFlow() {
    TwoFluidSection[] sections = sections();
    for (TwoFluidSection section : sections) {
      section.setGasDensity(800.0);
      section.setOilDensity(800.0);
      section.setWaterDensity(800.0);
      section.setLiquidDensity(800.0);
      section.setGasVelocity(0.0);
      section.setLiquidVelocity(0.0);
      section.updateDerivedQuantities();
      section.updateConservativeVariables();
    }
    LagrangianSlugTracker tracker = tracker();
    tracker.setReferenceVelocity(10.0);
    SlugBubbleUnit slug = initialize(tracker, sections, 20.0, 30.0);
    tracker.advanceTimeStep(sections, 0.1);
    assertEquals(0.0, slug.frontVelocity, 0.0);
    assertEquals(0.0, slug.tailVelocity, 0.0);
    assertEquals(30.0, slug.frontPosition, 0.0);
    assertEquals(20.0, slug.tailPosition, 0.0);
  }

  @Test
  void testClosedOutletDoesNotDeliverOrRemoveMassThroughAnExit() {
    TwoFluidSection[] sections = sections();
    LagrangianSlugTracker tracker = tracker();
    tracker.setClosedBoundaries(true, true);
    SlugBubbleUnit slug = initialize(tracker, sections, 69.0, 79.0);
    double[] outletState = sections[7].getStateVector();
    tracker.advanceTimeStep(sections, 10.0);
    assertTrue(slug.frontPosition <= 80.0);
    assertTrue(slug.tailPosition <= 80.0);
    assertEquals(0, tracker.getTotalSlugsExited());
    assertEquals(0.0, tracker.getTotalMassExitedAtOutlet(), 0.0);
    assertArrayEquals(outletState, sections[7].getStateVector(), 0.0);
  }

  @Test
  void testClosedInletContainsReverseMovingMarker() {
    TwoFluidSection[] sections = sections();
    for (TwoFluidSection section : sections) {
      section.setGasVelocity(-3.0);
      section.setLiquidVelocity(-1.0);
      section.updateDerivedQuantities();
      section.updateConservativeVariables();
    }
    LagrangianSlugTracker tracker = tracker();
    tracker.setClosedBoundaries(true, false);
    SlugBubbleUnit slug = initialize(tracker, sections, 0.2, 2.0);
    double[] inletState = sections[0].getStateVector();
    tracker.advanceTimeStep(sections, 2.0);
    assertTrue(slug.frontPosition >= 0.0);
    assertTrue(slug.tailPosition >= 0.0);
    assertEquals(0, tracker.getTotalSlugsExited());
    assertArrayEquals(inletState, sections[0].getStateVector(), 0.0);
  }

  @Test
  void testOpenInletRemovesEntireReverseExitWithoutRecordingAnOutletArrival() {
    for (double inletPosition : new double[] { 0.0, 100.0 }) {
      TwoFluidSection[] sections = sections();
      double[][] original = new double[sections.length][];
      for (int cell = 0; cell < sections.length; cell++) {
        TwoFluidSection section = sections[cell];
        section.setPosition(section.getPosition() + inletPosition);
        section.setGasDensity(1000.0);
        section.setOilDensity(1000.0);
        section.setWaterDensity(1000.0);
        section.setLiquidDensity(1000.0);
        section.setWaterCut(1.0);
        section.setGasHoldup(0.2);
        section.setLiquidHoldup(0.8);
        section.setGasVelocity(-2.0);
        section.setLiquidVelocity(-2.0);
        section.updateDerivedQuantities();
        section.updateConservativeVariables();
        original[cell] = section.getStateVector();
      }
      LagrangianSlugTracker tracker = tracker();
      SlugProbe probe = tracker.addProbe(inletPosition);
      SlugBubbleUnit slug = initialize(tracker, sections, inletPosition + 1.0, inletPosition + 3.0);

      tracker.advanceTimeStep(sections, 2.0);

      assertTrue(slug.frontPosition < inletPosition, "The complete marker crossed the physical upstream boundary");
      assertEquals(0, tracker.getSlugCount(), "An upstream-exited marker must not remain active outside the pipe");
      assertTrue(slug.hasExited);
      assertEquals(0, tracker.getTotalSlugsExited(), "The existing counter describes downstream outlet exits");
      assertEquals(1, tracker.getTotalSlugsExitedAtInlet());
      assertEquals(slug.slugLiquidMass, tracker.getTotalMassExitedAtInlet(), 1e-12);
      assertEquals(0, tracker.getTotalSlugsDissipated(), "A boundary exit is not marker dissipation");
      assertEquals(0.0, tracker.getTotalMassExitedAtOutlet(), 0.0);
      assertTrue(tracker.getOutletSlugLengths().isEmpty());
      assertTrue(tracker.getOutletSlugVolumes().isEmpty());
      assertEquals(-1.0, tracker.getLastOutletArrivalTime(), 0.0);
      assertEquals(0.0, tracker.getMassConservationError(), 1e-10);
      assertEquals(2, probe.getEvents().size(), "Both reverse crossings must be retained before marker removal");
      assertEquals(SlugProbe.InterfaceType.TAIL, probe.getEvents().get(0).getInterfaceType());
      assertEquals(SlugProbe.InterfaceType.FRONT, probe.getEvents().get(1).getInterfaceType());
      for (int cell = 0; cell < sections.length; cell++) {
        assertArrayEquals(original[cell], sections[cell].getStateVector(), 0.0);
      }
      tracker.reset();
      assertEquals(0, tracker.getTotalSlugsExitedAtInlet());
      assertEquals(0.0, tracker.getTotalMassExitedAtInlet(), 0.0);
      assertEquals(0.0, tracker.getMassConservationError(), 0.0);
    }
  }

  @Test
  void testAllMarkerKinematicsUseTheSameAcceptedGeometryAsTheCflQuery() throws Exception {
    TwoFluidSection[] sections = sections();
    for (TwoFluidSection section : sections) {
      section.setLiquidHoldup(0.4);
      section.setGasHoldup(0.6);
      section.setGasVelocity(3.0);
      section.setLiquidVelocity(0.8);
      section.updateDerivedQuantities();
      section.updateConservativeVariables();
    }
    LagrangianSlugTracker tracker = tracker();
    SlugBubbleUnit following = initialize(tracker, sections, 1.0, 12.0);
    SlugBubbleUnit leading = initialize(tracker, sections, 16.0, 17.0);
    SlugBubbleUnit[] markers = { following, leading };
    Method kinematics = LagrangianSlugTracker.class.getDeclaredMethod("conservativeKinematics", SlugBubbleUnit.class,
        PipeSection[].class);
    kinematics.setAccessible(true);
    double[][] expected = new double[markers.length][];
    double[] initialFront = new double[markers.length];
    double[] initialTail = new double[markers.length];
    for (int marker = 0; marker < markers.length; marker++) {
      expected[marker] = (double[]) kinematics.invoke(tracker, markers[marker], sections);
      initialFront[marker] = markers[marker].frontPosition;
      initialTail[marker] = markers[marker].tailPosition;
    }
    double predictedMaximum = tracker.getMaximumInterfaceSpeed(sections);
    double dt = 0.02;

    tracker.advanceTimeStep(sections, dt);

    assertEquals(2, tracker.getSlugCount(), "The separated markers must not merge in this step");
    double actualMaximum = 0.0;
    for (int marker = 0; marker < markers.length; marker++) {
      SlugBubbleUnit actual = markers[marker];
      assertEquals(initialFront[marker] + dt * expected[marker][0], actual.frontPosition, 1e-12,
          "A marker must not sample another marker's partially advanced geometry or body-velocity target");
      assertEquals(initialTail[marker] + dt * expected[marker][1], actual.tailPosition, 1e-12);
      assertEquals(expected[marker][4], actual.slugLiquidVelocity, 1e-10);
      actualMaximum = Math.max(actualMaximum, Math.max(Math.abs(actual.frontVelocity), Math.abs(actual.tailVelocity)));
    }
    assertEquals(predictedMaximum, actualMaximum, 1e-10,
        "The accepted marker advance must use the same kinematic state as its CFL estimate");
  }

  /** @return deterministic tracker with only explicitly initialized markers */
  private LagrangianSlugTracker tracker() {
    LagrangianSlugTracker tracker = new LagrangianSlugTracker(13L);
    tracker.setConservativeFilmCouplingEnabled(true);
    tracker.setEnableInletSlugGeneration(false);
    tracker.setEnableWakeEffects(false);
    return tracker;
  }

  /**
   * Initialize an assumed marker over an independently initialized Eulerian body.
   *
   * @param tracker tracker
   * @param sections Eulerian sections
   * @param tail upstream endpoint (m)
   * @param front downstream endpoint (m)
   * @return marker
   */
  private SlugBubbleUnit initialize(LagrangianSlugTracker tracker, TwoFluidSection[] sections, double tail,
      double front) {
    SlugCharacteristics chars = new SlugCharacteristics();
    chars.tailPosition = tail;
    chars.frontPosition = front;
    chars.length = front - tail;
    chars.holdup = 0.85;
    chars.volume = chars.length * sections[0].getArea() * chars.holdup;
    return tracker.initializeTerrainSlug(chars, sections);
  }

  /** @return piecewise gas/oil/water body and films with independent phase masses */
  private TwoFluidSection[] sections() {
    TwoFluidSection[] sections = new TwoFluidSection[8];
    for (int i = 0; i < sections.length; i++) {
      TwoFluidSection section = new TwoFluidSection((i + 0.5) * 10.0, 10.0, 0.1, 0.0);
      double holdup = i == 2 ? 0.85 : (i == 1 ? 0.15 : 0.2);
      double liquidVelocity = i == 2 ? 2.5 : (i == 1 ? 0.5 : 0.8);
      section.setGasDensity(50.0);
      section.setOilDensity(800.0);
      section.setWaterDensity(1000.0);
      section.setLiquidDensity(860.0);
      section.setLiquidHoldup(holdup);
      section.setGasHoldup(1.0 - holdup);
      section.setWaterCut(0.3);
      section.setGasVelocity(3.0);
      section.setLiquidVelocity(liquidVelocity);
      section.setPressure(1.0e5);
      section.setGasEnthalpy(5.0e5);
      section.setLiquidEnthalpy(2.0e5);
      section.setTemperature(300.0);
      section.setMixtureHeatCapacity(2000.0);
      section.updateDerivedQuantities();
      section.updateConservativeVariables();
      sections[i] = section;
    }
    return sections;
  }
}
