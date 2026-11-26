package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.UUID;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.TwoFluidMassBalanceReport.Phase;
import neqsim.process.equipment.pipeline.twophasepipe.LagrangianSlugTracker;
import neqsim.process.equipment.pipeline.twophasepipe.LagrangianSlugTracker.SlugBubbleUnit;
import neqsim.process.equipment.pipeline.twophasepipe.LiquidAccumulationTracker.SlugCharacteristics;
import neqsim.process.equipment.pipeline.twophasepipe.TwoFluidSection;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.TimeIntegrator;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.TwoFluidUnsplitIntegrator;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.UnsplitTransientSolver;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Public runTransient selection, complete publication and repeated frozen-EOS continuation. */
class TwoFluidPipeUnsplitRunTest {
  private static final Logger logger = LogManager.getLogger(TwoFluidPipeUnsplitRunTest.class);

  @Test
  void selectionIsExplicitDefensiveAndSerializable() {
    TwoFluidPipe pipe = createPipe(false, 4);
    assertNull(pipe.getUnsplitTransientSolver());
    assertEquals(0.0, pipe.getUnsplitMaximumTimeStep(), 0.0);
    assertEquals(8, pipe.getUnsplitMaximumNonlinearHalvings());
    pipe.setUnsplitMaximumNonlinearHalvings(12);
    UnsplitTransientSolver selected = solver();
    pipe.setUnsplitTransientSolver(selected, 0.1);
    selected.setRelativeTolerance(0.01);
    assertEquals(1.0e-9, pipe.getUnsplitTransientSolver().getRelativeTolerance(), 0.0);
    pipe.getUnsplitTransientSolver().setRelativeTolerance(0.01);
    assertEquals(1.0e-9, pipe.getUnsplitTransientSolver().getRelativeTolerance(), 0.0);
    TwoFluidPipe restored = SerializationUtils.clone(pipe);
    assertEquals(0.1, restored.getUnsplitMaximumTimeStep(), 0.0);
    assertEquals(12, restored.getUnsplitMaximumNonlinearHalvings());
    assertEquals(UnsplitTransientSolver.TimeIntegrationMethod.BACKWARD_EULER,
        restored.getUnsplitTransientSolver().getTimeIntegrationMethod());
    assertThrows(IllegalArgumentException.class, () -> pipe.setUnsplitMaximumNonlinearHalvings(-1));
    assertThrows(IllegalArgumentException.class, () -> pipe.setUnsplitMaximumNonlinearHalvings(31));
    assertThrows(IllegalArgumentException.class, () -> pipe.setUnsplitTransientSolver(solver(), Double.NaN));
    assertEquals(0.1, pipe.getUnsplitMaximumTimeStep(), 0.0);
    pipe.setUnsplitTransientSolver(null, 0.0);
    assertNull(pipe.getUnsplitTransientSolver());
  }

  @Test
  void fiveSecondGasRunsPublishAcceptedMassAndContinueWithPersistentEos() throws Exception {
    for (int cells : new int[] {8, 16}) {
      verifyGasContinuation(cells, 0.05);
    }
  }

  @Test
  void coarseGasQualificationMustAlsoCompleteFiveSeconds() throws Exception {
    verifyGasContinuation(4, 0.1);
  }

