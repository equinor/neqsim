package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.twophasepipe.TwoFluidComponentTransport;
import neqsim.process.equipment.pipeline.twophasepipe.ThermodynamicCoupling;
import neqsim.process.equipment.pipeline.twophasepipe.TwoFluidSection;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.TimeIntegrator;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Sustained steady-state handoff with phase transfer, EOS refresh, and thermal transport active. */
class TwoFluidPipeSustainedThermodynamicRegressionTest {
  private static final Logger logger = LogManager.getLogger(TwoFluidPipeSustainedThermodynamicRegressionTest.class);
  private static final double RELAXATION_SECONDS = 30.0;

  enum PhaseState {
    GAS, GAS_OIL, GAS_OIL_WATER
  }

  @ParameterizedTest
  @EnumSource(PhaseState.class)
  void hydraulicSlipDoesNotDriveTransferBetweenEquilibratedPhases(PhaseState phaseState) throws Exception {
    TwoFluidPipe pipe = createPipe(phaseState);
    ThermodynamicCoupling coupling = new ThermodynamicCoupling(pipe.getInletStream().getFluid());
    TwoFluidSection[] cells = sections(pipe);
    TwoFluidComponentTransport components = new TwoFluidComponentTransport(pipe.getInletStream().getFluid(), cells);
    for (int cell = 0; cell < cells.length; cell++) {
      TwoFluidSection section = cells[cell];
      SystemInterface equilibrium = components.createThermodynamicState(cell, pipe.getInletStream().getFluid(),
          section.getPressure(), section.getTemperature());
      double source = coupling.calcPhaseMassTransferRatePerLength(section, RELAXATION_SECONDS, equilibrium)
          .getGasSourceKgPerMetreSecond();
      double totalMass = section.getGasMassPerLength() + section.getOilMassPerLength()
          + section.getWaterMassPerLength();
      logger.info("{} steady section {}: alphaL={}, phase-transfer source={} kg/(m s)", phaseState,
          section.getPosition(), section.getLiquidHoldup(), source);
      assertEquals(0.0, source, 1.0e-7 * totalMass / RELAXATION_SECONDS,
          "Slip changes phase residence inventories, not phase equilibrium at unchanged pressure and temperature");
    }
  }

  @Test
  void localComponentEquilibriumRetainsHeatingAndCoolingPhaseTransfer() throws Exception {
    TwoFluidPipe pipe = createPipe(PhaseState.GAS_OIL);
    TwoFluidSection[] cells = sections(pipe);
    TwoFluidComponentTransport components = new TwoFluidComponentTransport(pipe.getInletStream().getFluid(), cells);
    ThermodynamicCoupling coupling = new ThermodynamicCoupling(pipe.getInletStream().getFluid());
    TwoFluidSection section = cells[0];
    SystemInterface cooled = components.createThermodynamicState(0, pipe.getInletStream().getFluid(),
        section.getPressure(), section.getTemperature() - 20.0);
    SystemInterface heated = components.createThermodynamicState(0, pipe.getInletStream().getFluid(),
        section.getPressure(), section.getTemperature() + 20.0);
    TwoFluidSection coldSection = section.clone();
    coldSection.setTemperature(cooled.getTemperature());
    TwoFluidSection hotSection = section.clone();
    hotSection.setTemperature(heated.getTemperature());
    double coldSource = coupling.calcPhaseMassTransferRatePerLength(coldSection, RELAXATION_SECONDS, cooled)
        .getGasSourceKgPerMetreSecond();
    double hotSource = coupling.calcPhaseMassTransferRatePerLength(hotSection, RELAXATION_SECONDS, heated)
        .getGasSourceKgPerMetreSecond();
    assertTrue(coldSource < 0.0, "Cooling the same conserved composition must condense gas");
    assertTrue(hotSource > 0.0, "Heating the same conserved composition must evaporate liquid");
  }

