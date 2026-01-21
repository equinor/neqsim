package neqsim.thermo.util.steam;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the {@link Iapws_if97} steam table implementation.
 *
 * <p>
 * All numeric literals include units explicitly in comments. Input pressure values are given in MPa
 * and temperatures in Kelvin. Output values are documented with their units alongside the
 * assertions.
 * </p>
 */
public class Iapws_if97Test {
  @Test
  public void testSaturation() {
    // input pressure in MPa, result is saturation temperature in Kelvin
    double tSat = Iapws_if97.tsat_p(1.0); // MPa -> K
    Assertions.assertEquals(453.0356, tSat, 1e-3); // Kelvin

    // input temperature in Kelvin, result is saturation pressure in MPa
    double pSat = Iapws_if97.psat_t(373.15); // K -> MPa
    Assertions.assertEquals(0.10142, pSat, 1e-5); // MPa
  }

  @Test
  public void testProperties() {
    // properties at 1 MPa and 773.15 K (500 °C)
    double h = Iapws_if97.h_pt(1.0, 773.15); // MPa, K -> kJ/kg
    Assertions.assertEquals(3479.00, h, 1e-2); // kJ/kg

    double s = Iapws_if97.s_pt(1.0, 773.15); // MPa, K -> kJ/(kg*K)
    Assertions.assertEquals(7.76397, s, 1e-5); // kJ/(kg*K)

    double v = Iapws_if97.v_pt(1.0, 773.15); // MPa, K -> m^3/kg
    Assertions.assertEquals(0.35411, v, 1e-5); // m^3/kg
  }
}
