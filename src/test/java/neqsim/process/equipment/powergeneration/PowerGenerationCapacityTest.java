package neqsim.process.equipment.powergeneration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.capacity.CapacityConstraint;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for power generation capacity constraint integration with the production optimization
 * framework.
 */
class PowerGenerationCapacityTest {

  private SystemSrkEos fuelGasFluid;
  private Stream fuelGasStream;
  private SystemSrkEos steamFluid;
  private Stream steamStream;

  @BeforeEach
  void setUp() {
    // Fuel gas for gas turbine
    fuelGasFluid = new SystemSrkEos(298.15, 1.0);
    fuelGasFluid.addComponent("nitrogen", 1.0);
    fuelGasFluid.addComponent("CO2", 2.0);
    fuelGasFluid.addComponent("methane", 92.0);
    fuelGasFluid.addComponent("ethane", 4.0);
    fuelGasFluid.addComponent("propane", 2.0);
    fuelGasFluid.setMixingRule("classic");

    fuelGasStream = new Stream("fuel gas", fuelGasFluid);
    fuelGasStream.setFlowRate(1.0, "MSm3/day");
    fuelGasStream.setTemperature(50.0, "C");
    fuelGasStream.setPressure(2.0, "bara");

    // Steam for steam turbine
    steamFluid = new SystemSrkEos(273.15 + 400.0, 40.0);
    steamFluid.addComponent("water", 1.0);
    steamFluid.setMixingRule("classic");

    steamStream = new Stream("HP steam", steamFluid);
    steamStream.setFlowRate(10.0, "kg/sec");
    steamStream.setTemperature(400.0, "C");
    steamStream.setPressure(40.0, "bara");
  }

  // ====== GasTurbine capacity tests ======

  @Test
  void testGasTurbineRatedPowerSetting() {
    GasTurbine gt = new GasTurbine("GT-1");
    gt.setRatedPower(30.0, "MW");
    assertEquals(30.0e6, gt.getRatedPower(), 1.0);
    assertEquals(30.0, gt.getRatedPower("MW"), 0.001);
    assertEquals(30000.0, gt.getRatedPower("kW"), 0.1);
  }

  @Test
  void testGasTurbineCapacityConstraintsExist() {
    GasTurbine gt = new GasTurbine("GT-1", fuelGasStream);
    gt.setRatedPower(30.0, "MW");

    Map<String, CapacityConstraint> constraints = gt.getCapacityConstraints();
    assertTrue(constraints.containsKey("power"),
        "GasTurbine should have 'power' capacity constraint");
  }

  @Test
  void testGasTurbineGetCapacityDutyAndMax() {
    GasTurbine gt = new GasTurbine("GT-1", fuelGasStream);
    gt.setRatedPower(30.0, "MW");

    // Before running, duty is 0
    assertEquals(0.0, gt.getCapacityDuty(), 0.001);
    // Max is rated power
    assertEquals(30.0e6, gt.getCapacityMax(), 1.0);
  }

  @Test
  void testGasTurbineGetPowerUnits() {
    GasTurbine gt = new GasTurbine("GT-1", fuelGasStream);
    // Set the internal power field directly via run (if available) or test unit conversion
    gt.setRatedPower(25.0, "MW");
    assertEquals(25.0, gt.getRatedPower("MW"), 0.001);
    assertEquals(25000.0, gt.getRatedPower("kW"), 0.1);
    assertEquals(25.0e6, gt.getRatedPower("W"), 1.0);
  }

  @Test
  void testGasTurbineAutoSize() {
    // AutoSize should set rated power from current operating power * safety factor
    GasTurbine gt = new GasTurbine("GT-1", fuelGasStream);
    // Simulate a scenario where power is set by the run method
    // Since GasTurbine.run() may have issues (it's @Disabled in tests),
    // we test autoSize API directly
    gt.autoSize(1.2); // Should be no-op if power is 0
    assertEquals(0.0, gt.getRatedPower(), 0.001);
  }

  // ====== SteamTurbine capacity tests ======

  @Test
  void testSteamTurbineRatedPowerSetting() {
    SteamTurbine st = new SteamTurbine("ST-1");
    st.setRatedPower(15.0, "MW");
    assertEquals(15.0e6, st.getRatedPower(), 1.0);
    assertEquals(15.0, st.getRatedPower("MW"), 0.001);
  }

  @Test
  void testSteamTurbineCapacityConstraintsExist() {
    SteamTurbine st = new SteamTurbine("ST-1", steamStream);
    st.setRatedPower(15.0, "MW");

    Map<String, CapacityConstraint> constraints = st.getCapacityConstraints();
    assertTrue(constraints.containsKey("power"),
        "SteamTurbine should have 'power' capacity constraint");
  }

