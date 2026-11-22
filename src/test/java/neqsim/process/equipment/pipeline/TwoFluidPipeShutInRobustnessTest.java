package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.process.equipment.pipeline.TwoFluidMassBalanceReport.Phase;
import neqsim.process.equipment.pipeline.twophasepipe.TwoFluidConservationEquations;
import neqsim.process.equipment.pipeline.twophasepipe.TwoFluidSection;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.TimeIntegrator;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Well shut-in robustness of the transient two-fluid pipe.
 *
 * <p>
 * A closed wellhead choke delivers a stream whose phases hold zero moles. The prescribed inlet face must accept it, and
 * a draining riser must not be blown down through the fixed-pressure outlet by a velocity that primitive recovery
 * silently clipped.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
class TwoFluidPipeShutInRobustnessTest {
  /** Riser outlet pressure of the well-to-topside geometry in bara. */
  private static final double OUTLET_PRESSURE_BARA = 56.40099362;

  /**
   * Create a gas, oil and water well stream.
   *
   * @param massFlowKgPerSecond feed rate in kg/s
   * @return flashed stream at 70 bara and 35 C
   */
  private static Stream wellStream(double massFlowKgPerSecond) {
    SystemInterface fluid = new SystemSrkEos(308.15, 70.0);
    fluid.addComponent("methane", 0.46);
    fluid.addComponent("propane", 0.07);
    fluid.addComponent("n-heptane", 0.15);
    fluid.addComponent("water", 0.32);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    Stream stream = new Stream("well", fluid);
    stream.setFlowRate(massFlowKgPerSecond, "kg/sec");
    stream.run();
    return stream;
  }

  /**
   * Configure the transient options used by the well-to-topside study.
   *
   * @param pipe pipe to configure
   */
  private static void useStudyNumerics(TwoFluidPipe pipe) {
    pipe.setEnableJouleThomson(false);
    pipe.setEnableWaterOilSlip(true);
    pipe.setOutletPressure(OUTLET_PRESSURE_BARA, "bara");
    pipe.setAllowOutletPhaseBackflow(false);
    pipe.setEnableCoupledPressureMomentum(true);
    pipe.setEnableInterfacialPressure(true);
    pipe.setTimeIntegrationMethod(TimeIntegrator.Method.EULER);
    pipe.setEnableAdaptiveTimestepping(true);
  }

  @Test
  void closedChokeFeedIsAcceptedByThePrescribedInletFace() {
    Stream well = wellStream(8.0);
    TwoFluidPipe pipe = new TwoFluidPipe("short tieback", well);
    pipe.setLength(1000.0);
    pipe.setDiameter(0.2032);
    pipe.setRoughness(4.5e-5);
    pipe.setNumberOfSections(10);
    double[] faces = new double[11];
    for (int face = 0; face <= 10; face++) {
      faces[face] = -20.0 + 2.0 * face;
    }
    pipe.setCellFaceElevationProfile(faces);
    useStudyNumerics(pipe);
    pipe.run();

    // A closed choke keeps the phase objects of its outlet stream but with zero moles. Before the fix the inlet face
    // asked such a phase for J/kg enthalpy and failed with "Total mass cannot be zero".
    well.setFlowRate(0.0, "kg/sec");
    well.run();
    UUID id = UUID.randomUUID();
    for (int step = 0; step < 4; step++) {
      pipe.runTransient(0.5, id);
      TwoFluidMassBalanceReport report = pipe.getLastMassBalanceReport();
      assertEquals(0.0, report.getInletMassKg(Phase.TOTAL), 1.0e-9, "A shut-in well delivers no mass");
      for (Phase phase : new Phase[] {Phase.GAS, Phase.OIL, Phase.WATER}) {
        assertTrue(Math.abs(report.getResidualKg(phase)) < 1.0e-6, "Phase inventory must close for " + phase);
      }
      for (double pressure : pipe.getPressureProfile()) {
        assertTrue(Double.isFinite(pressure) && pressure > 0.0, "Pressures stay finite and positive");
      }
    }

    // Reopening the well uses the feed properties again.
    well.setFlowRate(8.0, "kg/sec");
    well.run();
    pipe.runTransient(0.5, id);
    assertTrue(pipe.getLastMassBalanceReport().getInletMassKg(Phase.TOTAL) > 0.0, "Restart feeds the pipe");
  }

