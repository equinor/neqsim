package neqsim.process.fielddevelopment.reservoir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.fielddevelopment.reservoir.ReservoirCouplingExporter.FlowRateType;
import neqsim.process.fielddevelopment.reservoir.ReservoirCouplingExporter.VfpTable;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Unit tests for ProcessSystemLiftCurveGenerator.
 *
 * @author ESOL
 */
public class ProcessSystemLiftCurveGeneratorTest {

  private SystemInterface baseFluid;
  private ProcessSystem separationProcess;

  @BeforeEach
  void setUp() {
    // Create base fluid with typical oil/gas composition
    baseFluid = new SystemSrkEos(330.0, 35.0);
    baseFluid.addComponent("nitrogen", 0.5);
    baseFluid.addComponent("CO2", 2.0);
    baseFluid.addComponent("methane", 65.0);
    baseFluid.addComponent("ethane", 8.0);
    baseFluid.addComponent("propane", 5.0);
    baseFluid.addComponent("i-butane", 1.5);
    baseFluid.addComponent("n-butane", 2.5);
    baseFluid.addComponent("i-pentane", 1.0);
    baseFluid.addComponent("n-pentane", 1.5);
    baseFluid.addComponent("n-hexane", 3.0);
    baseFluid.addComponent("n-heptane", 4.0);
    baseFluid.addComponent("n-octane", 3.0);
    baseFluid.addComponent("nC10", 3.0);
    baseFluid.setMixingRule("classic");

    // Create typical offshore separation process
    separationProcess = createOilGasSeparationProcess();
  }

  /**
   * Creates a typical three-stage oil/gas separation process.
   */
  private ProcessSystem createOilGasSeparationProcess() {
    ProcessSystem process = new ProcessSystem("Offshore Separation Train");

    // Inlet well stream
    SystemInterface inlet = baseFluid.clone();
    inlet.setTemperature(330.0);
    inlet.setPressure(35.0);

    Stream wellStream = new Stream("well stream", inlet);
    wellStream.setFlowRate(100000.0, "kg/hr");
    wellStream.setTemperature(57.0, "C");
    wellStream.setPressure(35.0, "bara");
    process.add(wellStream);

    // HP Separator (35 bara)
    ThreePhaseSeparator hpSep = new ThreePhaseSeparator("HP Separator", wellStream);
    process.add(hpSep);

    // HP to MP valve
    ThrottlingValve hpValve = new ThrottlingValve("HP to MP Valve", hpSep.getOilOutStream());
    hpValve.setOutletPressure(10.0);
    process.add(hpValve);

    // MP Separator (10 bara)
    ThreePhaseSeparator mpSep = new ThreePhaseSeparator("MP Separator", hpValve.getOutletStream());
    process.add(mpSep);

    // MP to LP valve
    ThrottlingValve mpValve = new ThrottlingValve("MP to LP Valve", mpSep.getOilOutStream());
    mpValve.setOutletPressure(2.5);
    process.add(mpValve);

    // LP Separator (2.5 bara)
    ThreePhaseSeparator lpSep = new ThreePhaseSeparator("LP Separator", mpValve.getOutletStream());
    process.add(lpSep);

    // Export stable oil
    Stream stableOil = new Stream("stable oil", lpSep.getOilOutStream());
    process.add(stableOil);

    // Gas mixing and compression
    Mixer gasMixer = new Mixer("Gas Mixer");
    gasMixer.addStream(hpSep.getGasOutStream());
    gasMixer.addStream(mpSep.getGasOutStream());
    gasMixer.addStream(lpSep.getGasOutStream());
    process.add(gasMixer);

    // Export compressor
    Compressor compressor = new Compressor("Export Compressor", gasMixer.getOutletStream());
    compressor.setOutletPressure(70.0);
    compressor.setPolytropicEfficiency(0.78);
    process.add(compressor);

    // Export gas stream
    Stream exportGas = new Stream("export gas", compressor.getOutletStream());
    process.add(exportGas);

    return process;
  }

