package neqsim.process.safety.depressurization;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Tests for {@link VesselFillingSimulator}.
 *
 * <p>
 * Verifies that charging a vessel raises its pressure and mass inventory, that the characteristic temperature rise of
 * fast filling is reproduced, that liner temperature limits are flagged correctly, and that both the fixed and
 * Woodfield internal film-coefficient paths execute.
 *
 * @author ESOL
 * @version 1.0
 */
public class VesselFillingSimulatorTest {

  /**
   * Build a single-phase methane gas at the given conditions.
   *
   * @param pressureBara pressure in bara
   * @param temperatureC temperature in degrees Celsius
   * @return a flashed gas {@link SystemInterface}
   */
  private SystemInterface buildGas(double pressureBara, double temperatureC) {
    SystemInterface fluid = new SystemSrkEos(273.15 + temperatureC, pressureBara);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();
    fluid.initProperties();
    return fluid;
  }

  /** Charging a vessel must raise its pressure and mass inventory. */
  @Test
  public void fillingRaisesPressureAndMass() {
    SystemInterface gas = buildGas(20.0, 25.0);
    VesselFillingSimulator sim = new VesselFillingSimulator(gas, 0.05);
    sim.setInletConditions(288.15, 300.0, 0.01);
    sim.setTargetPressure(200.0);
    sim.setTimeStep(0.5);
    sim.setMaxTime(600.0);
    VesselFillingSimulator.VesselFillingResult res = sim.run();

    double firstP = res.pressureBara.get(0);
    double lastP = res.pressureBara.get(res.pressureBara.size() - 1);
    double firstMass = res.massKg.get(0);
    double lastMass = res.massKg.get(res.massKg.size() - 1);

    assertTrue(lastP > firstP, "filling must raise pressure, " + firstP + " -> " + lastP + " bara");
    assertTrue(lastMass > firstMass, "filling must add mass, " + firstMass + " -> " + lastMass + " kg");
    assertTrue(Double.isFinite(res.maxFluidTemperatureK), "max temperature must be finite");
  }

  /** Fast charging must reproduce a gas-temperature rise above the supply temperature. */
  @Test
  public void fillingProducesTemperatureRise() {
    SystemInterface gas = buildGas(20.0, 15.0);
    VesselFillingSimulator sim = new VesselFillingSimulator(gas, 0.05);
    sim.setInletConditions(288.15, 350.0, 0.02);
    sim.setTargetPressure(250.0);
    sim.setTimeStep(0.5);
    sim.setMaxTime(600.0);
    VesselFillingSimulator.VesselFillingResult res = sim.run();

    double initialTempK = res.temperatureK.get(0);
    assertTrue(res.maxFluidTemperatureK > initialTempK,
        "fast filling should heat the gas, max " + res.maxFluidTemperatureK + " K vs initial " + initialTempK + " K");
    assertTrue(res.maxFluidTemperatureK < 600.0, "gas temperature must stay physical");
  }

  /** A tight liner upper limit must be flagged as violated when filling heats the gas above it. */
  @Test
  public void linerOverTemperatureIsFlagged() {
    SystemInterface gas = buildGas(20.0, 15.0);
    VesselFillingSimulator sim = new VesselFillingSimulator(gas, 0.05);
    sim.setInletConditions(288.15, 350.0, 0.02);
    sim.setTargetPressure(250.0);
    sim.setLinerTemperatureLimits(233.15, 300.0);
    sim.setTimeStep(0.5);
    sim.setMaxTime(600.0);
    VesselFillingSimulator.VesselFillingResult res = sim.run();

    if (res.maxFluidTemperatureK > 300.0) {
      assertTrue(res.linerOverTemperature, "liner over-temperature should be flagged");
      assertTrue(!res.linerLimitsMet, "liner limits should not be met");
    }
  }

  /** The Woodfield mixed-convection film-coefficient path must run end to end. */
  @Test
  public void woodfieldHeatTransferPathRuns() {
    SystemInterface gas = buildGas(20.0, 25.0);
    VesselFillingSimulator sim = new VesselFillingSimulator(gas, 0.05);
    sim.setInletConditions(288.15, 300.0, 0.01);
    sim.setTargetPressure(200.0);
    sim.setWall(50.0, 1.0, 470.0, 50.0);
    sim.setWoodfieldHeatTransfer(0.01, 0.8);
    sim.setTimeStep(0.5);
    sim.setMaxTime(600.0);
    VesselFillingSimulator.VesselFillingResult res = sim.run();

    double lastP = res.pressureBara.get(res.pressureBara.size() - 1);
    assertTrue(lastP > 20.0, "Woodfield path should still fill the vessel");
    assertTrue(Double.isFinite(res.wallTempK.get(res.wallTempK.size() - 1)), "wall temperature must stay finite");
  }

  /** Running without configuring inlet conditions must fail fast. */
  @Test
  public void runWithoutInletConditionsThrows() {
    SystemInterface gas = buildGas(20.0, 25.0);
    VesselFillingSimulator sim = new VesselFillingSimulator(gas, 0.05);
    assertThrows(IllegalStateException.class, sim::run);
  }

  /** Constructor and setters must reject non-physical arguments. */
  @Test
  public void constructorAndSettersValidate() {
    SystemInterface gas = buildGas(20.0, 25.0);
    assertThrows(IllegalArgumentException.class, () -> new VesselFillingSimulator(null, 0.05));
    assertThrows(IllegalArgumentException.class, () -> new VesselFillingSimulator(gas, 0.0));
    VesselFillingSimulator sim = new VesselFillingSimulator(gas, 0.05);
    assertThrows(IllegalArgumentException.class, () -> sim.setInletConditions(288.15, 0.0, 0.01));
    assertThrows(IllegalArgumentException.class, () -> sim.setTargetPressure(-1.0));
    assertThrows(IllegalArgumentException.class, () -> sim.setWoodfieldHeatTransfer(0.0, 1.0));
    assertThrows(IllegalArgumentException.class, () -> sim.setLinerTemperatureLimits(300.0, 250.0));
  }
}
