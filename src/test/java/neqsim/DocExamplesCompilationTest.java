package neqsim;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import neqsim.integration.EOSComparison;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.CoolingWaterSystem;
import neqsim.process.equipment.heatexchanger.FiredHeater;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.util.fielddevelopment.DCFCalculator;
import neqsim.process.util.heatintegration.PinchAnalyzer;
import neqsim.process.util.optimizer.DebottleneckAnalyzer;
import neqsim.process.util.optimizer.MonteCarloSimulator;
import neqsim.process.util.optimizer.SensitivityAnalysis;
import neqsim.process.util.report.HeatMaterialBalance;
import neqsim.process.util.report.ProcessValidator;
import neqsim.pvtsimulation.flowassurance.HydrateRiskMapper;
import neqsim.thermo.system.FluidBuilder;
import neqsim.thermo.system.SystemInterface;

/**
 * Verifies that every code example shown in the engineering utilities documentation actually
 * compiles and runs without error.
 *
 * @author copilot
 * @version 1.0
 */
public class DocExamplesCompilationTest {

  /**
   * FluidBuilder fluent API example from docs/util/engineering_utilities.md.
   */
  @Test
  public void testFluidBuilderFluentAPI() {
    SystemInterface fluid = FluidBuilder.create(273.15 + 25.0, 60.0).addComponent("methane", 0.85)
        .addComponent("ethane", 0.10).addComponent("propane", 0.05).withMixingRule("classic")
        .build();
    assertNotNull(fluid);
    assertEquals(3, fluid.getNumberOfComponents());
  }

  /**
   * FluidBuilder with EOS selection from docs.
   */
  @Test
  public void testFluidBuilderWithEOS() {
    SystemInterface fluid = FluidBuilder.create(273.15 + 80.0, 200.0)
        .withEOS(FluidBuilder.EOSType.PR).addComponent("methane", 0.50)
        .addComponent("n-hexane", 0.50).withMixingRule("classic").withMultiPhaseCheck().build();
    assertNotNull(fluid);
  }

  /**
   * FluidBuilder oil characterization from docs.
   */
  @Test
  public void testFluidBuilderOilCharacterization() {
    SystemInterface oil = FluidBuilder.create(273.15 + 80.0, 250.0).withEOS(FluidBuilder.EOSType.PR)
        .addComponent("methane", 0.30).addComponent("ethane", 0.08)
        .addTBPFraction("C7", 0.06, 0.092, 0.727).addTBPFraction("C8", 0.05, 0.104, 0.749)
        .addPlusFraction("C20", 0.23, 0.350, 0.88).withLumpedComponents(6).withMixingRule("classic")
        .build();
    assertNotNull(oil);
  }

  /**
   * FluidBuilder preset fluids from docs.
   */
  @Test
  public void testFluidBuilderPresets() {
    assertNotNull(FluidBuilder.leanNaturalGas(298.15, 60.0));
    assertNotNull(FluidBuilder.richNaturalGas(298.15, 60.0));
    assertNotNull(FluidBuilder.typicalBlackOil(353.15, 250.0));
    assertNotNull(FluidBuilder.gasCondensate(353.15, 150.0));
    assertNotNull(FluidBuilder.dryExportGas(298.15, 70.0));
    assertNotNull(FluidBuilder.co2Rich(298.15, 80.0));
    assertNotNull(FluidBuilder.acidGas(298.15, 50.0));
  }

  /**
   * HeatMaterialBalance usage from docs.
   */
  @Test
  public void testHeatMaterialBalanceDoc() {
    SystemInterface gas = new neqsim.thermo.system.SystemSrkEos(298.15, 50.0);
    gas.addComponent("methane", 0.9);
    gas.addComponent("ethane", 0.1);
    gas.setMixingRule("classic");

    Stream feed = new Stream("feed", gas);
    feed.setFlowRate(1000.0, "kg/hr");
    Separator sep = new Separator("Sep", feed);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(sep);
    process.run();

    HeatMaterialBalance hmb = new HeatMaterialBalance(process);
    String json = hmb.toJson();
    String csv = hmb.streamTableToCSV();

    hmb.setTemperatureUnit("K").setPressureUnit("barg").setFlowUnit("kg/sec");

    assertNotNull(json);
    assertNotNull(csv);
    assertFalse(json.isEmpty());
  }