  private static void verifyGasContinuation(int cells, double maximumStep) throws Exception {
    TwoFluidPipe pipe = createPipe(false, cells);
    UnsplitTransientSolver accurate = solver();
    accurate.setRelativeTolerance(1.0e-10);
    pipe.setUnsplitTransientSolver(accurate, maximumStep);
    pipe.setUnsplitPressureInterpolationEnabled(true);
    StreamInterface inlet = pipe.getInletStream();
    StreamInterface outlet = pipe.getOutletStream();
    SystemInterface feed = inlet.getFluid();
    byte[] feedBefore = SerializationUtils.serialize(feed);
    double initialMass = pipe.getTotalMassInventory();
    double inletMass = 0.0;
    double outletMass = 0.0;
    int acceptedSubsteps = 0;
    for (int interval = 0; interval < 2; interval++) {
      UUID id = UUID.randomUUID();
      pipe.runTransient(2.5, id);
      TwoFluidMassBalanceReport report = pipe.getLastMassBalanceReport();
      assertEquals(2.5, report.getElapsedTimeSeconds(), 0.0);
      assertTrue(report.getAcceptedSubsteps() >= (int) Math.ceil(2.5 / maximumStep));
      assertTrue(report.getRejectedAttempts() >= 0);
      assertTrue(report.getModelEvaluations() >= report.getAcceptedSubsteps());
      for (Phase phase : Phase.values()) {
        assertEquals(0.0, report.getRelativeResidual(phase), 1.0e-8);
      }
      inletMass += report.getInletMassKg(Phase.TOTAL);
      outletMass += report.getOutletMassKg(Phase.TOTAL);
      acceptedSubsteps += report.getAcceptedSubsteps();
      assertEquals(report.getOutletMassKg(Phase.TOTAL) / 2.5, outlet.getFlowRate("kg/sec"), 1.0e-12);
      assertSame(id, pipe.getCalculationIdentifier());
      assertEquals(2.5 * (interval + 1), pipe.getSimulationTime(), 0.0);
      assertEquals(pipe.getSimulationTime(), pipe.getTime(), 0.0);
      assertEquals(pipe.getSimulationTime(), ((TimeIntegrator) field(pipe, "timeIntegrator")).getCurrentTime(), 0.0);
      TwoFluidSection[] reference = (TwoFluidSection[]) field(pipe, "unsplitReferenceSections");
      assertEquals(cells, reference.length);
      assertNotNull(field(pipe, "unsplitDensityModel"));
      if (interval == 0) {
        byte[] eos = SerializationUtils.serialize((java.io.Serializable) field(pipe, "unsplitDensityModel"));
        byte[] compositionReferences = SerializationUtils.serialize(reference);
        pipe.setUnsplitTransientSolver(null, 0.0);
        pipe.setUnsplitTransientSolver(accurate, maximumStep);
        assertArrayEquals(eos, SerializationUtils.serialize((java.io.Serializable) field(pipe, "unsplitDensityModel")));
        assertArrayEquals(compositionReferences,
            SerializationUtils.serialize((TwoFluidSection[]) field(pipe, "unsplitReferenceSections")));
      }
      for (TwoFluidSection section : pipe.getSectionSnapshots()) {
        assertEquals(0.0, section.getOilMassPerLength(), 0.0);
        assertEquals(0.0, section.getWaterMassPerLength(), 0.0);
      }
    }
    double massResidual = pipe.getTotalMassInventory() - initialMass - inletMass + outletMass;
    assertEquals(0.0, massResidual, 1.0e-8);
    logger.info("Five-second unsplit gas continuation: cells={}, maximumStep={} s, substeps={}, massResidual={} kg",
        cells, maximumStep, acceptedSubsteps, massResidual);
    assertSame(inlet, pipe.getInletStream());
    assertSame(outlet, pipe.getOutletStream());
    assertSame(feed, inlet.getFluid());
    assertArrayEquals(feedBefore, SerializationUtils.serialize(feed));
    Stream downstream = new Stream("downstream", outlet);
    downstream.run();
    assertEquals(outlet.getFlowRate("kg/sec"), downstream.getFlowRate("kg/sec"), 1.0e-12);
  }

