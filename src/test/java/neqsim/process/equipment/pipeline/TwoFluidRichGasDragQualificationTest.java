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
import neqsim.process.equipment.pipeline.twophasepipe.reporting.TwoFluidPipeReport;
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

  /** Refinement must preserve the terrain and the physical length of the level outlet. */
  @Test
  void levelOutletUsesTheSamePhysicalGeometryOnEveryMesh() {
    double[] coarse = createRichGasFaceElevations(12, true);
    double[] refined = createRichGasFaceElevations(24, true);
    double[] finer = createRichGasFaceElevations(48, true);
    for (int face = 0; face < coarse.length; face++) {
      assertEquals(coarse[face], refined[2 * face], 0.0);
      assertEquals(coarse[face], finer[4 * face], 0.0);
      if (face < coarse.length - 1) {
        assertEquals(0.5 * (coarse[face] + coarse[face + 1]), refined[2 * face + 1], 1.0e-12);
      }
    }
    for (int face = 9; face <= 12; face++) {
      assertEquals(-333.5, coarse[face], 1.0e-12);
    }
  }

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
    double relativePeakOilDifference = Math.abs(coarse[0] - refined[0]) / Math.max(coarse[0], refined[0]);
    double relativeFinalOilDifference = Math.abs(coarse[1] - refined[1]) / Math.max(coarse[1], refined[1]);
    String csv = "cells,dt_s,heat_supply_w_per_m,peak_oil_kg,final_oil_kg,minimum_heat_scale,"
        + "maximum_interface_area_m2,maximum_wall_temperature_change_k,trace_area_fallback,"
        + "refill_pressure_peak_to_peak_pa,refill_max_pressure_rate_pa_s,refill_excess_variation_pa,"
        + "refill_ringing_ratio,refill_direction_reversals\n12,0.5,100," + csvValues(coarse) + "\n24,0.5,100,"
        + csvValues(refined) + "\n";
    java.nio.file.Files.write(java.nio.file.Paths.get("target/hydrocarbon-pool-cycle-refinement.csv"),
        csv.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    assertTrue(relativePeakOilDifference < 0.1,
        "The two full-cycle meshes must agree within the predeclared 10% peak-oil spatial gate; observed="
            + relativePeakOilDifference);
    assertTrue(relativeFinalOilDifference < 0.1,
        "The two full-cycle meshes must agree within the predeclared 10% final-oil spatial gate; observed="
            + relativeFinalOilDifference);
    logger.info("Finite-rate 780 s cycle: 12/24-cell peak oil={}/{}, final oil={}/{}, difference={} %", coarse[0],
        refined[0], coarse[1], refined[1], 100.0 * relativeFinalOilDifference);
    assertTrue(coarse[3] > 0.0 && refined[3] > 0.0,
        "The resolved hydrostatic pool must expose a finite gas/oil interface");
    assertTrue(coarse[4] > 0.0 && refined[4] > 0.0,
        "The wall thermal inventory must respond during the accepted event cycle");
    assertEquals(0.0, coarse[5], 0.0, "The coarse cycle must resolve every accepted trace wedge");
    assertEquals(0.0, refined[5], 0.0, "The refined cycle must resolve every accepted trace wedge");
    for (int metric = 6; metric < coarse.length; metric++) {
      assertTrue(Double.isFinite(coarse[metric]) && Double.isFinite(refined[metric]));
    }
  }

  /** Run one complete declared-parameter cycle and return peak/final oil inventory. */
  private static double[] runFiniteRatePoolCycle(int cells, double dt) {
    String[] names = {"methane", "ethane", "propane", "n-butane", "n-pentane", "n-heptane"};
    double[] coefficients = {0.05, 0.05, 0.05, 0.05, 0.05, 0.05};
    double[] evaporationEnthalpies = {5.1e5, 4.9e5, 4.3e5, 3.8e5, 3.5e5, 3.2e5};
    TwoFluidPipe pipe = createRichGasPipe(cells, true);
    pipe.setGasOnlyOutletTakeoff(true);
    pipe.setWallProperties(0.026, 7850.0, 500.0);
    pipe.setHeatTransferCoefficient(4.0);
    pipe.setSurfaceTemperature(277.15, "K");
    pipe.setHydrocarbonPoolTransfer(
        HydrocarbonPoolTransfer.forStratifiedFlow(names, coefficients, evaporationEnthalpies, 100.0));
    double[] initialWallTemperature = pipe.getWallTemperatureProfile();
    double initialMass = pipe.getTotalMassInventory();
    double cumulativeBoundaryMass = 0.0;
    double maximumOil = 0.0;
    double minimumHeatScale = 1.0;
    boolean observedRefill = false;
    double maximumInterfaceArea = 0.0;
    double maximumWallTemperatureChange = 0.0;
    boolean observedTraceAreaFallback = false;

    int rampSteps = (int) Math.round(120.0 / dt);
    for (int step = 1; step <= rampSteps; step++) {
      pipe.setOutletPressure(150.0 - 140.0 * step / rampSteps, "bara");
      pipe.runTransient(dt, null);
      cumulativeBoundaryMass += acceptedBoundaryMass(pipe);
      maximumOil = Math.max(maximumOil, pipe.getLastMassBalanceReport().getFinalMassKg(Phase.OIL));
      assertAcceptedPoolInterval(pipe);
      minimumHeatScale = Math.min(minimumHeatScale, minimumHeatScale(pipe));
      maximumInterfaceArea = Math.max(maximumInterfaceArea, maximumInterfaceArea(pipe));
      maximumWallTemperatureChange = Math.max(maximumWallTemperatureChange,
          maximumAbsoluteDifference(initialWallTemperature, pipe.getWallTemperatureProfile()));
      observedTraceAreaFallback |= observedTraceAreaFallback(pipe);
    }

    pipe.setGasOnlyOutletTakeoff(false);
    pipe.closeOutlet();
    for (int step = 0; step < (int) Math.round(120.0 / dt); step++) {
      pipe.runTransient(dt, null);
      cumulativeBoundaryMass += acceptedBoundaryMass(pipe);
      maximumOil = Math.max(maximumOil, pipe.getLastMassBalanceReport().getFinalMassKg(Phase.OIL));
      assertEquals(0.0, pipe.getLastMassBalanceReport().getOutletMassKg(Phase.TOTAL), 0.0);
      assertAcceptedPoolInterval(pipe);
      minimumHeatScale = Math.min(minimumHeatScale, minimumHeatScale(pipe));
      maximumInterfaceArea = Math.max(maximumInterfaceArea, maximumInterfaceArea(pipe));
      maximumWallTemperatureChange = Math.max(maximumWallTemperatureChange,
          maximumAbsoluteDifference(initialWallTemperature, pipe.getWallTemperatureProfile()));
      observedTraceAreaFallback |= observedTraceAreaFallback(pipe);
    }

    pipe.setInletBoundaryCondition(TwoFluidPipe.BoundaryCondition.CONSTANT_FLOW);
    pipe.setInletMassFlow(1000.0, "kg/sec");
    int refillSteps = (int) Math.round(420.0 / dt);
    double[] refillTimes = new double[refillSteps + 1];
    double[] refillInletPressures = new double[refillSteps + 1];
    refillInletPressures[0] = pipe.getPressureProfile()[0];
    for (int step = 0; step < refillSteps; step++) {
      pipe.runTransient(dt, null);
      double boundaryMass = acceptedBoundaryMass(pipe);
      cumulativeBoundaryMass += boundaryMass;
      observedRefill |= pipe.getLastMassBalanceReport().getInletMassKg(Phase.TOTAL) > 0.0;
      maximumOil = Math.max(maximumOil, pipe.getLastMassBalanceReport().getFinalMassKg(Phase.OIL));
      assertEquals(0.0, pipe.getLastMassBalanceReport().getOutletMassKg(Phase.TOTAL), 0.0);
      assertAcceptedPoolInterval(pipe);
      minimumHeatScale = Math.min(minimumHeatScale, minimumHeatScale(pipe));
      maximumInterfaceArea = Math.max(maximumInterfaceArea, maximumInterfaceArea(pipe));
      maximumWallTemperatureChange = Math.max(maximumWallTemperatureChange,
          maximumAbsoluteDifference(initialWallTemperature, pipe.getWallTemperatureProfile()));
      observedTraceAreaFallback |= observedTraceAreaFallback(pipe);
      refillTimes[step + 1] = (step + 1) * dt;
      refillInletPressures[step + 1] = pipe.getPressureProfile()[0];
    }

    TwoFluidPipeReport.PressureTraceMetrics refillPressure = TwoFluidPipeReport.analyzePressureTrace(refillTimes,
        refillInletPressures, 100.0);

    pipe.closeInlet();
    for (int step = 0; step < (int) Math.round(120.0 / dt); step++) {
      pipe.runTransient(dt, null);
      double boundaryMass = acceptedBoundaryMass(pipe);
      cumulativeBoundaryMass += boundaryMass;
      maximumOil = Math.max(maximumOil, pipe.getLastMassBalanceReport().getFinalMassKg(Phase.OIL));
      assertEquals(0.0, pipe.getLastMassBalanceReport().getOutletMassKg(Phase.TOTAL), 0.0);
      assertAcceptedPoolInterval(pipe);
      minimumHeatScale = Math.min(minimumHeatScale, minimumHeatScale(pipe));
      maximumInterfaceArea = Math.max(maximumInterfaceArea, maximumInterfaceArea(pipe));
      maximumWallTemperatureChange = Math.max(maximumWallTemperatureChange,
          maximumAbsoluteDifference(initialWallTemperature, pipe.getWallTemperatureProfile()));
      observedTraceAreaFallback |= observedTraceAreaFallback(pipe);
    }

    assertEquals(780.0, pipe.getSimulationTime(), 1.0e-9);
    assertTrue(maximumOil > 0.0, "The full cycle must exercise a hydrocarbon-liquid pool");
    assertTrue(minimumHeatScale < 1.0, "The declared finite heat supply must actively limit evaporation");
    assertTrue(observedRefill, "The original same-gas inlet donor must fund actual inlet refill");
    assertEquals(initialMass + cumulativeBoundaryMass, pipe.getTotalMassInventory(), 1.0e-5);
    return new double[] {maximumOil, pipe.getLastMassBalanceReport().getFinalMassKg(Phase.OIL), minimumHeatScale,
        maximumInterfaceArea, maximumWallTemperatureChange, observedTraceAreaFallback ? 1.0 : 0.0,
        refillPressure.getPeakToPeakPressurePa(), refillPressure.getMaximumAbsolutePressureRatePaPerSecond(),
        refillPressure.getExcessVariationPa(), refillPressure.getRingingRatio(),
        refillPressure.getDirectionReversals()};
  }

  /** Return comma-separated values for the full-cycle receipt. */
  private static String csvValues(double[] values) {
    StringBuilder row = new StringBuilder();
    for (int value = 0; value < values.length; value++) {
      if (value > 0) {
        row.append(',');
      }
      row.append(values[value]);
    }
    return row.toString();
  }

  /** Return whether the latest stage used the explicit sub-resolution trace-area fallback. */
  private static boolean observedTraceAreaFallback(TwoFluidPipe pipe) {
    for (boolean fallback : pipe.getLastHydrocarbonPoolTransferResult().getUnresolvedTraceAreaFallback()) {
      if (fallback) {
        return true;
      }
    }
    return false;
  }

  /** Return the largest resolved gas/oil interface area in the latest accepted stage. */
  private static double maximumInterfaceArea(TwoFluidPipe pipe) {
    double maximum = 0.0;
    for (double area : pipe.getLastHydrocarbonPoolTransferResult().getInterfaceAreasSquareMetres()) {
      maximum = Math.max(maximum, area);
    }
    return maximum;
  }

  /** Return the largest absolute cell difference between two equally sized profiles. */
  private static double maximumAbsoluteDifference(double[] first, double[] second) {
    double maximum = 0.0;
    for (int cell = 0; cell < first.length; cell++) {
      maximum = Math.max(maximum, Math.abs(first[cell] - second[cell]));
    }
    return maximum;
  }

  /** Return the smallest accepted cell heat-limit scale. */
  private static double minimumHeatScale(TwoFluidPipe pipe) {
    double minimum = 1.0;
    for (double scale : pipe.getLastHydrocarbonPoolTransferResult().getHeatScale()) {
      minimum = Math.min(minimum, scale);
    }
    return minimum;
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
   * @param levelOutlet whether to level the final 1250 m of the line
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
    pipe.setCellFaceElevationProfile(createRichGasFaceElevations(cells, levelOutlet));
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
    double[] initialWallTemperature = new double[cells];
    java.util.Arrays.fill(initialWallTemperature, 277.15);
    pipe.setInitialWallTemperatureProfile(initialWallTemperature, "K");
    pipe.run();
    pipe.prepareConservativeEnergyTransientFromSteadyState();
    pipe.closeInlet();
    pipe.setMassTransferRelaxationTime(30.0);
    return pipe;
  }

  /**
   * Refine the original twelve straight route segments without changing physical geometry.
   *
   * @param cells physical cell count
   * @param levelOutlet whether to level the final 1250 m of the 5000 m line
   * @return face elevations in metres
   */
  private static double[] createRichGasFaceElevations(int cells, boolean levelOutlet) {
    int routeSegments = 12;
    double[] routeElevations = new double[routeSegments + 1];
    for (int vertex = 0; vertex < routeElevations.length; vertex++) {
      double position = vertex / (double) routeSegments;
      if (levelOutlet) {
        position = Math.min(position, 0.75);
      }
      double firstWave = Math.sin(Math.PI * position);
      double thirdWave = Math.sin(3.0 * Math.PI * position);
      routeElevations[vertex] = -300.0 - 55.0 * firstWave * firstWave - 12.0 * thirdWave * thirdWave;
    }
    double[] elevations = new double[cells + 1];
    for (int face = 0; face < elevations.length; face++) {
      double position = routeSegments * face / (double) cells;
      int segment = Math.min(routeSegments - 1, (int) position);
      double fraction = position - segment;
      elevations[face] = (1.0 - fraction) * routeElevations[segment] + fraction * routeElevations[segment + 1];
    }
    return elevations;
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
