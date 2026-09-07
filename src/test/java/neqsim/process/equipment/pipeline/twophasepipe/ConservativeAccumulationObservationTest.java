package neqsim.process.equipment.pipeline.twophasepipe;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.TwoFluidPipe;
import neqsim.process.equipment.pipeline.TwoFluidPipe.SlugTrackingMode;
import neqsim.process.equipment.pipeline.twophasepipe.LiquidAccumulationTracker.AccumulationZone;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

/** Accumulation diagnostics must observe the finite-volume inventory without modifying the solution. */
class ConservativeAccumulationObservationTest {
  @Test
  void observationAndMarkerReleasePreserveThreePhaseCountercurrentStates() {
    for (double waterCut : new double[] { 0.0, 0.4, 1.0 }) {
      TwoFluidSection[] sections = sections(waterCut, 0.4);
      LiquidAccumulationTracker tracker = new LiquidAccumulationTracker();
      tracker.identifyAccumulationZones(sections);
      assertFalse(tracker.getAccumulationZones().isEmpty());
      double[][] before = snapshots(sections);
      for (int step = 0; step < 31; step++) {
        tracker.observeConservativeAccumulation(sections, 1.0);
      }
      for (AccumulationZone zone : tracker.getAccumulationZones()) {
        double expected = volume(zone, sections);
        assertEquals(expected, zone.liquidVolume, 1e-14);
        assertEquals(0.0, zone.netInflowRate, 0.0, "A fixed state must not invent accumulation");
        double beforeRelease = zone.liquidVolume;
        assertNotNull(tracker.checkForSlugRelease(zone, sections));
        assertEquals(beforeRelease, zone.liquidVolume, 0.0, "A marker must not consume observed liquid");
      }
      for (int index = 0; index < sections.length; index++) {
        assertArrayEquals(before[index], snapshot(sections[index]), 0.0);
      }
    }
  }

  @Test
  void observedInventoryFallsWhenTheConservativeCellsDrain() {
    TwoFluidSection[] sections = sections(0.4, 0.4);
    LiquidAccumulationTracker tracker = new LiquidAccumulationTracker();
    tracker.identifyAccumulationZones(sections);
    tracker.observeConservativeAccumulation(sections, 1.0);
    double before = tracker.getAccumulationZones().get(0).liquidVolume;
    for (TwoFluidSection section : sections) {
      // Change only the conserved masses: observation must not use a stale primitive holdup.
      section.setOilMassPerLength(section.getOilMassPerLength() * 0.5);
      section.setWaterMassPerLength(section.getWaterMassPerLength() * 0.5);
    }
    tracker.observeConservativeAccumulation(sections, 2.0);
    AccumulationZone zone = tracker.getAccumulationZones().get(0);
    assertEquals(before * 0.5, zone.liquidVolume, 1e-14);
    assertEquals(-before * 0.25, zone.netInflowRate, 1e-14);
  }

  @Test
  void dryGasCannotAccumulateLiquidEvenWithUnsetAbsentPhaseDensities() {
    TwoFluidSection[] sections = sections(0.0, 0.0);
    for (TwoFluidSection section : sections) {
      section.setOilDensity(0.0);
      section.setWaterDensity(0.0);
    }
    LiquidAccumulationTracker tracker = new LiquidAccumulationTracker();
    tracker.identifyAccumulationZones(sections);
    tracker.observeConservativeAccumulation(sections, 100.0);
    for (AccumulationZone zone : tracker.getAccumulationZones()) {
      assertEquals(0.0, zone.liquidVolume, 0.0);
      assertFalse(zone.isOverflowing);
    }
  }

  @Test
  void observationUsesTheExistingUnsetPhaseDensityFallbacks() {
    TwoFluidSection[] sections = sections(0.4, 0.4);
    for (TwoFluidSection section : sections) {
      section.setOilDensity(0.0);
      section.setWaterDensity(Double.NaN);
      section.updateConservativeVariables();
    }
    LiquidAccumulationTracker tracker = new LiquidAccumulationTracker();
    tracker.identifyAccumulationZones(sections);
    tracker.observeConservativeAccumulation(sections, 1.0);
    for (AccumulationZone zone : tracker.getAccumulationZones()) {
      assertEquals(volume(zone, sections), zone.liquidVolume, 1e-14);
    }
  }

  @Test
  void passiveTrackingDoesNotChangeTheAcceptedPipeSolution() throws Exception {
    TwoFluidPipe reference = pipe(SlugTrackingMode.DISABLED);
    TwoFluidPipe simplified = pipe(SlugTrackingMode.SIMPLIFIED);
    TwoFluidPipe lagrangian = pipe(SlugTrackingMode.LAGRANGIAN);
    UUID id = UUID.randomUUID();
    for (int step = 0; step < 3; step++) {
      reference.runTransient(1e-3, id);
      for (TwoFluidPipe tracked : new TwoFluidPipe[] { simplified, lagrangian }) {
        tracked.runTransient(1e-3, id);
        assertArrayEquals(reference.getPressureProfile(), tracked.getPressureProfile(), 0.0);
        assertArrayEquals(reference.getLiquidHoldupProfile(), tracked.getLiquidHoldupProfile(), 0.0);
        assertArrayEquals(reference.getLiquidVelocityProfile(), tracked.getLiquidVelocityProfile(), 0.0);
        TwoFluidSection[] expected = pipeSections(reference);
        TwoFluidSection[] actual = pipeSections(tracked);
        for (int index = 0; index < expected.length; index++) {
          assertArrayEquals(snapshot(expected[index]), snapshot(actual[index]), 0.0);
        }
      }
    }
  }