  @Test
  @DisplayName("Test generator creation with process system")
  void testGeneratorCreationWithProcessSystem() {
    ProcessSystemLiftCurveGenerator generator =
        new ProcessSystemLiftCurveGenerator(separationProcess, baseFluid);

    assertNotNull(generator);
    assertEquals("well stream", generator.getInletStreamName());
    assertEquals("export gas", generator.getExportGasStreamName());
    assertEquals("stable oil", generator.getExportOilStreamName());
  }

  @Test
  @DisplayName("Test generator creation with base fluid only")
  void testGeneratorCreationWithFluidOnly() {
    ProcessSystemLiftCurveGenerator generator = new ProcessSystemLiftCurveGenerator(baseFluid);

    assertNotNull(generator);
    generator.setProcessSystem(separationProcess);
    generator.setInletStreamName("well stream");
    generator.setExportGasStreamName("export gas");
    generator.setExportOilStreamName("stable oil");

    assertEquals("well stream", generator.getInletStreamName());
    assertEquals("export gas", generator.getExportGasStreamName());
    assertEquals("stable oil", generator.getExportOilStreamName());
  }

  @Test
  @DisplayName("Test VFP table generation with small parameter space")
  void testVfpTableGenerationSmall() {
    ProcessSystemLiftCurveGenerator generator =
        new ProcessSystemLiftCurveGenerator(separationProcess, baseFluid);

    // Set small parameter space for quick testing
    generator.setFlowRateRange(1000.0, 5000.0, 3);
    generator.setThpRange(25.0, 45.0, 3);
    generator.setWaterCutRange(0.0, 0.3, 2);
    generator.setGorRange(150.0, 250.0, 2);
    generator.setAlmRange(0.0, 0.0, 1);
    generator.setInletTemperature(330.0);

    VfpTable vfp = generator.generateVfpTable(1, "PLATFORM-1");

    assertNotNull(vfp);
    assertEquals(1, vfp.getTableNumber());
    assertEquals("PLATFORM-1", vfp.getWellName());
    assertEquals(3, vfp.getFlowRates().length);
    assertEquals(3, vfp.getThpValues().length);
    assertEquals(2, vfp.getWctValues().length);
    assertEquals(2, vfp.getGorValues().length);
    assertEquals(1, vfp.getAlmValues().length);

    // Verify BHP array dimensions
    assertNotNull(vfp.getBhpValues());
    assertEquals(3, vfp.getBhpValues().length);
    assertEquals(3, vfp.getBhpValues()[0].length);
    assertEquals(2, vfp.getBhpValues()[0][0].length);
  }

  @Test
  @DisplayName("Test BHP increases with flow rate")
  void testBhpIncreasesWithFlowRate() {
    ProcessSystemLiftCurveGenerator generator =
        new ProcessSystemLiftCurveGenerator(separationProcess, baseFluid);

    generator.setFlowRateRange(1000.0, 8000.0, 5);
    generator.setThpRange(35.0, 35.0, 1);
    generator.setWaterCutRange(0.2, 0.2, 1);
    generator.setGorRange(200.0, 200.0, 1);
    generator.setAlmRange(0.0, 0.0, 1);

    VfpTable vfp = generator.generateVfpTable(1, "TEST");

    // BHP should generally increase with flow rate
    double prevBhp = 0;
    for (int i = 0; i < vfp.getFlowRates().length; i++) {
      double bhp = vfp.getBhpValues()[i][0][0][0][0];
      assertTrue(bhp > 0, "BHP should be positive");
      if (i > 0) {
        assertTrue(bhp > prevBhp, "BHP should increase with flow rate");
      }
      prevBhp = bhp;
    }
  }

  @Test
  @DisplayName("Test BHP increases with THP")
  void testBhpIncreasesWithThp() {
    ProcessSystemLiftCurveGenerator generator =
        new ProcessSystemLiftCurveGenerator(separationProcess, baseFluid);

    generator.setFlowRateRange(3000.0, 3000.0, 1);
    generator.setThpRange(20.0, 60.0, 5);
    generator.setWaterCutRange(0.1, 0.1, 1);
    generator.setGorRange(180.0, 180.0, 1);
    generator.setAlmRange(0.0, 0.0, 1);

    VfpTable vfp = generator.generateVfpTable(1, "TEST");

    // BHP should increase with THP
    double prevBhp = 0;
    for (int i = 0; i < vfp.getThpValues().length; i++) {
      double bhp = vfp.getBhpValues()[0][i][0][0][0];
      assertTrue(bhp > 0, "BHP should be positive");
      if (i > 0) {
        assertTrue(bhp > prevBhp, "BHP should increase with THP");
      }
      prevBhp = bhp;
    }
  }

