package neqsim.process.fastsimulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for the cached K-value fast process simulation proxy.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
class KValueProcessSimulatorTest {
  /** Tolerance for base-case flow comparisons. */
  private static final double FLOW_TOL = 1.0e-6;

  /**
   * Builds a simple two-source separator process.
   *
   * @return solved process system
   */
  private ProcessSystem buildSimpleSeparatorProcess() {
    SystemSrkEos gasRich = new SystemSrkEos(298.15, 50.0);
    gasRich.addComponent("methane", 0.95);
    gasRich.addComponent("nC10", 0.05);
    gasRich.setMixingRule("classic");

    SystemSrkEos oilRich = new SystemSrkEos(298.15, 50.0);
    oilRich.addComponent("methane", 0.30);
    oilRich.addComponent("nC10", 0.70);
    oilRich.setMixingRule("classic");

    ProcessSystem process = new ProcessSystem();
    Stream feedA = new Stream("Well-A", gasRich);
    feedA.setFlowRate(1000.0, "kg/hr");
    feedA.setTemperature(25.0, "C");
    feedA.setPressure(50.0, "bara");
    process.add(feedA);

    Stream feedB = new Stream("Well-B", oilRich);
    feedB.setFlowRate(1500.0, "kg/hr");
    feedB.setTemperature(25.0, "C");
    feedB.setPressure(50.0, "bara");
    process.add(feedB);

    Mixer mixer = new Mixer("Commingle");
    mixer.addStream(feedA);
    mixer.addStream(feedB);
    process.add(mixer);

    Separator separator = new Separator("Inlet Separator", mixer.getOutletStream());
    process.add(separator);
    process.add(new Stream("ExportGas", separator.getGasOutStream()));
    process.add(new Stream("ExportOil", separator.getLiquidOutStream()));
    process.run();
    return process;
  }

  /**
   * Builds a simple two-source gas/oil/water separator process.
   *
   * @return solved process system
   */
  private ProcessSystem buildThreePhaseSeparatorProcess() {
    SystemSrkEos hydrocarbonFluid = new SystemSrkEos(298.15, 10.0);
    hydrocarbonFluid.addComponent("methane", 0.55);
    hydrocarbonFluid.addComponent("ethane", 0.05);
    hydrocarbonFluid.addComponent("propane", 0.05);
    hydrocarbonFluid.addComponent("nC10", 0.30);
    hydrocarbonFluid.addComponent("water", 0.05);
    hydrocarbonFluid.setMixingRule("classic");
    hydrocarbonFluid.setMultiPhaseCheck(true);

    SystemSrkEos waterRichFluid = new SystemSrkEos(298.15, 10.0);
    waterRichFluid.addComponent("methane", 0.05);
    waterRichFluid.addComponent("ethane", 0.01);
    waterRichFluid.addComponent("propane", 0.01);
    waterRichFluid.addComponent("nC10", 0.03);
    waterRichFluid.addComponent("water", 0.90);
    waterRichFluid.setMixingRule("classic");
    waterRichFluid.setMultiPhaseCheck(true);

    ProcessSystem process = new ProcessSystem();
    Stream hydrocarbonFeed = new Stream("HydrocarbonWell", hydrocarbonFluid);
    hydrocarbonFeed.setFlowRate(1500.0, "kg/hr");
    hydrocarbonFeed.setTemperature(25.0, "C");
    hydrocarbonFeed.setPressure(10.0, "bara");
    process.add(hydrocarbonFeed);

    Stream waterFeed = new Stream("WaterWell", waterRichFluid);
    waterFeed.setFlowRate(300.0, "kg/hr");
    waterFeed.setTemperature(25.0, "C");
    waterFeed.setPressure(10.0, "bara");
    process.add(waterFeed);

    Mixer mixer = new Mixer("Wet Commingle");
    mixer.addStream(hydrocarbonFeed);
    mixer.addStream(waterFeed);
    process.add(mixer);

    ThreePhaseSeparator separator = new ThreePhaseSeparator("Three Phase Separator", mixer.getOutletStream());
    process.add(separator);
    process.add(new Stream("ExportGas3P", separator.getGasOutStream()));
    process.add(new Stream("ExportOil3P", separator.getOilOutStream()));
    process.add(new Stream("ExportWater3P", separator.getWaterOutStream()));
    process.run();
    return process;
  }

