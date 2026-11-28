package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.TwoFluidMassBalanceReport.Phase;
import neqsim.process.equipment.pipeline.twophasepipe.FilmInterphaseTransfer;
import neqsim.process.equipment.pipeline.twophasepipe.FilmInterphaseTransfer.CellMode;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.TimeIntegrator;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;

/**
 * A gas line that carries a small aqueous phase of water and triethylene glycol, with and without condensate, blown
 * down in equilibrium mode and in non-equilibrium mode of the film-transfer closure (CPA).
 */
@Tag("slow")
class TwoFluidPipeAqueousTransferModesTest {
  private static final int CELLS = 4;
  private static final double DT = 10.0;

  /** Gas with an aqueous phase: both modes complete and close the balances; the film mode resolves the interface. */
  @Test
  void gasWithAqueousPhaseRunsInBothModes() {
    runBothModes(false);
  }

  /** Gas, condensate and aqueous phase: both modes complete and close the balances for all three phases. */
  @Test
  void threePhasesRunInBothModes() {
    runBothModes(true);
  }

  private static void runBothModes(boolean withOil) {
    double[] aqueous = new double[2];
    double[] oil = new double[2];
    for (int mode = 0; mode < 2; mode++) {
      TwoFluidPipe pipe = createPipe(withOil);
      FilmInterphaseTransfer closure = new FilmInterphaseTransfer();
      closure.setEquilibriumMode(mode == 0);
      closure.setUpdateInterval(60.0);
      pipe.setFilmInterphaseTransfer(closure);
      double initialMass = pipe.getTotalMassInventory();
      double initialAqueous = pipe.getLastMassBalanceReport() == null ? Double.NaN
          : pipe.getLastMassBalanceReport().getFinalMassKg(Phase.WATER);
      double boundaryMass = 0.0;
      double worstResidual = 0.0;
      int aqueousFilmCells = 0;
      int steps = (int) Math.round(60.0 / DT);
      for (int step = 1; step <= steps; step++) {
        pipe.setOutletPressure(130.0 - 6.0 * step / steps, "bara");
        pipe.runTransient(DT, null);
        TwoFluidMassBalanceReport report = pipe.getLastMassBalanceReport();
        boundaryMass += report.getInletMassKg(Phase.TOTAL) - report.getOutletMassKg(Phase.TOTAL);
        for (Phase phase : Phase.values()) {
          worstResidual = Math.max(worstResidual, report.getRelativeResidual(phase));
        }
        FilmInterphaseTransfer.Result result = pipe.getLastFilmInterphaseTransferResult();
        for (int cell = 0; cell < CELLS; cell++) {
          if (result.getAqueousModes()[cell] == CellMode.FILM) {
            aqueousFilmCells++;
          }
          if (mode == 0) {
            assertTrue(result.getAqueousModes()[cell] != CellMode.FILM && result.getModes()[cell] != CellMode.FILM);
          }
        }
      }
      String label = (withOil ? "three phases" : "gas and aqueous phase")
          + (mode == 0 ? ", equilibrium mode" : ", non-equilibrium mode");
      TwoFluidMassBalanceReport last = pipe.getLastMassBalanceReport();
      aqueous[mode] = last.getFinalMassKg(Phase.WATER);
      oil[mode] = last.getFinalMassKg(Phase.OIL);
      assertTrue(aqueous[mode] > 0.0, label + ": aqueous mass " + aqueous[mode]);
      assertTrue(withOil ? oil[mode] > 0.0 : oil[mode] == 0.0, label + ": oil mass " + oil[mode]);
      assertTrue(worstResidual < 1.0e-10, label + ": phase mass residual " + worstResidual);
      assertEquals(initialMass + boundaryMass, pipe.getTotalMassInventory(), 1.0e-9 * initialMass, label);
      assertTrue(pipe.getLastComponentConservationReport().isConverged(),
          label + ": " + pipe.getLastComponentConservationReport().getMessage());
      FilmInterphaseTransfer.Result result = pipe.getLastFilmInterphaseTransferResult();
      if (mode == 0) {
        assertEquals(0L, result.getFilmSolveCount(), label);
      } else {
        assertTrue(result.getFilmSolveCount() > 0L, label);
        assertTrue(aqueousFilmCells > 0, label + ": aqueous film cells observed " + aqueousFilmCells);
      }
      assertTrue(Double.isNaN(initialAqueous) || initialAqueous >= 0.0);
    }
    // A minute of blowdown moves little water in either mode: the two aqueous inventories stay close.
    assertEquals(aqueous[0], aqueous[1], 0.02 * aqueous[0]);
    if (withOil) {
      assertEquals(oil[0], oil[1], 0.25 * oil[0]);
    }
  }

  private static TwoFluidPipe createPipe(boolean withOil) {
    SystemInterface fluid = new SystemSrkCPAstatoil(278.15, 130.0);
    fluid.addComponent("nitrogen", 0.005);
    fluid.addComponent("CO2", 0.015);
    fluid.addComponent("methane", withOil ? 0.880 : 0.900);
    fluid.addComponent("ethane", 0.050);
    fluid.addComponent("propane", 0.020);
    fluid.addComponent("n-butane", 0.007);
    if (withOil) {
      fluid.addComponent("n-pentane", 0.004);
      fluid.addComponent("n-heptane", 0.006);
      fluid.addComponent("n-nonane", 0.003);
    } else {
      fluid.addComponent("n-pentane", 0.003);
    }
    fluid.addComponent("water", 150.0e-6);
    fluid.addComponent("TEG", 20.0e-6);
    fluid.setMixingRule(10);
    fluid.setMultiPhaseCheck(true);
    Stream inlet = new Stream("feed", fluid);
    inlet.setFlowRate(200.0, "kg/sec");
    inlet.setTemperature(5.0, "C");
    inlet.setPressure(130.0, "bara");
    inlet.run();

    TwoFluidPipe pipe = new TwoFluidPipe("aqueous transfer modes", inlet);
    pipe.setLength(20000.0);
    pipe.setDiameter(1.0128);
    pipe.setRoughness(45.0e-6);
    pipe.setNumberOfSections(CELLS);
    pipe.setCellFaceElevationProfile(new double[CELLS + 1]);
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
    pipe.setSurfaceTemperature(278.15, "K");
    pipe.setOutletPressure(130.0, "bara");
    pipe.setSteadyStateMaxWallClockTime(Double.POSITIVE_INFINITY);
    double[] wallTemperature = new double[CELLS];
    Arrays.fill(wallTemperature, 278.15);
    pipe.setInitialWallTemperatureProfile(wallTemperature, "K");
    pipe.run();
    pipe.prepareConservativeEnergyTransientFromSteadyState();
    pipe.closeInlet();
    pipe.setWallProperties(0.027, 7850.0, 500.0);
    pipe.setHeatTransferCoefficient(5.0);
    pipe.setSurfaceTemperature(278.15, "K");
    pipe.setGasOnlyOutletTakeoff(true);
    return pipe;
  }
}
