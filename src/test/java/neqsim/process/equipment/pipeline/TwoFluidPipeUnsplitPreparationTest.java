package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.apache.commons.lang3.SerializationUtils;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.TwoFluidPipe.BoundaryCondition;
import neqsim.process.equipment.pipeline.twophasepipe.TwoFluidConservationEquations;
import neqsim.process.equipment.pipeline.twophasepipe.TwoFluidSection;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.TimeIntegrator;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.TwoFluidUnsplitIntegrator.IntervalPreparationException;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.TwoFluidUnsplitIntegrator.PreparedInterval;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.TwoFluidUnsplitModelAdapter.PhaseDensityModel;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.UnsplitTransientSolver;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Actual steady-state handoffs and isolation contracts for the unsplit preparation interface. */
class TwoFluidPipeUnsplitPreparationTest {
  @Test
  void requiresSteadyInitializationBeforeSnapshotDensityOrPreparation() {
    TwoFluidPipe pipe = createPipe(false, false);
    assertThrows(IllegalStateException.class, pipe::getSectionSnapshots);
    assertThrows(IllegalStateException.class, pipe::createUnsplitDensityModel);
    assertThrows(IllegalStateException.class, () -> pipe.prepareUnsplitTransient(1.0e-4, solver()));
    assertEquals(0.0, pipe.getSimulationTime(), 0.0);
    assertEquals(0.0, pipe.getTime(), 0.0);
  }

