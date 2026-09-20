package neqsim.util.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    assertThrows(UnsupportedOperationException.class, () -> RateUnit.convert(720.0, "kg/hr", "mol/sec"));
  }

  @Test
  void conversionFactorsCoverSupportedUnitsAndRejectUnknown() {
    RateUnit rate = new RateUnit(1.0, "mol/sec", 0.020, 800.0, 100.0);
    String[] units = {"mole/sec", "mol/sec", "SI", "mol", "mole/min", "mol/min", "mole/hr", "mol/hr", "kmole/sec",
        "kmol/sec", "kmole/min", "kmol/min", "kmole/hr", "kmol/hr", "kmole/day", "kmol/day", "Nlitre/min", "Nlitre/sec",
        "Am3/hr", "m3/hr", "Am3/day", "m3/day", "Am3/min", "m3/min", "Am3/sec", "m3/sec", "kg/sec", "kg/min", "kg/hr",
        "kg/day", "Sm3/sec", "Sm3/min", "Sm3/hr", "Sm3/day", "MSm3/day", "MSm3/hr", "idSm3/sec", "idSm3/min",
        "idSm3/hr", "idSm3/day", "gallons/min", "lb/hr", "lbmole/hr", "lbmol/hr", "barrel/day", "bbl/day"};
    for (String u : units) {
      assertTrue(rate.getConversionFactor(u) > 0.0, "factor should be positive for " + u);
    }
    assertThrows(RuntimeException.class, () -> rate.getConversionFactor("furlong/fortnight"));
  }

  @Test
  void roundTripConversionsAreConsistent() {
    RateUnit rate = new RateUnit(3.6, "kmol/hr", 0.020, 800.0, 100.0);
    assertEquals(1.0, rate.getSIvalue(), 1.0e-12);
    assertEquals(3600.0, rate.getValue("mol/hr"), 1.0e-9);
    assertEquals(1.0e-3, rate.getValue("kmol/sec"), 1.0e-15);
    assertEquals(72.0, rate.getValue("kg/hr"), 1.0e-9);
    assertEquals(1.2, rate.getValue("kg/min"), 1.0e-9);
    assertEquals(0.02, rate.getValue("kg/sec"), 1.0e-12);
  }

  @Test
  void lowBoilingPointUsesIdealGasBasisForNormalVolume() {
    RateUnit gasRate = new RateUnit(1.0, "mol/sec", 0.016, 0.8, 10.0);
    assertTrue(gasRate.getConversionFactor("Nlitre/sec") > 0.0);
    assertTrue(gasRate.getConversionFactor("Nlitre/min") > 0.0);
  }
}