  @Test
  void closedUniformThreePhaseStateIsAnAcceptedFixedPoint() throws Exception {
    TwoFluidPipe pipe = createPipe(true, 4);
    pipe.closeInlet();
    pipe.closeOutlet();
    TwoFluidSection[] cells = (TwoFluidSection[]) field(pipe, "sections");
    double[] initial = new double[3];
    for (TwoFluidSection cell : cells) {
      double[] state = cell.getStateVector();
      for (int phase = 0; phase < 3; phase++) {
        assertTrue(state[phase] > 0.0);
        initial[phase] += state[phase] * cell.getLength();
        state[phase + 3] = 0.0;
      }
      cell.setPressure(60.0e5);
      cell.setConservativeEndpoint(state, 1.0e-8);
    }
    pipe.setUnsplitTransientSolver(solver(), 0.01);
    pipe.runTransient(0.02, UUID.randomUUID());
    TwoFluidMassBalanceReport report = pipe.getLastMassBalanceReport();
    Phase[] phases = {Phase.GAS, Phase.OIL, Phase.WATER};
    for (int phase = 0; phase < 3; phase++) {
      assertEquals(initial[phase], report.getFinalMassKg(phases[phase]), 1.0e-8);
      assertEquals(0.0, report.getOutletMassKg(phases[phase]), 0.0);
      assertEquals(0.0, report.getInletMassKg(phases[phase]), 0.0);
    }
    assertEquals(0.0, pipe.getOutletStream().getFlowRate("kg/sec"), 0.0);
  }

  @Test
  void failedIntervalAndUnsupportedCouplingCannotPublishAPrefix() {
    TwoFluidPipe pipe = createPipe(false, 4);
    pipe.setUnsplitTransientSolver(solver(), 0.1);
    pipe.setMaximumTransientSubsteps(1);
    byte[] before = SerializationUtils.serialize(pipe);
    assertThrows(RuntimeException.class, () -> pipe.runTransient(0.5, UUID.randomUUID()));
    assertArrayEquals(before, SerializationUtils.serialize(pipe));
    pipe.setMaximumTransientSubsteps(10000);
    pipe.setIncludeMassTransfer(true);
    before = SerializationUtils.serialize(pipe);
    assertThrows(IllegalStateException.class, () -> pipe.runTransient(0.01, UUID.randomUUID()));
    assertArrayEquals(before, SerializationUtils.serialize(pipe));
  }

  @Test
  void conservativeSlugGeometryAdvancesOnlyWithAnAcceptedUnsplitInterval() throws Exception {
    TwoFluidPipe pipe = createPipe(true, 4);
    prepareClosedConservativeSlug(pipe);
    SlugBubbleUnit originalMarker = seedSlug(pipe, 15.0, 25.0);
    double originalFront = originalMarker.frontPosition;
    pipe.setUnsplitTransientSolver(solver(), 0.005);
    pipe.setMaximumTransientSubsteps(1);
    byte[] before = SerializationUtils.serialize(pipe);
    assertThrows(RuntimeException.class, () -> pipe.runTransient(0.01, UUID.randomUUID()));
    assertArrayEquals(before, SerializationUtils.serialize(pipe));
    assertEquals(0.0, originalMarker.age, 0.0);
    assertEquals(originalFront, originalMarker.frontPosition, 0.0);

    pipe.setMaximumTransientSubsteps(256);
    pipe.runTransient(0.01, UUID.randomUUID());
    LagrangianSlugTracker accepted = pipe.getLagrangianSlugTracker();
    assertEquals(1, accepted.getSlugCount());
    SlugBubbleUnit acceptedMarker = accepted.getSlugs().get(0);
    assertEquals(0.01, acceptedMarker.age, 1.0e-14);
    assertTrue(Double.isFinite(acceptedMarker.frontPosition));
    assertTrue(Double.isFinite(acceptedMarker.tailPosition));
    assertTrue(acceptedMarker.frontPosition != originalFront);
    assertEquals(0.0, originalMarker.age, 0.0, "The accepted endpoint must replace, not mutate, the old tracker");
    for (Phase phase : Phase.values()) {
      assertEquals(0.0, pipe.getLastMassBalanceReport().getRelativeResidual(phase), 1.0e-8);
    }
  }

