package neqsim.process.corrosion;

/**
 * Unmitigated CO2 corrosion rate of water-wet carbon steel using NORSOK M-506 Rev. 2 (June 2005), sections 5.2 and 8.1.
 * Supply an in-situ pH and wall shear stress for the wetted surface.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
public final class NorsokM506Rev2 {
  private static final double[] TEMPERATURE_C = {5, 15, 20, 40, 60, 80, 90, 120, 150};
  private static final double[] RATE_CONSTANT = {0.42, 1.59, 4.762, 8.927, 10.695, 9.949, 6.250, 7.770, 5.203};

  /** Utility class. */
  private NorsokM506Rev2() {
  }

  /**
   * Evaluate the section 8.1 CO2 fugacity correlation (equations 4, 5, 7 and 8).
   *
   * @param temperatureC fluid temperature in degrees Celsius
   * @param totalPressureBara absolute total pressure in bar
   * @param gasCO2MoleFraction CO2 mole fraction in gas
   * @return CO2 fugacity in bar; the rate correlation accepts 0.1 to 10 bar
   * @throws IllegalArgumentException for non-finite or out-of-range inputs
   */
  public static double fugacityBar(double temperatureC, double totalPressureBara, double gasCO2MoleFraction) {
    requireRange(temperatureC, 5.0, 150.0, "temperature (degrees C)");
    requireRange(totalPressureBara, 1.0, 1000.0, "total pressure (bara)");
    requireRange(gasCO2MoleFraction, 0.0, 1.0, "gas CO2 mole fraction");
    double exponent = Math.min(250.0, totalPressureBara) * (0.0031 - 1.4 / (temperatureC + 273.15));
    return gasCO2MoleFraction * totalPressureBara * Math.pow(10.0, exponent);
  }

  /**
   * Calculate the bare-steel rate from the section 5.2 correlation. Intermediate temperatures linearly interpolate the
   * two surrounding rates at the supplied fugacity, shear stress and pH.
   *
   * @param temperatureC fluid temperature (5 to 150 degrees Celsius)
   * @param co2FugacityBar CO2 fugacity in gas (0.1 to 10 bar)
   * @param inSituPH water pH at operating conditions (3.5 to 6.5)
   * @param wallShearStressPa shear at the wetted wall (1 to 150 Pa)
   * @return unmitigated corrosion rate in mm/year
   * @throws IllegalArgumentException for non-finite or out-of-range inputs
   */
  public static double rateMmPerYear(double temperatureC, double co2FugacityBar, double inSituPH,
      double wallShearStressPa) {
    requireRange(temperatureC, 5.0, 150.0, "temperature (degrees C)");
    requireRange(co2FugacityBar, 0.1, 10.0, "CO2 fugacity (bar)");
    requireRange(inSituPH, 3.5, 6.5, "in-situ pH");
    requireRange(wallShearStressPa, 1.0, 150.0, "wall shear stress (Pa)");
    int upper = 0;
    while (TEMPERATURE_C[upper] < temperatureC) {
      upper++;
    }
    double shear = Math.pow(wallShearStressPa / 19.0, 0.146 + 0.0324 * Math.log10(co2FugacityBar));
    double upperRate = knotRate(upper, co2FugacityBar, inSituPH, shear);
    if (temperatureC == TEMPERATURE_C[upper]) {
      return upperRate;
    }
    int lower = upper - 1;
    double fraction = (temperatureC - TEMPERATURE_C[lower]) / (TEMPERATURE_C[upper] - TEMPERATURE_C[lower]);
    double lowerRate = knotRate(lower, co2FugacityBar, inSituPH, shear);
    return lowerRate + fraction * (upperRate - lowerRate);
  }

  /**
   * Evaluate the complete rate at one tabulated temperature.
   *
   * @param knot temperature index
   * @param fugacity CO2 fugacity in bar
   * @param ph in-situ water pH
   * @param shearFactor section 5.2 shear multiplier
   * @return rate in mm/year
   */
  private static double knotRate(int knot, double fugacity, double ph, double shearFactor) {
    return RATE_CONSTANT[knot] * Math.pow(fugacity, knot < 2 ? 0.36 : 0.62) * (knot == 0 ? 1.0 : shearFactor)
        * phFactor(knot, ph);
  }

  /**
   * Evaluate the Rev. 2 Table 2 pH factor for a temperature knot. At a shared pH boundary use the higher-pH interval.
   *
   * @param knot temperature index in Table 1
   * @param ph in-situ water pH
   * @return dimensionless pH factor
   */
  private static double phFactor(int knot, double ph) {
    if (knot <= 3 && ph < 4.6) {
      return 2.0676 - 0.2309 * ph;
    }
    switch (knot) {
    case 0:
      return 4.342 + ph * (-1.051 + 0.0708 * ph);
    case 1:
      return 4.986 + ph * (-1.191 + 0.0708 * ph);
    case 2:
    case 3:
      return 5.1885 + ph * (-1.2353 + 0.0708 * ph);
    case 4:
      return ph < 4.6 ? 1.836 - 0.1818 * ph : 15.444 + ph * (-6.1291 + ph * (0.8204 - 0.0371 * ph));
    case 5:
      return ph < 4.6 ? 2.6727 - 0.3636 * ph : 331.68 * Math.exp(-1.2618 * ph);
    case 6:
      if (ph < 4.57) {
        return 3.1355 - 0.4673 * ph;
      }
      return ph < 5.62 ? 21254.0 * Math.exp(-2.1811 * ph) : 0.4014 - 0.0538 * ph;
    case 7:
      if (ph < 4.3) {
        return 1.5375 - 0.125 * ph;
      }
      return ph < 5.0 ? 5.9757 - 1.157 * ph : 0.546125 - 0.071225 * ph;
    default:
      if (ph < 3.8) {
        return 1.0;
      }
      return ph < 5.0 ? 17.634 + ph * (-7.0945 + 0.715 * ph) : 0.037;
    }
  }

  /**
   * Check an inclusive finite range from the operating-input tables.
   *
   * @param value input value
   * @param minimum lower bound
   * @param maximum upper bound
   * @param name input name with units
   */
  private static void requireRange(double value, double minimum, double maximum, String name) {
    if (!Double.isFinite(value) || value < minimum || value > maximum) {
      throw new IllegalArgumentException(name + " must be between " + minimum + " and " + maximum + ": " + value);
    }
  }
}