  @Test
  void velocityLimiterRejectsOnlyResolvedPhasesAtTheirRecoveryBound() {

    int width = TwoFluidConservationEquations.IDX_WATER_MOMENTUM + 1;

    double[][] calm = new double[2][width];
    setPhase(calm[0], 0, 1.0, 20.0);
    setPhase(calm[0], 1, 10.0, -2.0);
    setPhase(calm[1], 0, 1.0, 99.0);
    setPhase(calm[1], 1, 10.0, 49.0);
    assertNull(TwoFluidPipe.describeEngagedVelocityLimiter(calm[0], 10.0), "Velocities inside the bounds pass");

    double[][] resolvedOil = copy(calm);
    setPhase(resolvedOil[1], 1, 10.0, -50.0);
    String message = TwoFluidPipe.describeEngagedVelocityLimiter(resolvedOil[1], 30.0);
    assertNotNull(message, "A resolved liquid at the 50 m/s bound marks an unstable substep");
    assertTrue(message.contains("oil") && message.contains("x=30.0"), message);

    double[][] fastGas = copy(calm);
    setPhase(fastGas[0], 0, 1.0, 100.0);
    assertNotNull(TwoFluidPipe.describeEngagedVelocityLimiter(fastGas[0], 10.0), "Gas at 100 m/s is rejected");

    double[][] traceWater = copy(calm);
    // 0.1 kg/m in an 11.1 kg/m cell is below the 5 % mass share: the historical trace-phase clipping applies.
    setPhase(traceWater[1], 2, 0.1, 80.0);
    assertNull(TwoFluidPipe.describeEngagedVelocityLimiter(traceWater[1], 30.0), "Trace phases keep clipping");

    double[][] empty = new double[1][width];
    assertNull(TwoFluidPipe.describeEngagedVelocityLimiter(empty[0], 0.0), "An empty cell is ignored");
  }

  @Test
  void steadyFeedTransientNeedsNoVelocityLimiterRetry() {
    Stream well = wellStream(8.0);
    TwoFluidPipe pipe = new TwoFluidPipe("short tieback", well);
    pipe.setLength(1000.0);
    pipe.setDiameter(0.2032);
    pipe.setNumberOfSections(10);
    useStudyNumerics(pipe);
    pipe.run();
    UUID id = UUID.randomUUID();
    for (int step = 0; step < 4; step++) {
      pipe.runTransient(0.5, id);
    }
    assertEquals(0L, pipe.getTransientVelocityLimiterRejectionCount());
    pipe.run();
    assertEquals(0L, pipe.getTransientVelocityLimiterRejectionCount(), "run() resets the diagnostic");
  }

