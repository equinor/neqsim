package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import neqsim.fluidmechanics.flowsystem.onephaseflowsystem.pipeflowsystem.PipeFlowSystem;
import neqsim.fluidmechanics.geometrydefinitions.GeometryDefinitionInterface;
import neqsim.fluidmechanics.geometrydefinitions.pipe.PipeData;
import neqsim.process.equipment.pipeline.TwoFluidComponentConservationReport.Phase;
import neqsim.process.equipment.pipeline.twophasepipe.TwoFluidComponentTransport;
import neqsim.process.equipment.pipeline.twophasepipe.TwoFluidSection;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.TimeIntegrator;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/** Acceptance tests for conservative named-component transport in {@link TwoFluidPipe}. */
class TwoFluidPipeComponentTransportTest {
  private static final UUID TRANSIENT_ID = UUID.fromString("00000000-0000-0000-0000-000000002826");

  @Test
  void onePhaseCompositionStepIsConservativeBoundedDeterministicAndStoredInHistory() {
    TwoFluidPipe first = createGasPipe("first", 6);
    TwoFluidPipe repeated = createGasPipe("repeated", 6);

    applyInletComposition(first, 0.80, 0.20);
    applyInletComposition(repeated, 0.80, 0.20);
    first.runTransient(0.02, TRANSIENT_ID);
    repeated.runTransient(0.02, TRANSIENT_ID);

    TwoFluidComponentConservationReport report = first.getLastComponentConservationReport();
    assertNotNull(report);
    assertTrue(report.isConverged(), report.getMessage());
    assertTrue(report.getMaximumRelativeInventoryResidual() <= 1.0e-8, report.getMessage());
    assertTrue(report.getMinimumMassFraction() >= -1.0e-12, report.getMessage());
    assertTrue(report.getMaximumMassFraction() <= 1.0 + 1.0e-12, report.getMessage());
    assertTrue(report.getMaximumMassFractionSumError() <= 1.0e-12, report.getMessage());
    assertEquals(0.0, report.getMaximumInterphaseTransferResidualKg(), 1.0e-12);
    assertEquals(0.0, report.getInterphaseLatentHeatEnergyJ(), 1.0e-12);
    assertTrue(first.getComponentMassFractionProfile(Phase.GAS, "nitrogen")[0] > 0.05,
        "The inlet composition front must enter the first physical cell");

    assertArrayEquals(first.getComponentMassFractionProfile(Phase.GAS, "nitrogen"),
        repeated.getComponentMassFractionProfile(Phase.GAS, "nitrogen"), 0.0);
    assertArrayEquals(report.getFinalInventoryKg(), repeated.getLastComponentConservationReport().getFinalInventoryKg(),
        0.0);
    assertEquals(1, first.getComponentConservationHistory().size());
    assertTrue(first.getComponentConservationHistory().toJson().contains("interphaseLatentHeatEnergyJ"));

    double[] defensiveProfile = report.getPhaseMassFractionProfile(Phase.GAS, "nitrogen");
    defensiveProfile[0] = Double.NaN;
    assertTrue(Double.isFinite(report.getPhaseMassFractionProfile(Phase.GAS, "nitrogen")[0]));

    TwoFluidPipe copied = (TwoFluidPipe) first.copy();
    first.runTransient(0.02, TRANSIENT_ID);
    copied.runTransient(0.02, TRANSIENT_ID);
    assertArrayEquals(first.getComponentMassFractionProfile(Phase.GAS, "nitrogen"),
        copied.getComponentMassFractionProfile(Phase.GAS, "nitrogen"), 0.0);
    assertEquals(first.getLastComponentConservationReport().toJson(),
        copied.getLastComponentConservationReport().toJson());
  }

