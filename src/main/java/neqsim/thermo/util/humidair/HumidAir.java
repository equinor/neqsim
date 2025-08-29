package neqsim.thermo.util.humidair;

/**
 * Utility class for common humid air calculations.
 *
 * <p>
 * The methods are adapted from the ASHRAE Handbook Fundamentals (2017) and the
 * <a href="https://github.com/CoolProp/CoolProp">CoolProp</a> implementation
 * {@code HumidAirProp.cpp}. Saturation vapour pressures are calculated from the IAPWS formulation
 * of Wagner and Pruss (2002).
 * </p>
 *
 * @author esol
 */
public final class HumidAir {

  /** Ratio of molar masses M_w/M_da. */
  private static final double EPSILON = 0.621945;

  private HumidAir() {}

  /**
   * Saturation vapour pressure of water.
   *
   * <p>
   * Implementation of the IAPWS equation for the vapour pressure of water valid above the triple
   * point. For temperatures below the triple point, a simplified sublimation correlation is
   * applied.
   * </p>
   *
   * @param temperature Temperature in K
   * @return saturation pressure in Pa
   */
  public static double saturationPressureWater(double temperature) {
    if (temperature >= 273.16) {
      // IAPWS formulation for saturation pressure of liquid water
      double Tc = 647.096; // K
      double Pc = 22064000; // Pa
      double theta = 1 - temperature / Tc;
      double a1 = -7.85951783;
      double a2 = 1.84408259;
      double a3 = -11.7866497;
      double a4 = 22.6807411;
      double a5 = -15.9618719;
      double a6 = 1.80122502;
      double lnP =
          Tc / temperature * (a1 * theta + a2 * Math.pow(theta, 1.5) + a3 * Math.pow(theta, 3)
              + a4 * Math.pow(theta, 3.5) + a5 * Math.pow(theta, 4) + a6 * Math.pow(theta, 7.5));
      return Pc * Math.exp(lnP);
    }
    // Sublimation pressure over ice, IAPWS formulation
    double theta = temperature / 273.16;
    double lnP = -13.928169 * (1 - Math.pow(theta, -1.5)) - 34.7078238 * (1 - Math.pow(theta, 1.5))
        + Math.log(611.657);
    return Math.exp(lnP);
  }

  /**
   * Humidity ratio from temperature, pressure and relative humidity.
   *
   * <p>
   * Formula from ASHRAE Fundamentals (2017) with enhancement factor neglected.
   * </p>
   *
   * @param temperature Temperature in K
   * @param pressure total pressure in Pa
   * @param relativeHumidity relative humidity [-]
   * @return humidity ratio (kg water/kg dry air)
   */
  public static double humidityRatioFromRH(double temperature, double pressure,
      double relativeHumidity) {
    double pws = saturationPressureWater(temperature);
    double pw = relativeHumidity * pws;
    return EPSILON * pw / (pressure - pw);
  }

  /**
   * Relative humidity from humidity ratio.
   *
   * @param temperature temperature in K
   * @param pressure total pressure in Pa
   * @param humidityRatio humidity ratio (kg/kg dry air)
   * @return relative humidity [-]
   */
  public static double relativeHumidity(double temperature, double pressure, double humidityRatio) {
    double pws = saturationPressureWater(temperature);
    double pw = humidityRatio * pressure / (EPSILON + humidityRatio);
    return pw / pws;
  }

  /**
   * Dew point temperature from humidity ratio and pressure.
   *
   * <p>
   * Calculated iteratively using the saturation pressure correlation.
   * </p>
   *
   * @param humidityRatio humidity ratio (kg/kg dry air)
   * @param pressure total pressure in Pa
   * @return dew point temperature in K
   */
  public static double dewPointTemperature(double humidityRatio, double pressure) {
    double pw = humidityRatio * pressure / (EPSILON + humidityRatio);
    double T = 273.15; // initial guess
    for (int i = 0; i < 50; i++) {
      double f = saturationPressureWater(T) - pw;
      if (Math.abs(f / pw) < 1e-6) {
        return T;
      }
      double dP = (saturationPressureWater(T + 0.01) - saturationPressureWater(T - 0.01)) / 0.02;
      T -= f / dP;
    }
    return T;
  }

  /**
   * Specific enthalpy of humid air on a dry-air basis.
   *
   * <p>
   * Correlation from ASHRAE Fundamentals (2017) in kJ/kg dry air.
   * </p>
   *
   * @param temperature temperature in K
   * @param humidityRatio humidity ratio (kg/kg dry air)
   * @return specific enthalpy in kJ/kg dry air
   */
  public static double enthalpy(double temperature, double humidityRatio) {
    double tC = temperature - 273.15;
    return 1.006 * tC + humidityRatio * (2501.0 + 1.86 * tC);
  }

  /**
   * Humid air saturation specific heat at 1 atmosphere.
   *
   * <p>
   * Correlation from CoolProp based on EES, valid from 250&nbsp;K to 300&nbsp;K.
   * </p>
   *
   * @param temperature temperature in K
   * @return specific heat in kJ/kg·K
   */
  public static double cairSat(double temperature) {
    double T = temperature;
    return 2.14627073E+03 - 3.28917768E+01 * T + 1.89471075E-01 * T * T - 4.86290986E-04 * T * T * T
        + 4.69540143E-07 * T * T * T * T;
  }
}
