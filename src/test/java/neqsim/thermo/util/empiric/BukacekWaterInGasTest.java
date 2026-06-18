package neqsim.thermo.util.empiric;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

public class BukacekWaterInGasTest {
  @Test
  void testGetWaterInGas() {

  }

  @Test
  void testWaterDewPointTemperature() {
    assertEquals(-24.81413239, BukacekWaterInGas.waterDewPointTemperature(20.0e-6, 70.0) - 273.15,
        0.001);
    assertEquals(-1.1135250417,
        BukacekWaterInGas.waterDewPointTemperature(2000.0e-6, 70.0) - 273.15, 0.01);
    assertEquals(-1.1135250417,
        BukacekWaterInGas.waterDewPointTemperature(20000.0e-6, 1.01) - 273.15);
  }
}