  /**
   * SensitivityAnalysis usage from docs.
   */
  @Test
  public void testSensitivityAnalysisDoc() {
    SystemInterface gas = new neqsim.thermo.system.SystemSrkEos(298.15, 50.0);
    gas.addComponent("methane", 0.9);
    gas.addComponent("ethane", 0.1);
    gas.setMixingRule("classic");

    Stream feed = new Stream("feed", gas);
    feed.setFlowRate(1000.0, "kg/hr");
    Compressor comp = new Compressor("Comp", feed);
    comp.setOutletPressure(100.0);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(comp);
    process.run();

    SensitivityAnalysis sa = new SensitivityAnalysis(process);

    sa.setParameter("Outlet Pressure", 70.0, 150.0, 8, (proc, val) -> {
      Compressor c = (Compressor) proc.getUnit("Comp");
      c.setOutletPressure(val);
    });

    sa.addOutput("Power (kW)", proc -> {
      Compressor c = (Compressor) proc.getUnit("Comp");
      return c.getPower("kW");
    });

    SensitivityAnalysis.SensitivityResult result = sa.run();
    assertNotNull(result.toJson());
    assertTrue(result.getSize() > 0);
    assertNotNull(result.getOutput("Power (kW)"));
    assertNotNull(result.getParameterValues());
  }

  /**
   * MonteCarloSimulator usage from docs.
   */
  @Test
  public void testMonteCarloSimulatorDoc() {
    SystemInterface gas = new neqsim.thermo.system.SystemSrkEos(298.15, 50.0);
    gas.addComponent("methane", 0.9);
    gas.addComponent("ethane", 0.1);
    gas.setMixingRule("classic");

    Stream feed = new Stream("feed", gas);
    feed.setFlowRate(1000.0, "kg/hr");
    Compressor comp = new Compressor("Comp", feed);
    comp.setOutletPressure(100.0);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(comp);
    process.run();

    MonteCarloSimulator mc = new MonteCarloSimulator(process, 10);
    mc.setSeed(42);

    mc.addTriangularParameter("Outlet P", 80.0, 100.0, 150.0, (proc, val) -> {
      Compressor c = (Compressor) proc.getUnit("Comp");
      c.setOutletPressure(val);
    });

    mc.setOutputExtractor("Power (kW)", proc -> {
      Compressor c = (Compressor) proc.getUnit("Comp");
      return c.getPower("kW");
    });

    MonteCarloSimulator.MonteCarloResult result = mc.run();
    assertTrue(result.getP10() > 0);
    assertTrue(result.getP50() > 0);
    assertTrue(result.getP90() > 0);
    assertTrue(result.getMean() > 0);
    assertTrue(result.getStdDev() >= 0);
    // getProbabilityBelow used in doc
    double prob = result.getProbabilityBelow(0);
    assertTrue(prob >= 0 && prob <= 100);
    assertNotNull(result.toJson());
    assertNotNull(result.getTornado());
  }

  /**
   * ConvergenceDiagnostics usage from docs.
   */
  @Test
  public void testConvergenceDiagnosticsDoc() {
    SystemInterface gas = new neqsim.thermo.system.SystemSrkEos(298.15, 50.0);
    gas.addComponent("methane", 0.9);
    gas.addComponent("ethane", 0.1);
    gas.setMixingRule("classic");

    Stream feed = new Stream("feed", gas);
    feed.setFlowRate(1000.0, "kg/hr");

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.run();

    neqsim.process.equipment.util.ConvergenceDiagnostics diag =
        new neqsim.process.equipment.util.ConvergenceDiagnostics(process);
    neqsim.process.equipment.util.ConvergenceDiagnostics.DiagnosticReport report = diag.analyze();

    // Doc API calls
    boolean converged = report.isConverged();
    List<String> suggestions = report.getSuggestions();
    String json = report.toJson();
    assertNotNull(json);
    assertNotNull(suggestions);
    assertNotNull(report.getRecycleStatuses());
    assertNotNull(report.getAdjusterStatuses());
  }

  /**
   * HydrateRiskMapper usage from docs.
   */
  @Test
  public void testHydrateRiskMapperDoc() {
    SystemInterface fluid = new neqsim.thermo.system.SystemSrkEos(298.15, 100.0);
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("ethane", 0.05);
    fluid.addComponent("propane", 0.03);
    fluid.addComponent("water", 0.07);
    fluid.setMixingRule("classic");

    HydrateRiskMapper mapper = new HydrateRiskMapper(fluid);

    // Add pipeline profile (km, bara, C) as shown in docs
    mapper.addProfilePoint(0.0, 100.0, 60.0);
    mapper.addProfilePoint(10.0, 95.0, 40.0);
    mapper.addProfilePoint(50.0, 75.0, 4.0);

    mapper.setRiskThresholds(3.0, 6.0);

    HydrateRiskMapper.RiskProfile profile = mapper.calculate();

    for (HydrateRiskMapper.RiskPoint rp : profile.getPoints()) {
      // doc references these exact fields
      double dist = rp.distanceKm;
      HydrateRiskMapper.RiskLevel level = rp.riskLevel;
      double subcooling = rp.subcoolingC;
      assertNotNull(level);
    }

    assertNotNull(profile.toJson());
    assertNotNull(profile.getOverallRisk());
    assertTrue(profile.getCriticalPointCount() >= 0);
  }

