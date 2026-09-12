package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import neqsim.process.equipment.pipeline.TwoFluidComponentConservationReport.Phase;
import neqsim.process.equipment.pipeline.twophasepipe.LagrangianSlugTracker.SlugBubbleUnit;
import neqsim.process.equipment.pipeline.twophasepipe.LiquidAccumulationTracker.SlugCharacteristics;
import neqsim.process.equipment.pipeline.twophasepipe.TwoFluidSection;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.TimeIntegrator;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/** Coupled transient contracts for supported TwoFluidPipe capabilities. */
class TwoFluidPipeCoupledCapabilityTest {
  private static final Logger logger = LogManager.getLogger(TwoFluidPipeCoupledCapabilityTest.class);
  private static final UUID TRANSIENT_ID = UUID.fromString("00000000-0000-0000-0000-000000002904");

  @Test
  void componentTransportAndSignedOutletBackflowFailBeforeMutationInEitherSetterOrder() {
    TwoFluidPipe componentsFirst = uninitializedPipe("components-first");
    componentsFirst.setComponentTransportEnabled(true);
    IllegalStateException backflowException = assertThrows(IllegalStateException.class,
        () -> componentsFirst.setAllowOutletPhaseBackflow(true));
    assertTrue(backflowException.getMessage().contains("external outlet composition"));
    assertFalse(componentsFirst.isOutletPhaseBackflowAllowed());
    assertTrue(componentsFirst.isComponentTransportEnabled());
    assertEquals(0.0, componentsFirst.getSimulationTime(), 0.0);

    TwoFluidPipe backflowFirst = uninitializedPipe("backflow-first");
    backflowFirst.setAllowOutletPhaseBackflow(true);
    IllegalStateException componentException = assertThrows(IllegalStateException.class,
        () -> backflowFirst.setComponentTransportEnabled(true));
    assertTrue(componentException.getMessage().contains("external outlet composition"));
    assertTrue(backflowFirst.isOutletPhaseBackflowAllowed());
    assertFalse(backflowFirst.isComponentTransportEnabled());
    assertEquals(0.0, backflowFirst.getSimulationTime(), 0.0);
  }

  @Test
  void multistageConservativeSlugPhaseTransferFailsBeforeStateMutation() {
    TwoFluidPipe pipe = uninitializedPipe("multistage-source");
    pipe.setComponentTransportEnabled(true);
    pipe.setIncludeMassTransfer(true);
    pipe.setTimeIntegrationMethod(TimeIntegrator.Method.RK2);
    pipe.setSlugTrackingMode(TwoFluidPipe.SlugTrackingMode.CONSERVATIVE_LAGRANGIAN);

    IllegalStateException exception = assertThrows(IllegalStateException.class,
        () -> pipe.runTransient(0.01, TRANSIENT_ID));
    assertTrue(exception.getMessage().contains("stage-local component inventories"));
    assertEquals(0.0, pipe.getSimulationTime(), 0.0);
  }

