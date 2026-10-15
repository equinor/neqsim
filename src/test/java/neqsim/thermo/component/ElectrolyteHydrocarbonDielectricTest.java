package neqsim.thermo.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Qualifies hydrocarbon liquid reference permittivities and both temperature derivatives.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class ElectrolyteHydrocarbonDielectricTest extends neqsim.NeqSimTest {
  /**
   * Checks Haynes measurements excluded from the alternating-row fit and independent Mopsik data.
   *
   * @param name database component name
   * @param temperature temperature in K
   * @param measured measured relative permittivity
   * @param tolerance absolute tolerance including correlation and source uncertainty
   */
  @ParameterizedTest
  @CsvSource({"n-butane, 140, 2.03139, 0.0002", "n-butane, 170, 1.98187, 0.0002", "n-butane, 200, 1.93297, 0.0002",
      "n-butane, 235, 1.87511, 0.0002", "n-butane, 255, 1.84117, 0.0002", "n-butane, 285, 1.78823, 0.0002",
      "n-butane, 300, 1.76031, 0.0002", "n-hexane, 273.15, 1.9178, 0.005", "n-hexane, 298.15, 1.8799, 0.005"})
  void liquidReferenceDataAndTemperatureDerivatives(String name, double temperature, double measured,
      double tolerance) {
    SystemSrkEos fluid = new SystemSrkEos(temperature, 1.0);
    fluid.addComponent(name, 1.0);
    ComponentInterface component = fluid.getPhase(0).getComponent(name);
    assertEquals(measured, component.getDielectricConstant(temperature), tolerance);
    assertTrue(component.getDielectricConstant(temperature) > 1.0);
    double step = 0.01;
    assertEquals(component.getDielectricConstantdT(temperature),
        (component.getDielectricConstant(temperature + step) - component.getDielectricConstant(temperature - step))
            / (2.0 * step),
        1e-10);
    assertEquals(component.getDielectricConstantdTdT(temperature),
        (component.getDielectricConstantdT(temperature + step) - component.getDielectricConstantdT(temperature - step))
            / (2.0 * step),
        1e-12);
  }
}
