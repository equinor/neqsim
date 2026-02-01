package neqsim.process.equipment.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkCPAstatoil;

/**
 * Tests for emissions calculation utilities.
 */
public class EmissionsCalculatorTest {

  @Test
  void testBasicEmissionsCalculation() {
    // Create a simple gas stream with known composition
    SystemSrkCPAstatoil gasFluid = new SystemSrkCPAstatoil(273.15 + 25.0, 1.01325);
    gasFluid.addComponent("CO2", 0.50);
    gasFluid.addComponent("methane", 0.40);
    gasFluid.addComponent("ethane", 0.08);
    gasFluid.addComponent("propane", 0.02);
    gasFluid.setMixingRule(10);

    Stream gasStream = new Stream("Test Gas", gasFluid);
    gasStream.setFlowRate(100.0, "kg/hr");
    gasStream.run();

    // Calculate emissions
    EmissionsCalculator calc = new EmissionsCalculator(gasStream);
    calc.calculate();

    // Verify total gas rate
    assertEquals(100.0, calc.getTotalGasRate("kg/hr"), 1.0);

    // Verify CO2 equivalent calculation includes all components
    double co2eq = calc.getCO2Equivalents("kg/hr");
    assertTrue(co2eq > 0, "CO2 equivalents should be positive");

    // Verify report generation
    String report = calc.generateReport();
    assertTrue(report.contains("CO2"), "Report should contain CO2");
    assertTrue(report.contains("Methane"), "Report should contain Methane");
  }

  @Test
  void testProducedWaterDegassingSystem() {
    // Create system with Gudrun-like conditions
    ProducedWaterDegassingSystem system = new ProducedWaterDegassingSystem("Test PW System");

    // Configure
    system.setWaterFlowRate(100.0, "m3/hr");
    system.setWaterTemperature(80.0, "C");
    system.setInletPressure(30.0, "bara");
    system.setDegasserPressure(4.0, "bara");
    system.setCFUPressure(1.0, "bara");
    system.setSalinity(10.0, "wt%");

    // Set gas composition (typical Gudrun)
    system.setDissolvedGasComposition(new String[] {"CO2", "methane", "ethane", "propane"},
        new double[] {0.51, 0.44, 0.04, 0.01});

    // Run simulation
    system.run();

    // Get emissions report
    String report = system.getEmissionsReport();
    System.out.println(report);

    // Verify emissions are calculated
    double totalCO2 = system.getTotalCO2EmissionRate("kg/hr");
    double totalCH4 = system.getTotalMethaneEmissionRate("kg/hr");
    double totalCO2eq = system.getTotalCO2Equivalents("tonnes/year");

    assertTrue(totalCO2 >= 0, "CO2 emission should be non-negative");
    assertTrue(totalCH4 >= 0, "Methane emission should be non-negative");
    assertTrue(totalCO2eq >= 0, "CO2 equivalents should be non-negative");

    // Verify JSON export works
    var data = system.toMap();
    assertTrue(data.containsKey("processConditions"));
    assertTrue(data.containsKey("totalEmissions"));
  }

  @Test
  void testEmissionsFromSeparator() {
    // Create fluid representing produced water at separator outlet
    SystemSrkCPAstatoil fluid = new SystemSrkCPAstatoil(273.15 + 80.0, 30.0);
    fluid.addComponent("water", 0.90);
    fluid.addComponent("CO2", 0.03);
    fluid.addComponent("methane", 0.05);
    fluid.addComponent("ethane", 0.02);
    fluid.setMixingRule(10);
    fluid.setMultiPhaseCheck(true);

    Stream inlet = new Stream("PW Inlet", fluid);
    inlet.setFlowRate(100000.0, "kg/hr");
    inlet.run();

    // Create degasser (flash to lower pressure)
    ThreePhaseSeparator degasser = new ThreePhaseSeparator("Degasser", inlet);
    degasser.run();

    // Calculate emissions from gas outlet
    EmissionsCalculator calc = new EmissionsCalculator(degasser);
    calc.calculate();

    System.out.println("\n=== Separator Emissions Test ===");
    System.out.println(calc.generateReport());

    // Gas composition should show emissions
    var composition = calc.getGasCompositionMass();
    assertTrue(composition.size() > 0 || calc.getTotalGasRate("kg/hr") == 0,
        "Should have composition or no gas");
  }

