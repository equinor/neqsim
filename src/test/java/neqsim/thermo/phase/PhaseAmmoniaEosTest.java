package neqsim.thermo.phase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemAmmoniaEos;
import neqsim.thermo.system.SystemInterface;

/**
 * Simple sanity check for the {@link PhaseAmmoniaEos} implementation.
 */
class PhaseAmmoniaEosTest {
  @Test
  void testPhaseProperties() {
    SystemInterface system = new SystemAmmoniaEos(298.15, 10.0);
    system.addComponent("ammonia", 1.0);
    system.init(0);
    system.init(3);

    double density = system.getPhase(0).getDensity();
    // reference value from the Ammonia2023 reference equation in kg/m3
    assertEquals(8.306908267489456, density, 8.306908267489456e-6);

    double cp = system.getPhase(0).getCp();
    assertTrue(cp > 0.0);
  }

  @Test
  void testTPflashGasAt5bar20C() {
    SystemInterface system = new SystemAmmoniaEos(293.15, 5.0);
    system.addComponent("ammonia", 1.0);
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();
    PhaseInterface gas = system.getPhase(0);
    assertEquals(PhaseType.GAS, gas.getType());
    double density = gas.getDensity();
    assertEquals(3.8034829682728444, density, 0.1); // density in kg/m3
  }


  @Test
  void testLiquidDensityAt10bar20C() {
    PhaseAmmoniaEos phase = new PhaseAmmoniaEos();
    phase.setTemperature(293.15);
    phase.setPressure(10.0);
    phase.setType(PhaseType.LIQUID);
    double density = phase.getDensity();
    assertEquals(415.2415567457873, density, 415.2415567457873e-6);
  }
}

