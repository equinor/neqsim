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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import neqsim.process.equipment.pipeline.TwoFluidMassBalanceReport.Phase;
import neqsim.process.equipment.pipeline.twophasepipe.TwoFluidSection;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.TimeIntegrator;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.UnsplitTransientSolver;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Public runTransient selection, complete publication and repeated frozen-EOS continuation. */
class TwoFluidPipeUnsplitRunTest {
  @Test
  void selectionIsExplicitDefensiveAndSerializable() {
    TwoFluidPipe pipe = createPipe(false, 4);
    assertNull(pipe.getUnsplitTransientSolver());
    assertEquals(0.0, pipe.getUnsplitMaximumTimeStep(), 0.0);
    UnsplitTransientSolver selected = solver();
    pipe.setUnsplitTransientSolver(selected, 0.1);
    selected.setRelativeTolerance(0.01);
    assertEquals(1.0e-9, pipe.getUnsplitTransientSolver().getRelativeTolerance(), 0.0);
    pipe.getUnsplitTransientSolver().setRelativeTolerance(0.01);
    assertEquals(1.0e-9, pipe.getUnsplitTransientSolver().getRelativeTolerance(), 0.0);
    TwoFluidPipe restored = SerializationUtils.clone(pipe);
    assertEquals(0.1, restored.getUnsplitMaximumTimeStep(), 0.0);
    assertEquals(UnsplitTransientSolver.TimeIntegrationMethod.BACKWARD_EULER,
        restored.getUnsplitTransientSolver().getTimeIntegrationMethod());
    assertThrows(IllegalArgumentException.class, () -> pipe.setUnsplitTransientSolver(solver(), Double.NaN));
    assertEquals(0.1, pipe.getUnsplitMaximumTimeStep(), 0.0);
    pipe.setUnsplitTransientSolver(null, 0.0);
    assertNull(pipe.getUnsplitTransientSolver());
  }

  @Test
  void fiveSecondGasRunsPublishAcceptedMassAndContinueWithPersistentEos() throws Exception {
    for (int cells : new int[] { 8, 16 }) {
      verifyGasContinuation(cells, 0.05);
    }
  }

  @Test
  @Tag("slow")
  @EnabledIfSystemProperty(named = "neqsim.unsplit.gas.coarse.qualification", matches = "true")
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
    for (int interval = 0; interval < 2; interval++) {
      UUID id = UUID.randomUUID();
      pipe.runTransient(2.5, id);
      TwoFluidMassBalanceReport report = pipe.getLastMassBalanceReport();
      assertEquals(2.5, report.getElapsedTimeSeconds(), 0.0);
      assertTrue(report.getAcceptedSubsteps() >= (int) Math.ceil(2.5 / maximumStep));
      for (Phase phase : Phase.values()) {
        assertEquals(0.0, report.getRelativeResidual(phase), 1.0e-8);
      }
      inletMass += report.getInletMassKg(Phase.TOTAL);
      outletMass += report.getOutletMassKg(Phase.TOTAL);
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
    assertEquals(0.0, pipe.getTotalMassInventory() - initialMass - inletMass + outletMass, 1.0e-8);
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
    Phase[] phases = { Phase.GAS, Phase.OIL, Phase.WATER };
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
  void aChangedInletCompositionRejectsWithoutReassigningPhaseInventories() {
    TwoFluidPipe pipe = createPipe(false, 4);
    pipe.setUnsplitTransientSolver(solver(), 0.001);
    pipe.runTransient(0.001, UUID.randomUUID());
    SystemInterface changed = SerializationUtils.clone(pipe.getInletStream().getFluid());
    changed.setMolarComposition(new double[] { 0.6, 0.4 });
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