  @Test
  @DisplayName("Test Eclipse keyword generation")
  void testEclipseKeywordGeneration() {
    ProcessSystemLiftCurveGenerator generator =
        new ProcessSystemLiftCurveGenerator(separationProcess, baseFluid);

    generator.setFlowRateRange(1000.0, 4000.0, 3);
    generator.setThpRange(30.0, 50.0, 2);
    generator.setWaterCutRange(0.0, 0.2, 2);
    generator.setGorRange(150.0, 200.0, 2);
    generator.setAlmRange(0.0, 0.0, 1);
    generator.setProcessDescription("Test Separation Process");

    generator.generateVfpTable(1, "TEST-WELL");

    String keywords = generator.getEclipseKeywords();

    assertNotNull(keywords);
    assertTrue(keywords.contains("VFPPROD"));
    assertTrue(keywords.contains("TEST-WELL"));
    assertTrue(keywords.contains("NeqSim"));
    assertTrue(keywords.contains("LIQ")); // Default flow rate type
    assertTrue(keywords.contains("WCT"));
    assertTrue(keywords.contains("GOR"));
    assertTrue(keywords.contains("THP"));
    assertTrue(keywords.contains("BHP"));
  }

  @Test
  @DisplayName("Test CSV export format")
  void testCsvExport() {
    ProcessSystemLiftCurveGenerator generator =
        new ProcessSystemLiftCurveGenerator(separationProcess, baseFluid);

    generator.setFlowRateRange(2000.0, 4000.0, 2);
    generator.setThpRange(35.0, 45.0, 2);
    generator.setWaterCutRange(0.1, 0.3, 2);
    generator.setGorRange(180.0, 220.0, 2);
    generator.setAlmRange(0.0, 0.0, 1);

    generator.generateVfpTable(1, "CSV-TEST");

    String csv = generator.exportToCsv();

    assertNotNull(csv);
    assertTrue(csv.contains("TableNumber"));
    assertTrue(csv.contains("WellName"));
    assertTrue(csv.contains("FlowRate_Sm3d"));
    assertTrue(csv.contains("BHP_bara"));
    assertTrue(csv.contains("ExportGas_Sm3d"));
    assertTrue(csv.contains("ExportOil_Sm3d"));
    assertTrue(csv.contains("Power_kW"));
    assertTrue(csv.contains("CSV-TEST"));

    // Check that data rows are present
    String[] lines = csv.split("\n");
    assertTrue(lines.length > 1, "Should have header and data rows");
  }

  @Test
  @DisplayName("Test JSON export format")
  void testJsonExport() {
    ProcessSystemLiftCurveGenerator generator =
        new ProcessSystemLiftCurveGenerator(separationProcess, baseFluid);

    generator.setFlowRateRange(1500.0, 3500.0, 2);
    generator.setThpRange(30.0, 40.0, 2);
    generator.setWaterCutRange(0.05, 0.15, 2);
    generator.setGorRange(160.0, 200.0, 2);
    generator.setAlmRange(0.0, 0.0, 1);
    generator.setProcessDescription("JSON Export Test Process");

    generator.generateVfpTable(1, "JSON-TEST");

    String json = generator.toJson();

    assertNotNull(json);
    assertTrue(json.contains("ProcessSystemLiftCurveGenerator"));
    assertTrue(json.contains("JSON-TEST"));
    assertTrue(json.contains("flowRates_Sm3d"));
    assertTrue(json.contains("bhp"));
    assertTrue(json.contains("exportGasRate"));
    assertTrue(json.contains("exportOilRate"));
    assertTrue(json.contains("compressionPower"));
    assertTrue(json.contains("JSON Export Test Process"));
  }

