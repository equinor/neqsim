package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.TwoFluidMassBalanceReport.Phase;
import neqsim.process.equipment.pipeline.twophasepipe.FilmInterphaseTransfer;
import neqsim.process.equipment.pipeline.twophasepipe.FilmInterphaseTransfer.CellMode;
import neqsim.process.equipment.pipeline.twophasepipe.HydrocarbonPoolTransfer;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.TimeIntegrator;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Non-equilibrium gas/oil transfer from the multicomponent film model inside the conservative-energy transient: a
 * rich-gas line is blown down into the two-phase region, shut in, refilled above the phase envelope and held.
 */
@Tag("slow")
class TwoFluidPipeFilmInterphaseTransferTest {
  private static final int CELLS = 6;
  private static final double DT = 0.5;

  /** The cycle completes with film-derived transfer in the wet cells and closes mass and component balances. */
  @Test
  void blowdownAndRefillCycleRunsOnFilmTransferAndConserves() {
    TwoFluidPipe pipe = createPipe();
    FilmInterphaseTransfer film = new FilmInterphaseTransfer();
    film.setUpdateInterval(2.0);
    pipe.setFilmInterphaseTransfer(film);
    double initialMass = pipe.getTotalMassInventory();
    double boundaryMass = 0.0;
    double maximumOil = 0.0;
    double worstResidual = 0.0;
    int filmCellObservations = 0;
    double shortestTimeConstant = Double.POSITIVE_INFINITY;
    double longestTimeConstant = 0.0;

    int blowdownSteps = (int) Math.round(60.0 / DT);
    for (int step = 1; step <= blowdownSteps; step++) {
      pipe.setOutletPressure(130.0 - 50.0 * step / blowdownSteps, "bara");
      pipe.runTransient(DT, null);
      boundaryMass += acceptedBoundaryMass(pipe);
      worstResidual = Math.max(worstResidual, worstResidual(pipe));
      maximumOil = Math.max(maximumOil, pipe.getLastMassBalanceReport().getFinalMassKg(Phase.OIL));
      FilmInterphaseTransfer.Result result = pipe.getLastFilmInterphaseTransferResult();
      for (int cell = 0; cell < CELLS; cell++) {
        if (result.getModes()[cell] == CellMode.FILM) {
          filmCellObservations++;
          double timeConstant = result.getCharacteristicTimesSeconds()[cell];
          // A cell reports no characteristic time while no component moves toward equilibrium.
          if (!Double.isNaN(timeConstant)) {
            shortestTimeConstant = Math.min(shortestTimeConstant, timeConstant);
            longestTimeConstant = Math.max(longestTimeConstant, timeConstant);
          }
          assertTrue(result.getInterfaceWidthsMetres()[cell] > 0.0);
          assertTrue(Double.isFinite(result.getInterfaceTemperaturesK()[cell]));
        }
      }
    }
    assertTrue(maximumOil > 1000.0, "The blowdown must condense liquid, got " + maximumOil + " kg");
    assertTrue(filmCellObservations > 50, "Film cells observed: " + filmCellObservations);
    assertTrue(shortestTimeConstant > 0.0 && Double.isFinite(longestTimeConstant) && longestTimeConstant > 0.0,
        "Film characteristic times " + shortestTimeConstant + " to " + longestTimeConstant + " s");

    pipe.setGasOnlyOutletTakeoff(false);
    pipe.closeOutlet();
    for (int step = 0; step < (int) Math.round(20.0 / DT); step++) {
      pipe.runTransient(DT, null);
      boundaryMass += acceptedBoundaryMass(pipe);
      worstResidual = Math.max(worstResidual, worstResidual(pipe));
    }

    double refillStartPressure = pipe.getInletFacePressure("Pa");
    pipe.setInletBoundaryCondition(TwoFluidPipe.BoundaryCondition.CONSTANT_PRESSURE);
    pipe.setInletBoundaryFluid(pipe.getInletStream().getFluid());
    int refillSteps = (int) Math.round(60.0 / DT);
    for (int step = 0; step < refillSteps; step++) {
      pipe.setInletPressure(refillStartPressure + (200.0e5 - refillStartPressure) * (step + 1) / refillSteps);
      pipe.runTransient(DT, null);
      boundaryMass += acceptedBoundaryMass(pipe);
      worstResidual = Math.max(worstResidual, worstResidual(pipe));
    }
    pipe.closeInlet();
    for (int step = 0; step < (int) Math.round(200.0 / DT); step++) {
      pipe.runTransient(DT, null);
      boundaryMass += acceptedBoundaryMass(pipe);
      worstResidual = Math.max(worstResidual, worstResidual(pipe));
    }

    FilmInterphaseTransfer.Result last = pipe.getLastFilmInterphaseTransferResult();
    assertTrue(last.getFilmSolveCount() > 100L, "Film solves: " + last.getFilmSolveCount());
    double finalOil = pipe.getLastMassBalanceReport().getFinalMassKg(Phase.OIL);
    // Above the envelope the liquid can only shrink, at the film rate where an interface exists.
    assertTrue(finalOil < 0.1 * maximumOil,
        "Oil left after the hold above the envelope: " + finalOil + " kg of a maximum of " + maximumOil + " kg");
    assertTrue(worstResidual < 1.0e-10, "Worst phase mass residual " + worstResidual);
    assertEquals(initialMass + boundaryMass, pipe.getTotalMassInventory(), 1.0e-9 * initialMass);
    assertTrue(pipe.getLastComponentConservationReport().isConverged(),
        pipe.getLastComponentConservationReport().getMessage());
  }