  @Test
  void trackedSubcellForcesRemainTransactionalAndConservative() throws Exception {
    TwoFluidPipe pipe = createPipe(true, 4);
    prepareClosedConservativeSlug(pipe);
    seedSlug(pipe, 15.0, 25.0);
    TwoFluidSection[] cells = (TwoFluidSection[]) field(pipe, "sections");
    for (TwoFluidSection cell : cells) {
      double[] state = cell.getStateVector();
      state[3] = 1.5 * state[0];
      state[4] = 0.4 * state[1];
      state[5] = 0.4 * state[2];
      cell.setConservativeEndpoint(state, 1.0e-8);
    }
    pipe.setSharedSlugForceBalanceEnabled(true);
    pipe.setConservativeSlugForceIntegrationEnabled(true);
    pipe.setUnsplitTransientSolver(solver(), 0.005);
    pipe.setMaximumTransientSubsteps(1);
    byte[] before = SerializationUtils.serialize(pipe);
    assertThrows(RuntimeException.class, () -> pipe.runTransient(0.01, UUID.randomUUID()));
    assertArrayEquals(before, SerializationUtils.serialize(pipe));

    pipe.setMaximumTransientSubsteps(256);
    TwoFluidUnsplitIntegrator.PreparedInterval interval = pipe.prepareUnsplitTransient(0.01, 0.005, solver(),
        pipe.createUnsplitDensityModel());
    double impulse = 0.0;
    for (double[] cellImpulse : interval.getConservativeFrictionImpulsePerLength()) {
      for (double phaseImpulse : cellImpulse) {
        assertTrue(Double.isFinite(phaseImpulse));
        impulse += Math.abs(phaseImpulse);
      }
    }
    assertTrue(impulse > 0.0, "The accepted tracked cells must publish their passive momentum impulse");
    for (double residual : interval.getMassResidualKg()) {
      assertEquals(0.0, residual, 1.0e-8);
    }
    assertEquals(0.0, pipe.getSimulationTime(), 0.0, "Preparation must not publish a prefix");

    pipe.runTransient(0.01, UUID.randomUUID());
    for (Phase phase : Phase.values()) {
      assertEquals(0.0, pipe.getLastMassBalanceReport().getRelativeResidual(phase), 1.0e-8);
    }
    assertEquals(0.01, pipe.getLagrangianSlugTracker().getSlugs().get(0).age, 1.0e-14);
  }

  @Test
  void movingSlugFilmCouplingIsStableAcrossMeshAndTimeStepRefinement() throws Exception {
    double coarse = runMovingSlugCase(4, 0.01);
    double refinedTime = runMovingSlugCase(4, 0.005);
    double refinedMesh = runMovingSlugCase(8, 0.005);
    assertEquals(coarse, refinedTime, 0.02);
    assertEquals(refinedTime, refinedMesh, 0.04,
        "The accepted marker front must agree to well below one percent of the refined cell length");
  }

  @Test
  void aChangedInletCompositionRejectsWithoutReassigningPhaseInventories() {
    TwoFluidPipe pipe = createPipe(false, 4);
    pipe.setUnsplitTransientSolver(solver(), 0.001);
    pipe.runTransient(0.001, UUID.randomUUID());
    SystemInterface changed = SerializationUtils.clone(pipe.getInletStream().getFluid());
    changed.setMolarComposition(new double[] {0.6, 0.4});
    Stream changedStream = new Stream("changed feed", changed);
    changedStream.run();
    pipe.getInletStream().setFluid(changedStream.getFluid());
    byte[] before = SerializationUtils.serialize(pipe);
    IllegalStateException failure = assertThrows(IllegalStateException.class,
        () -> pipe.runTransient(0.001, UUID.randomUUID()));
    assertTrue(failure.getMessage().contains("composition"));
    assertArrayEquals(before, SerializationUtils.serialize(pipe));
  }

