package neqsim.standards.gasquality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Reference-temperature input contracts shared by both ISO 6976 editions.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class ISO6976ReferenceTemperatureTest extends neqsim.NeqSimTest {
  /**
   * Creates a public synthetic gas without changing shared test state.
   *
   * @param edition2016 whether to use the 2016 tables
   * @param volume metering reference in degrees Celsius
   * @param energy combustion reference in degrees Celsius
   * @return the selected standard
   */
  private Standard_ISO6976 standard(boolean edition2016, double volume, double energy) {
    SystemInterface gas = new SystemSrkEos(288.15, 1.01325);
    gas.addComponent("methane", 0.85);
    gas.addComponent("ethane", 0.07);
    gas.addComponent("propane", 0.03);
    gas.addComponent("n-butane", 0.02);
    gas.addComponent("nitrogen", 0.03);
    gas.setMixingRule("classic");
    gas.init(0);
    return edition2016 ? new Standard_ISO6976_2016(gas, volume, energy, "volume")
        : new Standard_ISO6976(gas, volume, energy, "volume");
  }

  /** Rejects unsupported constructor inputs before changing the requested references. */
  @Test
  void rejectsUnsupportedReferencesOnCalculate() {
    double[] invalid = {30.0, 12.5, -40.0, 1.0e6, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY};
    for (boolean edition2016 : new boolean[] {false, true}) {
      for (double value : invalid) {
        Standard_ISO6976 energy = standard(edition2016, 15.0, value);
        assertThrows(IllegalArgumentException.class, energy::calculate);
        assertEquals(value, energy.getEnergyRefT());
        assertEquals(15.0, energy.getVolRefT());
        Standard_ISO6976 volume = standard(edition2016, value, 25.0);
        assertThrows(IllegalArgumentException.class, volume::calculate);
        assertEquals(value, volume.getVolRefT());
        assertEquals(25.0, volume.getEnergyRefT());
      }
    }
  }

  /** Blocks stale results after invalid setters and permits explicit recovery. */
  @Test
  void invalidReferencesCannotExposePreviousResults() {
    for (boolean edition2016 : new boolean[] {false, true}) {
      Standard_ISO6976 iso = standard(edition2016, 15.0, 25.0);
      iso.calculate();
      double baseline = iso.getValue("GCV");
      iso.setEnergyRefT(30.0);
      assertThrows(IllegalArgumentException.class, () -> iso.getValue("GCV"));
      assertThrows(IllegalArgumentException.class, () -> iso.getValue("WI", "kWh"));
      assertThrows(IllegalArgumentException.class, iso::checkReferenceCondition);
      assertEquals(30.0, iso.getEnergyRefT());
      iso.setEnergyRefT(25.0);
      iso.setVolRefT(12.5);
      assertThrows(IllegalArgumentException.class, () -> iso.getValue("CompressionFactor"));
      assertThrows(IllegalArgumentException.class, iso::calculate);
      assertEquals(12.5, iso.getVolRefT());
      iso.setVolRefT(15.0);
      iso.calculate();
      assertEquals(baseline, iso.getValue("GCV"), 1.0e-10);
    }
  }

  /** Treats roundoff as the same table key without qualifying nearby physical temperatures. */
  @Test
  void acceptsOnlySmallRoundoffAroundTableKeys() {
    for (boolean edition2016 : new boolean[] {false, true}) {
      for (double reference : new double[] {0.0, 15.0, 15.55, 20.0, 25.0}) {
        Standard_ISO6976 exact = standard(edition2016, reference, reference);
        exact.calculate();
        Standard_ISO6976 rounded = standard(edition2016, reference + 1.0e-12, reference - 1.0e-12);
        rounded.calculate();
        assertEquals(reference, rounded.getVolRefT());
        assertEquals(reference, rounded.getEnergyRefT());
        assertEquals(exact.getValue("GCV"), rounded.getValue("GCV"), 1.0e-10);
        Standard_ISO6976 nearby = standard(edition2016, reference + 1.0e-6, reference);
        assertThrows(IllegalArgumentException.class, nearby::calculate);
      }
    }
  }

  /** Does not silently round the exact Fahrenheit conversion onto the legacy 15.55 key. */
  @Test
  void convertedSixtyFahrenheitRequiresExplicitTableSelection() {
    double converted = (60.0 - 32.0) * 5.0 / 9.0;
    for (boolean edition2016 : new boolean[] {false, true}) {
      Standard_ISO6976 iso = standard(edition2016, converted, converted);
      IllegalArgumentException error = assertThrows(IllegalArgumentException.class, iso::calculate);
      assertTrue(error.getMessage().contains("15.55"));
      assertEquals(converted, iso.getVolRefT());
      assertEquals(converted, iso.getEnergyRefT());
    }
  }

  /** Rejects both inputs atomically before canonicalizing either accepted roundoff value. */
  @Test
  void failedValidationDoesNotPartlyRewriteInputsOrResults() {
    for (boolean edition2016 : new boolean[] {false, true}) {
      Standard_ISO6976 iso = standard(edition2016, 15.0, 25.0);
      iso.calculate();
      double molarMass = iso.Mmix;
      double superiorEnergy = iso.HsupIdeal25;
      iso.setVolRefT(20.0 + 1.0e-12);
      iso.setEnergyRefT(30.0);
      assertThrows(IllegalArgumentException.class, iso::calculate);
      assertEquals(20.0 + 1.0e-12, iso.getVolRefT());
      assertEquals(30.0, iso.getEnergyRefT());
      assertEquals(molarMass, iso.Mmix);
      assertEquals(superiorEnergy, iso.HsupIdeal25);
      iso.setVolRefT(30.0);
      iso.setEnergyRefT(20.0 + 1.0e-12);
      assertThrows(IllegalArgumentException.class, iso::checkReferenceCondition);
      assertEquals(20.0 + 1.0e-12, iso.getEnergyRefT());
      assertEquals(30.0, iso.getVolRefT());
    }
  }

  /** Keeps all existing table keys and finite outputs on the documented metering grid. */
  @Test
  void retainsSupportedReferencesAndVolumeConversion() {
    for (boolean edition2016 : new boolean[] {false, true}) {
      for (double volume : new double[] {0.0, 15.0, 15.55, 20.0}) {
        for (double energy : new double[] {0.0, 15.0, 15.55, 20.0, 25.0}) {
          Standard_ISO6976 iso = standard(edition2016, volume, energy);
          iso.calculate();
          double volumetric = iso.getValue("GCV");
          double compression = iso.getValue("CompressionFactor");
          iso.setReferenceType("molar");
          double molar = iso.getValue("GCV");
          double gasConstant = edition2016 ? 8.3144621 : 8.314510;
          assertEquals(molar * 1.01325e5 / (gasConstant * (volume + 273.15) * compression), volumetric, 1.0e-8);
          assertTrue(Double.isFinite(volumetric) && volumetric > 0.0);
          assertEquals(volume, iso.getVolRefT());
          assertEquals(energy, iso.getEnergyRefT());
        }
      }
    }
  }
}
