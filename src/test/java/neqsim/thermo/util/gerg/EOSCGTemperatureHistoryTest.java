package neqsim.thermo.util.gerg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.netlib.util.doubleW;

/** Pressure at a state must not depend on the temperature iteration history. */
class EOSCGTemperatureHistoryTest {
  @ParameterizedTest
  @ValueSource(doubles = {-5.0e-8, 5.0e-8})
  void smallTemperatureStepsMatchFreshState(double temperatureStep) {
    EOSCG reused = new EOSCG();
    double[] composition = new double[29];
    composition[3] = 1.0;
    double temperature = 289.0;
    pressure(reused, temperature, composition);
    for (int step = 0; step < 20; step++) {
      temperature += temperatureStep;
      assertEquals(pressure(new EOSCG(), temperature, composition), pressure(reused, temperature, composition), 1.0e-9,
          "Small temperature updates must agree with a freshly evaluated state");
    }
  }

  private static double pressure(EOSCG model, double temperature, double[] composition) {
    doubleW pressure = new doubleW(0.0);
    model.pressure(temperature, 18.0, composition, pressure, new doubleW(0.0));
    return pressure.val;
  }
}
