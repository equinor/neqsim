package neqsim.pvtsimulation.util;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Water and brine property correlations for reservoir engineering.
 *
 * <p>
 * Provides empirical correlations for water/brine thermodynamic and transport properties commonly
 * needed in reservoir simulation and production engineering. All public methods use Kelvin for
 * temperature and bara for pressure by default.
 *
 * <p>
 * <b>Default units:</b> temperature in Kelvin (K), pressure in bara, salinity in ppm (mg/L)
 * unless otherwise noted. Correlations are computed internally in their published unit systems
 * (typically Fahrenheit / psia) and converted at the API boundary.
 *
 * <p>
 * <b>Correlations included:</b>
 * <ul>
 * <li><b>Water FVF</b> - McCain (1991) and Osif (1988)</li>
 * <li><b>Water Viscosity</b> - McCain and Meehan (1980) dead + live water</li>
 * <li><b>Water Compressibility</b> - McCain (1991) with salinity correction</li>
 * <li><b>Brine Density</b> - Batzle and Wang (1992)</li>
 * <li><b>Solution Gas-Water Ratio</b> - Culberson and McKetta (1951)</li>
 * <li><b>Water-Gas Surface Tension</b> - Jennings and Newman (1971)</li>
 * </ul>
 *
 * @author ESOL
 * @version 2.0
 */
public final class WaterPropertyCorrelations {

  /** Conversion: Kelvin to Fahrenheit. */
  private static final double K_TO_F_SCALE = 9.0 / 5.0;
  /** Conversion offset: Kelvin to Fahrenheit. */
  private static final double K_TO_F_OFFSET = -459.67;
  /** Conversion: bara to psia. */
  private static final double BARA_TO_PSIA = 14.5038;
  /** Conversion: bara to MPa. */
  private static final double BARA_TO_MPA = 0.1;
  /** Conversion: 1/psi to 1/bara. */
  private static final double INV_PSI_TO_INV_BARA = 14.5038;

  private WaterPropertyCorrelations() {
    // Utility class
  }

  /**
   * Convert Kelvin to Fahrenheit.
   *
   * @param tempK Temperature in Kelvin
   * @return Temperature in Fahrenheit
   */
  private static double toF(double tempK) {
    return tempK * K_TO_F_SCALE + K_TO_F_OFFSET;
  }

  /**
   * Convert bara to psia.
   *
   * @param pBara Pressure in bara
   * @return Pressure in psia
   */
  private static double toPsia(double pBara) {
    return pBara * BARA_TO_PSIA;
  }

  // ==================== WATER FORMATION VOLUME FACTOR ====================

  /**
   * McCain (1991) water formation volume factor.
   *
   * <p>
   * Calculates Bw (res bbl/STB) for pure water (no salinity correction).
   * Internally uses Fahrenheit/psia coefficients.
   *
   * $$ B_w = (1 + \Delta V_{wT})(1 + \Delta V_{wP}) $$
   *
   * @param temperatureK Temperature (Kelvin). Valid: 275 - 475 K
   * @param pressureBara Pressure (bara). Valid: 1 - 350 bara
   * @return Water formation volume factor (res bbl/STB), dimensionless
   */
  public static double waterFVFMcCain(double temperatureK, double pressureBara) {
    double tf = toF(temperatureK);
    double pp = toPsia(pressureBara);

    double dvwt = -1.0001e-2 + 1.33391e-4 * tf + 5.50654e-7 * tf * tf;
    double dvwp = -1.95301e-9 * pp * tf - 1.72834e-13 * pp * pp * tf
        - 3.58922e-7 * pp - 2.25341e-10 * pp * pp;
    return (1.0 + dvwt) * (1.0 + dvwp);
  }

  /**
   * Osif (1988) water formation volume factor for brine.
   *
   * <p>
   * Includes salinity effects on water FVF.
   *
   * $$ B_w = \left[1 + \frac{a_1 (T-60) + a_2 (T-60)^2
   *   - a_3 P - a_4 P^2 + a_5 P (T-60)}{a_6}\right] $$
   *
   * @param temperatureK Temperature (Kelvin). Valid: 275 - 475 K
   * @param pressureBara Pressure (bara). Valid: 1 - 350 bara
   * @param salinityPpm  Total dissolved solids (ppm NaCl equivalent)
   * @return Water formation volume factor (res bbl/STB), dimensionless
   */
  public static double waterFVFOsif(double temperatureK, double pressureBara,
      double salinityPpm) {
    double tf = toF(temperatureK);
    double pp = toPsia(pressureBara);

    double s = salinityPpm / 1.0e6;
    double tShift = tf - 60.0;
    double numerator = 5.1e-8 * pp + (tf - 60.0) * (5.47e-6 - 1.95e-10 * pp)
        + (tShift * tShift) * (-3.23e-8) + 8.5e-13 * pp * pp;
    return 1.0 + numerator - s * (0.0840655 * s * Math.sqrt(tf) + 0.0);
  }