  /**
   * Builds the multistage PR reference process used by the production-allocation paper benchmark.
   *
   * @return solved process system
   */
  private ProcessSystem buildProductionAllocationReferenceProcess() {
    ProcessSystem process = new ProcessSystem();
    Stream feedGas = new Stream("Well-Gas",
	makePrFluid(new double[] { 0.80, 3.56, 84.0, 4.5, 2.0, 0.5, 0.9, 0.4, 0.5, 1.2, 0.8, 0.5, 0.3, 0.2, 0.04 }));
    feedGas.setFlowRate(120000.0, "kg/hr");
    feedGas.setTemperature(45.0, "C");
    feedGas.setPressure(75.0, "bara");
    process.add(feedGas);

    Stream feedOil = new Stream("Well-Oil",
	makePrFluid(new double[] { 0.30, 2.0, 45.0, 5.0, 4.0, 1.5, 2.5, 1.5, 2.0, 6.0, 7.0, 8.0, 6.0, 5.0, 4.0 }));
    feedOil.setFlowRate(90000.0, "kg/hr");
    feedOil.setTemperature(45.0, "C");
    feedOil.setPressure(75.0, "bara");
    process.add(feedOil);

    Mixer mixer = new Mixer("Commingling");
    mixer.addStream(feedGas);
    mixer.addStream(feedOil);
    process.add(mixer);

    Separator hpSeparator = new Separator("HP Separator", mixer.getOutletStream());
    process.add(hpSeparator);
    process.add(new Stream("HPGas", hpSeparator.getGasOutStream()));

    ThrottlingValve mpValve = new ThrottlingValve("MP Valve", hpSeparator.getLiquidOutStream());
    mpValve.setOutletPressure(8.6, "bara");
    process.add(mpValve);

    Heater oilHeater = new Heater("Oil Heater", mpValve.getOutletStream());
    oilHeater.setOutTemperature(90.0, "C");
    process.add(oilHeater);

    Separator mpSeparator = new Separator("MP Separator", oilHeater.getOutletStream());
    process.add(mpSeparator);
    process.add(new Stream("MPGas", mpSeparator.getGasOutStream()));

    ThrottlingValve lpValve = new ThrottlingValve("LP Valve", mpSeparator.getLiquidOutStream());
    lpValve.setOutletPressure(1.9, "bara");
    process.add(lpValve);

    Separator lpSeparator = new Separator("LP Separator", lpValve.getOutletStream());
    process.add(lpSeparator);
    process.add(new Stream("LPGas", lpSeparator.getGasOutStream()));
    process.add(new Stream("StabOil", lpSeparator.getLiquidOutStream()));
    process.run();
    return process;
  }

  /**
   * Creates a PR fluid using the paper benchmark component slate.
   *
   * @param composition unnormalised mole amounts for the reference slate
   * @return PR thermodynamic system
   */
  private SystemPrEos makePrFluid(double[] composition) {
    String[] defined = new String[] { "nitrogen", "CO2", "methane", "ethane", "propane", "i-butane", "n-butane",
	"i-pentane", "n-pentane" };
    String[] tbpNames = new String[] { "C6", "C7", "C8", "C9", "C10", "C12" };
    double[] tbpMolarMass = new double[] { 0.08499, 0.09787, 0.11000, 0.12500, 0.13700, 0.16100 };
    double[] tbpDensity = new double[] { 0.695, 0.718, 0.745, 0.766, 0.781, 0.804 };

    double total = 0.0;
    for (int i = 0; i < composition.length; i++) {
      total += composition[i];
    }
    SystemPrEos fluid = new SystemPrEos(273.15 + 45.0, 75.0);
    for (int i = 0; i < defined.length; i++) {
      fluid.addComponent(defined[i], composition[i] / total);
    }
    for (int i = 0; i < tbpNames.length; i++) {
      int compositionIndex = defined.length + i;
      fluid.addTBPfraction(tbpNames[i], composition[compositionIndex] / total, tbpMolarMass[i], tbpDensity[i]);
    }
    fluid.setMixingRule("classic");
    fluid.init(0);
    return fluid;
  }