  /**
   * Run a short shut-in and restart for every nonempty subset of gas, oil and water.
   *
   * @param phases bit mask for methane, heptane and water, respectively
   */
  @ParameterizedTest
  @ValueSource(ints = {1, 2, 4, 3, 5, 6, 7})
  void materialWaveRestartPreservesPhaseInventories(int phases) {
    SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
    String[] components = {"methane", "n-heptane", "water"};
    double[] amounts = {0.6, 0.2, 0.2};
    for (int i = 0; i < components.length; i++) {
      if ((phases & (1 << i)) != 0) {
        fluid.addComponent(components[i], amounts[i]);
      }
    }
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(1.0, "kg/sec");
    feed.run();
    assertEquals(Integer.bitCount(phases), feed.getFluid().getNumberOfPhases());
    TwoFluidPipe pipe = new TwoFluidPipe("horizontal line", feed);
    pipe.setLength(100.0);
    pipe.setDiameter(0.2);
    pipe.setNumberOfSections(4);
    pipe.setCellFaceElevationProfile(new double[5]);
    useStudyNumerics(pipe);
    pipe.setOutletPressure(50.0, "bara");
    pipe.setMaterialWaveRusanovEnabled(true);
    pipe.run();
    Phase[] phaseNames = {Phase.GAS, Phase.OIL, Phase.WATER};
    for (double rate : new double[] {1.0, 0.0, 0.0, 1.0}) {
      feed.setFlowRate(rate, "kg/sec");
      feed.run();
      pipe.runTransient(0.1, UUID.randomUUID());
      TwoFluidMassBalanceReport report = pipe.getLastMassBalanceReport();
      assertEquals(0.1 * rate, report.getInletMassKg(Phase.TOTAL), 1.0e-9);
      for (int i = 0; i < phaseNames.length; i++) {
        Phase phase = phaseNames[i];
        assertEquals(0.0, report.getResidualKg(phase), 1.0e-7, phase.toString());
        if ((phases & (1 << i)) == 0) {
          assertEquals(0.0, report.getFinalMassKg(phase), 0.0, phase.toString());
        } else {
          assertTrue(report.getFinalMassKg(phase) > 0.0, phase.toString());
        }
      }
      for (double pressure : pipe.getPressureProfile()) {
        assertTrue(Double.isFinite(pressure) && pressure > 0.0);
      }
    }
  }

  /** An exact two-cell impedance problem has a known velocity jump, phase momentum and total energy. */
  @Test
  void acousticTractionConservesInventoryMomentumAndEnergy() {
    TwoFluidSection[] cells = {new TwoFluidSection(0.5, 1.0, 0.2, 0.0), new TwoFluidSection(2.0, 2.0, 0.2, 0.0)};
    for (TwoFluidSection cell : cells) {
      cell.setGasSoundSpeed(4.0);
      cell.setLiquidSoundSpeed(1000.0);
    }
    // Both gas inventories are 2 kg. The line impedances are 8 and 4 kg/s.
    double[][] state = {{2.0, 0.0, 1.0, 6.0, 0.0, 1.0, 100.0}, {1.0, 2.0, 0.0, -1.0, 6.0, 0.0, 100.0}};
    TwoFluidConservationEquations.applyAcousticFaceTraction(state, cells, 0.75);
    // dt * Z_left * Z_right / (Z_left + Z_right) = 2 kg. Backward Euler contracts the jump by 1/3.
    assertEquals(4.0 / 3.0, state[0][3] / 2.0 - state[1][3], 1e-12);
    assertEquals(4.0, state[0][3] + 2.0 * state[1][3], 1e-12);
    assertEquals(300.0, state[0][6] + 2.0 * state[1][6], 1e-12);
    assertEquals(2.0, state[0][0], 0.0);
    assertEquals(1.0, state[1][0], 0.0);
    assertEquals(6.0, state[1][4], 0.0);
    assertEquals(1.0, state[0][5], 0.0);
  }

