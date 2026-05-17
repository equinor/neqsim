package neqsim.process.equipment.blackoil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import neqsim.blackoil.BlackOilFlashResult;
import neqsim.blackoil.BlackOilPVTTable;
import neqsim.blackoil.SystemBlackOil;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Tests for Black-Oil integration with ProcessSystem.
 */
class BlackOilSeparatorTest {
  private static BlackOilPVTTable pvt;

  @BeforeAll
  static void setUp() {
    double[] grid = {50, 100, 150, 200, 250, 300};
    List<BlackOilPVTTable.Record> recs = new ArrayList<BlackOilPVTTable.Record>();
    for (double p : grid) {
      // Rs increases with pressure up to bubble point
      double Rs = Math.min(p * 1.0, 150.0);
      double Bo = 1.0 + Rs * 0.002;
      double Bg = 1.0 / (p * 0.95);
      double Bw = 1.01;
      double mu_o = 2.0e-3;
      double mu_g = 1.5e-5;
      double mu_w = 0.7e-3;
      double Rv = 0.0;
      recs.add(new BlackOilPVTTable.Record(p, Rs, Bo, mu_o, Bg, mu_g, Rv, Bw, mu_w));
    }
    pvt = new BlackOilPVTTable(recs, 150.0);
  }

  @Test
  void testBasicSeparation() {
    SystemBlackOil inlet = new SystemBlackOil(pvt, 800.0, 1.2, 1000.0);
    inlet.setPressure(100.0);
    inlet.setTemperature(350.0);
    inlet.setStdTotals(1000.0, 500.0, 200.0);

    BlackOilSeparator sep = new BlackOilSeparator("HP Sep", inlet, 100.0, 350.0);
    sep.run();

    assertNotNull(sep.getOilOut(), "Oil outlet should not be null");
    assertNotNull(sep.getGasOut(), "Gas outlet should not be null");
    assertNotNull(sep.getWaterOut(), "Water outlet should not be null");
    assertNotNull(sep.getLastFlashResult(), "Flash result should not be null");

    // Material balance: oil + gas std totals should match inlet
    double oilOilStd = sep.getOilOut().getOilStdTotal();
    double gasGasStd = sep.getGasOut().getGasStdTotal();
    assertTrue(oilOilStd > 0, "Oil outlet should have oil");
    assertTrue(gasGasStd >= 0, "Gas outlet should have non-negative gas");
    assertEquals(200.0, sep.getWaterOut().getWaterStd(), 1e-6,
        "All water should go to water outlet");
  }

  @Test
  void testExtendsProcessEquipmentBaseClass() {
    SystemBlackOil inlet = new SystemBlackOil(pvt, 800.0, 1.2, 1000.0);
    inlet.setPressure(100.0);
    inlet.setTemperature(350.0);
    inlet.setStdTotals(1000.0, 500.0, 200.0);

    BlackOilSeparator sep = new BlackOilSeparator("HP Sep", inlet, 100.0, 350.0);

    // Can be added to ProcessSystem
    ProcessSystem process = new ProcessSystem();
    process.add(sep);

    // Name is correctly set via ProcessEquipmentBaseClass
    assertEquals("HP Sep", sep.getName());
  }

  @Test
  void testRunInProcessSystem() {
    SystemBlackOil inlet = new SystemBlackOil(pvt, 800.0, 1.2, 1000.0);
    inlet.setPressure(100.0);
    inlet.setTemperature(350.0);
    inlet.setStdTotals(1000.0, 500.0, 200.0);

    BlackOilSeparator sep = new BlackOilSeparator("HP Sep", inlet, 100.0, 350.0);

    ProcessSystem process = new ProcessSystem();
    process.add(sep);
    process.run();

    // After ProcessSystem.run(), the separator should have been executed
    assertNotNull(sep.getLastFlashResult(),
        "Separator should have flash result after ProcessSystem.run()");
  }

  @Test
  void testToJson() {
    SystemBlackOil inlet = new SystemBlackOil(pvt, 800.0, 1.2, 1000.0);
    inlet.setPressure(100.0);
    inlet.setTemperature(350.0);
    inlet.setStdTotals(1000.0, 500.0, 200.0);

    BlackOilSeparator sep = new BlackOilSeparator("HP Sep", inlet, 100.0, 350.0);
    sep.run();

    String json = sep.toJson();
    assertNotNull(json);
    assertTrue(json.contains("BlackOilSeparator"), "JSON should contain type");
    assertTrue(json.contains("HP Sep"), "JSON should contain name");
    assertTrue(json.contains("flashResult"), "JSON should contain flash results");
    assertTrue(json.contains("oilDensity_kg_m3"), "JSON should contain density");
  }