  /**
   * Verifies that the cached K-value proxy reproduces a solved two-phase separator base case.
   */
  @Test
  void baseCaseKValueProxyMatchesSeparatorProducts() {
    ProcessSystem process = buildSimpleSeparatorProcess();
    Separator separator = (Separator) process.getUnit("Inlet Separator");

    KValueProcessSimulator simulator = process.createKValueProcessSimulator();
    KValueProcessResult result = simulator.run();

    assertNotNull(result);
    assertTrue(simulator.getUnitProfiles().get(1).usesKValueRouting());
    assertEquals(separator.getGasOutStream().getFlowRate("kg/hr"),
	result.getStreamTotalFlow("Inlet Separator.gasOutStream", "kg/hr"), FLOW_TOL);
    assertEquals(separator.getLiquidOutStream().getFlowRate("kg/hr"),
	result.getStreamTotalFlow("Inlet Separator.liquidOutStream", "kg/hr"), FLOW_TOL);
    assertEquals(result.getStreamTotalFlow("Inlet Separator.gasOutStream", "kg/hr"),
	result.getStreamTotalFlow("ExportGas", "kg/hr"), FLOW_TOL);
  }

  /**
   * Verifies that source rate scenarios conserve total terminal mass and can be run through ProcessSystem convenience
   * methods.
   */
  @Test
  void sourceRateScenarioConservesTerminalMass() {
    ProcessSystem process = buildSimpleSeparatorProcess();
    KValueProcessSimulator simulator = process.createKValueProcessSimulator();

    Map<String, Double> multipliers = new LinkedHashMap<String, Double>();
    multipliers.put("Well-B", 1.25);
    KValueProcessResult result = simulator.runWithSourceFlowMultipliers(multipliers);
    KValueProcessResult convenienceResult = process.runFastKValueSimulation();

    double sourceMass = result.getStreamTotalFlow("Well-A", "kg/hr") + result.getStreamTotalFlow("Well-B", "kg/hr");
    assertEquals(sourceMass, result.getTerminalTotalFlow("kg/hr"), 1.0e-8 * sourceMass);
    assertTrue(result.getMaxResidual() < 1.0e-8);
    assertNotNull(convenienceResult.toJson());
  }

  /**
   * Verifies that gas/oil/water separators use true three-phase K-value routing rather than frozen fallback factors.
   */
  @Test
  void threePhaseSeparatorUsesThreePhaseKValueRouting() {
    ProcessSystem process = buildThreePhaseSeparatorProcess();
    ThreePhaseSeparator separator = (ThreePhaseSeparator) process.getUnit("Three Phase Separator");

    KValueProcessSimulator simulator = process.createKValueProcessSimulator();
    KValueProcessResult result = simulator.run();

    assertTrue(simulator.getUnitProfiles().get(1).usesThreePhaseKValueRouting());
    assertNotNull(simulator.getUnitProfiles().get(1).getWaterLiquidKValues());
    assertTrue(separator.getGasOutStream().getFlowRate("kg/hr") > 0.0);
    assertTrue(separator.getOilOutStream().getFlowRate("kg/hr") > 0.0);
    assertTrue(separator.getWaterOutStream().getFlowRate("kg/hr") > 0.0);
    assertEquals(separator.getGasOutStream().getFlowRate("kg/hr"),
	result.getStreamTotalFlow("Three Phase Separator.gasOutStream", "kg/hr"), FLOW_TOL);
    assertEquals(separator.getOilOutStream().getFlowRate("kg/hr"),
	result.getStreamTotalFlow("Three Phase Separator.liquidOutStream", "kg/hr"), FLOW_TOL);
    assertEquals(separator.getWaterOutStream().getFlowRate("kg/hr"),
	result.getStreamTotalFlow("Three Phase Separator.waterOutStream", "kg/hr"), FLOW_TOL);
  }

