package neqsim.thermo.phase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemAmmoniaEos;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.ThermodynamicConstantsInterface;

/**
 * Simple sanity check for the {@link PhaseAmmoniaEos} implementation.
 */
class PhaseAmmoniaEosTest {
  @Test
  void testPhaseProperties() {
    SystemInterface system = new SystemAmmoniaEos(298.15, 10.0);
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
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();
    PhaseInterface gas = system.getPhase(0);
    assertEquals(PhaseType.GAS, gas.getType());
    double density = gas.getDensity();
    assertEquals(3.8034829682728444, density, 0.1); // density in kg/m3
  }

  @Test
  void testGasPropertiesAt1atm30C() {
    SystemInterface system = new SystemAmmoniaEos(303.15,
        ThermodynamicConstantsInterface.referencePressure);
    system.addComponent("ammonia", 1.0);
    system.setNumberOfPhases(1);
    system.setMaxNumberOfPhases(1);
    system.setForcePhaseTypes(true);
    system.init(0);
    system.setPhaseType(0, PhaseType.GAS);
    system.init(3);

    PhaseInterface gas = system.getPhase(0);
    assertEquals(0.691, gas.getDensity(), 0.01);
    assertEquals(36.86, gas.getCp(), 0.37);
    assertEquals(28.04, gas.getCv(), 0.28);
    assertEquals(436.7, gas.getSoundSpeed(), 5.0);
    assertEquals(0.0254, gas.getThermalConductivity(), 5.0e-4);
    assertEquals(1.03e-5, gas.getViscosity(), 5.0e-7);
  }


  @Test
  void testLiquidPropertiesAt10bar20C() {
    SystemInterface system = new SystemAmmoniaEos(293.15, 10.0);
    system.addComponent("ammonia", 1.0);
    system.setNumberOfPhases(1);
    system.setMaxNumberOfPhases(1);
    system.setForcePhaseTypes(true);
    system.init(0);
    system.setPhaseType(0, PhaseType.LIQUID);
    system.init(3);

    PhaseInterface liq = system.getPhase(0);
    assertEquals(415.24155674578753, liq.getDensity(), 4.2e-4);
    assertEquals(92.846, liq.getCp(), 0.1);
    assertEquals(87.457, liq.getCv(), 0.1);
    assertEquals(0.0, liq.getSoundSpeed(), 1.0e-6);
    assertEquals(0.23094, liq.getThermalConductivity(), 1.0e-3);
    assertEquals(6.55e-5, liq.getViscosity(), 1.0e-7);
  }
}

