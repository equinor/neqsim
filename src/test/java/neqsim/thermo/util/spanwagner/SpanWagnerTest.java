package neqsim.thermo.util.spanwagner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import org.junit.jupiter.api.Test;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSpanWagnerEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/** Tests for Span-Wagner CO2 model. */
public class SpanWagnerTest {
  private void init(SystemInterface sys) {
    sys.init(0);
    sys.init(1);
    sys.init(2);
    sys.init(3);
  }

  @Test
  public void testGasProperties() {
    SystemInterface sys = new SystemSpanWagnerEos(300.0, 10.0);
    init(sys);
    PhaseInterface phase = sys.getPhase(0);
    double moles = phase.getNumberOfMolesInPhase();
    assertEquals(422.164519, phase.getDensity() / phase.getMolarMass(), 1e-3);
    assertEquals(0.949642965410136, phase.getZ(), 1e-6);
    assertEquals(21953.7555, phase.getEnthalpy() / moles, 1e-2);
    assertEquals(100.754536, phase.getEntropy() / moles, 1e-3);
    assertEquals(40.528088, phase.getCp() / moles, 1e-3);
    assertEquals(30.022037, phase.getCv() / moles, 1e-3);
    assertEquals(19585.0108, phase.getInternalEnergy() / moles, 1e-2);
    assertEquals(-8272.60538, phase.getGibbsEnergy() / moles, 1e-2);
    assertEquals(262.43047, phase.getSoundSpeed(), 1e-2);
    assertEquals(1.0801733, phase.getJouleThomsonCoefficient(), 1e-4);
  }

  @Test
  public void testLiquidProperties() {
    SystemInterface sys = new SystemSpanWagnerEos(280.0, 50.0);
    init(sys);
    PhaseInterface phase = sys.getPhase(1);
    double moles = phase.getNumberOfMolesInPhase();
    assertEquals(20311.453, phase.getDensity() / phase.getMolarMass(), 0.2);
    assertEquals(0.10573879, phase.getZ(), 1e-5);
    assertEquals(9501.661, phase.getEnthalpy() / moles, 0.2);
    assertEquals(46.275024, phase.getEntropy() / moles, 0.02);
    assertEquals(117.93392, phase.getCp() / moles, 0.05);
    assertEquals(41.711594, phase.getCv() / moles, 0.05);
    assertEquals(9255.495, phase.getInternalEnergy() / moles, 0.2);
    assertEquals(-3455.3457, phase.getGibbsEnergy() / moles, 0.2);
    assertEquals(495.5176, phase.getSoundSpeed(), 0.1);
    assertEquals(0.0568959, phase.getJouleThomsonCoefficient(), 1e-6);
  }

  @Test
  public void testTPflashGas() {
    SystemInterface sys = new SystemSpanWagnerEos(300.0, 10.0);
    ThermodynamicOperations ops = new ThermodynamicOperations(sys);
    ops.TPflash();
    assertEquals(PhaseType.GAS, sys.getPhase(0).getType());
  }

  @Test
  public void testTPflashLiquid() {
    SystemInterface sys = new SystemSpanWagnerEos(280.0, 50.0);
    ThermodynamicOperations ops = new ThermodynamicOperations(sys);
    ops.TPflash();
    assertEquals(PhaseType.LIQUID, sys.getPhase(0).getType());
  }

  @Test
  public void testRepeatedInitRestoresCoherentCachedState() {
    SystemInterface sys = new SystemSpanWagnerEos(300.0, 10.0);
    sys.setNumberOfPhases(1);
    sys.setMaxNumberOfPhases(1);
    sys.setForcePhaseTypes(true);
    sys.setPhaseType(0, PhaseType.GAS);
    sys.init(3);
    PhaseInterface phase = sys.getPhase(0);
    double[] first = state(phase);

    sys.init(3);
    double[] repeated = state(phase);
    for (int i = 0; i < first.length; i++) {
      assertEquals(first[i], repeated[i], 0.0, "repeated Span-Wagner property " + i);
    }

    assertEquals(phase.getPressure() * 1.0e5 / (phase.getDensity("mol/m3") * 8.31451 * phase.getTemperature()),
        phase.getZ(), 1e-12);
  }

  @Test
  public void testCacheRefreshesAcrossGasLiquidAndSupercriticalStates() {
    SystemInterface sys = new SystemSpanWagnerEos(300.0, 10.0);
    sys.setNumberOfPhases(1);
    sys.setMaxNumberOfPhases(1);
    sys.setForcePhaseTypes(true);

    double[] gas = state(sys, 300.0, 10.0, PhaseType.GAS);
    double[] liquid = state(sys, 280.0, 50.0, PhaseType.LIQUID);
    double[] supercritical = state(sys, 320.0, 80.0, PhaseType.GAS);
    assertNotEquals(gas[1], liquid[1]);
    assertNotEquals(liquid[1], supercritical[1]);
    assertNotEquals(gas[3], supercritical[3]);

    double[] returned = state(sys, 300.0, 10.0, PhaseType.GAS);
    for (int i = 0; i < gas.length; i++) {
      assertEquals(gas[i], returned[i], 0.0, "returned Span-Wagner property " + i);
    }
  }

  private static double[] state(SystemInterface system, double temperature, double pressure, PhaseType phaseType) {
    system.setTemperature(temperature);
    system.setPressure(pressure);
    system.setPhaseType(0, phaseType);
    system.init(3);
    double[] first = state(system.getPhase(0));
    system.init(3);
    double[] repeated = state(system.getPhase(0));
    for (int i = 0; i < first.length; i++) {
      assertEquals(first[i], repeated[i], 0.0, "same-state Span-Wagner property " + i);
    }
    return first;
  }

  private static double[] state(PhaseInterface phase) {
    return new double[] {phase.getZ(), phase.getDensity("mol/m3"), phase.getComponent(0).getFugacityCoefficient(),
        phase.getEnthalpy("J/mol"), phase.getEntropy("J/molK"), phase.getCp("J/molK"), phase.getCv("J/molK"),
        phase.getSoundSpeed(), phase.getJouleThomsonCoefficient()};
  }
}