  @Test
  void referenceFlashCannotPromoteAnAlgebraicDensityExtensionToAPhysicalPhase() throws Exception {
    TwoFluidPipe pipe = createPipe(false, true);
    Field reference = TwoFluidPipe.class.getDeclaredField("referenceFluid");
    reference.setAccessible(true);
    reference.set(pipe, createPipe(true, true).getInletStream().getFluid());
    PublishedState published = new PublishedState(pipe);
    IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, pipe::createUnsplitDensityModel);
    assertTrue(failure.getMessage().contains("algebraic density extension"));
    published.assertUnchanged(pipe);
  }

  @Test
  void aNewInletPhaseCannotUseAnUnavailableComposition() throws Exception {
    TwoFluidPipe pipe = createPipe(false, true);
    PhaseDensityModel frozen = pipe.createUnsplitDensityModel();
    pipe.getInletStream().setThermoSystem(createPipe(true, true).getInletStream().getFluid());
    PublishedState published = new PublishedState(pipe);
    IllegalStateException failure = assertThrows(IllegalStateException.class,
        () -> pipe.prepareUnsplitTransient(1.0e-5, solver(), frozen));
    assertTrue(failure.getMessage().contains("Inlet phase has no frozen composition"));
    published.assertUnchanged(pipe);
  }

  @Test
  void preparationCannotInitializeTheLiveInletPhysicalPropertyCache() throws Exception {
    TwoFluidPipe pipe = createPipe(false, true);
    PhaseDensityModel frozen = pipe.createUnsplitDensityModel();
    SystemInterface fluid = pipe.getInletStream().getFluid();
    for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
      fluid.getPhase(phase).resetPhysicalProperties();
    }
    byte[] before = SerializationUtils.serialize(fluid);
    PublishedState published = new PublishedState(pipe);
    pipe.prepareUnsplitTransient(1.0e-5, solver(), frozen);
    assertArrayEquals(before, SerializationUtils.serialize(fluid));
    published.assertUnchanged(pipe);
  }

  @Test
  void anInconsistentAcceptedVolumeCannotBeRepairedByATinyPreparedStep() throws Exception {
    TwoFluidPipe pipe = createPipe(false, true);
    PhaseDensityModel density = pipe.createUnsplitDensityModel();
    Field cellsField = TwoFluidPipe.class.getDeclaredField("sections");
    cellsField.setAccessible(true);
    TwoFluidSection[] cells = (TwoFluidSection[]) cellsField.get(pipe);
    cells[0].setGasMassPerLength(1.01 * cells[0].getGasMassPerLength());
    PublishedState published = new PublishedState(pipe);
    IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
        () -> pipe.prepareUnsplitTransient(1.0e-9, solver(), density));
    assertTrue(failure.getMessage().contains("Accepted volume closure"));
    published.assertUnchanged(pipe);
  }

  @Test
  void sectionSnapshotsCannotAlterAcceptedInventoryOrClosureConfiguration() {
    TwoFluidPipe pipe = createPipe(true, true);
    TwoFluidSection[] accepted = pipe.getSectionSnapshots();
    TwoFluidSection[] edited = pipe.getSectionSnapshots();
    double[] state = edited[0].getStateVector();
    state[0] = -1.0;
    edited[0].setStateVector(state);
    edited[0].setPressure(1.0);
    edited[0].setTemperature(1.0);
    edited[0].getOilWaterDetector().setCriticalWeber(2.4);
    edited[0].getOilWaterDetector().setInversionConstant(0.7);
    edited[1] = null;
    assertSectionsEqual(accepted, pipe.getSectionSnapshots());
  }

  @Test
  void frozenEosHandoffMatchesActualSteadyPhaseDensitiesWithoutChangingThePipe() throws Exception {
    for (boolean threePhase : new boolean[] { false, true }) {
      TwoFluidPipe pipe = createPipe(threePhase, true);
      PublishedState published = new PublishedState(pipe);
      PhaseDensityModel density = pipe.createUnsplitDensityModel();
      TwoFluidSection[] accepted = pipe.getSectionSnapshots();
      double[] inventory = phaseInventories(accepted);
      assertTrue(inventory[0] > 0.0);
      if (threePhase) {
        assertTrue(inventory[1] > 0.0, "The steady fixture must contain oil");
        assertTrue(inventory[2] > 0.0, "The steady fixture must contain water");
      } else {
        assertEquals(0.0, inventory[1], 0.0);
        assertEquals(0.0, inventory[2], 0.0);
      }
      for (int cell = 0; cell < accepted.length; cell++) {
        TwoFluidSection section = accepted[cell];
        double[] actual = density.calculate(cell, section.getStateVector(), section.getPressure(), 0.0);
        double[] reference = { section.getGasDensity(), section.getOilDensity(), section.getWaterDensity() };
        for (int phase = 0; phase < 3; phase++) {
          assertTrue(Double.isFinite(actual[phase]) && actual[phase] > 0.0);
          if (section.getStateVector()[phase] > 0.0) {
            assertEquals(reference[phase], actual[phase], reference[phase] * 1.0e-10,
                "Handoff density in cell " + cell + ", phase " + phase);
          }
        }
        double[] compressed = density.calculate(cell, section.getStateVector(), 1.01 * section.getPressure(), 0.0);
        assertTrue(compressed[0] > actual[0], "The gas EOS response must retain positive compressibility");
      }
      published.assertUnchanged(pipe);
    }
  }

  @Test
  void preparesPureGasAndThreePhaseSteadyStatesWithAnIndependentAcceptedMassLedger() throws Exception {
    for (boolean threePhase : new boolean[] { false, true }) {
      TwoFluidPipe pipe = createPipe(threePhase, true);
      pipe.setEnableCoupledPressureMomentum(true);
      pipe.setImplicitInterfacialPressureCoupling(true);
      pipe.setTimeIntegrationMethod(TimeIntegrator.Method.IMEX_PRESSURE_CORRECTION);
      pipe.setMomentumForceDiagnosticsEnabled(true);
      PublishedState published = new PublishedState(pipe);
      UnsplitTransientSolver solver = solver();
      double dt = 1.0e-5;

      PreparedInterval interval = pipe.prepareUnsplitTransient(dt, solver);

      assertEquals(0.0, interval.getStartTimeSeconds(), 0.0);
      assertEquals(dt, interval.getEndTimeSeconds(), 0.0);
      assertFalse(interval.getSubsteps().isEmpty());
      assertTrue(interval.getMaximumScaledResidual() <= 1.0e-8);
      assertAcceptedMassLedger(published.sections, interval);
      assertEquals(TimeIntegrator.Method.IMEX_PRESSURE_CORRECTION, pipe.getTimeIntegrationMethod());
      assertTrue(pipe.isCoupledPressureMomentumEnabled());
      assertTrue(pipe.isImplicitInterfacialPressureCoupling());
      assertEquals(1.0e-9, solver.getRelativeTolerance(), 0.0);
      assertEquals(20, solver.getMaximumIterations());
      published.assertUnchanged(pipe);
    }
  }

  @Test
  void repeatedPreparationRetainsThePreviouslyPublishedTransientReportAndOutlet() throws Exception {
    TwoFluidPipe pipe = createPipe(false, true);
    pipe.runTransient(1.0e-5, UUID.randomUUID());
    assertNotNull(pipe.getLastMassBalanceReport());
    PublishedState published = new PublishedState(pipe);
    PhaseDensityModel density = pipe.createUnsplitDensityModel();

    PreparedInterval first = pipe.prepareUnsplitTransient(1.0e-5, solver(), density);
    PreparedInterval repeat = pipe.prepareUnsplitTransient(1.0e-5, solver(), density);

    assertEquals(published.simulationTime, first.getStartTimeSeconds(), 0.0);
    assertEquals(published.simulationTime + 1.0e-5, first.getEndTimeSeconds(), 0.0);
    assertSectionsEqual(first.getEndpointSections(), repeat.getEndpointSections());
    assertMatrixEquals(first.getPhaseMassFaceTransferKg(), repeat.getPhaseMassFaceTransferKg());
    assertMatrixEquals(first.getPhaseMassSourceTransferKg(), repeat.getPhaseMassSourceTransferKg());
    assertEquals(first.getRejectedAttempts(), repeat.getRejectedAttempts());
    assertEquals(first.getModelEvaluations(), repeat.getModelEvaluations());
    published.assertUnchanged(pipe);
  }

  @Test
  void failedNonlinearPreparationPreservesPreviousReportsClocksAndEquationDiagnostics() throws Exception {
    TwoFluidPipe pipe = createPipe(false, true);
    pipe.runTransient(1.0e-5, UUID.randomUUID());
    pipe.closeInlet();
    pipe.closeOutlet();
    pipe.setMomentumForceDiagnosticsEnabled(true);
    PublishedState published = new PublishedState(pipe);
    final TwoFluidSection[] accepted = pipe.getSectionSnapshots();
    final double start = pipe.getSimulationTime();
    // Manufactured incompatible volume after the initial check. A closed pure-gas domain cannot
    // double every density at unchanged occupied volume while conserving its total phase mass.
    // Exactly absent phases still need a positive algebraic density during nonlinear probes.
    PhaseDensityModel incompatible = (cell, state, pressure,
        time) -> new double[] { accepted[cell].getGasDensity() * (time > start ? 2.0 : 1.0),
            absentPhaseDensity(accepted[cell].getOilMassPerLength(), accepted[cell].getOilDensity()),
            absentPhaseDensity(accepted[cell].getWaterMassPerLength(), accepted[cell].getWaterDensity()) };
    UnsplitTransientSolver solver = solver();
    solver.setMaximumIterations(1);

    IntervalPreparationException failure = assertThrows(IntervalPreparationException.class,
        () -> pipe.prepareUnsplitTransient(0.01, solver, incompatible));

    assertTrue(failure.getRejectedAttempts() > 0);
    assertEquals(0, failure.getPreparedSubsteps());
    assertEquals(1, solver.getMaximumIterations());
    published.assertUnchanged(pipe);
  }

  @Test
  void aDensityFailureDuringANonlinearProbeCannotPublishAnyTrialState() throws Exception {
    TwoFluidPipe pipe = createPipe(false, true);
    pipe.runTransient(1.0e-5, UUID.randomUUID());
    PublishedState published = new PublishedState(pipe);
    PhaseDensityModel reference = pipe.createUnsplitDensityModel();
    IllegalStateException sentinel = new IllegalStateException("Injected nonlinear density failure");
    PhaseDensityModel failing = (cell, state, pressure, time) -> {
      if (time > published.simulationTime) {
        throw sentinel;
      }
      return reference.calculate(cell, state, pressure, time);
    };

    assertSame(sentinel,
        assertThrows(IllegalStateException.class, () -> pipe.prepareUnsplitTransient(1.0e-4, solver(), failing)));
    published.assertUnchanged(pipe);
  }

  @Test
  void incompatibleAndMalformedInitialDensitiesFailWithoutRepairingTheHandoff() throws Exception {
    TwoFluidPipe pipe = createPipe(true, true);
    PublishedState published = new PublishedState(pipe);
    PhaseDensityModel reference = pipe.createUnsplitDensityModel();
    for (int alteredPhase = 0; alteredPhase < 3; alteredPhase++) {
      final int phase = alteredPhase;
      PhaseDensityModel mismatched = (cell, state, pressure, time) -> {
        double[] density = reference.calculate(cell, state, pressure, time);
        if (cell == 1) {
          density[phase] *= 1.01;
        }
        return density;
      };
      assertThrows(IllegalArgumentException.class, () -> pipe.prepareUnsplitTransient(1.0e-4, solver(), mismatched));
      published.assertUnchanged(pipe);
    }
    PhaseDensityModel nullCell = (cell, state, pressure, time) -> cell == 1 ? null
        : reference.calculate(cell, state, pressure, time);
    assertThrows(IllegalArgumentException.class, () -> pipe.prepareUnsplitTransient(1.0e-4, solver(), nullCell));
    assertThrows(IllegalArgumentException.class, () -> pipe.prepareUnsplitTransient(1.0e-4, solver(), null));
    published.assertUnchanged(pipe);
  }

  @Test
  void rejectsInvalidPreparationArgumentsBeforeCallingTheDensityModel() throws Exception {
    TwoFluidPipe pipe = createPipe(false, true);
    PublishedState published = new PublishedState(pipe);
    PhaseDensityModel neverCalled = (cell, state, pressure, time) -> {
      throw new AssertionError("Invalid inputs must fail before density evaluation");
    };
    for (double invalid : new double[] { 0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY }) {
      assertThrows(IllegalArgumentException.class, () -> pipe.prepareUnsplitTransient(invalid, solver(), neverCalled));
    }
    assertThrows(IllegalArgumentException.class, () -> pipe.prepareUnsplitTransient(1.0e-4, null, neverCalled));
    UnsplitTransientSolver loose = new UnsplitTransientSolver();
    assertThrows(IllegalArgumentException.class, () -> pipe.prepareUnsplitTransient(1.0e-4, loose, neverCalled));
    published.assertUnchanged(pipe);
  }

  @Test
  void unsupportedPhysicalModelsFailBeforeDensityEvaluationOrMutation() throws Exception {
    List<Consumer<TwoFluidPipe>> unsupported = Arrays.asList(pipe -> pipe.setEnableSlugTracking(true),
        pipe -> pipe.setIncludeEnergyEquation(true), pipe -> pipe.setIncludeMassTransfer(true),
        pipe -> pipe.setComponentTransportEnabled(true), pipe -> pipe.setEnableStiffBubbleDrag(true), pipe -> {
          pipe.setHeatTransferCoefficient(10.0);
          pipe.setIncludeEnergyEquation(false);
        }, pipe -> {
          pipe.setDirectElectricalHeatingPowerPerMeter(10.0);
          pipe.setIncludeEnergyEquation(false);
        }, pipe -> pipe.initializeUpstreamCompressibleVolume(0.1));
    PhaseDensityModel neverCalled = (cell, state, pressure, time) -> {
      throw new AssertionError("Unsupported models must fail before density evaluation");
    };
    for (Consumer<TwoFluidPipe> configure : unsupported) {
      TwoFluidPipe pipe = createPipe(false, true);
      configure.accept(pipe);
      PublishedState published = new PublishedState(pipe);
      assertThrows(IllegalStateException.class, () -> pipe.prepareUnsplitTransient(1.0e-4, solver(), neverCalled));
      published.assertUnchanged(pipe);
    }
  }

  @Test
  void unsupportedBoundaryTypesFailBeforeDensityEvaluationOrMutation() throws Exception {
    List<Consumer<TwoFluidPipe>> unsupported = Arrays.asList(
        pipe -> pipe.setInletBoundaryCondition(BoundaryCondition.CONSTANT_PRESSURE),
        pipe -> pipe.setInletBoundaryCondition(BoundaryCondition.CONSTANT_FLOW),
        pipe -> pipe.setInletBoundaryCondition(BoundaryCondition.CHARACTERISTIC),
        pipe -> pipe.setOutletBoundaryCondition(BoundaryCondition.CONSTANT_FLOW),
        pipe -> pipe.setOutletBoundaryCondition(BoundaryCondition.STREAM_CONNECTED),
        pipe -> pipe.setOutletBoundaryCondition(BoundaryCondition.CHARACTERISTIC));
    PhaseDensityModel neverCalled = (cell, state, pressure, time) -> {
      throw new AssertionError("Unsupported boundaries must fail before density evaluation");
    };
    for (Consumer<TwoFluidPipe> configure : unsupported) {
      TwoFluidPipe pipe = createPipe(false, true);
      configure.accept(pipe);
      PublishedState published = new PublishedState(pipe);
      assertThrows(IllegalStateException.class, () -> pipe.prepareUnsplitTransient(1.0e-4, solver(), neverCalled));
      published.assertUnchanged(pipe);
    }
  }

  /** Build a small physical pipe through the public steady-state initializer. */
  private static TwoFluidPipe createPipe(boolean threePhase, boolean initialize) {
    SystemInterface fluid = new SystemSrkEos(300.0, 60.0);
    fluid.addComponent("methane", 1.0);
    if (threePhase) {
      fluid.addComponent("n-decane", 1.0);
      fluid.addComponent("water", 1.0);
    }
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    Stream feed = new Stream("unsplit preparation feed", fluid);
    feed.setFlowRate(0.1, "kg/sec");
    feed.run();
    TwoFluidPipe pipe = new TwoFluidPipe("unsplit preparation", feed);
    pipe.setLength(40.0);
    pipe.setDiameter(0.2);
    pipe.setNumberOfSections(4);
    pipe.setElevationProfile(new double[4]);
    pipe.setIncludeMassTransfer(false);
    pipe.setEnableSlugTracking(false);
    pipe.setEnableJouleThomson(false);
    pipe.setThermodynamicUpdateInterval(Integer.MAX_VALUE);
    pipe.setSteadyStateMaxWallClockTime(Double.POSITIVE_INFINITY);
    if (initialize) {
      pipe.run();
      assertFalse(pipe.isSteadyStateWallClockLimited());
    }
    return pipe;
  }

  /** Use a fixed stricter solve gate for independent preparation verification. */
  private static UnsplitTransientSolver solver() {
    UnsplitTransientSolver solver = new UnsplitTransientSolver();
    solver.setRelativeTolerance(1.0e-9);
    return solver;
  }

  /** Check domain and cell conservation using only candidate states and accepted transport integrals. */
  private static void assertAcceptedMassLedger(TwoFluidSection[] initial, PreparedInterval interval) {
    TwoFluidSection[] endpoint = interval.getEndpointSections();
    double[][] faces = interval.getPhaseMassFaceTransferKg();
    double[][] sources = interval.getPhaseMassSourceTransferKg();
    double[] initialMass = phaseInventories(initial);
    double[] finalMass = phaseInventories(endpoint);
    for (int phase = 0; phase < 3; phase++) {
      double residual = finalMass[phase] - initialMass[phase] - faces[0][phase] + faces[endpoint.length][phase];
      for (int cell = 0; cell < initial.length; cell++) {
        residual -= sources[cell][phase];
        double[] before = initial[cell].getStateVector();
        double[] after = endpoint[cell].getStateVector();
        double delta = (after[phase] - before[phase]) * initial[cell].getLength();
        double transfer = faces[cell][phase] - faces[cell + 1][phase] + sources[cell][phase];
        assertEquals(0.0, (delta - transfer) / Math.max(1.0, before[phase] * initial[cell].getLength()), 1.0e-8);
        assertEquals(before[6], after[6], 0.0, "Isothermal preparation must retain the energy variable");
      }
      assertEquals(0.0, residual / Math.max(1.0, initialMass[phase]), 1.0e-8);
      assertEquals(residual, interval.getMassResidualKg()[phase], 1.0e-12);
    }
  }

  /** Integrate the three conservative phase inventories using actual section lengths. */
  private static double[] phaseInventories(TwoFluidSection[] sections) {
    double[] mass = new double[3];
    for (TwoFluidSection section : sections) {
      double[] state = section.getStateVector();
      for (int phase = 0; phase < 3; phase++) {
        mass[phase] += state[phase] * section.getLength();
      }
    }
    return mass;
  }

  /** Supply a positive algebraic extension only when a legacy phase has exactly zero mass and density. */
  private static double absentPhaseDensity(double massPerLength, double density) {
    return massPerLength == 0.0 && density == 0.0 ? 1.0 : density;
  }

  /** Compare conserved, algebraic, thermodynamic and configured closure state exactly. */
  private static void assertSectionsEqual(TwoFluidSection[] expected, TwoFluidSection[] actual) {
    assertEquals(expected.length, actual.length);
    for (int cell = 0; cell < expected.length; cell++) {
      TwoFluidSection before = expected[cell];
      TwoFluidSection after = actual[cell];
      assertArrayEquals(before.getStateVector(), after.getStateVector(), 0.0);
      assertArrayEquals(sectionProperties(before), sectionProperties(after), 0.0);
      assertEquals(before.getFlowRegime(), after.getFlowRegime());
      assertEquals(before.getRegimeWeights(), after.getRegimeWeights());
      assertEquals(before.getOilWaterFlowRegime(), after.getOilWaterFlowRegime());
      assertEquals(before.getOilWaterDetector().getCriticalWeber(), after.getOilWaterDetector().getCriticalWeber(),
          0.0);
      assertEquals(before.getOilWaterDetector().getInversionConstant(),
          after.getOilWaterDetector().getInversionConstant(), 0.0);
    }
  }

  /** Numerical section state whose mutation would change accepted profiles or subsequent operators. */
  private static double[] sectionProperties(TwoFluidSection section) {
    return new double[] { section.getPosition(), section.getLength(), section.getDiameter(), section.getInclination(),
        section.getPressure(), section.getTemperature(), section.getGasDensity(), section.getOilDensity(),
        section.getWaterDensity(), section.getLiquidDensity(), section.getGasVelocity(), section.getOilVelocity(),
        section.getWaterVelocity(), section.getLiquidVelocity(), section.getGasHoldup(), section.getOilHoldup(),
        section.getWaterHoldup(), section.getLiquidHoldup(), section.getWaterCut(), section.getGasViscosity(),
        section.getOilViscosity(), section.getWaterViscosity(), section.getLiquidViscosity(),
        section.getGasSoundSpeed(), section.getLiquidSoundSpeed(), section.getGasWallShear(),
        section.getLiquidWallShear(), section.getInterfacialShear(), section.getEntrainmentFraction() };
  }

  /** Compare all rows of a defensive numerical ledger. */
  private static void assertMatrixEquals(double[][] expected, double[][] actual) {
    assertEquals(expected.length, actual.length);
    for (int row = 0; row < expected.length; row++) {
      assertArrayEquals(expected[row], actual[row], 0.0);
    }
  }

  /** Read retained private state only where no public accessor exposes the transaction surface. */
  private static Object field(TwoFluidPipe pipe, String name) throws Exception {
    Field field = TwoFluidPipe.class.getDeclaredField(name);
    field.setAccessible(true);
    return field.get(pipe);
  }

  /** Snapshot externally published values and evaluator-owned state before preparing a candidate. */
  private static final class PublishedState {
    private final TwoFluidSection[] sections;
    private final StreamInterface outlet;
    private final SystemInterface outletFluid;
    private final SystemInterface inletFluid;
    private final double[] outletProperties;
    private final double[] outletComposition;
    private final double[] inletComposition;
    private final double[][] profiles;
    private final double simulationTime;
    private final double equipmentTime;
    private final double integratorTime;
    private final UUID identifier;
    private final TwoFluidMassBalanceReport massReport;
    private final Object thermalReport;
    private final Object componentReport;
    private final byte[] equationState;
    private final int currentStep;
    private final boolean transientMode;

    private PublishedState(TwoFluidPipe pipe) throws Exception {
      sections = pipe.getSectionSnapshots();
      outlet = pipe.getOutletStream();
      outletFluid = outlet.getFluid();
      inletFluid = pipe.getInletStream().getFluid();
      outletProperties = new double[] { outlet.getPressure(), outlet.getTemperature(), outlet.getFlowRate("kg/sec") };
      outletComposition = outletFluid.getMolarComposition().clone();
      inletComposition = inletFluid.getMolarComposition().clone();
      profiles = profiles(pipe);
      simulationTime = pipe.getSimulationTime();
      equipmentTime = pipe.getTime();
      integratorTime = ((TimeIntegrator) field(pipe, "timeIntegrator")).getCurrentTime();
      identifier = pipe.getCalculationIdentifier();
      massReport = pipe.getLastMassBalanceReport();
      thermalReport = pipe.getLastThermalEnergyBalanceReport();
      componentReport = pipe.getLastComponentConservationReport();
      equationState = SerializationUtils.serialize((TwoFluidConservationEquations) field(pipe, "equations"));
      currentStep = (Integer) field(pipe, "currentStep");
      transientMode = (Boolean) field(pipe, "isTransientMode");
    }

    private void assertUnchanged(TwoFluidPipe pipe) throws Exception {
      assertSectionsEqual(sections, pipe.getSectionSnapshots());
      assertSame(outlet, pipe.getOutletStream());
      assertSame(outletFluid, pipe.getOutletStream().getFluid());
      assertSame(inletFluid, pipe.getInletStream().getFluid());
      assertArrayEquals(outletProperties,
          new double[] { outlet.getPressure(), outlet.getTemperature(), outlet.getFlowRate("kg/sec") }, 0.0);
      assertArrayEquals(outletComposition, outlet.getFluid().getMolarComposition(), 0.0);
      assertArrayEquals(inletComposition, inletFluid.getMolarComposition(), 0.0);
      assertMatrixEquals(profiles, profiles(pipe));
      assertEquals(simulationTime, pipe.getSimulationTime(), 0.0);
      assertEquals(equipmentTime, pipe.getTime(), 0.0);
      assertEquals(integratorTime, ((TimeIntegrator) field(pipe, "timeIntegrator")).getCurrentTime(), 0.0);
      assertSame(identifier, pipe.getCalculationIdentifier());
      assertSame(massReport, pipe.getLastMassBalanceReport());
      assertSame(thermalReport, pipe.getLastThermalEnergyBalanceReport());
      assertSame(componentReport, pipe.getLastComponentConservationReport());
      assertArrayEquals(equationState,
          SerializationUtils.serialize((TwoFluidConservationEquations) field(pipe, "equations")));
      assertEquals(currentStep, ((Integer) field(pipe, "currentStep")).intValue());
      assertEquals(transientMode, ((Boolean) field(pipe, "isTransientMode")).booleanValue());
    }

    private static double[][] profiles(TwoFluidPipe pipe) {
      return new double[][] { pipe.getPressureProfile(), pipe.getTemperatureProfile(), pipe.getLiquidHoldupProfile(),
          pipe.getGasVelocityProfile(), pipe.getLiquidVelocityProfile(), pipe.getOilVelocityProfile(),
          pipe.getWaterVelocityProfile(), pipe.getGasMassFlowProfile(), pipe.getOilMassFlowProfile(),
          pipe.getWaterMassFlowProfile() };
    }
  }
}
