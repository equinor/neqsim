package neqsim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import neqsim.integration.EOSComparison;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.CoolingWaterSystem;
import neqsim.process.equipment.heatexchanger.FiredHeater;
import neqsim.process.equipment.pipeline.twophasepipe.closure.InterfacialFriction;
import neqsim.process.equipment.pipeline.twophasepipe.closure.InterfacialFriction.InterfacialFrictionResult;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.mechanicaldesign.heatexchanger.BellDelawareMethod;
import neqsim.process.mechanicaldesign.heatexchanger.LMTDcorrectionFactor;
import neqsim.process.mechanicaldesign.heatexchanger.ThermalDesignCalculator;
import neqsim.process.mechanicaldesign.heatexchanger.VibrationAnalysis;
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

  // ===========================================================================
  // Thermal-Hydraulic Design Documentation Examples
  // (from docs/process/mechanical_design/thermal_hydraulic_design.md)
  // ===========================================================================

  /**
   * Standalone ThermalDesignCalculator example from Quick Start section.
   */
  @Test
  public void testThermalDesignCalculatorStandaloneDoc() {
    ThermalDesignCalculator calc = new ThermalDesignCalculator();

    // Geometry: 3/4" tubes, 500mm shell
    calc.setTubeODm(0.01905);
    calc.setTubeIDm(0.01483);
    calc.setTubeLengthm(6.096);
    calc.setTubeCount(100);
    calc.setTubePasses(2);
    calc.setTubePitchm(0.02381);
    calc.setTriangularPitch(true);
    calc.setShellIDm(0.5);
    calc.setBaffleSpacingm(0.2);
    calc.setBaffleCount(10);
    calc.setBaffleCut(0.25);

    // Tube side: cooling water
    calc.setTubeSideFluid(998.0, 0.001, 4180.0, 0.60, 5.0, true);

    // Shell side: process gas
    calc.setShellSideFluid(50.0, 1.5e-5, 2200.0, 0.03, 3.0);

    // Fouling resistances (m2*K/W)
    calc.setFoulingTube(0.00018);
    calc.setFoulingShell(0.00035);

    calc.calculate();

    assertTrue(calc.getTubeSideHTC() > 0, "Tube-side HTC should be positive");
    assertTrue(calc.getShellSideHTC() > 0, "Shell-side HTC should be positive");
    assertTrue(calc.getOverallU() > 0, "Overall U should be positive");
    assertTrue(calc.getTubeSidePressureDropBar() > 0, "Tube-side dP should be positive");
    assertTrue(calc.getShellSidePressureDropBar() > 0, "Shell-side dP should be positive");
  }

  /**
   * Bell-Delaware method direct usage from documentation.
   */
  @Test
  public void testBellDelawareDirectUsageDoc() {
    double de = BellDelawareMethod.calcShellEquivDiameter(0.01905, 0.02381, true);
    assertTrue(de > 0, "Shell equivalent diameter should be positive");

    double aCross = BellDelawareMethod.calcCrossflowArea(0.5, 0.2, 0.01905, 0.02381);
    assertTrue(aCross > 0, "Crossflow area should be positive");

    double hKern = BellDelawareMethod.calcKernShellSideHTC(50.0, de, 1.5e-5, 2200.0, 0.03, 1.5e-5);
    assertTrue(hKern > 0, "Kern shell-side HTC should be positive");

    double Jc = BellDelawareMethod.calcJc(0.25);
    assertTrue(Jc > 0 && Jc < 2.0, "Jc should be in reasonable range");

    double Jl = BellDelawareMethod.calcJl(0.0004, 0.003, aCross, 100, 0.01905, 0.2);
    assertTrue(Jl > 0 && Jl <= 1.0, "Jl should be between 0 and 1");

    double Jb = BellDelawareMethod.calcJb(0.005, aCross, true, 2, 15);
    assertTrue(Jb > 0 && Jb <= 1.0, "Jb should be between 0 and 1");

    double Js = BellDelawareMethod.calcJs(0.2, 0.25, 0.25, 10);
    assertTrue(Js > 0, "Js should be positive");

    double Jr = BellDelawareMethod.calcJr(20000.0, 15);
    assertTrue(Jr > 0, "Jr should be positive");
  }

  /**
   * LMTD correction factor usage from documentation.
   */
  @Test
  public void testLMTDCorrectionFactorDoc() {
    double Ft = LMTDcorrectionFactor.calcFt1ShellPass(150.0, 90.0, 30.0, 80.0);
    assertTrue(Ft > 0.0 && Ft <= 1.0, "Ft for 1 shell pass should be between 0 and 1");

    double R = LMTDcorrectionFactor.calcR(150.0, 90.0, 30.0, 80.0);
    assertTrue(R > 0, "R should be positive");

    double P = LMTDcorrectionFactor.calcP(150.0, 90.0, 30.0, 80.0);
    assertTrue(P > 0 && P < 1.0, "P should be between 0 and 1");

    double Ft2 = LMTDcorrectionFactor.calcFt(150.0, 90.0, 30.0, 80.0, 2);
    assertTrue(Ft2 >= Ft, "2 shell passes should give Ft >= 1 shell pass");

    int passes = LMTDcorrectionFactor.requiredShellPasses(200.0, 50.0, 30.0, 170.0);
    assertTrue(passes >= 1, "Should need at least 1 shell pass");
  }

  /**
   * Vibration screening complete example from documentation.
   */
  @Test
  public void testVibrationScreeningDoc() {
    VibrationAnalysis.VibrationResult result = VibrationAnalysis.performScreening(0.01905, // tube
                                                                                           // OD (m)
        0.01483, // tube ID (m)
        0.4, // unsupported span (m)
        0.02381, // tube pitch (m)
        200e9, // Young's modulus (Pa) - carbon steel
        7800.0, // tube material density (kg/m3)
        2.0, // shell-side crossflow velocity (m/s)
        50.0, // shell fluid density (kg/m3)
        800.0, // tube fluid density (kg/m3)
        0.5, // shell ID (m)
        340.0, // sonic velocity in shell fluid (m/s)
        0.03, // damping ratio
        true); // triangular pitch

    assertNotNull(result, "Vibration result should not be null");
    assertNotNull(result.getSummary(), "Summary should not be null");
    assertTrue(result.naturalFrequencyHz > 0, "Natural frequency should be positive");
    assertTrue(result.vortexSheddingFrequencyHz > 0, "Vortex shedding freq should be positive");
  }

  /**
   * Individual vibration calculations from documentation.
   */
  @Test
  public void testVibrationIndividualCalcsDoc() {
    double fn = VibrationAnalysis.calcNaturalFrequency(0.01905, 0.01483, 0.4, 200e9, 7800.0, 800.0,
        50.0, "pinned");
    assertTrue(fn > 0, "Natural frequency should be positive");

    double fvs = VibrationAnalysis.calcVortexSheddingFrequency(2.0, 0.01905, 0.02381);
    assertTrue(fvs > 0, "Vortex shedding frequency should be positive");

    double vCrit =
        VibrationAnalysis.calcCriticalVelocityConnors(fn, 0.01905, 0.03, 1.5, 50.0, true);
    assertTrue(vCrit > 0, "Critical velocity should be positive");

    double fac = VibrationAnalysis.calcAcousticFrequency(0.5, 340.0, 1);
    assertTrue(fac > 0, "Acoustic frequency should be positive");
  }

  /**
   * Zone-by-zone analysis from documentation.
   */
  @Test
  public void testZoneByZoneAnalysisDoc() {
    ThermalDesignCalculator calc = new ThermalDesignCalculator();
    // Configure geometry
    calc.setTubeODm(0.01905);
    calc.setTubeIDm(0.01483);
    calc.setTubeLengthm(6.096);
    calc.setTubeCount(100);
    calc.setTubePasses(2);
    calc.setTubePitchm(0.02381);
    calc.setTriangularPitch(true);
    calc.setShellIDm(0.5);
    calc.setBaffleSpacingm(0.2);
    calc.setBaffleCount(10);
    calc.setBaffleCut(0.25);

    ThermalDesignCalculator.ZoneDefinition zone1 = new ThermalDesignCalculator.ZoneDefinition();
    zone1.zoneName = "desuperheating";
    zone1.dutyFraction = 0.15;
    zone1.totalDuty = 150000.0;
    zone1.lmtd = 40.0;
    zone1.tubeDensity = 5.0;
    zone1.tubeViscosity = 1.2e-5;
    zone1.tubeCp = 2100.0;
    zone1.tubeConductivity = 0.025;
    zone1.shellDensity = 998.0;
    zone1.shellViscosity = 0.001;
    zone1.shellCp = 4180.0;
    zone1.shellConductivity = 0.60;

    ThermalDesignCalculator.ZoneDefinition[] zones =
        new ThermalDesignCalculator.ZoneDefinition[] {zone1};

    ThermalDesignCalculator.ZoneResult[] results = calc.calculateZones(zones);
    assertNotNull(results, "Zone results should not be null");
    assertEquals(1, results.length, "Should have 1 zone result");
    ThermalDesignCalculator.ZoneResult zr = results[0];
    assertEquals("desuperheating", zr.zoneName);
    assertTrue(zr.overallU > 0, "Zone overall U should be positive");
    assertTrue(zr.requiredArea > 0, "Zone required area should be positive");
  }

  /**
   * Bell-Delaware shell-side method selection from documentation.
   */
  @Test
  public void testShellSideMethodSelectionDoc() {
    ThermalDesignCalculator calc = new ThermalDesignCalculator();

    calc.setTubeODm(0.01905);
    calc.setTubeIDm(0.01483);
    calc.setTubeLengthm(6.096);
    calc.setTubeCount(100);
    calc.setTubePasses(2);
    calc.setTubePitchm(0.02381);
    calc.setTriangularPitch(true);
    calc.setShellIDm(0.5);
    calc.setBaffleSpacingm(0.2);
    calc.setBaffleCount(10);
    calc.setBaffleCut(0.25);
    calc.setTubeSideFluid(998.0, 0.001, 4180.0, 0.60, 5.0, true);
    calc.setShellSideFluid(50.0, 1.5e-5, 2200.0, 0.03, 3.0);

    // Test Kern method (default)
    calc.setShellSideMethod(ThermalDesignCalculator.ShellSideMethod.KERN);
    calc.calculate();
    double uKern = calc.getOverallU();
    assertTrue(uKern > 0, "Kern overall U should be positive");

    // Test Bell-Delaware method
    calc.setShellSideMethod(ThermalDesignCalculator.ShellSideMethod.BELL_DELAWARE);
    calc.calculate();
    double uBD = calc.getOverallU();
    assertTrue(uBD > 0, "Bell-Delaware overall U should be positive");
  }

  /**
   * Full mechanical design integration from documentation.
   */
  @Test
  public void testFullMechanicalDesignIntegrationDoc() {
    neqsim.thermo.system.SystemSrkEos fluid =
        new neqsim.thermo.system.SystemSrkEos(273.15 + 60.0, 20.0);
    fluid.addComponent("methane", 120.0);
    fluid.addComponent("ethane", 120.0);
    fluid.addComponent("n-heptane", 3.0);
    fluid.createDatabase(true);
    fluid.setMixingRule(2);

    Stream hot = new Stream("hot", fluid);
    hot.setTemperature(100.0, "C");
    hot.setFlowRate(1000.0, "kg/hr");

    Stream cold = new Stream("cold", (neqsim.thermo.system.SystemInterface) fluid.clone());
    cold.setTemperature(20.0, "C");
    cold.setFlowRate(310.0, "kg/hr");

    neqsim.process.equipment.heatexchanger.HeatExchanger hx =
        new neqsim.process.equipment.heatexchanger.HeatExchanger("E-100", hot, cold);
    hx.setUAvalue(1000.0);

    ProcessSystem ps = new ProcessSystem();
    ps.add(hot);
    ps.add(cold);
    ps.add(hx);
    ps.run();

    hx.initMechanicalDesign();
    neqsim.process.mechanicaldesign.heatexchanger.HeatExchangerMechanicalDesign design =
        hx.getMechanicalDesign();
    design.calcDesign();

    assertNotNull(design.getSelectedType(), "Selected type should not be null");
    assertNotNull(design.getSelectedSizingResult(), "Sizing result should not be null");
    assertTrue(design.getSelectedSizingResult().getRequiredArea() > 0,
        "Required area should be positive");
    assertTrue(design.getWeightTotal() > 0, "Weight should be positive");
  }

  /**
   * Rating mode in process simulation from documentation.
   */
  @Test
  public void testRatingModeDocExample() {
    neqsim.thermo.system.SystemSrkEos hotFluid =
        new neqsim.thermo.system.SystemSrkEos(273.15 + 100.0, 20.0);
    hotFluid.addComponent("methane", 0.85);
    hotFluid.addComponent("ethane", 0.10);
    hotFluid.addComponent("propane", 0.05);
    hotFluid.setMixingRule("classic");

    Stream hotStream = new Stream("hot", hotFluid);
    hotStream.setTemperature(100.0, "C");
    hotStream.setFlowRate(1000.0, "kg/hr");

    Stream coldStream = new Stream("cold", (neqsim.thermo.system.SystemInterface) hotFluid.clone());
    coldStream.setTemperature(20.0, "C");
    coldStream.setFlowRate(500.0, "kg/hr");

    neqsim.process.equipment.heatexchanger.HeatExchanger hx =
        new neqsim.process.equipment.heatexchanger.HeatExchanger("E-100", hotStream, coldStream);

    ThermalDesignCalculator ratingCalc = new ThermalDesignCalculator();
    ratingCalc.setTubeODm(0.01905);
    ratingCalc.setTubeIDm(0.01483);
    ratingCalc.setTubeLengthm(6.096);
    ratingCalc.setTubeCount(100);
    ratingCalc.setTubePasses(2);
    ratingCalc.setTubePitchm(0.02381);
    ratingCalc.setTriangularPitch(true);
    ratingCalc.setShellIDm(0.5);
    ratingCalc.setBaffleSpacingm(0.2);
    ratingCalc.setBaffleCount(10);

    hx.setRatingCalculator(ratingCalc);
    hx.setRatingArea(50.0);

    ProcessSystem ps = new ProcessSystem();
    ps.add(hotStream);
    ps.add(coldStream);
    ps.add(hx);
    ps.run();

    double computedU = hx.getRatingU();
    assertTrue(computedU >= 0, "Computed U from rating mode should be non-negative");
  }

  /**
   * ThermalDesignCalculator.toJson() as referenced in CHANGELOG, PR_DESCRIPTION, and
   * neqsim-api-patterns SKILL.md documentation.
   */
  @Test
  public void testThermalDesignCalculatorToJsonDoc() {
    ThermalDesignCalculator calc = new ThermalDesignCalculator();
    calc.setTubeODm(0.01905);
    calc.setTubeIDm(0.01483);
    calc.setTubeLengthm(6.096);
    calc.setTubeCount(100);
    calc.setTubePasses(2);
    calc.setTubePitchm(0.02381);
    calc.setTriangularPitch(true);
    calc.setShellIDm(0.489);
    calc.setBaffleSpacingm(0.15);
    calc.setBaffleCount(30);
    calc.setBaffleCut(0.25);
    calc.setTubeSideFluid(995.0, 0.0008, 4180.0, 0.62, 5.0, true);
    calc.setShellSideFluid(820.0, 0.003, 2200.0, 0.13, 8.0);
    calc.setShellSideMethod(ThermalDesignCalculator.ShellSideMethod.BELL_DELAWARE);
    calc.calculate();

    String json = calc.toJson();
    assertNotNull(json, "toJson() should not return null");
    assertFalse(json.isEmpty(), "toJson() should not return empty string");
    assertTrue(json.contains("overallU_Wpm2K"), "JSON should contain overallU_Wpm2K");
    assertTrue(json.contains("bellDelawareCorrections"),
        "JSON should contain Bell-Delaware corrections");

    Map<String, Object> map = calc.toMap();
    assertNotNull(map, "toMap() should not return null");
    assertTrue(map.containsKey("tubeSide"), "Map should contain tubeSide");
    assertTrue(map.containsKey("shellSide"), "Map should contain shellSide");
    assertTrue(map.containsKey("overallHeatTransfer"), "Map should contain overallHeatTransfer");
  }

  /**
   * InterfacialFriction Hart correlation as corrected in two_fluid_model.md — instance method with
   * 8 parameters returning InterfacialFrictionResult.
   */
  @Test
  public void testInterfacialFrictionHartCorrelationDoc() {
    InterfacialFriction ifCalc = new InterfacialFriction();
    InterfacialFrictionResult result = ifCalc.calcHartCorrelation(10.0, // gasVelocity m/s
        0.5, // liquidVelocity m/s
        50.0, // gasDensity kg/m3
        800.0, // liquidDensity kg/m3
        1.5e-5, // gasViscosity Pa.s
        0.001, // liquidViscosity Pa.s
        0.3, // liquidHoldup
        0.3); // diameter m
    assertNotNull(result, "Hart correlation result should not be null");
    assertTrue(result.frictionFactor >= 0, "Friction factor should be non-negative");
  }

  /**
   * InterfacialFriction Andreussi-Persen correlation as corrected in two_fluid_model.md — instance
   * method with 8 parameters returning InterfacialFrictionResult.
   */
  @Test
  public void testInterfacialFrictionAndreussiPersenDoc() {
    InterfacialFriction ifCalc = new InterfacialFriction();
    InterfacialFrictionResult result = ifCalc.calcAndreussiPersenCorrelation(10.0, // gasVelocity
                                                                                   // m/s
        0.5, // liquidVelocity m/s
        50.0, // gasDensity kg/m3
        800.0, // liquidDensity kg/m3
        1.5e-5, // gasViscosity Pa.s
        0.3, // liquidHoldup
        0.3, // diameter m
        0.0); // inclination radians (horizontal)
    assertNotNull(result, "Andreussi-Persen result should not be null");
    assertTrue(result.frictionFactor >= 0, "Friction factor should be non-negative");
  }

  /**
   * Gas scrubber mechanical design + TR3500 conformity check example from
   * docs/process/equipment/separators.md. Verifies the full API path used by the Kollsnes scrubber
   * performance task (GasScrubber → initMechanicalDesign → GasScrubberMechanicalDesign setters →
   * setConformityRules("TR3500") → checkConformity()).
   */
  @Test
  public void testGasScrubberConformityCheckDoc() {
    // Build a simple natural-gas feed
    neqsim.thermo.system.SystemSrkEos fluid =
        new neqsim.thermo.system.SystemSrkEos(273.15 + 40.0, 80.0);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("ethane", 0.05);
    fluid.addComponent("propane", 0.03);
    fluid.addComponent("n-butane", 0.02);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(100000.0, "kg/hr");
    feed.setTemperature(40.0, "C");
    feed.setPressure(80.0, "bara");

    neqsim.process.equipment.separator.GasScrubber scrubber =
        new neqsim.process.equipment.separator.GasScrubber("25-VA301", feed);
    scrubber.setInternalDiameter(2.900);
    scrubber.setSeparatorLength(4.230);
    scrubber.setOrientation("vertical");
    scrubber.initMechanicalDesign();

    neqsim.process.mechanicaldesign.separator.GasScrubberMechanicalDesign d =
        (neqsim.process.mechanicaldesign.separator.GasScrubberMechanicalDesign) scrubber
            .getMechanicalDesign();
    d.setMaxOperationPressure(100.0);
    d.setInletNozzleID((762.0 - 2 * 62.75) / 1000.0);
    d.setInletDevice("schoepentoeter");
    d.setMeshPad(Math.PI / 4.0 * 2.9 * 2.9, 250.0);
    d.setDemistingCyclones(256, 0.110, 3.287, 0.943);
    d.setDrainPipeDiameterM(8 * 25.4 / 1000.0);
    d.setLaHHElevationM(0.930);
    d.setLaHElevationM(0.830);
    d.setConformityRules("TR3500");

    ProcessSystem proc = new ProcessSystem();
    proc.add(feed);
    proc.add(scrubber);
    proc.run();

    neqsim.process.mechanicaldesign.separator.conformity.ConformityReport rep = d.checkConformity();
    assertNotNull(rep, "Conformity report must not be null");
    assertFalse(rep.getResults().isEmpty(), "Report must contain at least one check");
    for (neqsim.process.mechanicaldesign.separator.conformity.ConformityResult r : rep
        .getResults()) {
      assertNotNull(r.getCheckName());
      assertNotNull(r.getStatus());
    }
  }
}