  @Test
  @Tag("slow")
  @Timeout(value = 5, unit = TimeUnit.MINUTES)
  void slugComponentPhaseChangeAndThermalCouplingClosesAndRefines() throws Exception {
    CoupledResult coarse = runCoupledCase(0.05);
    CoupledResult refined = runCoupledCase(0.025);

    assertTrue(coarse.waterTransferKg > 0.0 && refined.waterTransferKg > 0.0,
        "Cooling below the water dew point must create aqueous water on both time grids");
    assertTrue(Math.abs(coarse.latentHeatJ) > 0.0 && Math.abs(refined.latentHeatJ) > 0.0,
        "The same component transfer must produce compositional latent heat");
    assertTrue(coarse.meanTemperatureChangeK < 0.0 && refined.meanTemperatureChangeK < 0.0,
        "A colder wall must cool the closed fluid");
    assertEquals(0.05, coarse.slugAgeSeconds, 1.0e-12);
    assertEquals(0.05, refined.slugAgeSeconds, 1.0e-12);
    assertTrue(coarse.slugLengthMetres > 0.0 && refined.slugLengthMetres > 0.0);

    double waterSensitivity = relativeDifference(coarse.waterTransferKg, refined.waterTransferKg);
    double latentSensitivity = relativeDifference(coarse.latentHeatJ, refined.latentHeatJ);
    double temperatureSensitivity = relativeDifference(coarse.meanTemperatureChangeK, refined.meanTemperatureChangeK);
    logger.info("Coupled outer-step sensitivity: water transfer={}, latent heat={}, temperature response={}",
        waterSensitivity, latentSensitivity, temperatureSensitivity);
    assertTrue(waterSensitivity < 0.10, "Aqueous-water transfer outer-step sensitivity was " + waterSensitivity);
    assertTrue(latentSensitivity < 0.10, "Latent-heat outer-step sensitivity was " + latentSensitivity);
    assertTrue(temperatureSensitivity < 0.10,
        "Mean-temperature-response outer-step sensitivity was " + temperatureSensitivity);
    assertEquals(coarse.slugFrontMetres, refined.slugFrontMetres, 1.0e-12,
        "Outer-step partitioning must not change accepted slug travel over the same physical duration");
    assertEquals(coarse.slugLengthMetres, refined.slugLengthMetres, 1.0e-12,
        "Outer-step partitioning must not change tracked slug length over the same physical duration");
  }

  private static CoupledResult runCoupledCase(double outerStepSeconds) throws Exception {
    return runCoupledCase(outerStepSeconds, false);
  }

  @Test
  @Tag("slow")
  @Timeout(value = 5, unit = TimeUnit.MINUTES)
  void wholePipeTransactionRetainsCoupledSlugPhaseComponentAndThermalBehavior() throws Exception {
    CoupledResult legacy = runCoupledCase(0.025, false);
    CoupledResult transaction = runCoupledCase(0.025, true);
    assertEquals(legacy.waterTransferKg, transaction.waterTransferKg, 1.0e-16);
    assertEquals(legacy.latentHeatJ, transaction.latentHeatJ, 1.0e-10);
    assertEquals(legacy.meanTemperatureChangeK, transaction.meanTemperatureChangeK, 1.0e-10);
    assertEquals(legacy.slugAgeSeconds, transaction.slugAgeSeconds, 1.0e-12);
    assertEquals(legacy.slugFrontMetres, transaction.slugFrontMetres, 1.0e-12);
    assertEquals(legacy.slugLengthMetres, transaction.slugLengthMetres, 1.0e-12);
  }

