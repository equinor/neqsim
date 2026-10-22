package neqsim.thermo.phase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import neqsim.thermo.component.ComponentSrkCPA;
import neqsim.thermo.system.SystemElectrolyteCPAMM;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;

/**
 * Validates ordinary CPA association-site temperature sensitivities.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class PhaseSrkCPASiteDerivativeTest {
  /** Compares pure-water analytical site sensitivities with fixed-volume differences. */
  @Test
  void pureWaterSecondTemperatureDerivativeMatchesFiniteDifference() {
    SystemInterface fluid = new SystemSrkCPAstatoil(298.15, 1.01325);
    fluid.addComponent("water", 1.0);
    fluid.setMixingRule(10);
    assertTemperatureDerivatives(fluid, "water", 0, 0.01);
  }

  /** Compares K15 electrolyte-CPA water-site sensitivities with fixed-volume differences. */
  @Test
  void k15WaterSecondTemperatureDerivativeMatchesFiniteDifference() {
    SystemInterface fluid = new SystemElectrolyteCPAMM(298.15, 1.01325);
    fluid.addComponent("CO2", 0.1);
    fluid.addComponent("water", 1.0 / 0.01801528);
    double saltMoles = (14.97 / (100.0 - 14.97)) / 0.0745513;
    fluid.addComponent("K+", saltMoles);
    fluid.addComponent("Cl-", saltMoles);
    fluid.setMixingRule(10);
    assertTemperatureDerivatives(fluid, "water", 0, 0.01);
  }

  /**
   * Initializes a fixed-volume state and checks its first and second site sensitivities.
   *
   * @param fluid configured CPA system
   * @param componentName associating component name
   * @param phaseIndex phase containing the association sites
   * @param step central-difference temperature step in K
   */
  private void assertTemperatureDerivatives(SystemInterface fluid, String componentName, int phaseIndex, double step) {
    fluid.init(0);
    fluid.init(3);
    fluid.setUseTVasIndependentVariables(true);
    fluid.init(3);

    ComponentSrkCPA component = component(fluid, componentName, phaseIndex);
    double x = component.getXsite()[0];
    double analyticalFirst = component.getXsitedT()[0];
    double analyticalSecond = component.getXsitedTdT()[0];
    double plus = siteFractionAtTemperature(fluid, componentName, phaseIndex, fluid.getTemperature() + step);
    double minus = siteFractionAtTemperature(fluid, componentName, phaseIndex, fluid.getTemperature() - step);
    double numericalFirst = (plus - minus) / (2.0 * step);
    double numericalSecond = (plus - 2.0 * x + minus) / (step * step);

    assertTrue(Math.abs(analyticalSecond) > 1.0e-12, "Second site sensitivity must be populated: X=" + x + ", X_T="
        + analyticalFirst + ", X_TT=" + analyticalSecond + ", numerical X_TT=" + numericalSecond);
    assertEquals(numericalFirst, analyticalFirst, tolerance(numericalFirst, 5.0e-5, 1.0e-10));
    assertEquals(numericalSecond, analyticalSecond, tolerance(numericalSecond, 5.0e-4, 1.0e-9));
  }

  /**
   * Returns one CPA component from a phase.
   *
   * @param fluid initialized CPA system
   * @param componentName component name
   * @param phaseIndex phase index
   * @return CPA component
   */
  private ComponentSrkCPA component(SystemInterface fluid, String componentName, int phaseIndex) {
    return (ComponentSrkCPA) fluid.getPhase(phaseIndex).getComponent(componentName);
  }

  /**
   * Evaluates one association-site fraction after a fixed-volume temperature perturbation.
   *
   * @param base initialized fixed-volume system
   * @param componentName component name
   * @param phaseIndex phase index
   * @param temperature perturbed temperature in K
   * @return first site fraction
   */
  private double siteFractionAtTemperature(SystemInterface base, String componentName, int phaseIndex,
      double temperature) {
    SystemInterface perturbed = base.clone();
    perturbed.setTemperature(temperature);
    perturbed.init(3);
    return component(perturbed, componentName, phaseIndex).getXsite()[0];
  }

  /**
   * Combines a relative and absolute comparison tolerance.
   *
   * @param value reference value
   * @param relative relative tolerance
   * @param absolute absolute tolerance
   * @return comparison tolerance
   */
  private double tolerance(double value, double relative, double absolute) {
    return Math.max(absolute, Math.abs(value) * relative);
  }
}