  /**
   * Verifies that three-phase K-value routing remains conservative for changed water-rich source rates.
   */
  @Test
  void threePhaseSourceRateScenarioConservesTerminalMass() {
    ProcessSystem process = buildThreePhaseSeparatorProcess();
    KValueProcessSimulator simulator = process.createKValueProcessSimulator();

    Map<String, Double> multipliers = new LinkedHashMap<String, Double>();
    multipliers.put("WaterWell", 1.8);
    KValueProcessResult result = simulator.runWithSourceFlowMultipliers(multipliers);

    ((Stream) process.getUnit("WaterWell")).setFlowRate(540.0, "kg/hr");
    process.run();
    ThreePhaseSeparator separator = (ThreePhaseSeparator) process.getUnit("Three Phase Separator");

    double sourceMass = result.getStreamTotalFlow("HydrocarbonWell", "kg/hr")
	+ result.getStreamTotalFlow("WaterWell", "kg/hr");
    assertEquals(sourceMass, result.getTerminalTotalFlow("kg/hr"), 1.0e-8 * sourceMass);
    assertEquals(separator.getGasOutStream().getFlowRate("kg/hr"),
	result.getStreamTotalFlow("Three Phase Separator.gasOutStream", "kg/hr"),
	0.05 * separator.getGasOutStream().getFlowRate("kg/hr"));
    assertEquals(separator.getOilOutStream().getFlowRate("kg/hr"),
	result.getStreamTotalFlow("Three Phase Separator.liquidOutStream", "kg/hr"),
	0.05 * separator.getOilOutStream().getFlowRate("kg/hr"));
    assertEquals(separator.getWaterOutStream().getFlowRate("kg/hr"),
	result.getStreamTotalFlow("Three Phase Separator.waterOutStream", "kg/hr"),
	0.05 * separator.getWaterOutStream().getFlowRate("kg/hr"));
    assertTrue(result.getStreamTotalFlow("ExportWater3P", "kg/hr") > 0.0);
    assertTrue(result.getMaxResidual() < 1.0e-8);
  }

  /**
   * Verifies the public API sequence shown in the process documentation.
   */
  @Test
  void documentationExampleUsesKValueProcessSimulatorApi() {
    ProcessSystem process = buildSimpleSeparatorProcess();
    process.run();

    KValueProcessSimulator simulator = process.createKValueProcessSimulator();
    simulator.setMaxIterations(50);
    simulator.setTolerance(1.0e-9);

    Map<String, Double> multipliers = new LinkedHashMap<String, Double>();
    multipliers.put("Well-B", 1.25);

    KValueProcessResult result = simulator.runWithSourceFlowMultipliers(multipliers);
    double exportGasKgPerHr = result.getStreamTotalFlow("ExportGas", "kg/hr");
    double methaneKgPerHr = result.getStreamComponentFlow("ExportGas", "methane", "kg/hr");
    double totalProductKgPerHr = result.getTerminalTotalFlow("kg/hr");
    String resultJson = result.toJson();

    Map<String, Double> sourceRates = new LinkedHashMap<String, Double>();
    sourceRates.put("Well-A", 1200.0);
    KValueProcessResult rateCase = simulator.runWithSourceFlowRates(sourceRates, "kg/hr");

    KValueProcessBenchmarkResult benchmark = simulator.benchmarkSourceFlowMultipliers("Well-B",
	new double[] { 0.80, 1.00, 1.20 });
    double speedup = benchmark.getSpeedup();
    String benchmarkJson = benchmark.toJson();

    assertTrue(exportGasKgPerHr > 0.0);
    assertTrue(methaneKgPerHr > 0.0);
    assertTrue(totalProductKgPerHr > 0.0);
    assertNotNull(resultJson);
    assertEquals(1200.0, rateCase.getStreamTotalFlow("Well-A", "kg/hr"), FLOW_TOL);
    assertTrue(speedup > 0.0);
    assertNotNull(benchmarkJson);
  }

  /**
   * Benchmarks the K-value proxy on the same multistage reference process family as the recovery-factor allocation
   * paper.
   */
  @Test
  void benchmarkShowsProxyAdvantageOnReferenceProcess() {
    ProcessSystem process = buildProductionAllocationReferenceProcess();
    KValueProcessSimulator simulator = KValueProcessSimulator.fromBaseCase(process);
    KValueProcessBenchmarkResult benchmark = simulator.benchmarkSourceFlowMultipliers("Well-Oil",
	new double[] { 0.80, 1.00, 1.20, 1.40 });

    assertTrue(benchmark.getProxyAverageTimeNanos() > 0.0);
    assertTrue(benchmark.getRigorousAverageTimeNanos() > 0.0);
    assertTrue(benchmark.getSpeedup() > 1.0, benchmark.toJson());
    assertTrue(benchmark.getMaxTerminalMassDeviation() < 1.0e-8, benchmark.toJson());
  }
}