  @Test
  @DisplayName("Test flow rate type configuration")
  void testFlowRateTypeConfiguration() {
    ProcessSystemLiftCurveGenerator generator =
        new ProcessSystemLiftCurveGenerator(separationProcess, baseFluid);

    // Test OIL flow rate type
    generator.setFlowRateType(FlowRateType.OIL);
    generator.setFlowRateRange(1000.0, 3000.0, 2);
    generator.setThpRange(35.0, 35.0, 1);
    generator.setWaterCutRange(0.1, 0.1, 1);
    generator.setGorRange(180.0, 180.0, 1);
    generator.setAlmRange(0.0, 0.0, 1);

    VfpTable vfp = generator.generateVfpTable(2, "OIL-TYPE");

    assertEquals(FlowRateType.OIL, vfp.getFlowRateType());

    String keywords = generator.getEclipseKeywords();
    assertTrue(keywords.contains("'OIL'"));
  }

  @Test
  @DisplayName("Test gas flow rate type")
  void testGasFlowRateType() {
    ProcessSystemLiftCurveGenerator generator =
        new ProcessSystemLiftCurveGenerator(separationProcess, baseFluid);

    generator.setFlowRateType(FlowRateType.GAS);
    generator.setFlowRateRange(50000.0, 150000.0, 2);
    generator.setThpRange(30.0, 40.0, 2);
    generator.setWaterCutRange(0.0, 0.0, 1);
    generator.setGorRange(200.0, 200.0, 1);
    generator.setAlmRange(0.0, 0.0, 1);

    generator.generateVfpTable(3, "GAS-WELL");

    String keywords = generator.getEclipseKeywords();
    assertTrue(keywords.contains("'GAS'"));
  }

  @Test
  @DisplayName("Test datum depth setting")
  void testDatumDepthSetting() {
    ProcessSystemLiftCurveGenerator generator =
        new ProcessSystemLiftCurveGenerator(separationProcess, baseFluid);

    generator.setDatumDepth(2500.0);
    generator.setFlowRateRange(2000.0, 4000.0, 2);
    generator.setThpRange(35.0, 45.0, 2);
    generator.setWaterCutRange(0.1, 0.1, 1);
    generator.setGorRange(180.0, 180.0, 1);
    generator.setAlmRange(0.0, 0.0, 1);

    VfpTable vfp = generator.generateVfpTable(4, "DEPTH-TEST");

    assertEquals(2500.0, vfp.getDatumDepth(), 0.1);
  }

  @Test
  @DisplayName("Test temperature unit conversion")
  void testTemperatureUnitConversion() {
    ProcessSystemLiftCurveGenerator generator = new ProcessSystemLiftCurveGenerator(baseFluid);

    // Test Celsius
    generator.setInletTemperature(60.0, "C");
    // Internal should be ~333.15 K

    // Test Fahrenheit
    generator.setInletTemperature(140.0, "F");
    // Internal should be ~333.15 K

    // Test Kelvin (default)
    generator.setInletTemperature(330.0, "K");
    // Should remain 330 K

    // Just verifying no exceptions are thrown during conversion
    assertNotNull(generator);
  }

  @Test
  @DisplayName("Test multiple VFP tables")
  void testMultipleVfpTables() {
    ProcessSystemLiftCurveGenerator generator =
        new ProcessSystemLiftCurveGenerator(separationProcess, baseFluid);

    generator.setFlowRateRange(1000.0, 3000.0, 2);
    generator.setThpRange(30.0, 40.0, 2);
    generator.setWaterCutRange(0.1, 0.1, 1);
    generator.setGorRange(180.0, 180.0, 1);
    generator.setAlmRange(0.0, 0.0, 1);

    generator.generateVfpTable(1, "WELL-A");
    generator.generateVfpTable(2, "WELL-B");
    generator.generateVfpTable(3, "WELL-C");

    assertEquals(3, generator.getVfpTables().size());

    String keywords = generator.getEclipseKeywords();
    assertTrue(keywords.contains("WELL-A"));
    assertTrue(keywords.contains("WELL-B"));
    assertTrue(keywords.contains("WELL-C"));

    // Count VFPPROD occurrences
    int vfpCount = keywords.split("VFPPROD").length - 1;
    assertEquals(3, vfpCount);
  }