  private static CoupledResult runCoupledCase(double outerStepSeconds, boolean transactional) throws Exception {
    SystemInterface wetGas = wetGasNearWaterDewPoint();
    double initialTemperatureK = wetGas.getTemperature("K");
    Stream inlet = new Stream("coupled-inlet-" + outerStepSeconds, wetGas);
    inlet.setFlowRate(6.0, "kg/sec");
    inlet.setPressure(70.0, "bara");
    inlet.run();

    TwoFluidPipe pipe = new TwoFluidPipe("coupled-pipe-" + outerStepSeconds, inlet);
    pipe.setLength(20.0);
    pipe.setDiameter(0.20);
    pipe.setRoughness(1.0e-5);
    pipe.setNumberOfSections(4);
    pipe.setTimeIntegrationMethod(TimeIntegrator.Method.EULER);
    pipe.setEnableAdaptiveTimestepping(false);
    pipe.setEnableSlugTracking(true);
    pipe.setIncludeMassTransfer(true);
    pipe.setMassTransferRelaxationTime(30.0);
    pipe.setThermodynamicUpdateInterval(1);
    pipe.setSteadyStateMaxWallClockTime(Double.POSITIVE_INFINITY);
    pipe.setComponentTransportEnabled(true);
    pipe.run();
    pipe.closeInlet();
    pipe.closeOutlet();
    pipe.setEnableJouleThomson(false);
    pipe.setWallProperties(0.005, 1000.0, 100.0);
    pipe.setHeatTransferCoefficient(5000.0);
    pipe.setSurfaceTemperature(initialTemperatureK - 10.0, "K");
    pipe.setSlugTrackingMode(TwoFluidPipe.SlugTrackingMode.CONSERVATIVE_LAGRANGIAN);
    pipe.getLagrangianSlugTracker().setEnableInletSlugGeneration(false);
    pipe.getLagrangianSlugTracker().setEnableWakeEffects(false);
    SlugBubbleUnit slug = seedSlug(pipe);
    pipe.setTransactionalTransientEnabled(transactional);
    if (transactional) {
      pipe.setMaximumTransientSubsteps(1);
      byte[] before = SerializationUtils.serialize(pipe);
      assertThrows(IllegalStateException.class, () -> pipe.runTransient(outerStepSeconds, TRANSIENT_ID));
      assertArrayEquals(before, SerializationUtils.serialize(pipe));
      pipe.setMaximumTransientSubsteps(10000);
    }

    double cumulativeWaterTransferKg = 0.0;
    double cumulativeLatentHeatJ = 0.0;
    int steps = (int) Math.round(0.05 / outerStepSeconds);
    for (int step = 0; step < steps; step++) {
      pipe.runTransient(outerStepSeconds, TRANSIENT_ID);
      TwoFluidComponentConservationReport componentReport = pipe.getLastComponentConservationReport();
      TwoFluidMassBalanceReport phaseReport = pipe.getLastMassBalanceReport();
      TwoFluidThermalEnergyBalanceReport energyReport = pipe.getLastThermalEnergyBalanceReport();

      assertTrue(componentReport.isConverged(), componentReport.getMessage());
      assertTrue(componentReport.getMaximumRelativeInventoryResidual() <= 1.0e-8, componentReport.getMessage());
      assertTrue(componentReport.getMaximumInterphaseTransferResidualKg() <= 1.0e-10, componentReport.getMessage());
      assertTrue(componentReport.getMinimumMassFraction() >= -1.0e-12, componentReport.getMessage());
      assertTrue(componentReport.getMaximumMassFraction() <= 1.0 + 1.0e-12, componentReport.getMessage());
      assertTrue(componentReport.getMaximumMassFractionSumError() <= 1.0e-12, componentReport.getMessage());
      for (TwoFluidMassBalanceReport.Phase phase : TwoFluidMassBalanceReport.Phase.values()) {
        assertTrue(phaseReport.isWithinTolerance(phase, 1.0e-7, 1.0e-10),
            phase + " mass residual was " + phaseReport.getResidualKg(phase) + " kg");
      }
      assertTrue(energyReport.isWithinTolerance(1.0e-5, 1.0e-10),
          "Thermal residual was " + energyReport.getResidualJ() + " J");
      assertTrue(pipe.isCoupledPressureMomentumConverged());
      assertFalse(pipe.isTransientCoupledPressureMomentumFailureDetected());
      assertEquals(0, pipe.getTransientCoupledPressureMomentumRejectedSubsteps());
      assertEquals(componentReport.getInterphaseLatentHeatEnergyJ(), energyReport.getLatentHeatEnergyJ(), 1.0e-8);
      for (String component : componentReport.getComponentNames()) {
        assertEquals(0.0,
            componentReport.getInterphaseTransferKg(Phase.GAS, component)
                + componentReport.getInterphaseTransferKg(Phase.OIL, component)
                + componentReport.getInterphaseTransferKg(Phase.WATER, component),
            1.0e-12, component);
      }
      cumulativeWaterTransferKg += componentReport.getInterphaseTransferKg(Phase.WATER, "water");
      cumulativeLatentHeatJ += componentReport.getInterphaseLatentHeatEnergyJ();
    }

    assertTrue(pipe.getLagrangianSlugTracker().isConservativeFilmCouplingEnabled());
    assertFalse(pipe.isTransientOutletBackflowClamped());
    double finalMeanTemperatureK = mean(pipe.getTemperatureProfile());
    if (transactional) {
      // Successful transactions publish new owned tracker snapshots; the seeded marker is the only slug.
      assertEquals(1, pipe.getLagrangianSlugTracker().getSlugs().size());
      slug = pipe.getLagrangianSlugTracker().getSlugs().get(0);
    }
    return new CoupledResult(cumulativeWaterTransferKg, cumulativeLatentHeatJ,
        finalMeanTemperatureK - initialTemperatureK, slug.age, slug.frontPosition, slug.slugLength);
  }

