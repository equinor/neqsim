package neqsim.thermo.phase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemGERG2008Eos;
import neqsim.thermo.system.SystemInterface;

/**
 * Basic property check for {@link PhaseGERG2008Eos} against direct GERG2008 evaluation.
 */
class PhaseGERG2008EosTest {
  @Test
  void testPhasePropertiesMatchGERG() {
    SystemInterface system = new SystemGERG2008Eos(298.15, 10.0);
    system.addComponent("methane", 0.85);
    system.addComponent("hydrogen", 0.15);
    system.init(0);
    system.init(3);

    double[] props = system.getPhase(0).getProperties_GERG2008();
    double moles = system.getPhase(0).getNumberOfMolesInPhase();

    double enthalpy = system.getPhase(0).getEnthalpy() / moles;
    assertEquals(props[7], enthalpy, 1e-9);

    double internalEnergy = system.getPhase(0).getInternalEnergy() / moles;
    assertEquals(props[6], internalEnergy, 1e-9);

    double entropy = system.getPhase(0).getEntropy() / moles;
    assertEquals(props[8], entropy, 1e-9);

    double cv = system.getPhase(0).getCv() / moles;
    assertEquals(props[9], cv, 1e-9);

    double cp = system.getPhase(0).getCp() / moles;
    assertEquals(props[10], cp, 1e-9);

    double soundSpeed = system.getPhase(0).getSoundSpeed();
    assertEquals(props[11], soundSpeed, 1e-2);

    double jt = system.getPhase(0).getJouleThomsonCoefficient();
    assertEquals(props[13] * 1e2, jt, 1e-6);

    double density = system.getPhase(0).getDensity();
    double gergDensity = system.getPhase(0).getDensity_GERG2008();
    assertEquals(gergDensity, density, 1e-10);
  }
}
