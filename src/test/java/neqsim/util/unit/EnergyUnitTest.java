package neqsim.util.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

class EnergyUnitTest extends neqsim.NeqSimTest {
  @Test
  void testSIValue() {
    assertEquals(1000.0, new EnergyUnit(1.0, "kJ").getSIvalue(), 1e-12);
    assertEquals(3.6e6, new EnergyUnit(1.0, "kWh").getSIvalue(), 1e-6);
  }

  @Test
  void testAllConversionFactorsAndConvert() {
    assertEquals(1.0, new EnergyUnit(1.0, "J").getSIvalue(), 1e-12);
    assertEquals(1.0e6, new EnergyUnit(1.0, "MJ").getSIvalue(), 1e-6);
    assertEquals(3600.0, new EnergyUnit(1.0, "Wh").getSIvalue(), 1e-9);
    assertEquals(3.6e9, new EnergyUnit(1.0, "MWh").getSIvalue(), 1e-3);
    assertEquals(1055.05585, new EnergyUnit(1.0, "BTU").getSIvalue(), 1e-6);
    assertEquals(4184.0, new EnergyUnit(1.0, "kcal").getSIvalue(), 1e-9);

    assertEquals(1000.0, EnergyUnit.convert(1.0, "MWh", "kWh"), 1e-6);
    assertEquals(1.0, new EnergyUnit(1000.0, "kJ").getValue("MJ"), 1e-12);
    assertEquals(0.001, new EnergyUnit(1.0, "J").getValue("kJ"), 1e-15);
  }

  @Test
  void testUnsupportedUnitThrows() {
    assertThrows(RuntimeException.class, () -> new EnergyUnit(1.0, "erg").getSIvalue());
    assertThrows(RuntimeException.class, () -> new EnergyUnit(1.0, "J").getValue("erg"));
  }
}