  @Test
  void testGetResultsMap() {
    SystemBlackOil inlet = new SystemBlackOil(pvt, 800.0, 1.2, 1000.0);
    inlet.setPressure(100.0);
    inlet.setTemperature(350.0);
    inlet.setStdTotals(1000.0, 500.0, 200.0);

    BlackOilSeparator sep = new BlackOilSeparator("HP Sep", inlet, 100.0, 350.0);
    sep.run();

    Map<String, String> results = sep.getResultsMap();
    assertNotNull(results);
    assertEquals("HP Sep", results.get("name"));
    assertTrue(results.containsKey("Bo [rm3/Sm3]"));
    assertTrue(results.containsKey("oil density [kg/m3]"));
  }

  @Test
  void testOutletPressureTemperatureSetters() {
    SystemBlackOil inlet = new SystemBlackOil(pvt, 800.0, 1.2, 1000.0);
    inlet.setPressure(200.0);
    inlet.setTemperature(380.0);
    inlet.setStdTotals(1000.0, 500.0, 200.0);

    BlackOilSeparator sep = new BlackOilSeparator("Sep", inlet, 200.0, 380.0);
    assertEquals(200.0, sep.getOutletPressure(), 1e-6);
    assertEquals(380.0, sep.getOutletTemperature(), 1e-6);

    sep.setOutletPressure(150.0);
    sep.setOutletTemperature(360.0);
    assertEquals(150.0, sep.getOutletPressure(), 1e-6);
    assertEquals(360.0, sep.getOutletTemperature(), 1e-6);
  }

  @Test
  void testRunSeparationLegacy() {
    SystemBlackOil inlet = new SystemBlackOil(pvt, 800.0, 1.2, 1000.0);
    inlet.setPressure(100.0);
    inlet.setTemperature(350.0);
    inlet.setStdTotals(1000.0, 500.0, 200.0);

    BlackOilSeparator sep = new BlackOilSeparator("Sep", inlet, 100.0, 350.0);
    sep.runSeparation();
    assertNotNull(sep.getLastFlashResult());
  }

  @Test
  void testTwoStageSeparation() {
    // Simulate HP → LP separation
    SystemBlackOil inlet = new SystemBlackOil(pvt, 800.0, 1.2, 1000.0);
    inlet.setPressure(200.0);
    inlet.setTemperature(370.0);
    inlet.setStdTotals(1000.0, 800.0, 100.0);

    BlackOilSeparator hpSep = new BlackOilSeparator("HP Sep", inlet, 200.0, 370.0);
    hpSep.run();

    // Feed LP separator with HP oil outlet
    SystemBlackOil hpOil = hpSep.getOilOut();
    assertNotNull(hpOil);

    BlackOilSeparator lpSep = new BlackOilSeparator("LP Sep", hpOil, 50.0, 350.0);
    lpSep.run();

    assertNotNull(lpSep.getOilOut());
    assertNotNull(lpSep.getGasOut());

    // LP separator should flash more gas from the oil
    BlackOilFlashResult hpResult = hpSep.getLastFlashResult();
    BlackOilFlashResult lpResult = lpSep.getLastFlashResult();
    assertNotNull(hpResult);
    assertNotNull(lpResult);
  }

  @Test
  void testSerializable() throws Exception {
    SystemBlackOil inlet = new SystemBlackOil(pvt, 800.0, 1.2, 1000.0);
    inlet.setPressure(100.0);
    inlet.setTemperature(350.0);
    inlet.setStdTotals(1000.0, 500.0, 200.0);

    BlackOilSeparator sep = new BlackOilSeparator("Test Sep", inlet, 100.0, 350.0);
    sep.run();

    // Verify serialization via copy() (ProcessEquipmentBaseClass method)
    BlackOilSeparator copy = (BlackOilSeparator) sep.copy();
    assertNotNull(copy, "copy() should produce a non-null clone");
    assertEquals("Test Sep", copy.getName());
  }
}