  /**
   * EOSComparison usage from docs.
   */
  @Test
  public void testEOSComparisonDoc() {
    EOSComparison comp = new EOSComparison();
    comp.addComponent("methane", 0.85);
    comp.addComponent("ethane", 0.10);
    comp.addComponent("propane", 0.05);
    comp.setConditions(273.15 + 25.0, 60.0);
    comp.setEOSTypes(EOSComparison.EOSType.SRK, EOSComparison.EOSType.PR,
        EOSComparison.EOSType.GERG2008);

    EOSComparison.ComparisonResult result = comp.compare();
    assertNotNull(result.toJson());

    // Check property deviations as in doc
    double densityDev = result.getMaxDeviation("density");
    assertTrue(densityDev >= 0);
  }

  /**
   * PinchAnalyzer usage from docs/process/engineering_utilities_v2.md.
   */
  @Test
  public void testPinchAnalyzerDoc() {
    ProcessSystem process = new ProcessSystem();
    PinchAnalyzer analyzer = new PinchAnalyzer(process);
    analyzer.setMinApproachTemperature(10.0);

    // Manual stream specification as in doc
    analyzer.addHotStream("Hot1", 473.15, 323.15, 150000.0);
    analyzer.addColdStream("Cold1", 293.15, 393.15, 100000.0);
    analyzer.analyze();

    // All doc API calls
    double pinchT = analyzer.getPinchTemperature();
    double minHot = analyzer.getMinHotUtilityDuty();
    double minCold = analyzer.getMinColdUtilityDuty();
    double recovery = analyzer.getEnergyRecoveryFraction();
    List<PinchAnalyzer.HeatExchangerMatch> matches = analyzer.getMatches();

    List<double[]> hotCurve = analyzer.getHotCompositeCurve();
    List<double[]> coldCurve = analyzer.getColdCompositeCurve();
    List<double[]> grandCurve = analyzer.getGrandCompositeCurve();

    String json = analyzer.toJson();

    assertNotNull(json);
    assertNotNull(matches);
    assertNotNull(hotCurve);
    assertNotNull(coldCurve);
    assertNotNull(grandCurve);
    assertTrue(recovery >= 0.0 && recovery <= 1.0);
  }

  /**
   * DCFCalculator usage from docs.
   */
  @Test
  public void testDCFCalculatorDoc() {
    DCFCalculator dcf = new DCFCalculator();
    dcf.setDiscountRate(0.08);
    dcf.setProjectLifeYears(20);
    dcf.setTaxRate(0.22);
    dcf.setRoyaltyRate(0.0);
    dcf.setDepreciationYears(6.0);
    dcf.setInflationRate(0.02);

    dcf.addCapex(0, 500e6);
    dcf.addCapex(1, 300e6);

    double[] production = new double[20];
    for (int i = 2; i < 20; i++) {
      production[i] = 10e6;
    }
    dcf.setAnnualProduction(production);
    dcf.setProductPrice(1.5);
    dcf.setAnnualOpex(50e6);

    dcf.calculate();

    double npv = dcf.getNPV();
    double irr = dcf.getIRR();
    int payback = dcf.getPaybackYear();
    double pi = dcf.getProfitabilityIndex();
    double[] cashFlow = dcf.getAnnualCashFlow();
    double[] discounted = dcf.getDiscountedCashFlow();
    String json = dcf.toJson();

    assertNotNull(json);
    assertNotNull(cashFlow);
    assertNotNull(discounted);
    assertEquals(20, cashFlow.length);
  }

  /**
   * DebottleneckAnalyzer usage from docs.
   */
  @Test
  public void testDebottleneckAnalyzerDoc() {
    SystemInterface gas = new neqsim.thermo.system.SystemSrkEos(298.15, 50.0);
    gas.addComponent("methane", 0.9);
    gas.addComponent("ethane", 0.1);
    gas.setMixingRule("classic");

    Stream feed = new Stream("feed", gas);
    feed.setFlowRate(1000.0, "kg/hr");
    Separator sep = new Separator("Sep", feed);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(sep);
    process.run();

    DebottleneckAnalyzer analyzer = new DebottleneckAnalyzer(process);
    analyzer.setWarningThreshold(0.85);
    analyzer.setCriticalThreshold(0.95);
    analyzer.analyze();

    String primary = analyzer.getPrimaryBottleneck();
    double util = analyzer.getOverallUtilization();
    int overloaded = analyzer.getOverloadedCount();

    List<DebottleneckAnalyzer.EquipmentStatus> ranked = analyzer.getRankedEquipment();
    for (DebottleneckAnalyzer.EquipmentStatus es : ranked) {
      // doc references these exact fields
      String name = es.name;
      String type = es.type;
      double maxU = es.maxUtilization;
      String status = es.status;
      String suggestion = es.suggestion;
    }

    List<DebottleneckAnalyzer.EquipmentStatus> constrained = analyzer.getConstrainedEquipment();
    String json = analyzer.toJson();
    assertNotNull(json);
  }