  /**
   * Complete shut-in and restart on the 5.4 km well-to-topside terrain.
   *
   * @param refinement subdivision of the 60-cell terrain
   */
  @ParameterizedTest
  @ValueSource(ints = {1, 2})
  @Tag("slow")
  void shutInRiserCompletesRestartAgainstFixedPressureOutlet(int refinement) {
    Stream well = wellStream(20.0);
    well.setPressure(70.0, "bara");
    well.run();
    double[] legLengths = {800, 900, 850, 1100, 1350, 400};
    double[] elevations = {-260, -278, -252, -289, -270, -280, 25};
    int[] legCells = {8, 9, 9, 11, 13, 10};
    double[] lengths = new double[60 * refinement];
    double[] faces = new double[lengths.length + 1];
    faces[0] = elevations[0];
    int index = 0;
    for (int leg = 0; leg < legCells.length; leg++) {
      int count = legCells[leg] * refinement;
      for (int cell = 1; cell <= count; cell++) {
        lengths[index] = legLengths[leg] / count;
        faces[++index] = elevations[leg] + (double) cell / count * (elevations[leg + 1] - elevations[leg]);
      }
    }
    TwoFluidPipe pipe = new TwoFluidPipe("flowline and riser", well);
    pipe.setLength(5400.0);
    pipe.setDiameter(0.2032);
    pipe.setRoughness(4.5e-5);
    pipe.setMaterialWaveRusanovEnabled(true);
    pipe.setSectionLengths(lengths);
    pipe.setCellFaceElevationProfile(faces);
    useStudyNumerics(pipe);
    pipe.run();

    UUID id = UUID.randomUUID();
    double timeStep = 0.5;
    double minimumOutletCellPressure = Double.POSITIVE_INFINITY;
    double maximumGasOutflow = 0.0;
    for (int step = 1; step * timeStep <= 300.0; step++) {
      double time = step * timeStep;
      boolean shutIn = time >= 20.0 && time < 190.0;
      well.setFlowRate(shutIn ? 0.0 : 20.0, "kg/sec");
      well.run();
      pipe.runTransient(timeStep, id);
      TwoFluidMassBalanceReport balance = pipe.getLastMassBalanceReport();
      assertEquals(shutIn ? 0.0 : 20.0 * timeStep, balance.getInletMassKg(Phase.TOTAL), 1.0e-8);
      for (Phase phase : new Phase[] {Phase.GAS, Phase.OIL, Phase.WATER}) {
        assertEquals(0.0, balance.getResidualKg(phase), 1.0e-6, phase.toString());
      }
      if (time > 30.0 && shutIn) {
        double[] pressure = pipe.getPressureProfile();
        minimumOutletCellPressure = Math.min(minimumOutletCellPressure, pressure[pressure.length - 1] / 1.0e5);
        maximumGasOutflow = Math.max(maximumGasOutflow,
            pipe.getLastMassBalanceReport().getOutletMassKg(Phase.GAS) / timeStep);
      }
    }
    assertTrue(minimumOutletCellPressure > 0.95 * OUTLET_PRESSURE_BARA,
        "The shut-in riser top must stay near the outlet pressure, minimum " + minimumOutletCellPressure + " bara");
    assertTrue(maximumGasOutflow < 10.0,
        "A closed well must not vent gas through the riser at blowdown rates, maximum " + maximumGasOutflow + " kg/s");
  }

  /**
   * Set one phase's mass and momentum in a conservative state row.
   *
   * @param row state row
   * @param phase 0 gas, 1 oil, 2 water
   * @param massPerLength phase mass in kg/m
   * @param velocity phase velocity in m/s
   */
  private static void setPhase(double[] row, int phase, double massPerLength, double velocity) {
    int[] mass = {TwoFluidConservationEquations.IDX_GAS_MASS, TwoFluidConservationEquations.IDX_OIL_MASS,
        TwoFluidConservationEquations.IDX_WATER_MASS};
    int[] momentum = {TwoFluidConservationEquations.IDX_GAS_MOMENTUM, TwoFluidConservationEquations.IDX_OIL_MOMENTUM,
        TwoFluidConservationEquations.IDX_WATER_MOMENTUM};
    row[mass[phase]] = massPerLength;
    row[momentum[phase]] = massPerLength * velocity;
  }

  /**
   * Deep-copy a state.
   *
   * @param state state rows
   * @return independent copy
   */
  private static double[][] copy(double[][] state) {
    double[][] result = new double[state.length][];
    for (int row = 0; row < state.length; row++) {
      result[row] = state[row].clone();
    }
    return result;
  }
}
