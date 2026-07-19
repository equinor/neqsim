package neqsim.thermo.phase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.system.SystemAmmoniaEos;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.exception.IsNaNException;

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
    SystemInterface system = new SystemAmmoniaEos(303.15, ThermodynamicConstantsInterface.referencePressure);
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
    system.setNumberOfPhases(1);
    system.setMaxNumberOfPhases(1);
    system.setForcePhaseTypes(true);
    system.init(0);
    system.setPhaseType(0, PhaseType.LIQUID);
    system.init(3);

    PhaseInterface liq = system.getPhase(0);
    // Reference values from the Gao et al. (2020) EOS as implemented in CoolProp 8.0.0.
    assertEquals(610.5159719028246, liq.getDensity(), 1.0e-3);
    assertEquals(80.67821299666132, liq.getCp(), 1.0e-3);
    assertEquals(47.49260694762637, liq.getCv(), 1.0e-3);
    assertEquals(1374.6068258903206, liq.getSoundSpeed(), 1.0e-2);
    assertEquals(0.5004989173082783, liq.getThermalConductivity(), 1.0e-4);
    assertEquals(1.3860846498812973e-4, liq.getViscosity(), 1.0e-6);
    assertTrue(liq.getCp() > liq.getCv());
  }

  @Test
  void testBubblePointPressureAgainstReferenceEquation() throws IsNaNException {
    double[] temperatures = { 260.0, 280.0, 300.0, 320.0 };
    double[] expectedPressures = { 2.552457115844972, 5.507043744582428, 10.611215021486935, 18.71755110160696 };

    for (int i = 0; i < temperatures.length; i++) {
      SystemInterface system = new SystemAmmoniaEos(temperatures[i], expectedPressures[i]);
      ThermodynamicOperations operations = new ThermodynamicOperations(system);
      operations.bubblePointPressureFlash(false);

      assertEquals(expectedPressures[i], system.getPressure(), 1.0e-4);
    }
  }
}