  @Test
  void testCumulativeTracking() {
    // Create gas stream
    SystemSrkCPAstatoil gasFluid = new SystemSrkCPAstatoil(273.15 + 25.0, 1.01325);
    gasFluid.addComponent("CO2", 0.50);
    gasFluid.addComponent("methane", 0.50);
    gasFluid.setMixingRule(10);

    Stream gasStream = new Stream("Test Gas", gasFluid);
    gasStream.setFlowRate(100.0, "kg/hr");
    gasStream.run();

    EmissionsCalculator calc = new EmissionsCalculator(gasStream);
    calc.calculate();

    // Simulate 24 hours of operation
    for (int hour = 0; hour < 24; hour++) {
      calc.updateCumulative(1.0); // 1 hour time step
    }

    // Verify cumulative tracking
    assertEquals(24.0, calc.getTotalRunTime(), 0.1);
    assertTrue(calc.getCumulativeCO2("kg") > 0, "Cumulative CO2 should be positive");
    assertTrue(calc.getCumulativeMethane("kg") > 0, "Cumulative methane should be positive");

    // Test reset
    calc.resetCumulative();
    assertEquals(0.0, calc.getTotalRunTime(), 0.001);
  }

  @Test
  void testGWMFCalculation() {
    // Create gas stream with known flow rate
    SystemSrkCPAstatoil gasFluid = new SystemSrkCPAstatoil(273.15 + 25.0, 1.01325);
    gasFluid.addComponent("methane", 1.0);
    gasFluid.setMixingRule(10);

    Stream gasStream = new Stream("Test Gas", gasFluid);
    gasStream.setFlowRate(100.0, "kg/hr"); // 100 kg/hr gas
    gasStream.run();

    EmissionsCalculator calc = new EmissionsCalculator(gasStream);
    calc.calculate();

    // Calculate GWMF: 100 kg/hr = 100,000 g/hr
    // For 100 m³/hr water and 10 bar pressure drop
    double gwmf = calc.calculateGWMF(100.0, 10.0);

    // Expected: 100,000 g/hr / 100 m³/hr / 10 bar = 100 g/m³/bar
    assertEquals(100.0, gwmf, 1.0);
  }

  @Test
  void testUnitConversions() {
    SystemSrkCPAstatoil gasFluid = new SystemSrkCPAstatoil(273.15 + 25.0, 1.01325);
    gasFluid.addComponent("CO2", 1.0);
    gasFluid.setMixingRule(10);

    Stream gasStream = new Stream("Test Gas", gasFluid);
    gasStream.setFlowRate(1000.0, "kg/hr"); // 1000 kg/hr = 1 tonne/hr
    gasStream.run();

    EmissionsCalculator calc = new EmissionsCalculator(gasStream);
    calc.calculate();

    // Test unit conversions
    double kghr = calc.getCO2EmissionRate("kg/hr");
    double tonneshr = calc.getCO2EmissionRate("tonnes/hr");
    double tonnesday = calc.getCO2EmissionRate("tonnes/day");
    double tonnesyear = calc.getCO2EmissionRate("tonnes/year");

    assertEquals(kghr / 1000.0, tonneshr, 0.01);
    assertEquals(tonneshr * 24.0, tonnesday, 0.1);
    assertEquals(tonneshr * 8760.0, tonnesyear, 1.0);
  }

  /**
   * Test Norwegian handbook conventional calculation methods.
   */
  @Test
  void testConventionalHandbookMethods() {
    // Norwegian offshore emission handbook values:
    // f_CH4 = 14 g/(m³·bar), f_nmVOC = 3.5 g/(m³·bar)

    // Test case: 100,000 m³/year water, 50 bar pressure drop
    double annualWaterVolume = 100000.0; // m³/year
    double pressureDrop = 50.0; // bar

    // Expected CH4: 14 * 100,000 * 50 = 70,000,000 g = 70 tonnes/year
    double expectedCH4 = 70.0;
    double calculatedCH4 =
        EmissionsCalculator.calculateConventionalCH4(annualWaterVolume, pressureDrop);
    assertEquals(expectedCH4, calculatedCH4, 0.1, "Conventional CH4 calculation");

    // Expected nmVOC: 3.5 * 100,000 * 50 = 17,500,000 g = 17.5 tonnes/year
    double expectedNMVOC = 17.5;
    double calculatedNMVOC =
        EmissionsCalculator.calculateConventionalNMVOC(annualWaterVolume, pressureDrop);
    assertEquals(expectedNMVOC, calculatedNMVOC, 0.1, "Conventional nmVOC calculation");

    // Test combined emissions calculation
    var convEmissions =
        EmissionsCalculator.calculateConventionalEmissions(annualWaterVolume, pressureDrop);
    assertEquals(expectedCH4, convEmissions.get("CH4_tonnes"), 0.1);
    assertEquals(expectedNMVOC, convEmissions.get("nmVOC_tonnes"), 0.1);

    // CO2 equivalents: CH4 * 28 + nmVOC * 2.2
    // = 70 * 28 + 17.5 * 2.2 = 1960 + 38.5 = 1998.5 tonnes/year
    double expectedCO2eq = 1998.5;
    assertEquals(expectedCO2eq, convEmissions.get("CO2eq_tonnes"), 1.0,
        "Conventional CO2eq calculation");
  }

