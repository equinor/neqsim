package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.TwoFluidComponentConservationReport.Phase;
import neqsim.process.equipment.pipeline.twophasepipe.NonEquilibriumFilmTransfer;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.TimeIntegrator;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.process.equipment.pipeline.twophasepipe.TwoFluidComponentTransport;
import neqsim.process.equipment.pipeline.twophasepipe.TwoFluidSection;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;

/**
 * Non-equilibrium MEG/water transfer and hydrodynamic coupling regressions for issue 4281.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class TwoFluidPipeFilmDryingTest {
  private static final Logger logger = LogManager.getLogger(TwoFluidPipeFilmDryingTest.class);

  /** Check simultaneous absorption and evaporation in the public post-pigging case. */
  @Test
  void wetGasAbsorbsWaterWhileMegEvaporates() {
    NonEquilibriumFilmTransfer closure = closure(0.001);
    double[] flux = closure.gasMassFlux(gas(), liquid());
    logger.info("Initial water/MEG gas-directed fluxes: {} / {} kg/(m2 s)", flux[0], flux[1]);
    assertTrue(flux[0] < 0.0, "35.54 ppm water gas must wet the initially 99 wt% MEG film");
    assertTrue(flux[1] > 0.0, "MEG must evaporate into initially MEG-free gas");
    assertArrayEquals(new double[2], closure(0.0).gasMassFlux(gas(), liquid()), 0.0);
  }

  /** Check component ledgers, independent initial inventory, and constant temperature. */
  @Test
  void closedPipeConservesComponentsWithOpposingTransfer() {
    TwoFluidPipe pipe = pipe(4, 0.001, false);
    pipe.setInletBoundaryCondition(TwoFluidPipe.BoundaryCondition.CLOSED);
    pipe.setOutletBoundaryCondition(TwoFluidPipe.BoundaryCondition.CLOSED);
    double[] initial = pipe.getLastComponentConservationReport().getFinalInventoryKg();
    double[] temperature = pipe.getTemperatureProfile().clone();
    pipe.runTransient(0.01, UUID.randomUUID());
    TwoFluidComponentConservationReport report = pipe.getLastComponentConservationReport();
    assertTrue(report.isConverged(), report.getMessage());
    assertArrayEquals(initial, report.getFinalInventoryKg(), 1.0e-9);
    assertTrue(report.getInterphaseTransferKg(Phase.WATER, "water") > 0.0);
    assertTrue(report.getInterphaseTransferKg(Phase.WATER, "MEG") < 0.0);
    assertArrayEquals(temperature, pipe.getTemperatureProfile(), 0.0);
    assertEquals(0.01, pipe.getLastMassBalanceReport().getElapsedTimeSeconds(), 1.0e-14);
  }

  /** Check open-boundary component transport and deterministic serialization. */
  @Test
  void pooledInventoryHasConservativeMovingLiquidAndRepeatableHistory() {
    TwoFluidPipe pipe = pipe(4, 0.001, true);
    pipe.setStoreComponentConservationHistory(true);
    pipe.setTransactionalTransientEnabled(true);
    TwoFluidPipe copy = (TwoFluidPipe) pipe.copy();
    pipe.runTransient(0.01, UUID.randomUUID());
    copy.runTransient(0.01, UUID.randomUUID());
    TwoFluidComponentConservationReport report = pipe.getLastComponentConservationReport();
    assertTrue(report.isConverged(), report.getMessage());
    assertArrayEquals(report.getFinalInventoryKg(), copy.getLastComponentConservationReport().getFinalInventoryKg(),
        1.0e-11);
    double moving = 0.0;
    for (double velocity : pipe.getWaterVelocityProfile()) {
      moving += Math.abs(velocity);
    }
    assertTrue(moving > 0.0, "Aqueous momentum must evolve; the residual liquid is not pinned to the wall");
    assertTrue(pipe.getEquivalentAqueousFilmThicknessProfile()[1] > pipe.getEquivalentAqueousFilmThicknessProfile()[0]);
  }

  /** A seeded low-point pool remobilizes over multiple accepted conservative intervals. */
  @Test
  void seededLowPointRemobilizesOverThreeSeconds() {
    TwoFluidPipe pipe = pipe(4, 0.001, true);
    pipe.setTransactionalTransientEnabled(true);
    double initialPool = pipe.getWaterHoldupProfile()[1];
    double waterTransfer = 0.0;
    double megTransfer = 0.0;
    for (int step = 0; step < 30; step++) {
      pipe.runTransient(0.1, UUID.randomUUID());
      TwoFluidComponentConservationReport report = pipe.getLastComponentConservationReport();
      assertTrue(report.isConverged(), report.getMessage());
      waterTransfer += report.getInterphaseTransferKg(Phase.WATER, "water");
      megTransfer += report.getInterphaseTransferKg(Phase.WATER, "MEG");
    }
    assertTrue(pipe.getWaterHoldupProfile()[1] < initialPool);
    assertTrue(waterTransfer > 0.0);
    assertTrue(megTransfer < 0.0);
    logger.info("Three-second seeded pool: holdup {} to {}, water/MEG transfer {} / {} kg", initialPool,
        pipe.getWaterHoldupProfile()[1], waterTransfer, megTransfer);
  }

  /** At CPA phase equilibrium both configured fluxes vanish; wetter liquid reverses water transfer. */
  @Test
  void equilibriumAndReversedWaterDrivingForce() {
    SystemInterface mixture = new SystemSrkCPAstatoil(278.15, 70.0);
    mixture.addComponent("methane", 100.0);
    mixture.addComponent("water", 1.0 / 0.01801528);
    mixture.addComponent("MEG", 99.0 / 0.0620678);
    mixture.setMixingRule(10);
    mixture.setMultiPhaseCheck(true);
    new ThermodynamicOperations(mixture).TPflash();
    mixture.initProperties();
    SystemInterface gas = mixture.phaseToSystem("gas");
    SystemInterface liquid = mixture.phaseToSystem("aqueous");
    gas.initProperties();
    liquid.initProperties();
    assertArrayEquals(new double[2], closure(0.001).gasMassFlux(gas, liquid), 1.0e-10);
    SystemInterface wetter = fluid(0.0, 20.0 / 0.01801528, 80.0 / 0.0620678, PhaseType.AQUEOUS);
    assertTrue(closure(0.001).gasMassFlux(gas(), wetter)[0] > 0.0);
  }

  /** Arbitrarily fast transfer cannot withdraw more than half of any donor component. */
  @Test
  void donorLimitAndAbsentFilmRemainConservative() {
    TwoFluidPipe pipe = pipe(4, 0.001, false);
    TwoFluidSection[] sections = pipe.getSectionSnapshots();
    TwoFluidComponentTransport transport = new TwoFluidComponentTransport(gas(), sections, liquid());
    double[][][] inventory = transport.getInventoryKg();
    double[][][] sources = closure(1.0e12).calculate(transport, sections, gas(), 2.0);
    for (int cell = 0; cell < sections.length; cell++) {
      for (int component = 0; component < inventory[cell][0].length; component++) {
        double transfer = sources[cell][0][component] * sections[cell].getLength() * 2.0;
        int donor = transfer > 0.0 ? 2 : 0;
        assertTrue(Math.abs(transfer) <= 0.500000000001 * inventory[cell][donor][component]);
        assertEquals(-sources[cell][0][component], sources[cell][2][component], 0.0);
      }
      sections[cell].setWaterMassPerLength(0.0);
    }
    double[][][] absent = closure(1.0e12).calculate(transport, sections, gas(), 2.0);
    for (double[][] cell : absent) {
      assertArrayEquals(new double[3], cell[0], 0.0);
    }
    assertArrayEquals(inventory[0][2], transport.getInventoryKg()[0][2], 0.0);
  }

  /** Short-time space/time refinement must preserve the CPA transfer response and mass ledgers. */
  @Test
  void transferIsStableUnderGridAndTimeRefinement() {
    double coarse = absorbedWater(4, 0.02, 1);
    double timeRefined = absorbedWater(4, 0.01, 2);
    double spaceRefined = absorbedWater(8, 0.01, 2);
    assertTrue(coarse > 0.0);
    assertEquals(coarse, timeRefined, 0.01 * coarse);
    assertEquals(coarse, spaceRefined, 0.02 * coarse);
  }

  /** The requested 10 km geometry can initialize and accept a short conservative transient. */
  @Test
  void tenKilometreInitialFilmAcceptsConservativeTransient() {
    TwoFluidPipe pipe = pipe(8, 0.001, false);
    pipe.setLength(10000.0);
    pipe.run();
    assertEquals(TwoFluidPipe.FilmDryingStatus.RUNNING, pipe.getFilmDryingStatus(1.0e-6, 0.1));
    pipe.runTransient(0.1, UUID.randomUUID());
    assertEquals(TwoFluidPipe.FilmDryingStatus.HORIZON_REACHED, pipe.getFilmDryingStatus(1.0e-6, 0.1));
    double massBeforeStatus = pipe.getTotalMassInventory();
    assertEquals(TwoFluidPipe.FilmDryingStatus.RESIDUAL_THRESHOLD_REACHED,
        pipe.getFilmDryingStatus(massBeforeStatus, 0.1));
    assertEquals(massBeforeStatus, pipe.getTotalMassInventory(), 0.0);
    assertThrows(IllegalArgumentException.class, () -> pipe.getFilmDryingStatus(0.0, 0.1));
    assertTrue(pipe.getLastComponentConservationReport().isConverged());
    assertTrue(pipe.getLastComponentConservationReport().getInterphaseTransferKg(Phase.WATER, "water") > 0.0);
    assertTrue(pipe.getLastComponentConservationReport().getInterphaseTransferKg(Phase.WATER, "MEG") < 0.0);
    assertEquals(0.1, pipe.getLastMassBalanceReport().getElapsedTimeSeconds(), 1.0e-13);
  }

  /**
   * Integrate a short closed-pipe absorption history.
   *
   * @param cells physical cell count
   * @param dt outer time step in seconds
   * @param steps number of outer steps
   * @return cumulative absorbed water mass, kg
   */
  private double absorbedWater(int cells, double dt, int steps) {
    TwoFluidPipe pipe = pipe(cells, 0.001, false);
    pipe.setInletBoundaryCondition(TwoFluidPipe.BoundaryCondition.CLOSED);
    pipe.setOutletBoundaryCondition(TwoFluidPipe.BoundaryCondition.CLOSED);
    double transfer = 0.0;
    for (int i = 0; i < steps; i++) {
      pipe.runTransient(dt, UUID.randomUUID());
      TwoFluidComponentConservationReport report = pipe.getLastComponentConservationReport();
      assertTrue(report.isConverged(), report.getMessage());
      transfer += report.getInterphaseTransferKg(Phase.WATER, "water");
    }
    return transfer;
  }

  /** Unsupported energy and multi-stage configurations fail explicitly. */
  @Test
  void unsupportedConfigurationsReject() {
    TwoFluidPipe pipe = pipe(4, 0.001, false);
    pipe.setTimeIntegrationMethod(TimeIntegrator.Method.RK4);
    assertThrows(IllegalStateException.class, () -> pipe.runTransient(0.01, UUID.randomUUID()));
    assertThrows(IllegalArgumentException.class,
        () -> new NonEquilibriumFilmTransfer(new String[] {"water"}, new double[] {-1.0}, 1.0));
  }

  /**
   * Build a reproducible synthetic residual-film/low-point example.
   *
   * @param cells cell count
   * @param coefficient imposed overall gas-side resistance coefficient, m/s
   * @param terrain whether to include a low point and a larger initial pool
   * @return initialized pipe
   */
  private TwoFluidPipe pipe(int cells, double coefficient, boolean terrain) {
    Stream inlet = new Stream("dry methane", gas());
    inlet.setFlowRate(0.5, "kg/sec");
    inlet.run();
    TwoFluidPipe pipe = new TwoFluidPipe("post-pigging", inlet);
    pipe.setLength(100.0);
    pipe.setDiameter(0.2);
    pipe.setNumberOfSections(cells);
    pipe.setSteadyStateMaxWallClockTime(5.0);
    pipe.setEnableSlugTracking(false);
    pipe.setComponentTransportEnabled(true);
    pipe.setIncludeMassTransfer(true);
    pipe.setTimeIntegrationMethod(TimeIntegrator.Method.EULER);
    pipe.setCflNumber(0.25);
    pipe.setNonEquilibriumFilmTransfer(closure(coefficient));
    double[] holdup = new double[cells];
    java.util.Arrays.fill(holdup, 0.002);
    if (terrain) {
      pipe.setCellFaceElevationProfile(new double[] {0.0, -0.1, -0.1, 0.0, 0.0});
      holdup[1] = 0.02;
    }
    pipe.setInitialAqueousFilm(liquid(), holdup);
    pipe.run();
    return pipe;
  }

  /**
   * Make a two-species transfer closure with prescribed full-wall contact area.
   *
   * @param coefficient overall gas-side coefficient in m/s
   * @return closure
   */
  private NonEquilibriumFilmTransfer closure(double coefficient) {
    return new NonEquilibriumFilmTransfer(new String[] {"water", "MEG"}, new double[] {coefficient, coefficient}, 1.0);
  }

  /** @return homogeneous methane at 5 C, 70 bara, with the issue's CPA water content */
  private SystemInterface gas() {
    SystemInterface gas = fluid(1.0 - 35.54e-6, 35.54e-6, 0.0, PhaseType.GAS);
    gas.setForcePhaseTypes(false);
    gas.setMaxNumberOfPhases(2);
    new ThermodynamicOperations(gas).TPflash();
    gas.initProperties();
    return gas;
  }

  /** @return independently initialized 99/1 wt% MEG/water liquid */
  private SystemInterface liquid() {
    return fluid(0.0, 1.0 / 0.01801528, 99.0 / 0.0620678, PhaseType.AQUEOUS);
  }

  /**
   * Construct a homogeneous CPA phase without a bulk equilibrium flash.
   *
   * @param methane methane amount in mol
   * @param water water amount in mol
   * @param meg MEG amount in mol
   * @param phase forced phase identity
   * @return initialized fluid
   */
  private SystemInterface fluid(double methane, double water, double meg, PhaseType phase) {
    SystemInterface fluid = new SystemSrkCPAstatoil(278.15, 70.0);
    fluid.addComponent("methane", methane);
    fluid.addComponent("water", water);
    fluid.addComponent("MEG", meg);
    fluid.setMixingRule(10);
    fluid.setNumberOfPhases(1);
    fluid.setMaxNumberOfPhases(1);
    fluid.setForcePhaseTypes(true);
    fluid.init(0);
    fluid.setNumberOfPhases(1);
    fluid.setBeta(0, 1.0);
    fluid.setPhaseType(0, phase);
    fluid.initProperties();
    return fluid;
  }
}
