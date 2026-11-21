package neqsim.thermo.phase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Regression tests for caloric properties read directly after a TP flash.
 *
 * <p>
 * A TP flash leaves every phase at init level 1. Before the lazy re-initialisation in
 * {@link Phase#ensureCaloricDerivatives()}, Cp, Cv, enthalpy and entropy then came from the temperature derivatives of
 * the last init(2) call, which could belong to another state. For an SRK-CPA gas/oil/water fluid this gave an aqueous
 * Cp of about 9.1 kJ/(kg*K) instead of 3.9 kJ/(kg*K).
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class CaloricPropertiesAfterTPflashTest {
  /**
   * Builds a gas/oil/water test fluid.
   *
   * @param fluid empty thermodynamic system to fill
   * @return the fluid with components and mixing rule set
   */
  private SystemInterface fill(SystemInterface fluid) {
    fluid.addComponent("methane", 60.0);
    fluid.addComponent("n-heptane", 10.0);
    fluid.addComponent("water", 30.0);
    fluid.addComponent("MEG", 3.0);
    if (fluid instanceof SystemSrkCPAstatoil) {
      fluid.setMixingRule(10);
    } else {
      fluid.setMixingRule("classic");
    }
    fluid.setMultiPhaseCheck(true);
    return fluid;
  }

  /**
   * Flashes the fluid at a first state with full initialisation, then moves it to the test state with a TP flash only.
   *
   * @param fluid the fluid to prepare
   */
  private void flashAtNewStateWithoutInit(SystemInterface fluid) {
    fluid.setTemperature(300.0);
    fluid.setPressure(10.0);
    new ThermodynamicOperations(fluid).TPflash();
    fluid.init(2);
    fluid.setTemperature(338.15);
    fluid.setPressure(100.0);
    new ThermodynamicOperations(fluid).TPflash();
  }

  /**
   * Compares phase and system caloric properties read at init level 1 with the values after an explicit init(2).
   *
   * @param fluid the fluid to check
   */
  private void assertCaloricPropertiesMatchInit2(SystemInterface fluid) {
    flashAtNewStateWithoutInit(fluid);
    int n = fluid.getNumberOfPhases();
    double[] cp = new double[n];
    double[] cv = new double[n];
    double[] h = new double[n];
    double[] s = new double[n];
    for (int i = 0; i < n; i++) {
      cp[i] = fluid.getPhase(i).getCp("J/kgK");
      cv[i] = fluid.getPhase(i).getCv("J/kgK");
      h[i] = fluid.getPhase(i).getEnthalpy("J/mol");
      s[i] = fluid.getPhase(i).getEntropy("J/molK");
    }
    double systemH = fluid.getEnthalpy("J/mol");

    fluid.init(2);
    for (int i = 0; i < n; i++) {
      PhaseInterface phase = fluid.getPhase(i);
      assertEquals(phase.getCp("J/kgK"), cp[i], 1e-6 * Math.abs(cp[i]), "Cp of " + phase.getType());
      assertEquals(phase.getCv("J/kgK"), cv[i], 1e-6 * Math.abs(cv[i]), "Cv of " + phase.getType());
      assertEquals(phase.getEnthalpy("J/mol"), h[i], 1e-6 * Math.abs(h[i]) + 1e-6, "H of " + phase.getType());
      assertEquals(phase.getEntropy("J/molK"), s[i], 1e-6 * Math.abs(s[i]) + 1e-9, "S of " + phase.getType());
    }
    assertEquals(fluid.getEnthalpy("J/mol"), systemH, 1e-6 * Math.abs(systemH));
  }

  /** CPA fluid: caloric properties after a TP flash equal those after init(2). */
  @Test
  void cpaCaloricPropertiesAfterTPflash() {
    assertCaloricPropertiesMatchInit2(fill(new SystemSrkCPAstatoil(338.15, 100.0)));
  }

  /** SRK fluid: caloric properties after a TP flash equal those after init(2). */
  @Test
  void srkCaloricPropertiesAfterTPflash() {
    assertCaloricPropertiesMatchInit2(fill(new SystemSrkEos(338.15, 100.0)));
  }

  /** The aqueous CPA heat capacity read after flash and transport-property init is physical (about 3.9 kJ/kgK). */
  @Test
  void aqueousCpaCpIsPhysicalAfterInitPhysicalProperties() {
    SystemInterface fluid = fill(new SystemSrkCPAstatoil(338.15, 100.0));
    flashAtNewStateWithoutInit(fluid);
    fluid.initPhysicalProperties();
    double cpAqueous = fluid.getPhase("aqueous").getCp("J/kgK");
    assertEquals(3800.0, cpAqueous, 400.0);
  }
}
