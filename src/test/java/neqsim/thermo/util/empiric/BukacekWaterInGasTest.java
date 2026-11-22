package neqsim.thermo.util.empiric;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

/**
 * Round-trip tests for {@link BukacekWaterInGas#waterDewPointTemperature(double, double)} over dry, wet and
 * low-pressure conditions (the fixed-step iteration alone does not converge for wet gas or low pressure).
 */
public class BukacekWaterInGasTest {
  @Test
  void dewPointRoundTripDryWetAndLowPressure() {
    double[][] cases = {{-40.0, 63.0}, {-18.0, 70.0}, {0.0, 63.0}, {25.0, 63.0}, {13.0, 61.0}, {-40.0, 3.5},
        {10.0, 3.5}, {40.0, 20.0}};
    for (double[] c : cases) {
      double x = BukacekWaterInGas.getWaterInGas(273.15 + c[0], c[1]);
      double t = BukacekWaterInGas.waterDewPointTemperature(x, c[1]) - 273.15;
      assertEquals(c[0], t, 0.05, "dew point round trip at " + c[0] + " C, " + c[1] + " bara");
    }
  }
}