  private static TwoFluidPipe createPipe(boolean threePhase, int cells) {
    SystemInterface fluid = new SystemSrkEos(300.0, 60.0);
    fluid.addComponent("methane", 0.8);
    fluid.addComponent("ethane", 0.2);
    if (threePhase) {
      fluid.addComponent("n-decane", 1.0);
      fluid.addComponent("water", 1.0);
    }
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    Stream inlet = new Stream("unsplit run feed", fluid);
    inlet.setFlowRate(0.1, "kg/sec");
    inlet.run();
    TwoFluidPipe pipe = new TwoFluidPipe("unsplit run", inlet);
    pipe.setLength(40.0);
    pipe.setDiameter(0.2);
    pipe.setNumberOfSections(cells);
    pipe.setCellFaceElevationProfile(new double[cells + 1]);
    pipe.setIncludeMassTransfer(false);
    pipe.setEnableSlugTracking(false);
    pipe.setEnableJouleThomson(false);
    pipe.setThermodynamicUpdateInterval(Integer.MAX_VALUE);
    pipe.setSteadyStateMaxWallClockTime(Double.POSITIVE_INFINITY);
    pipe.run();
    return pipe;
  }

  private static double runMovingSlugCase(int cells, double maximumStep) throws Exception {
    TwoFluidPipe pipe = createPipe(true, cells);
    prepareClosedConservativeSlug(pipe);
    seedSlug(pipe, 15.0, 25.0);
    pipe.setUnsplitTransientSolver(solver(), maximumStep);
    pipe.runTransient(0.01, UUID.randomUUID());
    TwoFluidMassBalanceReport report = pipe.getLastMassBalanceReport();
    for (Phase phase : Phase.values()) {
      assertEquals(0.0, report.getRelativeResidual(phase), 1.0e-8);
    }
    SlugBubbleUnit marker = pipe.getLagrangianSlugTracker().getSlugs().get(0);
    assertEquals(0.01, marker.age, 1.0e-14);
    assertTrue(Math.abs(marker.frontPosition - 25.0) > 1.0e-6);
    return marker.frontPosition;
  }

  private static void prepareClosedConservativeSlug(TwoFluidPipe pipe) throws Exception {
    pipe.closeInlet();
    pipe.closeOutlet();
    pipe.setSlugTrackingMode(TwoFluidPipe.SlugTrackingMode.CONSERVATIVE_LAGRANGIAN);
    pipe.getLagrangianSlugTracker().setEnableInletSlugGeneration(false);
    pipe.getLagrangianSlugTracker().setEnableTerrainSlugGeneration(false);
    pipe.getLagrangianSlugTracker().setEnableWakeEffects(false);
    TwoFluidSection[] cells = (TwoFluidSection[]) field(pipe, "sections");
    for (TwoFluidSection cell : cells) {
      double[] state = cell.getStateVector();
      state[3] = 0.0;
      state[4] = 0.0;
      state[5] = 0.0;
      cell.setPressure(60.0e5);
      cell.setConservativeEndpoint(state, 1.0e-8);
    }
  }

  private static SlugBubbleUnit seedSlug(TwoFluidPipe pipe, double tail, double front) throws Exception {
    pipe.getLagrangianSlugTracker().setEnableTerrainSlugGeneration(true);
    SlugCharacteristics characteristics = new SlugCharacteristics();
    characteristics.tailPosition = tail;
    characteristics.frontPosition = front;
    characteristics.length = front - tail;
    characteristics.holdup = 0.9;
    TwoFluidSection[] cells = (TwoFluidSection[]) field(pipe, "sections");
    characteristics.volume = characteristics.length * cells[0].getArea() * characteristics.holdup;
    SlugBubbleUnit marker = pipe.getLagrangianSlugTracker().initializeTerrainSlug(characteristics, cells);
    pipe.getLagrangianSlugTracker().setEnableTerrainSlugGeneration(false);
    assertNotNull(marker);
    return marker;
  }

  private static UnsplitTransientSolver solver() {
    UnsplitTransientSolver solver = new UnsplitTransientSolver();
    solver.setRelativeTolerance(1.0e-9);
    solver.setTimeIntegrationMethod(UnsplitTransientSolver.TimeIntegrationMethod.BACKWARD_EULER);
    return solver;
  }

  private static Object field(TwoFluidPipe pipe, String name) throws Exception {
    Field field = TwoFluidPipe.class.getDeclaredField(name);
    field.setAccessible(true);
    return field.get(pipe);
  }
}