  // ==================== WATER VISCOSITY ====================

  /**
   * McCain dead water viscosity (no dissolved gas).
   *
   * <p>
   * Based on Meehan (1980) correlation. Internally uses Fahrenheit.
   *
   * $$ \mu_{w,dead} = A T^B $$
   *
   * @param temperatureK Temperature (Kelvin). Valid: 275 - 475 K
   * @param salinityPpm  Total dissolved solids (ppm NaCl equivalent)
   * @return Dead water viscosity (cP)
   */
  public static double deadWaterViscosityMcCain(double temperatureK, double salinityPpm) {
    double tf = toF(temperatureK);
    if (tf < 100.0) {
      tf = 100.0;
    }
    if (tf > 400.0) {
      tf = 400.0;
    }

    double s = salinityPpm / 1.0e4;
    double a = 109.574 - 8.40564 * s + 0.313314 * s * s + 8.72213e-3 * s * s * s;
    double b = -1.12166 + 2.63951e-2 * s - 6.79461e-4 * s * s
        - 5.47119e-5 * s * s * s + 1.55586e-6 * s * s * s * s;
    return a * Math.pow(tf, b);
  }

  /**
   * McCain water viscosity with dissolved gas correction.
   *
   * <p>
   * Applies Meehan (1980) pressure correction to the dead water viscosity.
   *
   * $$ \mu_w = \mu_{w,dead} \times [0.9994 + 4.0295 \times 10^{-5} P
   *     + 3.1062 \times 10^{-9} P^2] $$
   *
   * @param temperatureK Temperature (Kelvin)
   * @param pressureBara Pressure (bara)
   * @param salinityPpm  Total dissolved solids (ppm NaCl equivalent)
   * @return Water viscosity at P and T (cP)
   */
  public static double waterViscosityMcCain(double temperatureK, double pressureBara,
      double salinityPpm) {
    double pp = toPsia(pressureBara);
    double muDead = deadWaterViscosityMcCain(temperatureK, salinityPpm);
    double factor = 0.9994 + 4.0295e-5 * pp + 3.1062e-9 * pp * pp;
    return muDead * factor;
  }

  // ==================== WATER COMPRESSIBILITY ====================

  /**
   * McCain (1991) water compressibility.
   *
   * <p>
   * Calculates isothermal compressibility of water or brine. Returns result in 1/bara.
   * Internal calculation in 1/psi, converted to 1/bara at the boundary.
   *
   * @param temperatureK Temperature (Kelvin). Valid: 275 - 450 K
   * @param pressureBara Pressure (bara). Valid: 7 - 350 bara
   * @param salinityPpm  Total dissolved solids (ppm NaCl equivalent)
   * @return Water compressibility (1/bara)
   */
  public static double waterCompressibilityMcCain(double temperatureK, double pressureBara,
      double salinityPpm) {
    double tf = toF(temperatureK);
    double pp = toPsia(pressureBara);
    double s = salinityPpm / 1.0e6;

    double cwPureInvPsi = (3.8546 - 0.000134 * pp)
        + (-(0.01052 + 4.77e-7 * pp)) * tf
        + (3.9267e-5 + 8.8e-10 * pp) * tf * tf;
    cwPureInvPsi = cwPureInvPsi * 1.0e-6;

    double salCorr = 1.0 + s * 0.7;
    double cwInvPsi = cwPureInvPsi * salCorr;

    // Convert from 1/psi to 1/bara
    return cwInvPsi * INV_PSI_TO_INV_BARA;
  }

  // ==================== BRINE DENSITY ====================

  /**
   * Batzle and Wang (1992) brine density correlation.
   *
   * <p>
   * Calculates brine density from temperature and pressure. Uses the Batzle-Wang
   * formulation with temperature in Celsius and pressure in MPa internally.
   *
   * @param temperatureK     Temperature (Kelvin). Valid: 275 - 475 K
   * @param pressureBara     Pressure (bara). Valid: 1 - 1000 bara
   * @param salinityWtFrac   Salinity as weight fraction [0, 0.3]
   * @return Brine density (kg/m3)
   */
  public static double brineDensityBatzleWang(double temperatureK, double pressureBara,
      double salinityWtFrac) {
    double tc = temperatureK - 273.15;
    double pMPa = pressureBara * BARA_TO_MPA;
    double s = salinityWtFrac;

    double rhoW = 1.0 + 1.0e-6
        * (-80.0 * tc - 3.3 * tc * tc + 0.00175 * tc * tc * tc + 489.0 * pMPa
            - 2.0 * tc * pMPa + 0.016 * tc * tc * pMPa - 1.3e-5 * tc * tc * tc * pMPa
            - 0.333 * pMPa * pMPa - 0.002 * tc * pMPa * pMPa);
    rhoW = rhoW * 1000.0;

    double rhoBrine = rhoW + s * (0.668 + 0.44 * s
        + 1.0e-6 * (300.0 * pMPa - 2400.0 * pMPa * s + tc
            * (80.0 + 3.0 * tc - 3300.0 * s - 13.0 * pMPa + 47.0 * pMPa * s)))
        * 1000.0;
    return rhoBrine;
  }

