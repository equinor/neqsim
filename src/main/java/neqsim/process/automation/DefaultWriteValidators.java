package neqsim.process.automation;

import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.valve.ThrottlingValve;

/**
 * Container of {@link WriteValidator} implementations that ship with NeqSim. Each inner class
 * targets a specific equipment class and performs a small set of inexpensive, physically meaningful
 * checks against the proposed write.
 *
 * <p>
 * These validators are deliberately conservative: they reject only writes that are unambiguously
 * unphysical (negative pressures, efficiencies outside {@code [0,1]}, valve openings outside
 * {@code [0,100]}) and emit {@link WriteValidationResult.Severity#WARNING WARNING}-level results
 * for values that are merely unusual (for example outlet pressure below the current inlet pressure
 * on a compressor). Hard hardware-specific limits (surge, choke, MAOP) should be added by users via
 * project-specific validators registered against the same {@link WriteValidatorRegistry}.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class DefaultWriteValidators {

  private DefaultWriteValidators() {
    // utility container
  }

  /**
   * Reads the current pressure of a stream in bara, returning {@code Double.NaN} when the stream or
   * its thermo system is not initialised.
   *
   * @param stream the stream to interrogate
   * @return the pressure in bara, or NaN when unavailable
   */
  private static double readStreamPressureBara(StreamInterface stream) {
    if (stream == null || stream.getThermoSystem() == null) {
      return Double.NaN;
    }
    try {
      return stream.getPressure("bara");
    } catch (Exception ex) {
      return Double.NaN;
    }
  }

  /**
   * Reads the current temperature of a stream in kelvin, returning {@code Double.NaN} when the
   * stream or its thermo system is not initialised.
   *
   * @param stream the stream to interrogate
   * @return the temperature in K, or NaN when unavailable
   */
  private static double readStreamTemperatureK(StreamInterface stream) {
    if (stream == null || stream.getThermoSystem() == null) {
      return Double.NaN;
    }
    try {
      return stream.getTemperature("K");
    } catch (Exception ex) {
      return Double.NaN;
    }
  }

  /**
   * Converts a pressure value to bara from one of the units accepted by NeqSim's stream API.
   * Returns {@code value} unchanged when {@code unit} is null/empty/unrecognised.
   *
   * @param value the pressure value
   * @param unit the unit of measure (case-insensitive)
   * @return the pressure in bara on a best-effort basis
   */
  private static double toBara(double value, String unit) {
    if (unit == null || unit.trim().isEmpty()) {
      return value;
    }
    String u = unit.trim().toLowerCase();
    if (u.equals("bara")) {
      return value;
    }
    if (u.equals("barg")) {
      return value + 1.01325;
    }
    if (u.equals("pa")) {
      return value / 1.0e5;
    }
    if (u.equals("kpa")) {
      return value / 100.0;
    }
    if (u.equals("psia")) {
      return value / 14.5037738;
    }
    if (u.equals("psig")) {
      return value / 14.5037738 + 1.01325;
    }
    if (u.equals("mpa")) {
      return value * 10.0;
    }
    return value;
  }

  /**
   * Converts a temperature value to kelvin from one of the units accepted by NeqSim's stream API.
   * Returns {@code value} unchanged when {@code unit} is null/empty/unrecognised.
   *
   * @param value the temperature value
   * @param unit the unit of measure (case-insensitive)
   * @return the temperature in K on a best-effort basis
   */
  private static double toKelvin(double value, String unit) {
    if (unit == null || unit.trim().isEmpty()) {
      return value;
    }
    String u = unit.trim().toLowerCase();
    if (u.equals("k")) {
      return value;
    }
    if (u.equals("c")) {
      return value + 273.15;
    }
    if (u.equals("f")) {
      return (value - 32.0) * 5.0 / 9.0 + 273.15;
    }
    if (u.equals("r")) {
      return value * 5.0 / 9.0;
    }
    return value;
  }

  /**
   * Validator for {@link Compressor}. Checks that outlet pressure is positive and at least equal to
   * the current inlet pressure, that efficiencies fall in {@code (0,1]}, and that the speed is
   * non-negative.
   */
  public static class CompressorWriteValidator implements WriteValidator {

    /**
     * Creates the validator.
     */
    public CompressorWriteValidator() {
      // intentionally empty
    }

    @Override
    public Class<? extends ProcessEquipmentInterface> getEquipmentClass() {
      return Compressor.class;
    }

    @Override
    public WriteValidationResult validate(ProcessEquipmentInterface equipment, String propertyPath,
        double value, String unit) {
      if (!(equipment instanceof Compressor)) {
        return WriteValidationResult.ok();
      }
      Compressor c = (Compressor) equipment;
      if ("outletPressure".equals(propertyPath)) {
        double bara = toBara(value, unit);
        if (bara <= 0.0) {
          return WriteValidationResult.fail("OUTLET_PRESSURE_NOT_POSITIVE",
              "Compressor outlet pressure must be > 0 bara (got " + bara + " bara)");
        }
        double inlet = readStreamPressureBara(c.getInletStream());
        if (!Double.isNaN(inlet) && bara < inlet) {
          return WriteValidationResult.fail("OUTLET_PRESSURE_BELOW_INLET",
              "Compressor outlet pressure " + bara + " bara is below inlet pressure " + inlet
                  + " bara — a compressor cannot reduce pressure");
        }
        if (!Double.isNaN(inlet) && bara > 50.0 * inlet) {
          return WriteValidationResult.warn("PRESSURE_RATIO_VERY_HIGH", "Compressor pressure ratio "
              + (bara / inlet) + " is unusually high for a single body");
        }
        return WriteValidationResult.ok();
      }
      if ("polytropicEfficiency".equals(propertyPath)
          || "isentropicEfficiency".equals(propertyPath)) {
        if (value <= 0.0 || value > 1.0) {
          return WriteValidationResult.fail("EFFICIENCY_OUT_OF_RANGE",
              propertyPath + " must be in (0,1] (got " + value + ")");
        }
        if (value < 0.4) {
          return WriteValidationResult.warn("EFFICIENCY_LOW",
              propertyPath + " " + value + " is unusually low for a compressor");
        }
        return WriteValidationResult.ok();
      }
      if ("speed".equals(propertyPath)) {
        if (value < 0.0) {
          return WriteValidationResult.fail("SPEED_NEGATIVE",
              "Compressor speed must be non-negative (got " + value + ")");
        }
        return WriteValidationResult.ok();
      }
      return WriteValidationResult.ok();
    }
  }

  /**
   * Validator for {@link Pump}. Checks that outlet pressure is positive and at least equal to the
   * current inlet pressure and that efficiency falls in {@code (0,1]}.
   */
  public static class PumpWriteValidator implements WriteValidator {

    /**
     * Creates the validator.
     */
    public PumpWriteValidator() {
      // intentionally empty
    }

    @Override
    public Class<? extends ProcessEquipmentInterface> getEquipmentClass() {
      return Pump.class;
    }

    @Override
    public WriteValidationResult validate(ProcessEquipmentInterface equipment, String propertyPath,
        double value, String unit) {
      if (!(equipment instanceof Pump)) {
        return WriteValidationResult.ok();
      }
      Pump p = (Pump) equipment;
      if ("outletPressure".equals(propertyPath)) {
        double bara = toBara(value, unit);
        if (bara <= 0.0) {
          return WriteValidationResult.fail("OUTLET_PRESSURE_NOT_POSITIVE",
              "Pump outlet pressure must be > 0 bara (got " + bara + " bara)");
        }
        double inlet = readStreamPressureBara(p.getInletStream());
        if (!Double.isNaN(inlet) && bara < inlet) {
          return WriteValidationResult.fail("OUTLET_PRESSURE_BELOW_INLET",
              "Pump outlet pressure " + bara + " bara is below inlet pressure " + inlet
                  + " bara — a pump cannot reduce pressure");
        }
        return WriteValidationResult.ok();
      }
      if ("isentropicEfficiency".equals(propertyPath)) {
        if (value <= 0.0 || value > 1.0) {
          return WriteValidationResult.fail("EFFICIENCY_OUT_OF_RANGE",
              "Pump efficiency must be in (0,1] (got " + value + ")");
        }
        return WriteValidationResult.ok();
      }
      if ("speed".equals(propertyPath) && value < 0.0) {
        return WriteValidationResult.fail("SPEED_NEGATIVE",
            "Pump speed must be non-negative (got " + value + ")");
      }
      return WriteValidationResult.ok();
    }
  }

  /**
   * Validator for {@link ThrottlingValve}. Checks that outlet pressure is positive and not higher
   * than inlet pressure, that Cv is non-negative, and that {@code percentValveOpening} falls in
   * {@code [0,100]}.
   */
  public static class ThrottlingValveWriteValidator implements WriteValidator {

    /**
     * Creates the validator.
     */
    public ThrottlingValveWriteValidator() {
      // intentionally empty
    }

    @Override
    public Class<? extends ProcessEquipmentInterface> getEquipmentClass() {
      return ThrottlingValve.class;
    }

    @Override
    public WriteValidationResult validate(ProcessEquipmentInterface equipment, String propertyPath,
        double value, String unit) {
      if (!(equipment instanceof ThrottlingValve)) {
        return WriteValidationResult.ok();
      }
      ThrottlingValve v = (ThrottlingValve) equipment;
      if ("outletPressure".equals(propertyPath)) {
        double bara = toBara(value, unit);
        if (bara <= 0.0) {
          return WriteValidationResult.fail("OUTLET_PRESSURE_NOT_POSITIVE",
              "Valve outlet pressure must be > 0 bara (got " + bara + " bara)");
        }
        double inlet = readStreamPressureBara(v.getInletStream());
        if (!Double.isNaN(inlet) && bara > inlet + 1.0e-6) {
          return WriteValidationResult.fail("OUTLET_PRESSURE_ABOVE_INLET",
              "Valve outlet pressure " + bara + " bara is above inlet pressure " + inlet
                  + " bara — a throttling valve cannot raise pressure");
        }
        return WriteValidationResult.ok();
      }
      if ("Cv".equals(propertyPath) && value < 0.0) {
        return WriteValidationResult.fail("CV_NEGATIVE",
            "Valve Cv must be non-negative (got " + value + ")");
      }
      if ("percentValveOpening".equals(propertyPath)) {
        if (value < 0.0 || value > 100.0) {
          return WriteValidationResult.fail("VALVE_OPENING_OUT_OF_RANGE",
              "Valve opening must be in [0,100] %% (got " + value + ")");
        }
        return WriteValidationResult.ok();
      }
      return WriteValidationResult.ok();
    }
  }

  /**
   * Validator for {@link Heater}. Checks that the new outlet temperature is not below the inlet
   * temperature.
   */
  public static class HeaterWriteValidator implements WriteValidator {

    /**
     * Creates the validator.
     */
    public HeaterWriteValidator() {
      // intentionally empty
    }

    @Override
    public Class<? extends ProcessEquipmentInterface> getEquipmentClass() {
      return Heater.class;
    }

    @Override
    public WriteValidationResult validate(ProcessEquipmentInterface equipment, String propertyPath,
        double value, String unit) {
      if (!(equipment instanceof Heater) || equipment instanceof Cooler) {
        return WriteValidationResult.ok();
      }
      Heater h = (Heater) equipment;
      if ("outletTemperature".equals(propertyPath)) {
        double tK = toKelvin(value, unit);
        if (tK <= 0.0) {
          return WriteValidationResult.fail("OUTLET_TEMPERATURE_NOT_POSITIVE",
              "Outlet temperature must be > 0 K (got " + tK + " K)");
        }
        double inletK = readStreamTemperatureK(h.getInletStream());
        if (!Double.isNaN(inletK) && tK < inletK - 1.0e-6) {
          return WriteValidationResult.fail("HEATER_COOLS_FLUID", "Heater outlet temperature " + tK
              + " K is below inlet temperature " + inletK + " K — use a Cooler for negative duty");
        }
      }
      return WriteValidationResult.ok();
    }
  }

  /**
   * Validator for {@link Cooler}. Checks that the new outlet temperature is not above the inlet
   * temperature.
   */
  public static class CoolerWriteValidator implements WriteValidator {

    /**
     * Creates the validator.
     */
    public CoolerWriteValidator() {
      // intentionally empty
    }

    @Override
    public Class<? extends ProcessEquipmentInterface> getEquipmentClass() {
      return Cooler.class;
    }

    @Override
    public WriteValidationResult validate(ProcessEquipmentInterface equipment, String propertyPath,
        double value, String unit) {
      if (!(equipment instanceof Cooler)) {
        return WriteValidationResult.ok();
      }
      Cooler c = (Cooler) equipment;
      if ("outletTemperature".equals(propertyPath)) {
        double tK = toKelvin(value, unit);
        if (tK <= 0.0) {
          return WriteValidationResult.fail("OUTLET_TEMPERATURE_NOT_POSITIVE",
              "Outlet temperature must be > 0 K (got " + tK + " K)");
        }
        double inletK = readStreamTemperatureK(c.getInletStream());
        if (!Double.isNaN(inletK) && tK > inletK + 1.0e-6) {
          return WriteValidationResult.fail("COOLER_HEATS_FLUID", "Cooler outlet temperature " + tK
              + " K is above inlet temperature " + inletK + " K — use a Heater for positive duty");
        }
      }
      return WriteValidationResult.ok();
    }
  }

  /**
   * Validator for {@link Separator}. Currently a placeholder that always passes; provided so
   * downstream projects can register additional separator-specific checks against
   * {@code Separator.class}.
   */
  public static class SeparatorWriteValidator implements WriteValidator {

    /**
     * Creates the validator.
     */
    public SeparatorWriteValidator() {
      // intentionally empty
    }

    @Override
    public Class<? extends ProcessEquipmentInterface> getEquipmentClass() {
      return Separator.class;
    }

    @Override
    public WriteValidationResult validate(ProcessEquipmentInterface equipment, String propertyPath,
        double value, String unit) {
      return WriteValidationResult.ok();
    }
  }
}
