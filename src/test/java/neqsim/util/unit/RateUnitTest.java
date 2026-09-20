package neqsim.util.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

/** Regression coverage for rate conversion with instance-specific fluid properties. */
class RateUnitTest {
  @Test
  void storedMassRateConvertsToMolarAndMassUnits() {
    RateUnit rate = new RateUnit(720.0, "kg/hr", 0.020, 800.0, 100.0);
    assertEquals(10.0, rate.getSIvalue(), 1.0e-12);
    assertEquals(10.0, rate.getValue("mol/sec"), 1.0e-12);
    assertEquals(0.2, rate.getValue("kg/sec"), 1.0e-12);
    assertEquals(720.0, rate.getValue("kg/hr"), 1.0e-12);
  }

  @Test
  void explicitConversionRetainsFluidPropertiesAndStoredValue() {
    Unit rate = new RateUnit(720.0, "kg/hr", 0.020, 800.0, 100.0);
    assertEquals(720.0, rate.getValue("kg/hr"), 1.0e-12);
    assertEquals(10.0, rate.getSIvalue(), 1.0e-12);
  }

  @Test
  void staticConversionUsesSuppliedFluidProperties() {
    assertEquals(10.0, RateUnit.convert(720.0, "kg/hr", "mol/sec", 0.020, 800.0, 100.0), 1.0e-12);
    assertEquals(5.0, RateUnit.convert(720.0, "kg/hr", "mol/sec", 0.040, 800.0, 100.0), 1.0e-12);
    assertEquals(2.0, RateUnit.convert(1600.0, "kg/hr", "m3/hr", 0.020, 800.0, 100.0), 1.0e-12);
  }

  @Test
  void propertyFreeStaticConversionAndUnsupportedUnitsFailExplicitly() {
    RateUnit rate = new RateUnit(720.0, "kg/hr", 0.020, 800.0, 100.0);
    assertThrows(UnsupportedOperationException.class, () -> RateUnit.convert(720.0, "kg/hr", "mol/sec"));
  }
}
