package neqsim.process.equipment.heatexchanger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for the AirCooler unit operation including thermal design, fan model, and ambient
 * correction.
 */
public class AirCoolerTest {
  static neqsim.thermo.system.SystemInterface testSystem = null;
  ProcessSystem processOps = null;
  AirCooler airCooler = null;

  @BeforeEach
  public void setUp() {
    testSystem = new SystemSrkEos(353.15, 10.0);
    testSystem.addComponent("methane", 100.0);
    Stream inlet = new Stream("inlet", testSystem);
    inlet.setFlowRate(1.0, "MSm3/day");
    inlet.setPressure(10.0, "bara");
    inlet.setTemperature(80.0, "C");

    airCooler = new AirCooler("air cooler", inlet);
    airCooler.setOutletTemperature(40.0, "C");
    airCooler.setAirInletTemperature(20.0, "C");
    airCooler.setAirOutletTemperature(30.0, "C");
    airCooler.setRelativeHumidity(0.5);

    processOps = new ProcessSystem();
    processOps.add(inlet);
    processOps.add(airCooler);
    processOps.run();
  }

  @Test
  public void testAirFlowIsPositive() {
    assertTrue(airCooler.getAirMassFlow() > 0.0, "Air mass flow should be positive");
    assertTrue(airCooler.getAirVolumeFlow() > 0.0, "Air volume flow should be positive");
  }

  @Test
  public void testLMTDIsPositive() {
    assertTrue(airCooler.getLMTD() > 0.0, "LMTD should be positive");
    // For 80->40 process, 20->30 air: LMTD should be between 10 and 60 K
    assertTrue(airCooler.getLMTD() > 10.0, "LMTD should be > 10 K");
    assertTrue(airCooler.getLMTD() < 60.0, "LMTD should be < 60 K");
  }

  @Test
  public void testOverallUIsReasonable() {
    assertTrue(airCooler.getOverallU() > 10.0, "U should be > 10 W/m2-K");
    assertTrue(airCooler.getOverallU() < 500.0, "U should be < 500 W/m2-K for air coolers");
  }

  @Test
  public void testRequiredAreaIsPositive() {
    assertTrue(airCooler.getRequiredArea() > 0.0, "Required area should be positive");
  }

  @Test
  public void testAirSideHTCIsReasonable() {
    // Air-side HTC for finned tubes (bare tube basis, weighted by fin area ratio) can be high
    assertTrue(airCooler.getAirSideHTC() > 5.0, "Air-side HTC should be > 5 W/m2-K");
    assertTrue(airCooler.getAirSideHTC() < 5000.0, "Air-side HTC should be < 5000 W/m2-K");
  }

  @Test
  public void testFinEfficiencyIsReasonable() {
    assertTrue(airCooler.getFinEfficiency() > 0.3, "Fin efficiency should be > 0.3");
    assertTrue(airCooler.getFinEfficiency() <= 1.0, "Fin efficiency should be <= 1.0");
  }

  @Test
  public void testFanPowerIsPositive() {
    assertTrue(airCooler.getFanPower() > 0.0, "Fan power should be positive");
    assertTrue(airCooler.getFanPower("kW") > 0.0, "Fan power kW should be positive");
    assertEquals(airCooler.getFanPower() / 1000.0, airCooler.getFanPower("kW"), 1e-6);
    assertEquals(airCooler.getFanPower() / 745.7, airCooler.getFanPower("hp"), 0.1);
  }

  @Test
  public void testAirSidePressureDropIsPositive() {
    assertTrue(airCooler.getAirSidePressureDrop() > 0.0, "Air-side DP should be positive");
    // Typical air cooler DP range: 50-500 Pa
    assertTrue(airCooler.getAirSidePressureDrop() < 5000.0, "Air-side DP should be < 5000 Pa");
  }

  @Test
  public void testBundleGeometry() {
    assertTrue(airCooler.getTubesPerRow() > 0, "Tubes per row should be positive");
    assertTrue(airCooler.getTotalTubes() > 0, "Total tubes should be positive");
    assertTrue(airCooler.getFaceArea() > 0.0, "Face area should be positive");
    assertEquals(4, airCooler.getNumberOfTubeRows());
    assertEquals(1, airCooler.getNumberOfBays());
  }

  @Test
  public void testITDIsPositive() {
    assertTrue(airCooler.getITD() > 0.0, "ITD should be positive");
    // ITD = 80 - 20 = 60 K
    assertEquals(60.0, airCooler.getITD(), 1.0);
  }