  @ParameterizedTest
  @EnumSource(PhaseState.class)
  void unchangedThermalBoundariesRemainStationaryForTwoMinutes(PhaseState phaseState) {
    TwoFluidPipe pipe = createPipe(phaseState);
    double initialMass = pipe.getTotalMassInventory();
    double[] initialTemperature = pipe.getTemperatureProfile().clone();
    double[] initialPressure = pipe.getPressureProfile().clone();
    double maximumTemperatureDrift = 0.0;
    double maximumPressureDrift = 0.0;
    for (int step = 0; step < 120; step++) {
      pipe.runTransient(1.0, null);
      assertTrue(pipe.isCoupledPressureMomentumConverged());
      assertTrue(pipe.getLastComponentConservationReport().isConverged(),
          "The local equilibrium source must preserve named component inventory");
      TwoFluidMassBalanceReport balance = pipe.getLastMassBalanceReport();
      for (TwoFluidMassBalanceReport.Phase phase : TwoFluidMassBalanceReport.Phase.values()) {
        assertTrue(balance.isWithinTolerance(phase, 1.0e-7, 1.0e-8),
            phaseState + " " + phase + " finite-volume balance must close at t=" + (step + 1));
      }
      double[] temperature = pipe.getTemperatureProfile();
      double[] pressure = pipe.getPressureProfile();
      if (phaseState != PhaseState.GAS_OIL_WATER) {
        for (double waterFlow : pipe.getWaterMassFlowProfile()) {
          assertEquals(0.0, waterFlow, 0.0, "An absent aqueous phase must not be created by evaporation round-off");
        }
        for (double waterHoldup : pipe.getWaterHoldupProfile()) {
          assertEquals(0.0, waterHoldup, 0.0);
        }
      }
      for (int cell = 0; cell < temperature.length; cell++) {
        maximumTemperatureDrift = Math.max(maximumTemperatureDrift,
            Math.abs(temperature[cell] - initialTemperature[cell]));
        maximumPressureDrift = Math.max(maximumPressureDrift,
            Math.abs(pressure[cell] - initialPressure[cell]) / initialPressure[cell]);
      }
      if ((step + 1) % 30 == 0) {
        logger.info("{} thermal null t={} s: inventory ratio={}, maximum temperature drift={} K, pressure drift={}",
            phaseState, step + 1, pipe.getTotalMassInventory() / initialMass, maximumTemperatureDrift,
            maximumPressureDrift);
      }
    }
    assertEquals(initialMass, pipe.getTotalMassInventory(), 1.0e-3 * initialMass,
        phaseState + " must preserve its converged inventory with unchanged thermal and flow boundaries");
    assertTrue(maximumTemperatureDrift < 1.0e-3,
        phaseState + " spontaneously changed temperature by " + maximumTemperatureDrift + " K");
    assertTrue(maximumPressureDrift < 1.0e-4,
        phaseState + " spontaneously changed pressure by " + maximumPressureDrift);
    assertFalse(pipe.isTransientCoupledPressureMomentumFailureDetected());
    assertFalse(pipe.isTransientCoupledPressureMomentumCorrectionLimited());
    assertFalse(pipe.isTransientOutletBackflowClamped());
    assertEquals(0, pipe.getTransientCoupledPressureMomentumRejectedSubsteps());
  }

  private static TwoFluidPipe createPipe(PhaseState phaseState) {
    SystemInterface fluid = new SystemSrkEos(298.15, 60.0);
    fluid.addComponent("methane", 1.0);
    if (phaseState == PhaseState.GAS) {
      fluid.addComponent("ethane", 0.02);
    }
    if (phaseState != PhaseState.GAS) {
      fluid.addComponent("n-decane", 1.0);
    }
    if (phaseState == PhaseState.GAS_OIL_WATER) {
      fluid.addComponent("water", 1.0);
    }
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    Stream feed = new Stream(phaseState + " sustained feed", fluid);
    feed.setFlowRate(0.5, "kg/sec");
    feed.run();
    assertEquals(phaseState.ordinal() + 1, feed.getFluid().getNumberOfPhases());

    TwoFluidPipe pipe = new TwoFluidPipe(phaseState + " sustained thermodynamic regression", feed);
    pipe.setLength(100.0);
    pipe.setDiameter(0.3);
    pipe.setNumberOfSections(8);
    pipe.setElevationProfile(new double[9]);
    pipe.setEnableCoupledPressureMomentum(true);
    pipe.setEnableInterfacialPressure(true);
    pipe.setImplicitInterfacialPressureCoupling(true);
    pipe.setTimeIntegrationMethod(TimeIntegrator.Method.IMEX_PRESSURE_CORRECTION);
    pipe.setEnableAdaptiveTimestepping(true);
    pipe.setEnableSlugTracking(false);
    pipe.setEnableTerrainTracking(false);
    pipe.setIncludeMassTransfer(true);
    pipe.setComponentTransportEnabled(true);
    pipe.setMassTransferRelaxationTime(RELAXATION_SECONDS);
    pipe.setThermodynamicUpdateInterval(1);
    pipe.setHeatTransferCoefficient(5.0);
    pipe.setSurfaceTemperature(298.15, "K");
    pipe.setEnableJouleThomson(false);
    pipe.setSteadyStateMaxWallClockTime(Double.POSITIVE_INFINITY);
    pipe.run();
    assertTrue(pipe.isSteadyStateConverged());
    return pipe;
  }

  private static TwoFluidSection[] sections(TwoFluidPipe pipe) throws Exception {
    Field field = TwoFluidPipe.class.getDeclaredField("sections");
    field.setAccessible(true);
    return (TwoFluidSection[]) field.get(pipe);
  }
}