  /**
   * Test GWR (Gas-to-Water Ratio) calculation methods.
   */
  @Test
  void testGWRCalculation() {
    // Test static GWR calculation from kmol to Sm³
    // 1 kmol = 22.414 Sm³ at standard conditions
    double gasMoles_kmol = 10.0; // kmol
    double waterVolume_m3 = 224.14; // m³
    double gwr = EmissionsCalculator.calculateGWR(gasMoles_kmol, waterVolume_m3);
    // Expected: 10 * 22.414 / 224.14 = 1.0 Sm³/m³
    assertEquals(1.0, gwr, 0.01, "GWR should be calculated correctly from kmol");

    // Test with zero water volume (should return 0.0 not infinity)
    double gwrZero = EmissionsCalculator.calculateGWR(100.0, 0.0);
    assertEquals(0.0, gwrZero, 0.001, "GWR with zero water should return 0");
  }

  /**
   * Test tuned kij parameters functionality.
   */
  @Test
  void testTunedKijParameters() {
    // Create system
    ProducedWaterDegassingSystem system = new ProducedWaterDegassingSystem("Tuned kij Test");

    // Configure
    system.setWaterFlowRate(100.0, "m3/hr");
    system.setWaterTemperature(80.0, "C");
    system.setInletPressure(65.0, "bara");
    system.setDegasserPressure(4.0, "bara");
    system.setCFUPressure(1.2, "bara");
    system.setSalinity(3.5, "wt%");
    system.setDissolvedGasComposition(new String[] {"CO2", "methane", "ethane", "propane"},
        new double[] {0.50, 0.45, 0.04, 0.01});

    // Enable tuned kij parameters
    system.setTunedInteractionParameters(true);

    // Set lab validation data
    system.setLabGWR(0.85);

    // Run and check validation
    system.run();

    var validation = system.getValidationResults();
    assertTrue(validation != null, "Validation results should be available");
    assertTrue(validation.containsKey("GWR"), "Should have GWR results");
    assertTrue(validation.containsKey("passesRegulatoryCriteria"),
        "Should have pass/fail assessment");
    assertTrue(validation.containsKey("regulatoryTolerancePercent"), "Should have tolerance info");

    // Check GWR details
    @SuppressWarnings("unchecked")
    var gwrResults = (java.util.Map<String, Double>) validation.get("GWR");
    assertTrue(gwrResults.containsKey("model_Sm3_Sm3"), "Should have model GWR");
    assertTrue(gwrResults.containsKey("lab_Sm3_Sm3"), "Should have lab GWR");
    assertTrue(gwrResults.containsKey("deviation_percent"), "Should have deviation");

    System.out.println("Validation results: " + validation);
  }

  /**
   * Test method comparison report generation.
   */
  @Test
  void testMethodComparisonReport() {
    // Create system
    ProducedWaterDegassingSystem system = new ProducedWaterDegassingSystem("Comparison Test");

    // Configure
    system.setWaterFlowRate(100.0, "m3/hr");
    system.setWaterTemperature(80.0, "C");
    system.setInletPressure(65.0, "bara");
    system.setDegasserPressure(4.0, "bara");
    system.setCFUPressure(1.2, "bara");
    system.setCaissonPressure(1.013, "bara");
    system.setSalinity(3.5, "wt%");
    system.setDissolvedGasComposition(new String[] {"CO2", "methane", "ethane", "propane"},
        new double[] {0.50, 0.45, 0.04, 0.01});

    // Run
    system.run();

    // Get comparison report
    String report = system.getMethodComparisonReport();
    System.out.println(report);

    // Verify report contains expected sections
    assertTrue(report.contains("METHOD COMPARISON"), "Should have comparison header");
    assertTrue(report.contains("Thermodynamic"), "Should mention thermodynamic method");
    assertTrue(report.contains("Conventional"), "Should mention conventional method");
    assertTrue(report.contains("CO2"), "Should include CO2 data");
    assertTrue(report.contains("Aktivitetsforskriften"), "Should reference Norwegian regulation");
  }
}
