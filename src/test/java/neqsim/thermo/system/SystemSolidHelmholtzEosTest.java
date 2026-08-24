package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.phase.PhaseSolidHelmholtzEos;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.util.solid.SolidHelmholtzEquation;
import neqsim.thermo.util.solid.SolidHelmholtzState;

/** Tests the one-phase lifecycle of {@link SystemSolidHelmholtzEos}. */
class SystemSolidHelmholtzEosTest {

  /** Verify initialization, inventory updates, cloning, and pure-component enforcement. */
  @Test
  void testPureSolidSystemLifecycle() {
    SystemSolidHelmholtzEos system = new SystemSolidHelmholtzEos(15.0, 100.0, "H2", new TestSolidEquation());

    system.init(3);
    assertEquals(1, system.getNumberOfPhases());
    assertEquals(1, system.getMaxNumberOfPhases());
    assertTrue(system.getPhase(0) instanceof PhaseSolidHelmholtzEos);
    assertEquals(PhaseType.SOLID, system.getPhase(0).getType());
    assertEquals(1.0, system.getNumberOfMoles(), 0.0);
    assertTrue(system.getPhase(0).getCp() > 0.0);

    system.addComponent("hydrogen", 0.5);
    system.init(3);
    assertEquals(1.5, system.getNumberOfMoles(), 1.0e-12);

    SystemSolidHelmholtzEos clonedSystem = system.clone();
    assertNotSame(system, clonedSystem);
    assertNotSame(system.getPhase(0), clonedSystem.getPhase(0));
    assertEquals(system.getPhase(0).getMolarVolume(), clonedSystem.getPhase(0).getMolarVolume(), 0.0);

    assertThrows(IllegalArgumentException.class, () -> system.addComponent("helium", 1.0));
  }

  /** Deterministic test equation used to isolate system integration. */
  private static final class TestSolidEquation implements SolidHelmholtzEquation {
    private static final long serialVersionUID = 1000L;

    /** {@inheritDoc} */
    @Override
    public SolidHelmholtzState evaluate(double temperature, double pressure) {
      double molarVolume = 2.3e-5;
      double entropy = 12.0;
      double internalEnergy = 500.0;
      double enthalpy = internalEnergy + pressure * 1.0e5 * molarVolume;
      double gibbsEnergy = enthalpy - temperature * entropy;
      return new SolidHelmholtzState(molarVolume, internalEnergy - temperature * entropy, internalEnergy, entropy,
          enthalpy, gibbsEnergy, 21.0, 20.0, -0.2);
    }
  }
}