  private static SlugBubbleUnit seedSlug(TwoFluidPipe pipe) throws Exception {
    SlugCharacteristics marker = new SlugCharacteristics();
    marker.tailPosition = 2.0;
    marker.frontPosition = 12.0;
    marker.length = 10.0;
    marker.holdup = 0.9;
    marker.volume = marker.length * sections(pipe)[0].getArea() * marker.holdup;
    return pipe.getLagrangianSlugTracker().initializeTerrainSlug(marker, sections(pipe));
  }

  private static SystemInterface wetGasNearWaterDewPoint() throws Exception {
    double waterMoleFraction = 22.0e-6;
    SystemInterface fluid = new SystemSrkCPAstatoil(260.15, 70.0);
    fluid.addComponent("CO2", 0.02);
    fluid.addComponent("nitrogen", 0.01);
    fluid.addComponent("methane", 0.9 - waterMoleFraction);
    fluid.addComponent("ethane", 0.05);
    fluid.addComponent("propane", 0.01);
    fluid.addComponent("i-butane", 0.005);
    fluid.addComponent("n-butane", 0.005);
    fluid.addComponent("water", waterMoleFraction);
    fluid.setMixingRule(10);
    fluid.setMultiPhaseCheck(true);
    SystemInterface dewPointFluid = fluid.clone();
    new ThermodynamicOperations(dewPointFluid).waterDewPointTemperatureMultiphaseFlash();
    fluid.setTemperature(dewPointFluid.getTemperature("K") + 0.02, "K");
    return fluid;
  }

  private static TwoFluidPipe uninitializedPipe(String name) {
    SystemInterface fluid = new SystemSrkEos(288.15, 70.0);
    fluid.addComponent("methane", 0.95);
    fluid.addComponent("nitrogen", 0.05);
    fluid.setMixingRule("classic");
    Stream inlet = new Stream(name + "-inlet", fluid);
    inlet.setFlowRate(5.0, "kg/sec");
    inlet.run();
    return new TwoFluidPipe(name, inlet);
  }

  private static TwoFluidSection[] sections(TwoFluidPipe pipe) throws Exception {
    Field field = TwoFluidPipe.class.getDeclaredField("sections");
    field.setAccessible(true);
    return (TwoFluidSection[]) field.get(pipe);
  }

  private static double mean(double[] values) {
    double sum = 0.0;
    for (double value : values) {
      sum += value;
    }
    return sum / values.length;
  }

  private static double relativeDifference(double first, double second) {
    return Math.abs(first - second) / Math.max(1.0e-30, Math.max(Math.abs(first), Math.abs(second)));
  }

  private static final class CoupledResult {
    private final double waterTransferKg;
    private final double latentHeatJ;
    private final double meanTemperatureChangeK;
    private final double slugAgeSeconds;
    private final double slugFrontMetres;
    private final double slugLengthMetres;

    private CoupledResult(double waterTransferKg, double latentHeatJ, double meanTemperatureChangeK,
        double slugAgeSeconds, double slugFrontMetres, double slugLengthMetres) {
      this.waterTransferKg = waterTransferKg;
      this.latentHeatJ = latentHeatJ;
      this.meanTemperatureChangeK = meanTemperatureChangeK;
      this.slugAgeSeconds = slugAgeSeconds;
      this.slugFrontMetres = slugFrontMetres;
      this.slugLengthMetres = slugLengthMetres;
    }
  }
}