  @Test
  @DisplayName("Test clear method")
  void testClearMethod() {
    ProcessSystemLiftCurveGenerator generator =
        new ProcessSystemLiftCurveGenerator(separationProcess, baseFluid);

    generator.setFlowRateRange(1000.0, 2000.0, 2);
    generator.setThpRange(35.0, 35.0, 1);
    generator.setWaterCutRange(0.1, 0.1, 1);
    generator.setGorRange(180.0, 180.0, 1);
    generator.setAlmRange(0.0, 0.0, 1);

    generator.generateVfpTable(1, "CLEAR-TEST");

    assertEquals(1, generator.getVfpTables().size());
    assertFalse(generator.getEclipseKeywords().isEmpty());

    generator.clear();

    assertEquals(0, generator.getVfpTables().size());
  }

  @Test
  @DisplayName("Test explicit flow rate values")
  void testExplicitFlowRateValues() {
    ProcessSystemLiftCurveGenerator generator =
        new ProcessSystemLiftCurveGenerator(separationProcess, baseFluid);

    double[] customRates = {1000.0, 2500.0, 5000.0, 7500.0, 10000.0};
    generator.setFlowRates(customRates);
    generator.setThpRange(35.0, 35.0, 1);
    generator.setWaterCutRange(0.1, 0.1, 1);
    generator.setGorRange(180.0, 180.0, 1);
    generator.setAlmRange(0.0, 0.0, 1);

    VfpTable vfp = generator.generateVfpTable(5, "CUSTOM-RATE");

    assertEquals(5, vfp.getFlowRates().length);
    assertEquals(1000.0, vfp.getFlowRates()[0], 0.1);
    assertEquals(2500.0, vfp.getFlowRates()[1], 0.1);
    assertEquals(5000.0, vfp.getFlowRates()[2], 0.1);
    assertEquals(7500.0, vfp.getFlowRates()[3], 0.1);
    assertEquals(10000.0, vfp.getFlowRates()[4], 0.1);
  }

  @Test
  @DisplayName("Test explicit THP values")
  void testExplicitThpValues() {
    ProcessSystemLiftCurveGenerator generator =
        new ProcessSystemLiftCurveGenerator(separationProcess, baseFluid);

    double[] customThp = {20.0, 35.0, 50.0, 70.0};
    generator.setFlowRateRange(2000.0, 2000.0, 1);
    generator.setThpValues(customThp);
    generator.setWaterCutRange(0.1, 0.1, 1);
    generator.setGorRange(180.0, 180.0, 1);
    generator.setAlmRange(0.0, 0.0, 1);

    VfpTable vfp = generator.generateVfpTable(6, "CUSTOM-THP");

    assertEquals(4, vfp.getThpValues().length);
    assertEquals(20.0, vfp.getThpValues()[0], 0.1);
    assertEquals(35.0, vfp.getThpValues()[1], 0.1);
    assertEquals(50.0, vfp.getThpValues()[2], 0.1);
    assertEquals(70.0, vfp.getThpValues()[3], 0.1);
  }

  @Test
  @DisplayName("Test with default process creation")
  void testWithDefaultProcess() {
    // Test generator with only base fluid - should create default process
    ProcessSystemLiftCurveGenerator generator = new ProcessSystemLiftCurveGenerator(baseFluid);

    generator.setInletStreamName("well stream");
    generator.setExportGasStreamName("export gas");
    generator.setExportOilStreamName("stable oil");

    generator.setFlowRateRange(1000.0, 3000.0, 2);
    generator.setThpRange(35.0, 45.0, 2);
    generator.setWaterCutRange(0.1, 0.1, 1);
    generator.setGorRange(180.0, 180.0, 1);
    generator.setAlmRange(0.0, 0.0, 1);

    VfpTable vfp = generator.generateVfpTable(7, "DEFAULT-PROC");

    assertNotNull(vfp);
    assertEquals(7, vfp.getTableNumber());
    assertEquals("DEFAULT-PROC", vfp.getWellName());

    // Check that BHP values are reasonable
    for (int iFlow = 0; iFlow < vfp.getFlowRates().length; iFlow++) {
      for (int iThp = 0; iThp < vfp.getThpValues().length; iThp++) {
        double bhp = vfp.getBhpValues()[iFlow][iThp][0][0][0];
        assertTrue(bhp > 0, "BHP should be positive: " + bhp);
        assertTrue(bhp < 1000, "BHP should be reasonable: " + bhp);
      }
    }
  }
}
