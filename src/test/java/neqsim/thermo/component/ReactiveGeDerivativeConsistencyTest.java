package neqsim.thermo.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import neqsim.thermo.phase.PhaseGE;
import neqsim.thermo.system.SystemDesmukhMather;
import neqsim.thermo.system.SystemEosGE;
import neqsim.thermo.system.SystemKentEisenberg;
import neqsim.thermo.system.SystemPitzer;

/**
 * Checks derivatives against the fugacity actually used by each reactive GE model.
 *
 * @author NeqSim
 * @version 1.0
 */
class ReactiveGeDerivativeConsistencyTest extends neqsim.NeqSimTest {
  /**
   * The complete derivative must preserve each model's Henry, ionic and water-Poynting conventions.
   *
   * @param model reactive GE model name
   * @param name component name
   */
  @ParameterizedTest
  @CsvSource({"pitzer, CO2", "pitzer, water", "pitzer, Na+", "desmukh, CO2", "desmukh, water", "desmukh, Na+",
      "kent, CO2", "kent, water", "kent, Na+"})
  void fugacityDerivativesMatchFixedCompositionDifferences(String model, String name) {
    SystemEosGE system = "pitzer".equals(model) ? new SystemPitzer(313.15, 10.0)
        : "desmukh".equals(model) ? new SystemDesmukhMather(313.15, 10.0) : new SystemKentEisenberg(313.15, 10.0);
    system.addComponent("water", 55.508);
    system.addComponent("CO2", 0.05);
    if (!"pitzer".equals(model)) {
      system.addComponent("MDEA", 1.0);
    }
    system.addComponent("Na+", 0.1);
    system.addComponent("Cl-", 0.1);
    system.setMixingRule("classic");
    system.init(0);
    system.init(1);
    PhaseGE phase = (PhaseGE) system.getGeLiquidPhase();
    ComponentGE component = (ComponentGE) phase.getComponent(name);
    double temperature = phase.getTemperature();
    double pressure = phase.getPressure();
    double temperatureStep = 1.0e-3;
    double pressureStep = 1.0e-4;
    double plus = logFugacity(phase, name, temperature + temperatureStep, pressure);
    double minus = logFugacity(phase, name, temperature - temperatureStep, pressure);
    double temperatureDerivative = (plus - minus) / (2.0 * temperatureStep);
    plus = logFugacity(phase, name, temperature, pressure + pressureStep);
    minus = logFugacity(phase, name, temperature, pressure - pressureStep);
    double pressureDerivative = (plus - minus) / (2.0 * pressureStep);
    logFugacity(phase, name, temperature, pressure);
    assertEquals(temperatureDerivative, component.fugcoefDiffTemp(phase), 1.0e-8, "temperature");
    assertEquals(pressureDerivative, component.fugcoefDiffPres(phase), 1.0e-9, "pressure");
  }

  /**
   * Refreshes the fixed-composition GE activity and returns the actual logarithmic fugacity coefficient.
   *
   * @param phase GE phase
   * @param name component name
   * @param temperature temperature in K
   * @param pressure pressure in bara
   * @return natural logarithm of the fugacity coefficient
   */
  private static double logFugacity(PhaseGE phase, String name, double temperature, double pressure) {
    phase.setTemperature(temperature);
    phase.setPressure(pressure);
    phase.getExcessGibbsEnergy(phase, phase.getNumberOfComponents(), temperature, pressure, phase.getType());
    double coefficient = phase.getComponent(name).fugcoef(phase);
    assertTrue(Double.isFinite(coefficient) && coefficient > 0.0);
    return Math.log(coefficient);
  }
}
