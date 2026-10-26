package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.component.ComponentEosInterface;
import neqsim.thermo.component.ComponentUMRCPAvolcor;
import neqsim.thermo.mixingrule.CPAMixingRulesInterface;
import neqsim.thermo.phase.PhaseCPAInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.phase.PhaseUMRCPAvolcor;

/**
 * Checks CPA caloric properties against independent numerical derivatives for issue 4634.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class CPATemperatureConsistencyTest {
  /**
   * Checks first, second and composition-temperature attraction derivatives at fixed amounts.
   *
   * @param temperature temperature in K
   * @param water water mole fraction
   * @param rule cubic mixing-rule selector
   */
  @ParameterizedTest
  @CsvSource({"430,0.25,10", "350,0.1,10", "390,0.5,10", "430,0.9,10", "430,1,10", "430,0,10", "430,0.25,7",
      "430,0.25,9"})
  void attractionDerivatives(double temperature, double water, int rule) {
    double step = 0.01;
    PhaseInterface center = phase("SystemSrkCPAstatoil", temperature, water, rule);
    PhaseInterface plus = phase("SystemSrkCPAstatoil", temperature + step, water, rule);
    PhaseInterface minus = phase("SystemSrkCPAstatoil", temperature - step, water, rule);
    assertEquals((plus.getA() - minus.getA()) / (2 * step), center.getAT(), 1e-5, "A_T");
    assertEquals((plus.getAT() - minus.getAT()) / (2 * step), center.getATT(), 1e-6, "A_TT");
    for (int i = 0; i < center.getNumberOfComponents(); i++) {
      if (center.getComponent(i).getx() == 0) {
        continue;
      }
      ComponentEosInterface component = (ComponentEosInterface) center.getComponent(i);
      double numerical = (((ComponentEosInterface) plus.getComponent(i)).getAi()
          - ((ComponentEosInterface) minus.getComponent(i)).getAi()) / (2 * step);
      assertEquals(numerical, component.getAiT(), 2e-5, "A_iT for component " + i);
    }
  }

  /**
   * Checks the reported residual enthalpy and Cp without an equilibrium flash.
   *
   * @param model system implementation to check
   */
  @ParameterizedTest
  @ValueSource(strings = {"SystemSrkCPA", "SystemSrkCPAs", "SystemSrkCPAstatoil", "SystemUMRCPAEoS",
      "SystemUMRCPAvolcor", "SystemElectrolyteCPA", "SystemElectrolyteCPAstatoil", "SystemElectrolyteCPAAdvanced",
      "SystemElectrolyteCPAMM", "SystemSrkCPAstatoilFullyImplicit", "SystemSrkCPAstatoilFullyImplicitReduced",
      "SystemSrkCPAstatoilReduced", "SystemSrkCPAstatoilBroydenImplicit", "SystemSrkCPAstatoilAndersonMixing",
      "SystemSrkCPAstatoilAndersonReduced"})
  void caloricPropertiesAcrossCpaModels(String model) {
    assertCaloricConsistency(model, 430, 0.25, 10);
  }

  /** Keeps the independent legacy PR-CPA initialization failure reproducible. */
  @Test
  @Disabled("Legacy PR-CPA initialization and association solve require repair: issue #4636")
  void legacyPrCpaCaloricConsistency() {
    assertCaloricConsistency("SystemPrCPA", 430, 0.25, 10);
  }

  /**
   * Checks multiple states, steps and pure-component/mixing-rule controls.
   *
   * @param temperature temperature in K
   * @param water water mole fraction
   * @param rule cubic mixing-rule selector
   */
  @ParameterizedTest
  @CsvSource({"350,0.1,10", "390,0.5,10", "430,0.9,10", "430,1,10", "430,0,10", "430,0.25,7", "430,0.25,9"})
  void caloricStateGrid(double temperature, double water, int rule) {
    assertCaloricConsistency("SystemSrkCPAstatoil", temperature, water, rule);
  }

  /**
   * Checks the translation derivatives against the actual implemented parameter expression.
   *
   * @param temperature temperature in K
   * @param water water mole fraction
   */
  @ParameterizedTest
  @CsvSource({"350,0.1", "390,0.5", "430,0.9", "430,0", "430,1"})
  void translatedCaloricStateGrid(double temperature, double water) {
    assertCaloricConsistency("SystemUMRCPAvolcor", temperature, water, 10);
    PhaseUMRCPAvolcor center = (PhaseUMRCPAvolcor) phase("SystemUMRCPAvolcor", temperature, water, 10);
    PhaseUMRCPAvolcor plus = (PhaseUMRCPAvolcor) phase("SystemUMRCPAvolcor", temperature + 0.01, water, 10);
    PhaseUMRCPAvolcor minus = (PhaseUMRCPAvolcor) phase("SystemUMRCPAvolcor", temperature - 0.01, water, 10);
    assertEquals((plus.getC() - minus.getC()) / 0.02, center.getCT(), 1e-9);
    for (int i = 0; i < center.getNumberOfComponents(); i++) {
      ComponentUMRCPAvolcor component = (ComponentUMRCPAvolcor) center.getComponent(i);
      double numerical = (((ComponentUMRCPAvolcor) plus.getComponent(i)).getCi()
          - ((ComponentUMRCPAvolcor) minus.getComponent(i)).getCi()) / 0.02;
      assertEquals(numerical, component.getCiT(), 1e-9);
    }
  }

  /**
   * Checks explicit association derivatives with the radial distribution held constant.
   *
   * @param partner second associating species; TEG uses CR1, methanol exercises ER
   */
  @ParameterizedTest
  @ValueSource(strings = {"TEG", "methanol", "MEG"})
  void associationDerivatives(String partner) {
    SystemInterface fluid = new SystemSrkCPAstatoil(430, 1.2);
    fluid.addComponent("water", 0.25);
    fluid.addComponent(partner, 0.75);
    fluid.setMixingRule(10);
    fluid.init(0);
    fluid.setPhaseType(0, PhaseType.LIQUID);
    fluid.init(3);
    PhaseInterface phase = fluid.getPhase(0);
    PhaseCPAInterface cpa = (PhaseCPAInterface) phase;
    CPAMixingRulesInterface rule = cpa.getCpaMixingRule();
    double step = 0.01;
    int active = 0;
    for (int i = 0; i < 2; i++) {
      for (int j = 0; j < 2; j++) {
        for (int a = 0; a < phase.getComponent(i).getNumberOfAssociationSites(); a++) {
          for (int b = 0; b < phase.getComponent(j).getNumberOfAssociationSites(); b++) {
            if (cpa.getCrossAssosiationScheme(i, j, a, b) == 0) {
              continue;
            }
            active++;
            phase.setTemperature(430 + step);
            double plus = rule.calcDeltaNog(a, b, i, j, phase, 430 + step, 1.2, 2);
            double firstPlus = rule.calcDeltadT(a, b, i, j, phase, 430 + step, 1.2, 2) / cpa.getGcpa();
            phase.setTemperature(430 - step);
            double minus = rule.calcDeltaNog(a, b, i, j, phase, 430 - step, 1.2, 2);
            double firstMinus = rule.calcDeltadT(a, b, i, j, phase, 430 - step, 1.2, 2) / cpa.getGcpa();
            phase.setTemperature(430);
            double first = rule.calcDeltadT(a, b, i, j, phase, 430, 1.2, 2) / cpa.getGcpa();
            double second = rule.calcDeltadTdT(a, b, i, j, phase, 430, 1.2, 2) / cpa.getGcpa();
            assertEquals((plus - minus) / (2 * step), first, Math.max(1e-9, Math.abs(first) * 1e-7),
                "Delta_T: " + partner + " components " + i + "," + j);
            assertEquals((firstPlus - firstMinus) / (2 * step), second, Math.max(1e-10, Math.abs(second) * 1e-7),
                "Delta_TT: " + partner);
          }
        }
      }
    }
    assertTrue(active > 0);
  }

  /**
   * Compares enthalpy with the Gibbs-Helmholtz identity and Cp with dH/dT at two steps.
   *
   * @param model system implementation
   * @param temperature temperature in K
   * @param water water mole fraction
   * @param rule mixing-rule selector
   */
  private void assertCaloricConsistency(String model, double temperature, double water, int rule) {
    PhaseInterface center = phase(model, temperature, water, rule);
    for (double step : new double[] {0.01, 0.001}) {
      PhaseInterface plus = phase(model, temperature + step, water, rule);
      PhaseInterface minus = phase(model, temperature - step, water, rule);
      double fromFugacity = -ThermodynamicConstantsInterface.R * temperature * temperature
          * (logFugacity(plus) - logFugacity(minus)) / (2 * step);
      assertEquals(fromFugacity, center.getHresTP() / center.getNumberOfMolesInPhase(), 0.01,
          model + " residual enthalpy, step=" + step);
      double cp = (plus.getEnthalpy() / plus.getNumberOfMolesInPhase()
          - minus.getEnthalpy() / minus.getNumberOfMolesInPhase()) / (2 * step);
      assertEquals(cp, center.getCp() / center.getNumberOfMolesInPhase(), 0.01, model + " Cp, step=" + step);
    }
  }

  /**
   * Computes the composition-weighted logarithmic fugacity coefficient.
   *
   * @param phase initialized phase
   * @return dimensionless residual Gibbs energy divided by RT
   */
  private double logFugacity(PhaseInterface phase) {
    double sum = 0;
    for (int i = 0; i < phase.getNumberOfComponents(); i++) {
      sum += phase.getComponent(i).getx() * Math.log(phase.getComponent(i).getFugacityCoefficient());
    }
    return sum;
  }

  /**
   * Builds an explicit liquid root without a flash or changes to the composition.
   *
   * @param model system class name in this package
   * @param temperature temperature in K
   * @param water water mole fraction
   * @param rule cubic mixing-rule selector
   * @return initialized liquid phase
   */
  private PhaseInterface phase(String model, double temperature, double water, int rule) {
    SystemInterface fluid;
    try {
      fluid = (SystemInterface) Class.forName("neqsim.thermo.system." + model)
          .getConstructor(double.class, double.class).newInstance(temperature, 1.2);
    } catch (ReflectiveOperationException ex) {
      throw new AssertionError("Cannot construct " + model, ex);
    }
    if (water > 0) {
      fluid.addComponent("water", water);
    }
    if (water < 1) {
      fluid.addComponent("TEG", 1 - water);
    }
    if (model.startsWith("SystemUMRCPA")) {
      fluid.setMixingRule("HV", "UNIFAC_UMRPRU");
    } else {
      fluid.setMixingRule(rule);
    }
    fluid.init(0);
    fluid.setPhaseType(0, PhaseType.LIQUID);
    fluid.init(3);
    return fluid.getPhase(0);
  }
}