  /**
   * FiredHeater usage from docs.
   */
  @Test
  public void testFiredHeaterDoc() {
    SystemInterface gas = new neqsim.thermo.system.SystemSrkEos(298.15, 50.0);
    gas.addComponent("methane", 0.9);
    gas.addComponent("ethane", 0.1);
    gas.setMixingRule("classic");

    Stream feedStream = new Stream("feed", gas);
    feedStream.setFlowRate(1000.0, "kg/hr");

    FiredHeater heater = new FiredHeater("Crude Heater", feedStream);
    heater.setOutTemperature(273.15 + 350.0);
    heater.setThermalEfficiency(0.85);
    heater.setFuelLHV(48.0e6);
    heater.setFuelCO2Factor(2.75);
    heater.setNoxFactor(0.08);
    heater.setStackTemperature(273.15 + 150.0);

    ProcessSystem process = new ProcessSystem();
    process.add(feedStream);
    process.add(heater);
    process.run();

    double absorbedDuty = heater.getAbsorbedDuty("kW");
    double firedDuty = heater.getFiredDuty("kW");
    double stackLoss = heater.getStackLoss("kW");
    double fuel = heater.getFuelConsumption("kg/hr");
    double co2 = heater.getCO2Emissions("kg/hr");
    double co2Annual = heater.getCO2Emissions("tonnes/hr") * 8760;
    double nox = heater.getNOxEmissions("kg/hr");
    String json = heater.toJson();
    assertNotNull(json);
  }

  /**
   * ProcessValidator usage from docs.
   */
  @Test
  public void testProcessValidatorDoc() {
    SystemInterface gas = new neqsim.thermo.system.SystemSrkEos(298.15, 50.0);
    gas.addComponent("methane", 0.9);
    gas.addComponent("ethane", 0.1);
    gas.setMixingRule("classic");

    Stream feed = new Stream("feed", gas);
    feed.setFlowRate(1000.0, "kg/hr");
    Separator sep = new Separator("Sep", feed);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(sep);
    process.run();

    ProcessValidator validator = new ProcessValidator(process);
    validator.setMassBalanceTolerance(0.001);
    validator.setTemperatureLimits(150.0, 1000.0);
    validator.setPressureLimits(1.0, 500.0);
    validator.validate();

    boolean passed = validator.isValid();
    int errors = validator.getErrorCount();
    int warnings = validator.getWarningCount();

    List<ProcessValidator.ValidationIssue> issues = validator.getIssues();
    for (ProcessValidator.ValidationIssue issue : issues) {
      // doc references these exact fields
      ProcessValidator.Severity sev = issue.severity;
      String loc = issue.location;
      String msg = issue.message;
      double val = issue.value;
    }

    List<ProcessValidator.ValidationIssue> errorsOnly = validator.getErrors();
    String json = validator.toJson();
    assertNotNull(json);
  }

  /**
   * CoolingWaterSystem usage from docs.
   */
  @Test
  public void testCoolingWaterSystemDoc() {
    CoolingWaterSystem cws = new CoolingWaterSystem();
    cws.addCoolingRequirement("After-Cooler", 5000.0, 40.0, 10.0);
    cws.addCoolingRequirement("Condenser", 3000.0, 55.0, 15.0);
    cws.setCoolingWaterSupplyTemperature(25.0);
    cws.setCoolingWaterReturnTemperature(35.0);
    cws.setSystemPressureDrop(3.0);
    cws.setPumpEfficiency(0.75);
    cws.setElectricityCost(0.10);
    cws.setAnnualOperatingHours(8000.0);
    cws.calculate();

    double cwFlow = cws.getTotalCWFlowRate();
    double pumpPower = cws.getPumpPower();
    double fanPower = cws.getTowerFanPower();
    double totalPower = cws.getTotalElectricalPower();
    double annualCost = cws.getAnnualOperatingCost();
    String json = cws.toJson();

    assertNotNull(json);
    assertTrue(cwFlow > 0);
    assertTrue(pumpPower > 0);
  }
}