  /** Only one finite-rate closure can own the interphase source. */
  @Test
  void filmTransferExcludesTheOtherFiniteRateClosures() {
    TwoFluidPipe pipe = createPipe();
    pipe.setFilmInterphaseTransfer(new FilmInterphaseTransfer());
    HydrocarbonPoolTransfer pool = HydrocarbonPoolTransfer.forStratifiedFlow(new String[] {"n-heptane"},
        new double[] {1.0e-4}, new double[] {3.5e5}, Double.POSITIVE_INFINITY);
    assertThrows(IllegalStateException.class, () -> pipe.setHydrocarbonPoolTransfer(pool));
    pipe.setFilmInterphaseTransfer(null);
    pipe.setHydrocarbonPoolTransfer(pool);
    assertThrows(IllegalStateException.class, () -> pipe.setFilmInterphaseTransfer(new FilmInterphaseTransfer()));
  }

  private static double acceptedBoundaryMass(TwoFluidPipe pipe) {
    TwoFluidMassBalanceReport report = pipe.getLastMassBalanceReport();
    return report.getInletMassKg(Phase.TOTAL) - report.getOutletMassKg(Phase.TOTAL);
  }

  private static double worstResidual(TwoFluidPipe pipe) {
    double worst = 0.0;
    for (Phase phase : Phase.values()) {
      worst = Math.max(worst, pipe.getLastMassBalanceReport().getRelativeResidual(phase));
    }
    return worst;
  }

  private static TwoFluidPipe createPipe() {
    SystemInterface fluid = new SystemSrkEos(278.15, 130.0);
    fluid.addComponent("methane", 0.916);
    fluid.addComponent("ethane", 0.050);
    fluid.addComponent("propane", 0.020);
    fluid.addComponent("n-butane", 0.007);
    fluid.addComponent("n-pentane", 0.003);
    fluid.addComponent("n-heptane", 0.004);
    fluid.setMixingRule("classic");
    Stream inlet = new Stream("feed", fluid);
    inlet.setFlowRate(0.001, "kg/sec");
    inlet.setTemperature(5.0, "C");
    inlet.setPressure(130.0, "bara");
    inlet.run();

    TwoFluidPipe pipe = new TwoFluidPipe("film transfer", inlet);
    pipe.setLength(5000.0);
    pipe.setDiameter(1.0128);
    pipe.setRoughness(45.0e-6);
    pipe.setNumberOfSections(CELLS);
    double[] faces = new double[CELLS + 1];
    for (int face = 0; face <= CELLS; face++) {
      faces[face] = -300.0 + 250.0 * face / CELLS;
    }
    pipe.setCellFaceElevationProfile(faces);
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
