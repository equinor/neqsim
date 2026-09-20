package neqsim.util.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

/** Regression for superclass stubs shadowing the stored-value conversion default. */
class LinearScaleUnitDispatchTest {
  @Test
  void storedConversionsDispatchThroughUnitAndPreserveState() {
    Unit[] units = {new LengthUnit(1.0, "ft"), new EnergyUnit(1.0, "kWh"), new PowerUnit(1.0, "MW"),
        new TimeUnit(1.0, "hr"), new RateUnit(720.0, "kg/hr", 0.020, 800.0, 100.0)};
    String[] sources = {"ft", "kWh", "MW", "hr", "kg/hr"};
    String[] targets = {"in", "MJ", "kW", "min", "mol/sec"};
    double[] original = {1.0, 1.0, 1.0, 1.0, 720.0};
    double[] expected = {12.0, 3.6, 1000.0, 60.0, 10.0};
    for (int i = 0; i < units.length; i++) {
      Unit unit = units[i];
      double siValue = unit.getSIvalue();
      assertEquals(expected[i], unit.getValue(targets[i]), 1.0e-10);
      assertEquals(expected[i], ((LinearScaleUnit) unit).getValue(targets[i]), 1.0e-10);
      assertEquals(original[i], unit.getValue(sources[i]), 1.0e-10);
      assertEquals(siValue, unit.getSIvalue(), 0.0);
      assertThrows(RuntimeException.class, () -> unit.getValue("unsupported"));
    }
  }
}
