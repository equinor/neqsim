package neqsim.process.safety.depressurization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import org.junit.jupiter.api.Test;

/**
 * Regression tests for {@link DepressurizationSimulator}.
 *
 * <p>
 * These tests guard the mole-basis scaling fix: the simulator must align the fluid mole inventory with the physical
 * vessel volume by scaling the per-component moles (preserving composition), not via {@code setTotalNumberOfMoles},
 * which only sets the scalar total and corrupts the average molar mass. A correct basis yields a physical cold
 * (no-fire) blowdown: monotonic pressure decay, Joule-Thomson cooling, and a self-consistent mass inventory.
 * </p>
 *
 * @author neqsim-engineering-tasks
 * @version 1.0
 */
public class DepressurizationSimulatorTest {

  /**
   * Build a representative lean-to-medium natural-gas vapour at the given conditions.
   *
   * @param pressureBara pressure in bara
   * @param temperatureC temperature in degrees Celsius
   * @return a flashed single-phase gas {@link SystemInterface}
   */
  private SystemInterface buildGas(double pressureBara, double temperatureC) {
    SystemInterface fluid = new SystemSrkEos(273.15 + temperatureC, pressureBara);
    fluid.addComponent("methane", 85.0);
    fluid.addComponent("ethane", 8.0);
    fluid.addComponent("propane", 4.0);
    fluid.addComponent("n-butane", 3.0);
    fluid.setMixingRule("classic");
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();
    fluid.initProperties();
    return fluid;
  }

  /**
   * A cold (no-fire) blowdown must depressurise the vessel, conserve a physically sized inventory, and produce
   * Joule-Thomson cooling rather than a non-physical temperature excursion.
   */
  @Test
  public void coldBlowdownIsPhysical() {
    double vesselVolumeM3 = 30.0;
    double orificeM = 0.030;
    double backPressurePa = 2.0e5;
    SystemInterface gas = buildGas(90.0, 40.0);

    DepressurizationSimulator sim = new DepressurizationSimulator(gas, vesselVolumeM3, orificeM, 0.85, backPressurePa);
    sim.setFireHeatInput(0.0);
    sim.setTimeStep(1.0);
    sim.setMaxTime(1800.0);
    sim.setStopPressure(1.5e5);
    DepressurizationSimulator.DepressurizationResult res = sim.run();

    // Initial state recorded correctly.
    assertEquals(90.0, res.initialPressureBara, 1.0);

    // Inventory must be physically sized (density * volume), not a 1-mol basis.
    double initialMass = res.massKg.get(0);
    assertTrue(initialMass > 100.0, "initial inventory should be hundreds of kg, was " + initialMass + " kg");

    // Pressure must decrease overall and stay finite.
    double firstP = res.pressureBara.get(0);
    double lastP = res.pressureBara.get(res.pressureBara.size() - 1);
    assertTrue(lastP < firstP, "pressure must drop during blowdown");

    // Temperature must remain physical (no 1000+ degC excursion) and show JT cooling.
    double initialTempK = res.temperatureK.get(0);
    assertTrue(res.minFluidTemperatureK < initialTempK, "blowdown should cool the fluid by Joule-Thomson expansion");
    assertTrue(res.minFluidTemperatureK > 100.0 && res.minFluidTemperatureK < 400.0,
	"fluid temperature must stay physical, min was " + res.minFluidTemperatureK + " K");

    // Mass must be monotonically non-increasing (no mass creation).
    for (int i = 1; i < res.massKg.size(); i++) {
      assertTrue(res.massKg.get(i) <= res.massKg.get(i - 1) + 1.0e-6,
	  "mass inventory must not increase during blowdown");
    }

    // The 50% depressurisation criterion should be reachable for this orifice/volume.
    assertTrue(res.halfPressureCriterionMet, "50% depressurisation should be met");
  }
}
