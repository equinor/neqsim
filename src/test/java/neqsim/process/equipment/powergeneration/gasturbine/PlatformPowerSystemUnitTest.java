package neqsim.process.equipment.powergeneration.gasturbine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.automation.ProcessAutomation;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.energy.CoupledProcessEnergyResult;
import neqsim.process.equipment.energy.CoupledProcessEnergySolver;
import neqsim.process.equipment.energy.EnergyNetworkSolver;
import neqsim.process.equipment.stream.EnergyBus;
import neqsim.process.equipment.stream.EnergyPortDirection;
import neqsim.process.equipment.stream.EnergyPortMode;
import neqsim.process.equipment.stream.EnergyType;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for {@link PlatformPowerSystemUnit}: coupling of the power system to {@link CoupledProcessEnergySolver} through
 * an electrical bus, and the automation addresses of the power system.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class PlatformPowerSystemUnitTest {

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
   * Build a two-generator power system.
   *
   * @return the power system
   */
  private static PlatformPowerSystem system() {
    PlatformPowerSystem sys = new PlatformPowerSystem("Platform A power");
    sys.addGenerator(unit("GT-A"), 0.97);
    sys.addGenerator(unit("GT-B"), 0.97);
    return sys;
  }

  /** Fixed electric consumer on a bus. */
  private static final class Load extends ProcessEquipmentBaseClass {
    private static final long serialVersionUID = 1L;
    private static final String PORT = "power";
    private double demand;

    private Load(String name, EnergyBus bus, double demand) {
      super(name);
      this.demand = demand;
      registerEnergyPort(PORT, EnergyType.ELECTRICAL, EnergyPortDirection.INPUT, EnergyPortMode.SPECIFICATION);
      connectEnergyStream(PORT, bus, EnergyPortMode.SPECIFICATION);
      getEnergyPort(PORT).setRequestedPower(demand);
    }

    private void setDemand(double demand) {
      this.demand = demand;
      getEnergyPort(PORT).setRequestedPower(demand);
    }

    @Override
    public void run(UUID id) {
      getEnergyPort(PORT).setRequestedPower(demand);
      setCalculationIdentifier(id);
    }
  }

  /**
   * Build a process with a load, the power unit and an energy network solver on one bus.
   *
   * @param power the power unit
   * @param load the load
   * @param bus the bus
   * @return the process system
   */
  private static ProcessSystem process(PlatformPowerSystemUnit power, Load load, EnergyBus bus) {
    ProcessSystem process = new ProcessSystem();
    process.setUseOptimizedExecution(false);
    process.setUseGraphBasedExecution(true);
    process.add(load);
    process.add(new EnergyNetworkSolver("network", bus));
    process.add(power);
    return process;
  }

  /**
   * The coupled solver converges, the generators supply the load and fuel and CO2 follow the demand; a hot day lowers
   * the capacity, a high demand then leaves a shortage on the bus and a deficit in the unit.
   */
  @Test
  void coupledSolverDispatchesPowerSystemAcrossAmbient() {
    PlatformPowerSystem sys = system();
    EnergyBus bus = new EnergyBus("Platform A 66 kV", EnergyType.ELECTRICAL);
    PlatformPowerSystemUnit power = new PlatformPowerSystemUnit("Platform A power", sys);
    power.connectToBus(bus);
    double cold = sys.getGeneratorCapacityW(263.15);
    double hot = sys.getGeneratorCapacityW(313.15);
    assertTrue(cold > hot);
    double demand = 0.5 * (cold + hot);
    Load load = new Load("consumers", bus, demand);
    ProcessSystem process = process(power, load, bus);

    power.setAmbientTemperature(-10.0, "C");
    CoupledProcessEnergyResult r1 = new CoupledProcessEnergySolver(process).solve();
    assertTrue(r1.isConverged());
    PlatformPowerSystem.Result coldResult = power.getLastResult();
    assertNotNull(coldResult);
    assertEquals(demand, coldResult.getDemandW(), 1.0);
    assertEquals(demand, coldResult.getGasTurbineElectricW(), 1.0e-3 * demand);
    assertFalse(coldResult.isCapacityLimited());
    assertTrue(coldResult.getFuelKgPerHr() > 0.0 && coldResult.getCo2KgPerHr() > 0.0);
    assertEquals(demand, bus.getLastReport().getServedDemand(), 1.0);

    power.setAmbientTemperature(40.0, "C");
    CoupledProcessEnergyResult r2 = new CoupledProcessEnergySolver(process).solve();
    assertTrue(r2.isConverged());
    PlatformPowerSystem.Result hotResult = power.getLastResult();
    assertEquals(hot, power.getAvailablePowerW(), 1.0);
    assertTrue(bus.getLastReport().getUnmetDemand() > 1.0, "unmet " + bus.getLastReport().getUnmetDemand());
    assertEquals(demand - hot, bus.getLastReport().getUnmetDemand(), 1.0e-3 * demand);
    assertEquals(hot, hotResult.getDemandW(), 1.0e-3 * hot);
    assertTrue(hotResult.getFuelKgPerHr() > 0.0 && hotResult.getCo2KgPerHr() > 0.0);

    load.setDemand(0.5 * demand);
    CoupledProcessEnergyResult r3 = new CoupledProcessEnergySolver(process).solve();
    assertTrue(r3.isConverged());
    assertEquals(0.5 * demand, power.getLastResult().getDemandW(), 1.0);
    assertEquals(0.0, bus.getLastReport().getUnmetDemand(), 1.0);
  }

  /**
   * Without a bus the unit uses the specified demand and validates its setup.
   */
  @Test
  void standaloneDemandAndValidation() {
    PlatformPowerSystemUnit power = new PlatformPowerSystemUnit("Platform A power", system());
    assertTrue(power.validateSetup().isValid());
    assertTrue(power.validateSetup().hasWarnings());
    power.setElectricDemand(20.0, "MW");
    power.setHeatDemand(5.0, "MW");
    power.run(UUID.randomUUID());
    assertEquals(20.0e6, power.getLastResult().getDemandW(), 1.0);
    assertThrows(IllegalArgumentException.class, () -> power.setElectricDemand(-1.0, "MW"));
    assertThrows(IllegalArgumentException.class, () -> new PlatformPowerSystemUnit("x", null));
    assertThrows(IllegalArgumentException.class, () -> power.setAmbientTemperature(1.0, "F"));
  }

  /**
   * Available power, demand, deficit, N-1 margin, fuel and CO2 are addressable; the ambient temperature is writable and
   * moves the capacity without a run.
   */
  @Test
  void automationAddresses() {
    PlatformPowerSystem sys = system();
    PlatformPowerSystemUnit power = new PlatformPowerSystemUnit("Platform A power", sys);
    ProcessSystem process = new ProcessSystem();
    process.add(power);
    ProcessAutomation auto = process.getAutomation();
    assertTrue(auto.getUnitList().contains("Platform A power"));
    assertTrue(auto.isWritableAddress("Platform A power.ambientTemperature"));
    assertTrue(auto.isWritableAddress("Platform A power.electricDemand"));
    assertFalse(auto.isWritableAddress("Platform A power.deficit"));
    assertTrue(Double.isNaN(auto.getVariableValue("Platform A power.deficit", "MW")));

    assertTrue(auto.setVariableValueSafe("Platform A power.ambientTemperature", 0.0, "C").contains("success"));
    double coldMw = auto.getVariableValue("Platform A power.availablePower", "MW");
    assertEquals(sys.getGeneratorCapacityW(273.15) / 1.0e6, coldMw, 1.0e-9);
    auto.setVariableValue("Platform A power.ambientTemperature", 35.0, "C");
    double hotMw = auto.getVariableValue("Platform A power.availablePower", "MW");
    assertTrue(hotMw < coldMw);
    assertFalse(auto.setVariableValueSafe("Platform A power.ambientTemperature", 400.0, "C").contains("success"));
    assertFalse(auto.setVariableValueSafe("Platform A power.electricDemand", -5.0, "MW").contains("success"));

    auto.setVariableValue("Platform A power.electricDemand", hotMw + 10.0, "MW");
    process.run();
    assertEquals(hotMw + 10.0, auto.getVariableValue("Platform A power.demand", "MW"), 1.0e-6);
    assertEquals(10.0, auto.getVariableValue("Platform A power.deficit", "MW"), 0.01);
    assertEquals(1.0, auto.getVariableValue("Platform A power.capacityLimited", ""), 0.0);
    assertTrue(auto.getVariableValue("Platform A power.n1Margin", "MW") < 0.0);
    assertTrue(auto.getVariableValue("Platform A power.fuel", "kg/hr") > 0.0);
    assertTrue(auto.getVariableValue("Platform A power.co2", "kg/hr") > 0.0);
    assertEquals(sys.getGenerators().get(0).getAvailablePowerAtAmbient(308.15) / 1.0e6,
        auto.getVariableValue("Platform A power.generator.GT-A.availablePower", "MW"), 1.0e-9);
    assertTrue(auto.getVariableValue("Platform A power.generator.GT-A.loadFraction", "") > 0.0);
    assertTrue(auto.getVariableValueSafe("Platform A power.generator.GT-X.loadFraction", "").length() > 0);
  }
}
