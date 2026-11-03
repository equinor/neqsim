package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.TwoFluidMassBalanceReport.Phase;
import neqsim.process.equipment.pipeline.twophasepipe.HydrocarbonPoolTransfer;
import neqsim.process.equipment.pipeline.twophasepipe.TwoFluidSection;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.TimeIntegrator;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Conservative first-oil-appearance and declared-parameter cycle qualification for the public seabed rich-gas fixture.
 *
 * <p>
 * The full-cycle regression qualifies conservation and mesh behaviour for its declared coefficients. It does not
 * experimentally validate evaporation rates or certify physical shutdown/restart behaviour.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
@Tag("slow")
class TwoFluidRichGasDragQualificationTest {
  private static final Logger logger = LogManager.getLogger(TwoFluidRichGasDragQualificationTest.class);

  /**
   * Complete the public 780 s blowdown, low-pressure settling, same-gas refill and hold sequence with explicit pool
   * kinetics.
   *
   * <p>
   * This is a numerical conservation/full-cycle regression for declared coefficients. It is not an experimental
   * evaporation-rate validation.
   * </p>
   */
  @Test
  void finiteRatePoolCompletesBlowdownSettleRefillAndHold() throws java.io.IOException {
    double[] coarse = runFiniteRatePoolCycle(12, 0.5);
    double[] refined = runFiniteRatePoolCycle(24, 0.5);
    double relativeFinalOilDifference = Math.abs(coarse[1] - refined[1]) / Math.max(coarse[1], refined[1]);
    assertTrue(relativeFinalOilDifference < 1.0,
        "The two full-cycle meshes must retain finite oil inventories of the same order");
    logger.info("Finite-rate 780 s cycle: 12/24-cell peak oil={}/{}, final oil={}/{}, difference={} %", coarse[0],
        refined[0], coarse[1], refined[1], 100.0 * relativeFinalOilDifference);
    String csv = "cells,dt_s,peak_oil_kg,final_oil_kg\n12,0.5," + coarse[0] + "," + coarse[1] + "\n24,0.5," + refined[0]
        + "," + refined[1] + "\n";
    java.nio.file.Files.write(java.nio.file.Paths.get("target/hydrocarbon-pool-cycle-refinement.csv"),
        csv.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  /** Run one complete declared-parameter cycle and return peak/final oil inventory. */
  private static double[] runFiniteRatePoolCycle(int cells, double dt) {
    String[] names = {"methane", "ethane", "propane", "n-butane", "n-pentane", "n-heptane"};
    double[] coefficients = {0.05, 0.05, 0.05, 0.05, 0.05, 0.05};
    double[] evaporationEnthalpies = {5.1e5, 4.9e5, 4.3e5, 3.8e5, 3.5e5, 3.2e5};
    TwoFluidPipe pipe = createRichGasPipe(cells, true);
    pipe.setGasOnlyOutletTakeoff(true);
    pipe.setHydrocarbonPoolTransfer(
        new HydrocarbonPoolTransfer(names, coefficients, evaporationEnthalpies, 0.5, Double.POSITIVE_INFINITY));
    double initialMass = pipe.getTotalMassInventory();
    double cumulativeBoundaryMass = 0.0;
    double maximumOil = 0.0;
    boolean observedRefill = false;

    int rampSteps = (int) Math.round(120.0 / dt);
    for (int step = 1; step <= rampSteps; step++) {
      pipe.setOutletPressure(150.0 - 140.0 * step / rampSteps, "bara");
      pipe.runTransient(dt, null);
      cumulativeBoundaryMass += acceptedBoundaryMass(pipe);
      maximumOil = Math.max(maximumOil, pipe.getLastMassBalanceReport().getFinalMassKg(Phase.OIL));
      assertAcceptedPoolInterval(pipe);
    }

    pipe.setGasOnlyOutletTakeoff(false);
    pipe.closeOutlet();
    for (int step = 0; step < (int) Math.round(120.0 / dt); step++) {
      pipe.runTransient(dt, null);
      cumulativeBoundaryMass += acceptedBoundaryMass(pipe);
      maximumOil = Math.max(maximumOil, pipe.getLastMassBalanceReport().getFinalMassKg(Phase.OIL));
      assertEquals(0.0, pipe.getLastMassBalanceReport().getOutletMassKg(Phase.TOTAL), 0.0);
      assertAcceptedPoolInterval(pipe);
    }

    pipe.setInletBoundaryCondition(TwoFluidPipe.BoundaryCondition.CONSTANT_FLOW);
    pipe.setInletMassFlow(1000.0, "kg/sec");
    for (int step = 0; step < (int) Math.round(420.0 / dt); step++) {
      pipe.runTransient(dt, null);
      double boundaryMass = acceptedBoundaryMass(pipe);
      cumulativeBoundaryMass += boundaryMass;
      observedRefill |= pipe.getLastMassBalanceReport().getInletMassKg(Phase.TOTAL) > 0.0;
      maximumOil = Math.max(maximumOil, pipe.getLastMassBalanceReport().getFinalMassKg(Phase.OIL));
      assertEquals(0.0, pipe.getLastMassBalanceReport().getOutletMassKg(Phase.TOTAL), 0.0);
      assertAcceptedPoolInterval(pipe);
    }

    pipe.closeInlet();
    for (int step = 0; step < (int) Math.round(120.0 / dt); step++) {
      pipe.runTransient(dt, null);
      double boundaryMass = acceptedBoundaryMass(pipe);
      cumulativeBoundaryMass += boundaryMass;
      maximumOil = Math.max(maximumOil, pipe.getLastMassBalanceReport().getFinalMassKg(Phase.OIL));
      assertEquals(0.0, pipe.getLastMassBalanceReport().getOutletMassKg(Phase.TOTAL), 0.0);
      assertAcceptedPoolInterval(pipe);
    }

    assertEquals(780.0, pipe.getSimulationTime(), 1.0e-9);
    assertTrue(maximumOil > 0.0, "The full cycle must exercise a hydrocarbon-liquid pool");
    assertTrue(observedRefill, "The original same-gas inlet donor must fund actual inlet refill");
    assertEquals(initialMass + cumulativeBoundaryMass, pipe.getTotalMassInventory(), 1.0e-5);
    return new double[] {maximumOil, pipe.getLastMassBalanceReport().getFinalMassKg(Phase.OIL)};
  }

  /** Positive is inventory entering through either boundary; negative is inventory leaving. */
  private static double acceptedBoundaryMass(TwoFluidPipe pipe) {
    TwoFluidMassBalanceReport report = pipe.getLastMassBalanceReport();
    return report.getInletMassKg(Phase.TOTAL) - report.getOutletMassKg(Phase.TOTAL);
  }

  /** Assert all conservative owners close for one accepted interval. */
  private static void assertAcceptedPoolInterval(TwoFluidPipe pipe) {
    assertTrue(pipe.getLastComponentConservationReport().isConverged(),
        pipe.getLastComponentConservationReport().getMessage());
    assertTrue(pipe.getLastConservativeEnergyReport().isWithinTolerance(1.0e-3, 1.0e-7));
    assertTrue(pipe.getLastHydrocarbonPoolTransferResult() != null);
    for (Phase phase : Phase.values()) {
      assertTrue(pipe.getLastMassBalanceReport().isWithinTolerance(phase, 1.0e-6, 1.0e-8));
    }
  }

  /**
   * Conserved component targets must complete the entire requested pressure ramp on both historical meshes.
   */
  @Test
  void originalTerrainCompletesPressureRampWithConservedComponentTargets() {
    for (int cells : new int[] {12, 24}) {
      double dt = cells == 12 ? 0.25 : 0.125;
      TwoFluidPipe pipe = createRichGasPipe(cells, false);
      for (int step = 1; step <= (int) Math.round(120.0 / dt); step++) {
        double elapsed = step * dt;
        pipe.setOutletPressure(150.0 - 140.0 * elapsed / 120.0, "bara");
        pipe.runTransient(dt, null);
        assertEquals(elapsed, pipe.getSimulationTime(), 1e-9);
        assertFalse(pipe.isTransientOutletBackflowClamped());
        assertTrue(pipe.getLastComponentConservationReport().isConverged());
        assertTrue(pipe.getLastConservativeEnergyReport().isWithinTolerance(1e-3, 1e-7));
        for (Phase phase : Phase.values()) {
          assertTrue(pipe.getLastMassBalanceReport().isWithinTolerance(phase, 1e-6, 1e-8));
        }
      }
    }
  }

  /** Cross the former trace-drag rejection on the original terrain. */
  @Test
  void originalTerrainCrossesConservativeFirstOilAppearance() {
    qualify(12, false, 0.125);
  }

  /** Cross the original wall/interface impulse with the historical external interval. */
  @Test
  void levelOutletCrossesConservativeFirstOilAppearanceAtOriginalInterval() {
    qualify(12, true, 0.25);
  }

  /** Cross the exact absent-momentum rejection on a refined terrain mesh. */
  @Test
  void refinedLevelOutletCrossesConservativeFirstOilAppearance() {
    qualify(24, true, 0.125);
  }

  /**
   * An explicit same-gas downstream reservoir must fund signed fallback without inventing component or energy content.
   */
  @Test
  void levelOutletAdvancesAcrossSignedFallbackWithExplicitThermodynamicDonor() {
    TwoFluidPipe pipe = createRichGasPipe(12, true);
    pipe.setOutletBoundaryFluid(pipe.getInletStream().getFluid());
    pipe.setAllowOutletPhaseBackflow(true);
    boolean observedSignedFallback = false;
    double dt = 0.25;
    int steps = (int) Math.round(40.0 / dt);
    for (int step = 1; step <= steps; step++) {
      double elapsed = step * dt;
      pipe.setOutletPressure(150.0 - 140.0 * elapsed / 120.0, "bara");
      pipe.runTransient(dt, null);
      assertEquals(elapsed, pipe.getSimulationTime(), 1.0e-9);
      TwoFluidComponentConservationReport components = pipe.getLastComponentConservationReport();
      assertTrue(components.isConverged(), components.getMessage());
      observedSignedFallback |= sum(components.getOutletBoundaryMassKg()) < 0.0;
      TwoFluidConservativeEnergyReport energy = pipe.getLastConservativeEnergyReport();
      assertTrue(energy.isWithinTolerance(1.0e-3, 1.0e-7));
    }
    double startingPressureBara = 150.0 - 140.0 * 40.0 / 120.0;
    for (int step = 1; step <= 16; step++) {
      double elapsed = 40.0 + step * dt;
      pipe.setOutletPressure(startingPressureBara + (160.0 - startingPressureBara) * step / 16.0, "bara");
      pipe.runTransient(dt, null);
      assertEquals(elapsed, pipe.getSimulationTime(), 1.0e-9);
      TwoFluidComponentConservationReport components = pipe.getLastComponentConservationReport();
      assertTrue(components.isConverged(), components.getMessage());
      observedSignedFallback |= sum(components.getOutletBoundaryMassKg()) < 0.0;
      assertTrue(pipe.getLastConservativeEnergyReport().isWithinTolerance(1.0e-3, 1.0e-7));
    }
    assertTrue(observedSignedFallback, "The fixture must exercise rather than merely configure reversed outlet flow");
  }

  /**
   * A gas takeoff must depressurize through gas while retaining every appeared liquid component at the external face.
   */
  @Test
  void gasOnlyTakeoffRetainsAppearedLiquidThroughPressureRamp() {
    double[] coarse = qualifyGasOnlyTakeoff(0.25);
    double[] refined = qualifyGasOnlyTakeoff(0.125);
    double relativeGasDifference = Math.abs(coarse[1] - refined[1]) / refined[1];

    assertTrue(relativeGasDifference < 0.01,
        "Cumulative gas discharge must change by less than 1% when the interval is halved");
    logger.info(
        "Gas-only rich-gas ramp: elapsed=33 s, coarse/refined peakOil={}/{} kg, "
            + "coarse/refined cumulativeGasOutlet={}/{} kg, gas difference={} %",
        coarse[0], refined[0], coarse[1], refined[1], 100.0 * relativeGasDifference);
  }

  /**
   * Run one gas-only boundary qualification interval.
   *
   * @param dt requested external interval in seconds
   * @return peak oil inventory and cumulative gas outlet mass, both in kg
   */
  private static double[] qualifyGasOnlyTakeoff(double dt) {
    TwoFluidPipe pipe = createRichGasPipe(12, true);
    pipe.setGasOnlyOutletTakeoff(true);
    double maximumOilKg = 0.0;
    double cumulativeGasOutletKg = 0.0;
    int steps = (int) Math.round(33.0 / dt);
    for (int step = 1; step <= steps; step++) {
      double elapsed = step * dt;
      pipe.setOutletPressure(150.0 - 140.0 * elapsed / 120.0, "bara");
      pipe.runTransient(dt, null);
      TwoFluidMassBalanceReport mass = pipe.getLastMassBalanceReport();
      TwoFluidComponentConservationReport components = pipe.getLastComponentConservationReport();
      TwoFluidConservativeEnergyReport energy = pipe.getLastConservativeEnergyReport();

      assertEquals(0.0, mass.getOutletMassKg(Phase.OIL), 0.0);
      assertEquals(0.0, mass.getOutletMassKg(Phase.WATER), 0.0);
      assertEquals(0.0, sum(components.getOutletBoundaryMassKg(TwoFluidComponentConservationReport.Phase.OIL)), 0.0);
      assertEquals(0.0, sum(components.getOutletBoundaryMassKg(TwoFluidComponentConservationReport.Phase.WATER)), 0.0);
      assertEquals(0.0, energy.getOutletAdvectedEnergyJ(TwoFluidConservativeEnergyReport.Phase.OIL), 0.0);
      assertEquals(0.0, energy.getOutletAdvectedEnergyJ(TwoFluidConservativeEnergyReport.Phase.WATER), 0.0);
      assertTrue(components.isConverged(), components.getMessage());
      assertTrue(energy.isWithinTolerance(1.0e-3, 1.0e-7));
      maximumOilKg = Math.max(maximumOilKg, mass.getFinalMassKg(Phase.OIL));
      cumulativeGasOutletKg += mass.getOutletMassKg(Phase.GAS);
    }
    assertTrue(maximumOilKg > 0.0, "The retention test must cross actual oil appearance");
    assertTrue(cumulativeGasOutletKg > 0.0, "The pressure ramp must discharge gas rather than seal the pipe");
    return new double[] {maximumOilKg, cumulativeGasOutletKg};
  }

  /**
   * Advance through the former 31.75--32.375 second rejection with all accepted ledgers intact.
   *
   * @param cells finite-volume cell count
   * @param levelOutlet whether to use the physical horizontal outlet segment
   * @param dt requested external interval in seconds
   */
  private static void qualify(int cells, boolean levelOutlet, double dt) {
    TwoFluidPipe pipe = createRichGasPipe(cells, levelOutlet);
    double maximumOilKg = 0.0;
    int steps = (int) Math.round(33.0 / dt);
    for (int step = 1; step <= steps; step++) {
      double elapsed = step * dt;
      pipe.setOutletPressure(150.0 - 140.0 * elapsed / 120.0, "bara");
      pipe.runTransient(dt, null);
      assertEquals(elapsed, pipe.getSimulationTime(), 1.0e-9);
      assertTrue(pipe.isCoupledPressureMomentumConverged());
      assertFalse(pipe.isTransientOutletBackflowClamped());
      TwoFluidMassBalanceReport mass = pipe.getLastMassBalanceReport();
      for (Phase phase : Phase.values()) {
        assertTrue(mass.isWithinTolerance(phase, 1.0e-6, 1.0e-8),
            phase + " residual at " + elapsed + " s: " + mass.getResidualKg(phase));
      }
      assertTrue(pipe.getLastComponentConservationReport().isConverged(),
          pipe.getLastComponentConservationReport().getMessage());
      TwoFluidConservativeEnergyReport energy = pipe.getLastConservativeEnergyReport();
      assertTrue(energy.getMaxRelativeVolumeResidual() < 1.0e-7);
      assertTrue(energy.getMaxRelativeThermodynamicEnergyResidual() < 1.0e-7);
      assertTrue(energy.isWithinTolerance(1.0e-3, 1.0e-7));
      maximumOilKg = Math.max(maximumOilKg, mass.getFinalMassKg(Phase.OIL));
      for (TwoFluidSection section : pipe.getSectionSnapshots()) {
        double[] state = section.getStateVector();
        for (int phase = 0; phase < 3; phase++) {
          assertTrue(state[phase] >= 0.0);
          if (state[phase] == 0.0) {
            assertEquals(0.0, state[phase + 3], 0.0);
          } else {
            assertTrue(Double.isFinite(state[phase + 3] / state[phase]));
          }
        }
      }
    }
    assertTrue(maximumOilKg > 0.0, "The fixture must enter actual oil appearance");
  }

  /**
   * Construct and prepare the accepted synthetic steady state without adding seed liquid.
   *
   * @param cells physical cell count
   * @param levelOutlet whether to level the final three face elevations
   * @return converged closed-inlet conservative-energy pipe
   */
  private static TwoFluidPipe createRichGasPipe(int cells, boolean levelOutlet) {
    SystemSrkEos fluid = new SystemSrkEos(277.15, 150.0);
    fluid.addComponent("methane", 0.916);
    fluid.addComponent("ethane", 0.050);
    fluid.addComponent("propane", 0.020);
    fluid.addComponent("n-butane", 0.007);
    fluid.addComponent("n-pentane", 0.003);
    fluid.addComponent("n-heptane", 0.004);
    fluid.setMixingRule("classic");
    Stream inlet = new Stream("synthetic rich-gas appearance feed", fluid);
    inlet.setFlowRate(0.001, "kg/sec");
    inlet.run();
    assertEquals(1, inlet.getFluid().getNumberOfPhases());

    TwoFluidPipe pipe = new TwoFluidPipe("synthetic source-funded oil appearance", inlet);
    pipe.setLength(5000.0);
    pipe.setDiameter(42.0 * 0.0254 - 2.0 * 0.026);
    pipe.setRoughness(1e-5);
    pipe.setNumberOfSections(cells);
    double[] faceElevationsMetres = new double[cells + 1];
    for (int face = 0; face < faceElevationsMetres.length; face++) {
      double firstWave = Math.sin(Math.PI * face / cells);
      double thirdWave = Math.sin(3.0 * Math.PI * face / cells);
      faceElevationsMetres[face] = -300.0 - 55.0 * firstWave * firstWave - 12.0 * thirdWave * thirdWave;
    }
    if (levelOutlet) {
      for (int face = cells - 2; face <= cells; face++) {
        faceElevationsMetres[face] = faceElevationsMetres[cells - 3];
      }
    }
    pipe.setCellFaceElevationProfile(faceElevationsMetres);
    pipe.setTimeIntegrationMethod(TimeIntegrator.Method.IMEX_PRESSURE_CORRECTION);
    pipe.setEnableSlugTracking(false);
    pipe.setEnableAdaptiveTimestepping(true);
    pipe.setEnableCoupledPressureMomentum(true);
    pipe.setEnableInterfacialPressure(true);
    pipe.setImplicitInterfacialPressureCoupling(true);
    pipe.setCflNumber(0.3);
    pipe.setMaximumTransientSubsteps(500);
    pipe.setComponentTransportEnabled(true);
    pipe.setStoreComponentConservationHistory(false);
    pipe.setIncludeMassTransfer(true);
    pipe.setMassTransferRelaxationTime(30.0);
    pipe.setThermodynamicUpdateInterval(1);
    pipe.setIncludeEnergyEquation(false);
    pipe.setEnableJouleThomson(false);
    pipe.setHeatTransferCoefficient(0.0);
    pipe.setSurfaceTemperature(277.15, "K");
    pipe.setOutletPressure(150.0, "bara");
    pipe.setSteadyStateMaxWallClockTime(Double.POSITIVE_INFINITY);
    pipe.run();
    pipe.prepareConservativeEnergyTransientFromSteadyState();
    pipe.closeInlet();
    pipe.setMassTransferRelaxationTime(30.0);
    return pipe;
  }

  /** Sum a component boundary vector. */
  private static double sum(double[] values) {
    double total = 0.0;
    for (double value : values) {
      total += value;
    }
    return total;
  }
}
