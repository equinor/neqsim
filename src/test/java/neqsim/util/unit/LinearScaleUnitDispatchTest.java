package neqsim.util.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

/** Regression for BaseUnit methods shadowing LinearScaleUnit defaults. */
class LinearScaleUnitDispatchTest {
  @Test
  void explicitConversionsUseArgumentsAndPreserveStoredValues() {
    Unit[] units = { new LengthUnit(2.0, "m"), new EnergyUnit(2.0, "J"), new PowerUnit(2.0, "W"),
        new TimeUnit(2.0, "s") };
    String[] sources = { "ft", "kWh", "MW", "hr" };
    String[] targets = { "in", "MJ", "kW", "min" };
    double[] expected = { 12.0, 3.6, 1000.0, 60.0 };
    for (int i = 0; i < units.length; i++) {
      Unit unit = units[i];
      assertEquals(expected[i], unit.getValue(1.0, sources[i], targets[i]), 1.0e-10);
      assertEquals(1.0, unit.getValue(expected[i], targets[i], sources[i]), 1.0e-12);
      assertEquals(expected[i], ((LinearScaleUnit) unit).getValue(1.0, sources[i], targets[i]), 1.0e-10);
      assertEquals(2.0, unit.getSIvalue(), 0.0);
      final String source = sources[i];
      final String target = targets[i];
      assertThrows(RuntimeException.class, () -> unit.getValue(1.0, "unsupported", target));
      assertThrows(RuntimeException.class, () -> unit.getValue(1.0, source, "unsupported"));
    }
  }
}
