package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.thermo.component.ComponentCPAInterface;
import neqsim.thermo.component.ComponentEosInterface;
import neqsim.thermo.component.ComponentPrCPA;
import neqsim.thermo.component.ComponentUMRCPA;
import neqsim.thermo.mixingrule.EosMixingRulesInterface;
import neqsim.thermo.mixingrule.EosMixingRuleType;
import neqsim.thermo.phase.PhaseCPAInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhasePrEos;
import neqsim.thermo.phase.PhasePrCPA;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.phase.PhaseUMRCPA;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Qualifies the repaired PR-CPA solver, parameter selection, derivatives and state lifecycle.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class PrCpaImplementationTest {
  /** Verifies that sharing a solver does not substitute the UMR-CPA parameter or alpha family. */
  @Test
  void usesPrCpaParametersAndPrAlpha() {
    SystemInterface fluid = liquid(false, 430, 1.2, 0.25, 10);
    assertTrue(fluid.getPhase(0) instanceof PhasePrEos);
    ComponentPrCPA water = (ComponentPrCPA) fluid.getPhase(0).getComponent(0);
    ComponentPrCPA teg = (ComponentPrCPA) fluid.getPhase(0).getComponent(1);
    assertEquals(13855.59696, water.calca(), 1e-8);
    assertEquals(1.456360879, water.calcb(), 1e-10);
    assertEquals(0.664155324, water.getAttractiveTerm().getm(), 1e-10);
    assertEquals(0.046473789, water.getAssociationVolume(), 1e-10);
    assertEquals(391260.0, teg.calca(), 1e-7);
    assertEquals(13.21, teg.calcb(), 1e-10);
    assertEquals(1, water.getAttractiveTermNumber());
    assertEquals(1, teg.getAttractiveTermNumber());
    assertEquals(-0.27,
        ((EosMixingRulesInterface) fluid.getPhase(0).getMixingRule()).getBinaryInteractionParameter(0, 1), 1e-12);
  }

  /**
   * Checks every association-site mass-action equation and the independent fugacity temperature slopes.
   *
   * @param temperature temperature in K
   * @param water water mole fraction
   * @param rule cubic mixing-rule selector
   */
  @ParameterizedTest
  @CsvSource({"350,0.1,7", "390,0.5,9", "430,0.25,10", "430,0.9,10"})
  void massActionAndFugacityDerivatives(double temperature, double water, int rule) {
    PhaseInterface center = liquid(false, temperature, 1.2, water, rule).getPhase(0);
    assertMassAction(center);
    double step = 0.001;
    PhaseInterface plus = liquid(false, temperature + step, 1.2, water, rule).getPhase(0);
    PhaseInterface minus = liquid(false, temperature - step, 1.2, water, rule).getPhase(0);
    assertEquals((plus.getA() - minus.getA()) / (2 * step), center.getAT(), 1e-5);
    assertEquals((plus.getAT() - minus.getAT()) / (2 * step), center.getATT(), 1e-6);
    for (int i = 0; i < center.getNumberOfComponents(); i++) {
      double attraction = (((ComponentEosInterface) plus.getComponent(i)).getAi()
          - ((ComponentEosInterface) minus.getComponent(i)).getAi()) / (2 * step);
      assertEquals(attraction, ((ComponentEosInterface) center.getComponent(i)).getAiT(), 1e-5);
      double numerical = (Math.log(plus.getComponent(i).getFugacityCoefficient())
          - Math.log(minus.getComponent(i).getFugacityCoefficient())) / (2 * step);
      assertEquals(numerical, center.getComponent(i).getdfugdt(), 1e-7);
    }
  }

  /**
   * Checks that non-associating components retain the ordinary PR cubic behavior.
   *
   * @param component pure non-associating component name
   * @param temperature temperature in K
   * @param pressure absolute pressure in bara
   * @param gas true for a gas root, false for a liquid root
   */
  @ParameterizedTest
  @CsvSource({"methane,300,50,true", "n-heptane,330,5,false"})
  void nonAssociatingLimitIsPr(String component, double temperature, double pressure, boolean gas) {
    SystemInterface cpa = new SystemPrCPA(temperature, pressure);
    SystemInterface pr = new SystemPrEos(temperature, pressure);
    for (SystemInterface fluid : new SystemInterface[] {cpa, pr}) {
      fluid.addComponent(component, 1);
      fluid.setMixingRule(1);
      fluid.init(0);
      fluid.setPhaseType(0, gas ? PhaseType.GAS : PhaseType.LIQUID);
      fluid.init(3);
    }
    PhaseInterface expected = pr.getPhase(0);
    PhaseInterface actual = cpa.getPhase(0);
    assertEquals(expected.getMolarVolume(), actual.getMolarVolume(), 1e-7);
    assertEquals(expected.getEnthalpy(), actual.getEnthalpy(), 1e-5);
    assertEquals(expected.getCp(), actual.getCp(), 1e-6);
    assertEquals(expected.getComponent(0).getFugacityCoefficient(), actual.getComponent(0).getFugacityCoefficient(),
        1e-8);
  }

  /**
   * Checks first/second temperature and higher volume site derivatives at fixed EOS volume.
   *
   * @param umr whether to check the existing UMR-CPA model sharing the solver
   */
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void siteDerivativesAtFixedVolume(boolean umr) {
    SystemInterface center = liquid(umr, 390, 1.2, 0.5, 10);
    center.setUseTVasIndependentVariables(true);
    center.init(3);
    SystemInterface plus = center.clone();
    SystemInterface minus = center.clone();
    plus.setTemperature(390.01);
    minus.setTemperature(389.99);
    plus.init(3);
    minus.init(3);
    for (int i = 0; i < 2; i++) {
      ComponentUMRCPA c = (ComponentUMRCPA) center.getPhase(0).getComponent(i);
      ComponentUMRCPA p = (ComponentUMRCPA) plus.getPhase(0).getComponent(i);
      ComponentUMRCPA m = (ComponentUMRCPA) minus.getPhase(0).getComponent(i);
      for (int a = 0; a < c.getNumberOfAssociationSites(); a++) {
        assertEquals((p.getXsite()[a] - m.getXsite()[a]) / 0.02, c.getXsitedT()[a], 1e-8);
        assertEquals((p.getXsitedT()[a] - m.getXsitedT()[a]) / 0.02, c.getXsitedTdT()[a], 1e-9);
        assertEquals((p.getXsitedV()[a] - m.getXsitedV()[a]) / 0.02, c.getXsitedTdV()[a], 1e-8);
      }
    }
    double volume = center.getPhase(0).getTotalVolume();
    double step = volume * 1e-4;
    plus = center.clone();
    minus = center.clone();
    plus.getPhase(0).setTotalVolume(volume + step);
    minus.getPhase(0).setTotalVolume(volume - step);
    plus.init(3);
    minus.init(3);
    for (int i = 0; i < 2; i++) {
      ComponentUMRCPA c = (ComponentUMRCPA) center.getPhase(0).getComponent(i);
      ComponentUMRCPA p = (ComponentUMRCPA) plus.getPhase(0).getComponent(i);
      ComponentUMRCPA m = (ComponentUMRCPA) minus.getPhase(0).getComponent(i);
      for (int a = 0; a < c.getNumberOfAssociationSites(); a++) {
        assertEquals((p.getXsitedV()[a] - m.getXsitedV()[a]) / (2 * step), c.getXsitedVdV()[a], 1e-8);
        assertEquals((p.getXsitedVdV()[a] - m.getXsitedVdV()[a]) / (2 * step), c.getXsitedVdVdV()[a], 1e-8);
        assertEquals((p.getXsitedT()[a] - m.getXsitedT()[a]) / (2 * step), c.getXsitedTdV()[a], 1e-8);
      }
    }
  }

  /**
   * Splitting methane into more than 100 identical species must not change the thermodynamic state.
   *
   * @param umr whether to check the existing UMR-CPA model sharing the solver
   */
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void compositionDerivativesScaleBeyondOneHundredComponents(boolean umr) {
    PhaseUMRCPA reference = splitMethanePhase(umr, 1);
    PhaseUMRCPA expanded = splitMethanePhase(umr, 101);
    assertEquals(reference.getMolarVolume(), expanded.getMolarVolume(), 1e-7);
    assertEquals(reference.getEnthalpy(), expanded.getEnthalpy(), 1e-5);
    assertEquals(reference.getCp(), expanded.getCp(), 1e-6);
    assertMassAction(expanded);
    ComponentUMRCPA water = (ComponentUMRCPA) expanded.getComponent(0);
    for (int a = 0; a < water.getNumberOfAssociationSites(); a++) {
      assertTrue(Double.isFinite(water.getXsitedni(a, 101)));
      assertEquals(water.getXsitedni(a, 1), water.getXsitedni(a, 101), 1e-12);
    }
  }

  /**
   * Checks the association-rule composition derivative at fixed total EOS volume.
   *
   * @param umr whether to check the existing UMR-CPA model sharing the solver
   */
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void associationCompositionDerivativeSupportsPrComponents(boolean umr) {
    SystemInterface center = liquid(umr, 390, 1.2, 0.5, 10);
    double volume = center.getPhase(0).getTotalVolume();
    double step = 1e-6;
    SystemInterface plus = center.clone();
    SystemInterface minus = center.clone();
    plus.addComponent("water", step);
    minus.addComponent("water", -step);
    for (SystemInterface perturbed : new SystemInterface[] {plus, minus}) {
      perturbed.init(0);
      perturbed.setUseTVasIndependentVariables(true);
      perturbed.getPhase(0).setTotalVolume(volume);
      perturbed.init(3);
    }
    PhaseCPAInterface cpa = (PhaseCPAInterface) center.getPhase(0);
    for (int a = 0; a < center.getPhase(0).getComponent(0).getNumberOfAssociationSites(); a++) {
      for (int b = 0; b < center.getPhase(0).getComponent(1).getNumberOfAssociationSites(); b++) {
        if (cpa.getCrossAssosiationScheme(0, 1, a, b) == 0) {
          continue;
        }
        double upper = cpa.getCpaMixingRule().calcDelta(a, b, 0, 1, plus.getPhase(0), 390, 1.2, 2);
        double lower = cpa.getCpaMixingRule().calcDelta(a, b, 0, 1, minus.getPhase(0), 390, 1.2, 2);
        double analytical = cpa.getCpaMixingRule().calcDeltadN(0, a, b, 0, 1, center.getPhase(0), 390, 1.2, 2);
        assertEquals((upper - lower) / (2 * step), analytical, Math.abs(analytical) * 1e-7);
      }
    }
  }

  /**
   * Builds equivalent methane/water phases with one or many independently numbered methane copies.
   *
   * @param umr true for UMR-CPA, false for PR-CPA
   * @param methaneCopies number of identical non-associating species
   * @return initialized liquid phase with one total mole
   */
  private PhaseUMRCPA splitMethanePhase(boolean umr, int methaneCopies) {
    PhaseUMRCPA phase = umr ? new PhaseUMRCPA() : new PhasePrCPA();
    phase.setTemperature(350);
    phase.setPressure(50);
    phase.addComponent("water", 0.75, 0.75, 0);
    for (int i = 1; i <= methaneCopies; i++) {
      phase.addComponent("methane", 0.25 / methaneCopies, 0.25 / methaneCopies, i);
      // Give the independent species unique names while retaining identical methane parameters.
      phase.getComponent(i).setComponentName("methane_copy_" + i);
    }
    phase.setMixingRule(EosMixingRuleType.NO);
    phase.init(1, methaneCopies + 1, 0, PhaseType.LIQUID, 1);
    phase.init(1, methaneCopies + 1, 3, PhaseType.LIQUID, 1);
    return phase;
  }

  /**
   * Checks independent cloned buffers, lazy derivative factorization and new-format serialization.
   *
   * @param umr whether to check the existing UMR-CPA model sharing the solver
   * @throws Exception if serialization fails
   */
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void clonesAndSerializedStatesRemainIndependent(boolean umr) throws Exception {
    SystemInterface source = liquid(umr, 390, 1.2, 0.5, 10);
    double enthalpy = source.getPhase(0).getEnthalpy();
    double cp = source.getPhase(0).getCp();
    SystemInterface copy = source.clone();
    PhaseUMRCPA phase = (PhaseUMRCPA) copy.getPhase(0);
    phase.initCPAMatrix(3);
    assertEquals(cp, phase.getCp(), 1e-9);
    double[] sourceBuffer = ((PhaseUMRCPA) source.getPhase(0)).getdFdNtemp();
    double[] expectedBuffer = sourceBuffer.clone();
    phase.getdFdNtemp()[0] += 1;
    assertArrayEquals(expectedBuffer, sourceBuffer, 0);
    ComponentUMRCPA originalWater = (ComponentUMRCPA) source.getPhase(0).getComponent(0);
    ComponentUMRCPA copiedWater = (ComponentUMRCPA) phase.getComponent(0);
    assertNotSame(originalWater.getXsitedni()[0], copiedWater.getXsitedni()[0]);
    copy.setTemperature(410);
    copy.init(3);
    assertEquals(enthalpy, source.getPhase(0).getEnthalpy(), 1e-9);
    assertEquals(cp, source.getPhase(0).getCp(), 1e-9);
    try (ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
      try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
        output.writeObject(source);
      }
      try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
        SystemInterface restored = (SystemInterface) input.readObject();
        ((PhaseUMRCPA) restored.getPhase(0)).initCPAMatrix(3);
        restored.init(3);
        assertEquals(enthalpy, restored.getPhase(0).getEnthalpy(), 1e-7);
        assertEquals(cp, restored.getPhase(0).getCp(), 1e-8);
        assertMassAction(restored.getPhase(0));
      }
    }
  }

  /** Verifies a wet-gas flash closes component balances and phase fugacities. */
  @Test
  void wetGasFlashClosesAndRepeats() {
    SystemInterface fluid = new SystemPrCPA(303.15, 50);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("water", 0.1);
    fluid.setMixingRule(10);
    fluid.setMultiPhaseCheck(true);
    ThermodynamicOperations operations = new ThermodynamicOperations(fluid);
    operations.TPflash();
    fluid.init(3);
    assertEquals(2, fluid.getNumberOfPhases());
    double enthalpy = fluid.getEnthalpy();
    for (int i = 0; i < 2; i++) {
      double inventory = 0;
      for (int p = 0; p < fluid.getNumberOfPhases(); p++) {
        inventory += fluid.getBeta(p) * fluid.getPhase(p).getComponent(i).getx();
        assertMassAction(fluid.getPhase(p));
      }
      assertEquals(i == 0 ? 0.9 : 0.1, inventory, 1e-9);
      double first = fluid.getPhase(0).getComponent(i).getx()
          * fluid.getPhase(0).getComponent(i).getFugacityCoefficient();
      double second = fluid.getPhase(1).getComponent(i).getx()
          * fluid.getPhase(1).getComponent(i).getFugacityCoefficient();
      assertEquals(0, Math.log(first / second), 1e-6);
    }
    operations.TPflash();
    fluid.init(3);
    assertEquals(enthalpy, fluid.getEnthalpy(), 1e-5);
  }

  /**
   * Evaluates the independently written normalized mass-action residual for every active site.
   *
   * @param phase initialized CPA phase
   */
  private void assertMassAction(PhaseInterface phase) {
    PhaseCPAInterface cpa = (PhaseCPAInterface) phase;
    for (int i = 0; i < phase.getNumberOfComponents(); i++) {
      ComponentCPAInterface component = (ComponentCPAInterface) phase.getComponent(i);
      for (int a = 0; a < component.getNumberOfAssociationSites(); a++) {
        double sum = 0;
        for (int j = 0; j < phase.getNumberOfComponents(); j++) {
          ComponentCPAInterface other = (ComponentCPAInterface) phase.getComponent(j);
          for (int b = 0; b < other.getNumberOfAssociationSites(); b++) {
            sum += other.getNumberOfMolesInPhase() * other.getXsite()[b] * cpa.getCpaMixingRule().calcDelta(a, b, i, j,
                phase, phase.getTemperature(), phase.getPressure(), phase.getNumberOfComponents())
                / phase.getTotalVolume();
          }
        }
        assertTrue(component.getXsite()[a] > 0 && component.getXsite()[a] < 1);
        assertEquals(1, component.getXsite()[a] * (1 + sum), 1e-8);
      }
    }
  }

  /**
   * Constructs an explicit liquid root with a fixed water/TEG composition.
   *
   * @param umr true for UMR-CPA, false for PR-CPA
   * @param temperature temperature in K
   * @param pressure absolute pressure in bara
   * @param water water mole fraction
   * @param rule PR-CPA mixing-rule selector
   * @return initialized system
   */
  private SystemInterface liquid(boolean umr, double temperature, double pressure, double water, int rule) {
    SystemInterface fluid = umr ? new SystemUMRCPAEoS(temperature, pressure) : new SystemPrCPA(temperature, pressure);
    fluid.addComponent("water", water);
    fluid.addComponent("TEG", 1 - water);
    if (umr) {
      fluid.setMixingRule("HV", "UNIFAC_UMRPRU");
    } else {
      fluid.setMixingRule(rule);
    }
    fluid.init(0);
    fluid.setPhaseType(0, PhaseType.LIQUID);
    fluid.init(3);
    return fluid;
  }
}
