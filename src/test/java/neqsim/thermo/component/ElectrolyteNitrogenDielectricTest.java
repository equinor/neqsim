package neqsim.thermo.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemInterface;

/**
 * Qualifies the nitrogen dielectric input against Solbraa (2002), Table 8-13, p. 176.
 *
 * <p>
 * The constant is a published electrolyte-model approximation, not a pure-gas property correlation.
 * </p>
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class ElectrolyteNitrogenDielectricTest extends neqsim.NeqSimTest {
  /**
   * Checks the source coefficients and their temperature derivatives.
   *
   * @param temperature temperature in kelvin
   */
  @ParameterizedTest
  @ValueSource(doubles = {273.15, 298.15, 373.15})
  void nitrogenRetainsPublishedModelDielectricAndTemperatureDerivatives(double temperature) {
    SystemInterface fluid = new SystemElectrolyteCPAstatoil(temperature, 50.0);
    fluid.addComponent("nitrogen", 1.0);
    ComponentInterface nitrogen = fluid.getPhase(0).getComponent("nitrogen");
    assertEquals(2.0, nitrogen.getDielectricConstant(temperature), 0.0, "Solbraa equation (8.17): d0=2, d1=d2=d3=d4=0");
    assertEquals(0.0, nitrogen.getDielectricConstantdT(temperature), 0.0);
    assertEquals(0.0, nitrogen.getDielectricConstantdTdT(temperature), 0.0);
    double h = 0.01;
    assertEquals(nitrogen.getDielectricConstantdT(temperature),
        (nitrogen.getDielectricConstant(temperature + h) - nitrogen.getDielectricConstant(temperature - h)) / (2.0 * h),
        1e-12);
    assertEquals(nitrogen.getDielectricConstantdTdT(temperature),
        (nitrogen.getDielectricConstantdT(temperature + h) - nitrogen.getDielectricConstantdT(temperature - h))
            / (2.0 * h),
        1e-12);
  }
}
