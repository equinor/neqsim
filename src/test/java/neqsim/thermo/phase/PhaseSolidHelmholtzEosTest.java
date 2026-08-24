package neqsim.thermo.phase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.component.ComponentSolidHelmholtzEos;
import neqsim.thermo.util.solid.SolidHelmholtzEquation;
import neqsim.thermo.util.solid.SolidHelmholtzState;

/** Tests the non-cubic lifecycle of {@link PhaseSolidHelmholtzEos}. */
class PhaseSolidHelmholtzEosTest {

  /** Verify two states initialize without cubic EOS machinery or fluid reclassification. */
  @Test
  void testTwoStateLifecycleBypassesCubicEos() {
    PhaseSolidHelmholtzEos phase = new PhaseSolidHelmholtzEos(new TestSolidEquation());
    phase.addComponent("hydrogen", 1.0, 1.0, 0);

    assertState(phase, 12.0, 5.0);
    assertState(phase, 25.0, 250.0);

    ComponentSolidHelmholtzEos component = (ComponentSolidHelmholtzEos) phase.getComponent(0);
    assertEquals(0.0, component.geta(), 0.0);
    assertEquals(0.0, component.getb(), 0.0);
    assertNull(component.getAttractiveParameter());

    PhaseSolidHelmholtzEos clonedPhase = phase.clone();
    assertNotNull(clonedPhase);
    assertNotSame(phase, clonedPhase);
    assertEquals(phase.getType(), clonedPhase.getType());
    assertEquals(phase.getMolarVolume(), clonedPhase.getMolarVolume(), 0.0);
    assertEquals(phase.getDensity(), clonedPhase.getDensity(), 0.0);
  }

  /**
   * Initialize and validate one state.
   *
   * @param phase phase under test
   * @param temperature temperature in K
   * @param pressure pressure in bara
   */
  private static void assertState(PhaseSolidHelmholtzEos phase, double temperature, double pressure) {
    phase.setTemperature(temperature);
    phase.setPressure(pressure);
    phase.init(1.0, 1, 3, PhaseType.GAS, 1.0);

    double expectedMolarVolumeSi = 2.3e-5 * (1.0 - pressure * 1.0e-6);
    assertEquals(PhaseType.SOLID, phase.getType());
    assertEquals(expectedMolarVolumeSi * 1.0e5, phase.getMolarVolume(), 1.0e-12);
    assertEquals(pressure * phase.getMolarVolume() / (ThermodynamicConstantsInterface.R * temperature), phase.getZ(),
        1.0e-12);
    assertEquals(phase.getMolarMass() / expectedMolarVolumeSi, phase.getDensity(), 1.0e-8);
    assertTrue(Double.isFinite(phase.getEnthalpy()));
    assertTrue(phase.getCp() > 0.0);
    assertTrue(phase.getComponent(0).fugcoef(phase) > 0.0);
  }

  /** Deterministic test equation used to isolate the NeqSim lifecycle. */
  private static final class TestSolidEquation implements SolidHelmholtzEquation {
    private static final long serialVersionUID = 1000L;

    /** {@inheritDoc} */
    @Override
    public SolidHelmholtzState evaluate(double temperature, double pressure) {
      double molarVolume = 2.3e-5 * (1.0 - pressure * 1.0e-6);
      double entropy = 10.0 + 0.1 * temperature;
      double internalEnergy = 100.0 + 20.0 * temperature;
      double enthalpy = internalEnergy + pressure * 1.0e5 * molarVolume;
      double gibbsEnergy = enthalpy - temperature * entropy;
      return new SolidHelmholtzState(molarVolume, internalEnergy - temperature * entropy, internalEnergy, entropy,
          enthalpy, gibbsEnergy, 21.0, 20.0, -0.25);
    }
  }
}