  /**
   * Simple water density using Batzle-Wang with zero salinity.
   *
   * @param temperatureK Temperature (Kelvin)
   * @param pressureBara Pressure (bara)
   * @return Water density (kg/m3)
   */
  public static double waterDensity(double temperatureK, double pressureBara) {
    return brineDensityBatzleWang(temperatureK, pressureBara, 0.0);
  }

  // ==================== SOLUTION GAS-WATER RATIO ====================

  /**
   * Culberson and McKetta (1951) solution gas-water ratio.
   *
   * <p>
   * Estimates the volume of gas dissolved in water at reservoir conditions.
   * Returns Rsw in Sm3/Sm3 (standard cubic metres of gas per standard cubic metre of water).
   *
   * @param temperatureK Temperature (Kelvin). Valid: 290 - 510 K
   * @param pressureBara Pressure (bara). Valid: 7 - 700 bara
   * @param salinityPpm  Total dissolved solids (ppm NaCl equivalent)
   * @return Solution gas-water ratio (Sm3/Sm3)
   */
  public static double solutionGasWaterRatioCulberson(double temperatureK, double pressureBara,
      double salinityPpm) {
    double tf = toF(temperatureK);
    double pp = toPsia(pressureBara);

    double a = 8.15839 - 6.12265e-2 * tf + 1.91663e-4 * tf * tf - 2.1654e-7 * tf * tf * tf;
    double b = 1.01021e-2 - 7.44241e-5 * tf + 3.05553e-7 * tf * tf
        - 2.94883e-10 * tf * tf * tf;
    double rswPure = a + b * pp;

    double s = salinityPpm / 1.0e4;
    double salCorr = 1.0 - (0.0753 - 1.73e-4 * tf) * s;
    double rswScfPerBbl = rswPure * salCorr;

    // Convert scf/bbl to Sm3/Sm3
    return rswScfPerBbl * 0.178108;
  }

  // ==================== SURFACE TENSION ====================

  /**
   * Jennings and Newman (1971) water-gas surface tension.
   *
   * <p>
   * Estimates the interfacial tension (IFT) between water and gas. Pressure correction
   * applied above 74 bara (approximately 1000 psig).
   *
   * @param temperatureK Temperature (Kelvin). Valid: 290 - 450 K
   * @param pressureBara Pressure (bara). Valid: 1 - 700 bara
   * @return Surface tension (dyne/cm = mN/m)
   */
  public static double waterGasSurfaceTension(double temperatureK, double pressureBara) {
    double tf = toF(temperatureK);
    double pp = toPsia(pressureBara);

    double sigma74 = 75.0 - 0.1108 * tf - 6.722e-5 * tf * tf;
    if (sigma74 < 1.0) {
      sigma74 = 1.0;
    }

    if (pp <= 1074.0) {
      return sigma74;
    }
    double c = 1.0 - 0.0001 * (pp - 1074.0);
    if (c < 0.2) {
      c = 0.2;
    }
    return sigma74 * c;
  }

  // ==================== SUMMARY METHOD ====================

  /**
   * Compute a summary of water/brine properties at given conditions.
   *
   * @param temperatureK Temperature (Kelvin)
   * @param pressureBara Pressure (bara)
   * @param salinityPpm  Total dissolved solids (ppm NaCl equivalent)
   * @return Map of property name to value (SI units)
   */
  public static Map<String, Double> waterPropertiesSummary(double temperatureK,
      double pressureBara, double salinityPpm) {
    Map<String, Double> props = new LinkedHashMap<String, Double>();
    props.put("temperature_K", temperatureK);
    props.put("pressure_bara", pressureBara);
    props.put("salinity_ppm", salinityPpm);
    props.put("Bw_McCain_resBblPerSTB", waterFVFMcCain(temperatureK, pressureBara));
    props.put("Bw_Osif_resBblPerSTB",
        waterFVFOsif(temperatureK, pressureBara, salinityPpm));
    props.put("muW_dead_cP", deadWaterViscosityMcCain(temperatureK, salinityPpm));
    props.put("muW_cP", waterViscosityMcCain(temperatureK, pressureBara, salinityPpm));
    props.put("cw_invBara",
        waterCompressibilityMcCain(temperatureK, pressureBara, salinityPpm));
    props.put("rhoW_kgPerM3", waterDensity(temperatureK, pressureBara));
    props.put("Rsw_Sm3PerSm3",
        solutionGasWaterRatioCulberson(temperatureK, pressureBara, salinityPpm));
    props.put("sigma_wg_dynePerCm", waterGasSurfaceTension(temperatureK, pressureBara));
    return props;
  }
}