  @Test
  void legacyDriftFluxTrackerRetainsItsSectionOverlay() {
    PipeSection[] sections = { new PipeSection(1.0, 2.0, 0.3, 0.0), new PipeSection(4.0, 4.0, 0.3, Math.PI / 2.0) };
    for (PipeSection section : sections) {
      section.setGasHoldup(0.7);
      section.setLiquidHoldup(0.3);
      section.setGasDensity(40.0);
      section.setLiquidDensity(800.0);
      section.setGasVelocity(2.0);
      section.setLiquidVelocity(1.0);
    }
    LiquidAccumulationTracker tracker = new LiquidAccumulationTracker();
    tracker.identifyAccumulationZones(sections);
    tracker.updateAccumulation(sections, 1.0);
    assertTrue(sections[0].getLiquidHoldup() > 0.3);
    assertTrue(sections[0].getLiquidVelocity() < 1.0);
  }

  private static TwoFluidSection[] sections(double waterCut, double holdup) {
    TwoFluidSection[] result = { new TwoFluidSection(1.0, 2.0, 0.2, -0.1), new TwoFluidSection(4.0, 4.0, 0.3, 0.0),
        new TwoFluidSection(9.0, 6.0, 0.4, 1.0) };
    result[1].setElevation(-1.0);
    for (TwoFluidSection section : result) {
      section.setGasHoldup(1.0 - holdup);
      section.setLiquidHoldup(holdup);
      section.setWaterCut(waterCut);
      section.setOilHoldup(holdup * (1.0 - waterCut));
      section.setWaterHoldup(holdup * waterCut);
      section.setGasDensity(40.0);
      section.setOilDensity(800.0);
      section.setWaterDensity(1000.0);
      section.setLiquidDensity(800.0 * (1.0 - waterCut) + 1000.0 * waterCut);
      section.setGasVelocity(3.0);
      section.setOilVelocity(-0.5);
      section.setWaterVelocity(-0.2);
      section.setLiquidVelocity(-0.4);
      section.updateConservativeVariables();
      assertEquals(holdup * (1.0 - waterCut) * 800.0 * section.getArea(), section.getOilMassPerLength(), 1e-14);
      assertEquals(holdup * waterCut * 1000.0 * section.getArea(), section.getWaterMassPerLength(), 1e-14);
    }
    return result;
  }

  private static double volume(AccumulationZone zone, TwoFluidSection[] sections) {
    double result = 0.0;
    for (int index : zone.sectionIndices) {
      TwoFluidSection section = sections[index];
      result += section.getArea() * section.getLength() * 0.4;
    }
    return result;
  }

  private static double[] snapshot(TwoFluidSection section) {
    double[] state = section.getStateVector();
    double[] result = new double[state.length + 9];
    System.arraycopy(state, 0, result, 0, state.length);
    double[] primitive = { section.getGasHoldup(), section.getOilHoldup(), section.getWaterHoldup(),
        section.getGasVelocity(), section.getOilVelocity(), section.getWaterVelocity(), section.getLiquidVelocity(),
        section.getPressure(), section.getMixtureDensity() };
    System.arraycopy(primitive, 0, result, state.length, primitive.length);
    return result;
  }

  private static double[][] snapshots(TwoFluidSection[] sections) {
    double[][] result = new double[sections.length][];
    for (int index = 0; index < sections.length; index++) {
      result[index] = snapshot(sections[index]);
    }
    return result;
  }

  private static TwoFluidPipe pipe(SlugTrackingMode mode) throws Exception {
    SystemSrkEos fluid = new SystemSrkEos(300.0, 60.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(0.1, "kg/sec");
    feed.run();
    TwoFluidPipe pipe = new TwoFluidPipe("observed-accumulation", feed);
    pipe.setLength(12.0);
    pipe.setDiameter(0.3);
    pipe.setNumberOfSections(3);
    pipe.setElevationProfile(new double[] { 0.0, -0.01, 0.0 });
    pipe.setIncludeMassTransfer(false);
    pipe.setEnableJouleThomson(false);
    pipe.setThermodynamicUpdateInterval(Integer.MAX_VALUE);
    pipe.setSteadyStateMaxWallClockTime(Double.POSITIVE_INFINITY);
    pipe.setEnableInterfacialPressure(true);
    pipe.setEnableCoupledPressureMomentum(true);
    pipe.run();
    TwoFluidSection[] sections = pipeSections(pipe);
    for (int index = 0; index < sections.length; index++) {
      TwoFluidSection section = sections[index];
      section.setGasDensity(40.0);
      section.setOilDensity(800.0);
      section.setWaterDensity(1000.0);
      section.setLiquidDensity(880.0);
      section.setGasHoldup(0.6);
      section.setLiquidHoldup(0.4);
      section.setWaterCut(0.4);
      section.setOilHoldup(0.24);
      section.setWaterHoldup(0.16);
      section.setGasVelocity(0.0);
      section.setOilVelocity(0.0);
      section.setWaterVelocity(0.0);
      section.setLiquidVelocity(0.0);
      section.updateConservativeVariables();
    }
    assertTrue(sections[0].getOilMassPerLength() > 0.0 && sections[0].getWaterMassPerLength() > 0.0);
    pipe.getAccumulationTracker().identifyAccumulationZones(sections);
    assertFalse(pipe.getAccumulationTracker().getAccumulationZones().isEmpty());
    pipe.setSlugTrackingMode(mode);
    pipe.closeInlet();
    pipe.closeOutlet();
    return pipe;
  }

  private static TwoFluidSection[] pipeSections(TwoFluidPipe pipe) throws Exception {
    Field field = TwoFluidPipe.class.getDeclaredField("sections");
    field.setAccessible(true);
    return (TwoFluidSection[]) field.get(pipe);
  }
}