  @Test
  void testSteamTurbineCapacityDutyAfterRun() {
    ProcessSystem process = new ProcessSystem();
    process.add(steamStream);

    SteamTurbine st = new SteamTurbine("ST-1", steamStream);
    st.setOutletPressure(1.0);
    st.setIsentropicEfficiency(0.85);
    st.setRatedPower(50.0, "MW");
    process.add(st);
    process.run();

    // After running, duty should be > 0
    assertTrue(st.getCapacityDuty() > 0, "SteamTurbine capacity duty should be positive after run");
    assertEquals(Math.abs(st.getPower()), st.getCapacityDuty(), 0.001);
    // Max should be rated power
    assertEquals(50.0e6, st.getCapacityMax(), 1.0);
  }

  @Test
  void testSteamTurbineAutoSizeAfterRun() {
    ProcessSystem process = new ProcessSystem();
    process.add(steamStream);

    SteamTurbine st = new SteamTurbine("ST-1", steamStream);
    st.setOutletPressure(1.0);
    st.setIsentropicEfficiency(0.85);
    process.add(st);
    process.run();

    double powerBeforeSize = st.getPower();
    assertTrue(powerBeforeSize > 0, "Steam turbine should produce power");

    st.autoSize(1.2);
    assertEquals(powerBeforeSize * 1.2, st.getRatedPower(), powerBeforeSize * 0.01);
  }

  @Test
  void testSteamTurbineCapacityMargin() {
    ProcessSystem process = new ProcessSystem();
    process.add(steamStream);

    SteamTurbine st = new SteamTurbine("ST-1", steamStream);
    st.setOutletPressure(1.0);
    st.setIsentropicEfficiency(0.85);
    process.add(st);
    process.run();

    // Auto-size with 20% margin
    st.autoSize(1.2);

    // Capacity margin = max - duty
    double margin = st.getCapacityMax() - st.getCapacityDuty();
    assertTrue(margin > 0, "Should have positive capacity margin after auto-size");
  }

  // ====== HRSG capacity tests ======

  @Test
  void testHRSGDesignHeatDutySetting() {
    HRSG hrsg = new HRSG("HRSG-1");
    hrsg.setDesignHeatDuty(50.0, "MW");
    assertEquals(50.0e6, hrsg.getDesignHeatDuty(), 1.0);
    assertEquals(50.0, hrsg.getDesignHeatDuty("MW"), 0.001);
    assertEquals(50000.0, hrsg.getDesignHeatDuty("kW"), 0.1);
  }

  @Test
  void testHRSGCapacityConstraintsExist() {
    HRSG hrsg = new HRSG("HRSG-1");
    hrsg.setDesignHeatDuty(50.0, "MW");

    Map<String, CapacityConstraint> constraints = hrsg.getCapacityConstraints();
    assertTrue(constraints.containsKey("heatDuty"),
        "HRSG should have 'heatDuty' capacity constraint");
  }

  @Test
  void testHRSGCapacityDutyAndMax() {
    HRSG hrsg = new HRSG("HRSG-1");
    hrsg.setDesignHeatDuty(50.0, "MW");

    assertEquals(0.0, hrsg.getCapacityDuty(), 0.001);
    assertEquals(50.0e6, hrsg.getCapacityMax(), 1.0);
  }

  // ====== CombinedCycleSystem capacity tests ======

  @Test
  void testCombinedCycleRatedPowerSetting() {
    CombinedCycleSystem cc = new CombinedCycleSystem("CC-1");
    cc.setRatedTotalPower(100.0, "MW");
    assertEquals(100.0e6, cc.getRatedTotalPower(), 1.0);
    assertEquals(100.0, cc.getRatedTotalPower("MW"), 0.001);
  }

  @Test
  void testCombinedCycleCapacityConstraintsExist() {
    CombinedCycleSystem cc = new CombinedCycleSystem("CC-1");
    cc.setRatedTotalPower(100.0, "MW");

    Map<String, CapacityConstraint> constraints = cc.getCapacityConstraints();
    assertTrue(constraints.containsKey("totalPower"),
        "CombinedCycleSystem should have 'totalPower' capacity constraint");
  }

  @Test
  void testCombinedCycleCapacityDutyAndMax() {
    CombinedCycleSystem cc = new CombinedCycleSystem("CC-1");
    cc.setRatedTotalPower(100.0, "MW");

    assertEquals(0.0, cc.getCapacityDuty(), 0.001);
    assertEquals(100.0e6, cc.getCapacityMax(), 1.0);
  }

  // ====== Cross-cutting: CapacityConstrainedEquipment interface ======

  @Test
  void testAllPowerGenImplementCapacityInterface() {
    GasTurbine gt = new GasTurbine("GT");
    SteamTurbine st = new SteamTurbine("ST");
    HRSG hrsg = new HRSG("HRSG");
    CombinedCycleSystem cc = new CombinedCycleSystem("CC");

    // All should return empty constraints initially (no rated values set)
    assertFalse(gt.isCapacityExceeded());
    assertFalse(st.isCapacityExceeded());
    assertFalse(hrsg.isCapacityExceeded());
    assertFalse(cc.isCapacityExceeded());
  }
}
