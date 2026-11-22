package neqsim.util.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

/**
 * Contracts shared by scale-only and offset-aware unit strategies.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class UnitStrategyContractTest {
  /** Verify public conversion entry points reject invalid unit names consistently. */
  @Test
  void invalidNamesHaveAnActionableException() {
    PressureUnit pressure = new PressureUnit(1.0, "bara");
    TemperatureUnit temperature = new TemperatureUnit(20.0, "C");
    RateUnit rate = new RateUnit(1.0, "mol/sec", 0.020, 800.0, 100.0);
    for (String invalid : new String[] {null, "", " ", "unsupported"}) {
      assertThrows(IllegalArgumentException.class, () -> pressure.toSIvalue(1.0, invalid));
      assertThrows(IllegalArgumentException.class, () -> pressure.fromSIvalue(1.0, invalid));
      assertThrows(IllegalArgumentException.class, () -> temperature.toSIvalue(1.0, invalid));
      assertThrows(IllegalArgumentException.class, () -> temperature.fromSIvalue(1.0, invalid));
      assertThrows(IllegalArgumentException.class, () -> rate.getConversionFactor(invalid));
      LinearScaleUnit[] scalarUnits = {new LengthUnit(1.0, "m"), new EnergyUnit(1.0, "J"), new PowerUnit(1.0, "W"),
          new TimeUnit(1.0, "s")};
      for (LinearScaleUnit scalar : scalarUnits) {
        assertThrows(IllegalArgumentException.class, () -> scalar.getConversionFactor(invalid));
        assertThrows(IllegalArgumentException.class, () -> scalar.getValue(invalid));
      }
      assertThrows(IllegalArgumentException.class, () -> PressureUnit.convertDifference(0.0, invalid, "Pa"));
      assertThrows(IllegalArgumentException.class, () -> PressureUnit.convertDifference(0.0, "Pa", invalid));
      assertThrows(IllegalArgumentException.class, () -> TemperatureUnit.convertDifference(0.0, invalid, "K"));
      assertThrows(IllegalArgumentException.class, () -> TemperatureUnit.convertDifference(0.0, "K", invalid));
      assertThrows(IllegalArgumentException.class, () -> Units.getSymbol(invalid));
      assertThrows(IllegalArgumentException.class, () -> Units.getSymbolName(invalid));
      assertThrows(IllegalArgumentException.class, () -> Units.setUnit(invalid, "Pa", "Pascal"));
    }
  }

  /** Verify clients cannot corrupt the units accepted by another instance. */
  @Test
  void allowedUnitsAreDefensiveCopies() {
    Unit[] units = {new PressureUnit(1.0, "bara"), new TemperatureUnit(20.0, "C"),
        new RateUnit(1.0, "mol/sec", 0.020, 800.0, 100.0), new LengthUnit(1.0, "m"), new EnergyUnit(1.0, "J"),
        new PowerUnit(1.0, "W"), new TimeUnit(1.0, "s")};
    for (Unit unit : units) {
      String[] names = unit.getAllowedUnits();
      String original = names[0];
      try {
        names[0] = "corrupted";
        assertEquals(original, unit.getAllowedUnits()[0]);
      } finally {
        names[0] = original;
      }
    }
  }

  /** Compare every pressure unit against an independently specified absolute pressure. */
  @Test
  void pressureAliasesShareAnAbsoluteReference() {
    String[] names = {"Pa", "kPa", "MPa", "bara", "bar", "barg", "atm", "psi", "psia", "psig"};
    double[] values = {101325.0, 101.325, 0.101325, 1.01325, 1.01325, 0.0, 1.0, 101325.0 / 6894.757293168,
        101325.0 / 6894.757293168, 0.0};
    for (int source = 0; source < names.length; source++) {
      Unit pressure = new PressureUnit(values[source], names[source]);
      assertEquals(101325.0, pressure.getSIvalue(), 1.0e-6);
      for (int target = 0; target < names.length; target++) {
        assertEquals(values[target], pressure.getValue(names[target]), 1.0e-6, names[source] + " to " + names[target]);
      }
      assertEquals(101325.0, pressure.getSIvalue(), 1.0e-6);
    }
  }

  /** Verify interface dispatch and independent stored values across fluid contexts. */
  @Test
  void strategiesPreserveSourceValues() {
    Unit pressure = new PressureUnit(0.0, "barg");
    Unit temperature = new TemperatureUnit(32.0, "F");
    LinearScaleUnit lightRate = new RateUnit(720.0, "kg/hr", 0.020, 800.0, 100.0);
    LinearScaleUnit heavyRate = new RateUnit(720.0, "kg/hr", 0.040, 900.0, 100.0);
    for (int repeat = 0; repeat < 3; repeat++) {
      assertEquals(1.01325, pressure.getValue("bara"), 1.0e-12);
      assertEquals(273.15, temperature.getSIvalue(), 1.0e-12);
      assertEquals(0.0, temperature.getValue("C"), 1.0e-12);
      assertEquals(10.0, lightRate.getSIvalue(), 1.0e-12);
      assertEquals(5.0, heavyRate.getSIvalue(), 1.0e-12);
      assertEquals(720.0, lightRate.getValue("kg/hr"), 1.0e-12);
      assertEquals(720.0, heavyRate.getValue("kg/hr"), 1.0e-12);
    }
  }

  /** Verify signed differences never acquire an absolute reference offset. */
  @Test
  void differencesUseOnlyTheScale() {
    assertEquals(1.0, PressureUnit.convertDifference(100.0, "kPa", "barg"), 1.0e-12);
    assertEquals(-6894.757293168, PressureUnit.convertDifference(-1.0, "psig", "Pa"), 1.0e-6);
    for (String source : new PressureUnit(0.0, "Pa").getAllowedUnits()) {
      for (String target : new PressureUnit(0.0, "Pa").getAllowedUnits()) {
        assertEquals(0.0, PressureUnit.convertDifference(0.0, source, target), 0.0);
      }
    }
    assertEquals(1.0e-20, PressureUnit.convertDifference(1.0e-20, "barg", "bar"), 1.0e-35);
    assertEquals(10.0, TemperatureUnit.convertDifference(18.0, "F", "C"), 1.0e-12);
    assertEquals(-18.0, TemperatureUnit.convertDifference(-10.0, "K", "R"), 1.0e-12);
    for (String source : new String[] {"K", "C", "F", "R"}) {
      for (String target : new String[] {"K", "C", "F", "R"}) {
        assertEquals(0.0, TemperatureUnit.convertDifference(0.0, source, target), 0.0);
      }
    }
    assertEquals(1.01325, PressureUnit.convert(0.0, "barg", "bar"), 1.0e-12);
    assertEquals(273.15, TemperatureUnit.convert(0.0, "C", "K"), 1.0e-12);
  }

  /** Verify old public scale methods retain their original, different reference units. */
  @Test
  @SuppressWarnings("deprecation")
  void deprecatedScaleMethodsKeepTheirOriginalUnits() {
    PressureUnit pressure = new PressureUnit(0.0, "Pa");
    assertEquals(1.0e-5, pressure.getConversionFactor("Pa"), 1.0e-16);
    assertEquals(0.0689475729317831, pressure.getConversionFactor("psig"), 1.0e-15);
    assertEquals(1.0, pressure.getConversionFactor("barg"), 1.0e-12);
    TemperatureUnit temperature = new TemperatureUnit(0.0, "C");
    assertEquals(1.0, temperature.getConversionFactor("C"), 1.0e-12);
    assertEquals(5.0 / 9.0, temperature.getConversionFactor("F"), 1.0e-12);
  }

  /** Verify legacy subclasses can still use protected storage and inherited SI readback. */
  @Test
  void legacySubclassRemainsUsableThroughUnit() {
    Unit unit = new LegacyUnit(2.0);
    assertEquals(20.0, unit.getSIvalue(), 1.0e-12);
    assertEquals(2.0, unit.getValue("legacy"), 1.0e-12);
  }

  /**
   * Source-compatibility fixture for third-party extensions of BaseUnit.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  @SuppressWarnings("deprecation")
  private static class LegacyUnit extends BaseUnit {
    /**
     * Construct an extension using the pre-refactor protected fields.
     *
     * @param value source value
     */
    LegacyUnit(double value) {
      super(value, "legacy");
      factor = 10.0;
      SIvalue = invalue * factor;
    }

    /** {@inheritDoc} */
    @Override
    public String[] getAllowedUnits() {
      return new String[] {"legacy"};
    }

    /** {@inheritDoc} */
    @Override
    public String getSIUnit() {
      return "SI";
    }

    /** {@inheritDoc} */
    @Override
    public double getValue(String unit) {
      validateAllowedUnit(unit);
      return getSIvalue() / factor;
    }
  }
}
