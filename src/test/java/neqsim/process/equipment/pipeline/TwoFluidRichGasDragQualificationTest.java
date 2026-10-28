package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.TwoFluidMassBalanceReport.Phase;
import neqsim.process.equipment.pipeline.twophasepipe.TwoFluidSection;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.TimeIntegrator;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Conservative first-oil-appearance qualification for the public seabed rich-gas fixture.
 *
 * <p>
 * This regression does not certify full blowdown, low-point pools, restart or evaporation rates.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
@Tag("slow")
class TwoFluidRichGasDragQualificationTest {
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
