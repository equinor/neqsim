package neqsim.process.equipment.powergeneration.gasturbine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for {@link PlatformPowerSystem}: ambient-dependent capacity, power from shore, WHRU and bottoming cycle.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class PlatformPowerSystemTest {

  /**
   * Build a generator set on LM2500 with a fuel stream.
   *
   * @param name unit name
   * @return the unit
   */
  private static GasTurbineUnit unit(String name) {
    SystemInterface sys = new SystemSrkEos(288.15, 30.0);
    sys.addComponent("methane", 0.92);
    sys.addComponent("ethane", 0.08);
    sys.setMixingRule("classic");
    sys.init(0);
    Stream s = new Stream(name + " fuel", sys);
    s.setFlowRate(10.0, "kg/hr");
    s.run();
    return new GasTurbineUnit(name, s, GasTurbineCatalog.get("LM2500"));
  }

  /**
   * Capacity falls on a hot day and the deficit appears when the demand exceeds it.
   */
  @Test
  void capacityBindsOnHotDay() {
    PlatformPowerSystem sys = new PlatformPowerSystem("OSA power");
    sys.addGenerator(unit("GT-A"), 0.97);
    sys.addGenerator(unit("GT-B"), 0.97);
    double demand = 0.5 * (sys.getGeneratorCapacityW(263.15) + sys.getGeneratorCapacityW(308.15));
    List<PlatformPowerSystem.Result> r = sys.evaluateAcrossAmbient(new double[] {263.15, 308.15}, demand, 0.0);
    assertFalse(r.get(0).isCapacityLimited());
    assertTrue(r.get(1).isCapacityLimited());
    assertTrue(r.get(0).getGeneratorCapacityW() > r.get(1).getGeneratorCapacityW());
    assertEquals(demand, r.get(0).getGasTurbineElectricW(), 1.0e-3 * demand);
    assertTrue(r.get(0).getFuelKgPerHr() > 0.0 && r.get(0).getCo2KgPerHr() > 0.0);
    assertTrue(r.get(0).getN1MarginW() < 0.0);
    assertTrue(r.get(1).toJson().contains("\"capacityLimited\":true"));
  }

  /**
   * Power from shore is dispatched first and relieves the turbines; WHRU heat follows the turbine load.
   */
  @Test
  void shorePowerAndWasteHeat() {
    PlatformPowerSystem sys = new PlatformPowerSystem("OSA power");
    sys.addGenerator(unit("GT-A"), 0.97);
    sys.addGenerator(unit("GT-B"), 0.97);
    sys.setPowerFromShoreLimit(20.0, "MW");
    sys.setWasteHeatRecovery(0.0, 423.15, 0.85);
    double demand = 40.0e6;
    PlatformPowerSystem.Result withShore = sys.evaluate(288.15, demand, 10.0e6);
    assertEquals(20.0e6, withShore.getShorePowerW(), 1.0);
    assertEquals(20.0e6, withShore.getGasTurbineElectricW(), 1.0e-3 * 20.0e6);
    assertFalse(withShore.isCapacityLimited());
    sys.setPowerFromShoreLimit(0.0, "MW");
    PlatformPowerSystem.Result noShore = sys.evaluate(288.15, demand, 10.0e6);
    assertTrue(noShore.getFuelKgPerHr() > withShore.getFuelKgPerHr());
    assertTrue(noShore.getWhruDutyW() > withShore.getWhruDutyW());
    assertEquals(noShore.getWhruDutyW(), noShore.getProcessHeatAvailableW(), 1.0);
    assertEquals(0.0, noShore.getHeatShortfallW(), 1.0);
  }

  /**
   * A bottoming cycle converts recovered heat to electricity and lowers the turbine output for the same demand.
   */
  @Test
  void bottomingCycleAddsPower() {
    PlatformPowerSystem sys = new PlatformPowerSystem("combined cycle");
    sys.addGenerator(unit("GT-A"), 0.97);
    sys.addGenerator(unit("GT-B"), 0.97);
    sys.setWasteHeatRecovery(0.25, 423.15, 0.85);
    PlatformPowerSystem.Result r = sys.evaluate(288.15, 45.0e6, 0.0);
    assertTrue(r.getBottomingCyclePowerW() > 1.0e6);
    assertEquals(45.0e6, r.getGasTurbineElectricW() + r.getBottomingCyclePowerW(), 5.0e4);
    assertEquals(0.25 * (r.getWhruDutyW() - r.getProcessHeatAvailableW()), r.getBottomingCyclePowerW(), 1.0e3);
  }
}