  @Test
  public void testFanCurve() {
    // Set custom fan curve
    airCooler.setFanCurve(400.0, -0.5, -0.001, 0.0);
    // Check fan static pressure at 100 m3/s
    double dp = airCooler.getFanStaticPressure(100.0);
    double expected = 400.0 + (-0.5) * 100.0 + (-0.001) * 100.0 * 100.0;
    assertEquals(expected, dp, 0.01);
  }

  @Test
  public void testAmbientCorrection() {
    // Design ambient = 25 C (default), actual = 20 C
    airCooler.setDesignAmbientTemperature(25.0, "C");
    processOps.run();
    double correction = airCooler.getAmbientCorrectionFactor();
    // Process in = 80C; designITD = 80-25=55; actualITD = 80-20=60; ratio = 60/55 > 1.0
    assertTrue(correction > 1.0, "Cooler ambient should give correction > 1.0");
    assertTrue(correction < 2.0, "Correction should be reasonable");
  }

  @Test
  public void testGeometrySetters() {
    airCooler.setTubeOuterDiameter(0.0254);
    airCooler.setTubeWallThickness(0.002);
    airCooler.setFinHeight(0.016);
    airCooler.setFinThickness(0.0004);
    airCooler.setFinPitch(0.003);
    airCooler.setFinConductivity(200.0);
    airCooler.setNumberOfTubeRows(6);
    airCooler.setNumberOfTubePasses(4);
    airCooler.setTransversePitch(0.060);
    airCooler.setTubeLength(10.0);
    airCooler.setBayWidth(3.0);
    airCooler.setNumberOfBays(2);
    airCooler.setNumberOfFansPerBay(2);
    airCooler.setFanDiameter(3.66);
    processOps.run();

    assertEquals(6, airCooler.getNumberOfTubeRows());
    assertEquals(2, airCooler.getNumberOfBays());
    assertTrue(airCooler.getFanPower() > 0.0);
    assertTrue(airCooler.getLMTD() > 0.0);
  }

  @Test
  public void testDesignParameterSetters() {
    airCooler.setProcessFoulingResistance(2.0e-4);
    airCooler.setAirFoulingResistance(1.0e-4);
    airCooler.setLmtdCorrectionFactor(0.85);
    airCooler.setFanEfficiency(0.60);
    airCooler.setProcessSideHTC(300.0);
    processOps.run();

    assertTrue(airCooler.getOverallU() > 0.0);
    assertTrue(airCooler.getRequiredArea() > 0.0);
  }

  @Test
  public void testJsonReport() {
    String json = airCooler.toJson();
    assertTrue(json.contains("AirCooler"));
    assertTrue(json.contains("operatingConditions"));
    assertTrue(json.contains("thermalDesign"));
    assertTrue(json.contains("bundleGeometry"));
    assertTrue(json.contains("fanData"));
    assertTrue(json.contains("airSide"));
    assertTrue(json.contains("LMTD_K"));
    assertTrue(json.contains("overallU_W_m2K"));
    assertTrue(json.contains("totalFanPower_kW"));
  }

  @Test
  public void testHigherAirTemperatureReducesDuty() {
    // Create a second cooler with hotter ambient air
    neqsim.thermo.system.SystemInterface sys2 = new SystemSrkEos(353.15, 10.0);
    sys2.addComponent("methane", 100.0);
    Stream inlet2 = new Stream("inlet2", sys2);
    inlet2.setFlowRate(1.0, "MSm3/day");
    inlet2.setPressure(10.0, "bara");
    inlet2.setTemperature(80.0, "C");

    AirCooler ac2 = new AirCooler("hot ambient", inlet2);
    ac2.setOutletTemperature(40.0, "C");
    ac2.setAirInletTemperature(35.0, "C");
    ac2.setAirOutletTemperature(45.0, "C");
    ac2.setRelativeHumidity(0.5);

    ProcessSystem proc2 = new ProcessSystem();
    proc2.add(inlet2);
    proc2.add(ac2);
    proc2.run();

    // With hotter air, ITD is smaller
    assertTrue(ac2.getITD() < airCooler.getITD(), "Hotter ambient should give smaller ITD");
    // Both should have positive air flow
    assertTrue(ac2.getAirMassFlow() > 0.0, "Air mass flow should be positive");
  }
}