  @Test
  void changedComponentSlateFailsLoudly() {
    TwoFluidPipe pipe = createGasPipe("changed-slate", 4);
    SystemInterface changed = new SystemSrkEos(288.15, 70.0);
    changed.addComponent("methane", 0.80);
    changed.addComponent("CO2", 0.20);
    changed.setMixingRule("classic");
    changed.setTotalFlowRate(5.0, "kg/sec");
    pipe.getInletStream().setThermoSystem(changed);
    pipe.getInletStream().run();

    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
        () -> pipe.runTransient(0.01, TRANSIENT_ID));
    assertTrue(exception.getMessage().contains("component slate change"));
  }

  @Test
  void componentIdentityIsIndependentOfInletArrayOrder() {
    TwoFluidPipe pipe = createGasPipe("component-order", 4);
    SystemInterface reordered = new SystemSrkEos(288.15, 70.0);
    reordered.addComponent("nitrogen", 0.20);
    reordered.addComponent("methane", 0.80);
    reordered.setMixingRule("classic");
    reordered.setTotalFlowRate(5.0, "kg/sec");
    pipe.getInletStream().setThermoSystem(reordered);
    pipe.getInletStream().run();

    pipe.runTransient(0.01, TRANSIENT_ID);

    assertTrue(pipe.getLastComponentConservationReport().isConverged(),
        pipe.getLastComponentConservationReport().getMessage());
    assertArrayEquals(new String[] { "methane", "nitrogen" },
        pipe.getLastComponentConservationReport().getComponentNames());
    assertTrue(pipe.getComponentMassFractionProfile(Phase.GAS, "nitrogen")[0] > 0.05);
  }

  @Test
  void directOilWaterTransferFailsBeforeComponentAllocation() {
    SystemInterface fluid = createGas(0.95, 0.05);
    TwoFluidSection section = new TwoFluidSection(0.0, 1.0, 0.20, 0.0);
    section.setPressure(70.0e5);
    section.setTemperature(288.15);
    section.setGasMassPerLength(1.0);
    TwoFluidComponentTransport transport = new TwoFluidComponentTransport(fluid, new TwoFluidSection[] { section });

    double[][] faceFluxesKgS = new double[2][3];
    double[][] phaseSourcesKgPerMetreSecond = { { 1.0, -2.0, 1.0 } };
    IllegalStateException exception = assertThrows(IllegalStateException.class, () -> transport.advance(1.0,
        faceFluxesKgS, phaseSourcesKgPerMetreSecond, new TwoFluidSection[] { section }, fluid, fluid, 1.0e-8));
    assertTrue(exception.getMessage().contains("Direct oil-water component transfer"));
  }

  @Test
  void reverseFlowThroughInletFailsBeforeComponentAdvection() {
    SystemInterface fluid = createGas(0.95, 0.05);
    TwoFluidSection section = new TwoFluidSection(0.0, 1.0, 0.20, 0.0);
    section.setPressure(70.0e5);
    section.setTemperature(288.15);
    section.setGasMassPerLength(1.0);
    TwoFluidComponentTransport transport = new TwoFluidComponentTransport(fluid, new TwoFluidSection[] { section });

    double[][] faceFluxesKgS = new double[2][3];
    faceFluxesKgS[0][0] = -1.0;
    double[][] phaseSourcesKgPerMetreSecond = new double[1][3];
    IllegalStateException exception = assertThrows(IllegalStateException.class, () -> transport.advance(1.0,
        faceFluxesKgS, phaseSourcesKgPerMetreSecond, new TwoFluidSection[] { section }, fluid, fluid, 1.0e-8));
    assertTrue(exception.getMessage().contains("inlet boundary"));
  }

  @Test
  void invalidTransientDurationFailsBeforeHistoryMutation() {
    TwoFluidPipe pipe = createGasPipe("invalid-duration", 4);

    assertThrows(IllegalArgumentException.class, () -> pipe.runTransient(0.0, TRANSIENT_ID));
    assertThrows(IllegalArgumentException.class, () -> pipe.runTransient(-0.1, TRANSIENT_ID));
    assertThrows(IllegalArgumentException.class, () -> pipe.runTransient(Double.NaN, TRANSIENT_ID));
    assertEquals(0, pipe.getComponentConservationHistory().size());
  }

  @Test
  void nullFluidTemplatesFailLoudly() {
    SystemInterface fluid = createGas(0.95, 0.05);
    TwoFluidSection section = new TwoFluidSection(0.0, 1.0, 0.20, 0.0);
    section.setPressure(70.0e5);
    section.setTemperature(288.15);
    section.setGasMassPerLength(1.0);
    TwoFluidComponentTransport transport = new TwoFluidComponentTransport(fluid, new TwoFluidSection[] { section });

    double[][] faceFluxesKgS = new double[2][3];
    double[][] phaseSourcesKgPerMetreSecond = new double[1][3];
    IllegalArgumentException advanceException = assertThrows(IllegalArgumentException.class,
        () -> transport.advance(1.0, faceFluxesKgS, phaseSourcesKgPerMetreSecond, new TwoFluidSection[] { section },
            fluid, null, 1.0e-8));
    assertTrue(advanceException.getMessage().contains("Fluid template"));

    IllegalArgumentException stateException = assertThrows(IllegalArgumentException.class,
        () -> transport.createThermodynamicState(0, null, 70.0e5, 288.15));
    assertTrue(stateException.getMessage().contains("Fluid template"));
  }

  @Test
  @Tag("slow")
  @Timeout(value = 5, unit = TimeUnit.MINUTES)
  void closedWetGasTransitionClosesEveryComponentPhaseAndThermalLedger() throws Exception {
    SystemInterface wetGas = createWetGas();
    SystemInterface dewPointFluid = wetGas.clone();
    new ThermodynamicOperations(dewPointFluid).waterDewPointTemperatureMultiphaseFlash();
    double dewPointTemperatureK = dewPointFluid.getTemperature("K");
    wetGas.setTemperature(dewPointTemperatureK + 0.02, "K");

    Stream inlet = new Stream("component-transition-inlet", wetGas);
    inlet.setFlowRate(6.0, "kg/sec");
    inlet.setPressure(70.0, "bara");
    inlet.run();

    TwoFluidPipe pipe = new TwoFluidPipe("component-transition-pipe", inlet);
    pipe.setLength(20.0);
    pipe.setDiameter(0.20);
    pipe.setRoughness(1.0e-5);
    pipe.setNumberOfSections(2);
    pipe.setTimeIntegrationMethod(TimeIntegrator.Method.EULER);
    pipe.setEnableAdaptiveTimestepping(false);
    pipe.setEnableSlugTracking(false);
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
    pipe.setSurfaceTemperature(dewPointTemperatureK - 10.0, "K");

    double cumulativeWaterTransferKg = 0.0;
    double cumulativeLatentHeatJ = 0.0;
    for (int step = 0; step < 3; step++) {
      pipe.runTransient(0.10, TRANSIENT_ID);
      TwoFluidComponentConservationReport componentReport = pipe.getLastComponentConservationReport();
      TwoFluidMassBalanceReport phaseReport = pipe.getLastMassBalanceReport();
      TwoFluidThermalEnergyBalanceReport energyReport = pipe.getLastThermalEnergyBalanceReport();
      assertTrue(componentReport.isConverged(), componentReport.getMessage());
      assertTrue(componentReport.getMaximumInterphaseTransferResidualKg() <= 1.0e-10, componentReport.getMessage());
      assertTrue(phaseReport.isWithinTolerance(TwoFluidMassBalanceReport.Phase.TOTAL, 1.0e-7, 1.0e-10));
      assertTrue(energyReport.isWithinTolerance(1.0e-5, 1.0e-10),
          "Thermal residual was " + energyReport.getResidualJ() + " J");
      assertEquals(componentReport.getInterphaseLatentHeatEnergyJ(), energyReport.getLatentHeatEnergyJ(), 1.0e-8);
      for (String component : componentReport.getComponentNames()) {
        double transferResidualKg = componentReport.getInterphaseTransferKg(Phase.GAS, component)
            + componentReport.getInterphaseTransferKg(Phase.OIL, component)
            + componentReport.getInterphaseTransferKg(Phase.WATER, component);
        assertEquals(0.0, transferResidualKg, 1.0e-12, component);
      }
      cumulativeWaterTransferKg += componentReport.getInterphaseTransferKg(Phase.WATER, "water");
      cumulativeLatentHeatJ += componentReport.getInterphaseLatentHeatEnergyJ();
    }

    assertTrue(cumulativeWaterTransferKg > 0.0, "Cooling below the dew point must transfer water to aqueous phase");
    assertTrue(Math.abs(cumulativeLatentHeatJ) > 0.0, "Phase transfer must contribute compositional latent heat");
  }

  @Test
  @Tag("slow")
  @Timeout(value = 5, unit = TimeUnit.MINUTES)
  void onePhasePulseAgreesWithPipeFlowSystemAndRefinesTowardItsOutletResponse() {
    double durationSeconds = 0.8;
    double referenceOutlet = runPipeFlowSystemPulse(8, 2.0, durationSeconds);
    double coarseOutlet = runTwoFluidPulse("coarse-limit", 4, 2.0, durationSeconds, 0.04);
    double refinedOutlet = runTwoFluidPulse("refined-limit", 8, 2.0, durationSeconds, 0.02);
    double coarseDifference = Math.abs(coarseOutlet - referenceOutlet);
    double refinedDifference = Math.abs(refinedOutlet - referenceOutlet);

    assertTrue(coarseOutlet > 0.05 && refinedOutlet > 0.05,
        "The finite-duration nitrogen event must enter and propagate through both grids");
    assertTrue(coarseDifference < 0.08, "Coarse one-phase limit differs from PipeFlowSystem by " + coarseDifference);
    assertTrue(refinedDifference < 0.08, "Refined one-phase limit differs from PipeFlowSystem by " + refinedDifference);
    assertTrue(refinedDifference <= coarseDifference + 0.01,
        "Joint grid/time refinement should not move away from PipeFlowSystem: coarse=" + coarseDifference + ", refined="
            + refinedDifference);
  }

  private static TwoFluidPipe createGasPipe(String name, int sections) {
    return createGasPipe(name, sections, 20.0);
  }

  private static TwoFluidPipe createGasPipe(String name, int sections, double lengthMetres) {
    Stream inlet = new Stream(name + "-inlet", createGas(0.95, 0.05));
    inlet.setFlowRate(5.0, "kg/sec");
    inlet.run();

    TwoFluidPipe pipe = new TwoFluidPipe(name + "-pipe", inlet);
    pipe.setLength(lengthMetres);
    pipe.setDiameter(0.20);
    pipe.setRoughness(1.0e-5);
    pipe.setNumberOfSections(sections);
    pipe.setOutletPressure(69.0, "bara");
    pipe.setTimeIntegrationMethod(TimeIntegrator.Method.EULER);
    pipe.setEnableAdaptiveTimestepping(false);
    pipe.setEnableSlugTracking(false);
    pipe.setThermodynamicUpdateInterval(1);
    pipe.setSteadyStateMaxWallClockTime(1.0);
    pipe.setComponentTransportEnabled(true);
    pipe.setStoreComponentConservationHistory(true);
    pipe.run();
    return pipe;
  }

  private static double runTwoFluidPulse(String name, int sections, double lengthMetres, double durationSeconds,
      double macroTimeStepSeconds) {
    TwoFluidPipe pipe = createGasPipe(name, sections, lengthMetres);
    applyInletComposition(pipe, 0.80, 0.20);
    int steps = (int) Math.round(durationSeconds / macroTimeStepSeconds);
    for (int step = 0; step < steps; step++) {
      pipe.runTransient(macroTimeStepSeconds, TRANSIENT_ID);
      assertTrue(pipe.getLastComponentConservationReport().isConverged(),
          pipe.getLastComponentConservationReport().getMessage());
    }
    return pipe.getOutletComponentMassFraction(Phase.GAS, "nitrogen");
  }

  private static double runPipeFlowSystemPulse(int nodes, double lengthMetres, double durationSeconds) {
    PipeFlowSystem pipe = new PipeFlowSystem();
    pipe.setInletThermoSystem(createGas(0.95, 0.05));
    pipe.setNumberOfLegs(1);
    pipe.setNumberOfNodesInLeg(nodes);
    GeometryDefinitionInterface[] geometry = { new PipeData(), new PipeData() };
    for (GeometryDefinitionInterface section : geometry) {
      section.setDiameter(0.20);
      section.setInnerSurfaceRoughness(1.0e-5);
    }
    pipe.setEquipmentGeometry(geometry);
    pipe.setLegHeights(new double[] { 0.0, 0.0 });
    pipe.setLegPositions(new double[] { 0.0, lengthMetres });
    pipe.setLegOuterTemperatures(new double[] { 288.15, 288.15 });
    pipe.setLegWallHeatTransferCoefficients(new double[] { 0.0, 0.0 });
    pipe.setLegOuterHeatTransferCoefficients(new double[] { 0.0, 0.0 });
    pipe.createSystem();
    pipe.init();
    pipe.solveSteadyState(1);
    pipe.setConservativeSpeciesTransport(true);
    pipe.setFailOnNonConvergence(true);
    pipe.getTimeSeries().setTimes(new double[] { 0.0, durationSeconds });
    pipe.getTimeSeries().setInletThermoSystems(new SystemInterface[] { createGas(0.80, 0.20) });
    pipe.getTimeSeries().setNumberOfTimeStepsInInterval(1);
    pipe.solveTransient(1);
    double[][] profile = pipe.getSpeciesConservationReport().getMassFractionProfile();
    int nitrogen = componentIndex(pipe.getSpeciesConservationReport().getComponentNames(), "nitrogen");
    return profile[nitrogen][profile[nitrogen].length - 1];
  }

  private static int componentIndex(String[] componentNames, String sought) {
    for (int index = 0; index < componentNames.length; index++) {
      if (componentNames[index].equals(sought)) {
        return index;
      }
    }
    throw new IllegalArgumentException("Missing component " + sought);
  }

  private static void applyInletComposition(TwoFluidPipe pipe, double methane, double nitrogen) {
    SystemInterface gas = createGas(methane, nitrogen);
    pipe.getInletStream().setThermoSystem(gas);
    pipe.getInletStream().run();
  }

  private static SystemInterface createGas(double methane, double nitrogen) {
    SystemInterface fluid = new SystemSrkEos(288.15, 70.0);
    fluid.addComponent("methane", methane);
    fluid.addComponent("nitrogen", nitrogen);
    fluid.setMixingRule("classic");
    fluid.setTotalFlowRate(5.0, "kg/sec");
    return fluid;
  }

  private static SystemInterface createWetGas() {
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
    return fluid;
  }
}
