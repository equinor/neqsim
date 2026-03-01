package neqsim.pvtsimulation.util;

/**
 * Gas pseudocritical property correlations for natural gas mixtures.
 *
 * <p>
 * Provides correlations to estimate pseudocritical temperature (Kelvin) and pressure (bara) of
 * natural gas mixtures from gas specific gravity. These are essential inputs for
 * corresponding-states correlations (Z-factor, viscosity, compressibility) when detailed
 * composition is unavailable.
 *
 * <p>
 * <b>Default units:</b> All public methods use Kelvin and bara. Internal correlation
 * coefficients use Rankine and psia as published, with conversion at the API boundary.
 *
 * <p>
 * <b>Supported correlations:</b>
 * <ul>
 * <li><b>Standing (1981)</b> - simple correlation from gas specific gravity</li>
 * <li><b>Sutton (1985)</b> - improved accuracy for wider range of gas gravities</li>
 * <li><b>Piper-McCain-Corredor (2012)</b> - uses non-hydrocarbon mole fractions</li>
 * </ul>
 *
 * <p>
 * <b>Acid gas correction:</b>
 * <ul>
 * <li><b>Wichert-Aziz (1972)</b> - correction for H2S and CO2 content</li>
 * </ul>
 *
 * @author ESOL
 * @version 2.0
 */
public final class GasPseudoCriticalProperties {

  /** Conversion factor: Rankine to Kelvin. */
  private static final double R_TO_K = 5.0 / 9.0;
  /** Conversion factor: Kelvin to Rankine. */
  private static final double K_TO_R = 9.0 / 5.0;
  /** Conversion factor: psia per bara. */
  private static final double PSIA_PER_BARA = 14.5038;

  private GasPseudoCriticalProperties() {
    // Utility class
  }

  // ==================== STANDING (1981) ====================

  /**
   * Standing correlation for pseudocritical temperature (1981).
   *
   * <p>
   * Internally uses:
   * $$ T_{pc} = 168 + 325 \gamma_g - 12.5 \gamma_g^2 \quad (\text{Rankine}) $$
   * then converts to Kelvin.
   *
   * @param gammaG Gas specific gravity (air = 1.0). Typical range: 0.55 - 1.8
   * @return Pseudocritical temperature (Kelvin)
   */
  public static double pseudoCriticalTemperatureStanding(double gammaG) {
    double tpcR = 168.0 + 325.0 * gammaG - 12.5 * gammaG * gammaG;
    return tpcR * R_TO_K;
  }

  /**
   * Standing correlation for pseudocritical pressure (1981).
   *
   * <p>
   * Internally uses:
   * $$ P_{pc} = 677 + 15 \gamma_g - 37.5 \gamma_g^2 \quad (\text{psia}) $$
   * then converts to bara.
   *
   * @param gammaG Gas specific gravity (air = 1.0). Typical range: 0.55 - 1.8
   * @return Pseudocritical pressure (bara)
   */
  public static double pseudoCriticalPressureStanding(double gammaG) {
    double ppcPsia = 677.0 + 15.0 * gammaG - 37.5 * gammaG * gammaG;
    return ppcPsia / PSIA_PER_BARA;
  }

  // ==================== SUTTON (1985) ====================

  /**
   * Sutton correlation for pseudocritical temperature (1985).
   *
   * <p>
   * Internally uses:
   * $$ T_{pc} = 169.2 + 349.5 \gamma_g - 74.0 \gamma_g^2 \quad (\text{Rankine}) $$
   * then converts to Kelvin.
   *
   * @param gammaG Gas specific gravity (air = 1.0). Valid range: 0.57 - 1.68
   * @return Pseudocritical temperature (Kelvin)
   */
  public static double pseudoCriticalTemperatureSutton(double gammaG) {
    double tpcR = 169.2 + 349.5 * gammaG - 74.0 * gammaG * gammaG;
    return tpcR * R_TO_K;
  }

  /**
   * Sutton correlation for pseudocritical pressure (1985).
   *
   * <p>
   * Internally uses:
   * $$ P_{pc} = 756.8 - 131.0 \gamma_g - 3.6 \gamma_g^2 \quad (\text{psia}) $$
   * then converts to bara.
   *
   * @param gammaG Gas specific gravity (air = 1.0). Valid range: 0.57 - 1.68
   * @return Pseudocritical pressure (bara)
   */
  public static double pseudoCriticalPressureSutton(double gammaG) {
    double ppcPsia = 756.8 - 131.0 * gammaG - 3.6 * gammaG * gammaG;
    return ppcPsia / PSIA_PER_BARA;
  }

  // ==================== PIPER-McCAIN-CORREDOR (2012) ====================

  /**
   * Piper-McCain-Corredor correlation for pseudocritical temperature (2012).
   *
   * @param gammaG Total gas specific gravity (air = 1.0)
   * @param yH2S  Mole fraction of H2S [0, 1]
   * @param yCO2  Mole fraction of CO2 [0, 1]
   * @param yN2   Mole fraction of N2 [0, 1]
   * @return Pseudocritical temperature (Kelvin)
   */
  public static double pseudoCriticalTemperaturePiper(double gammaG, double yH2S, double yCO2,
      double yN2) {
    double[] jk = piperJK(gammaG, yH2S, yCO2, yN2);
    double tpcR = jk[1] * jk[1] / jk[0];
    return tpcR * R_TO_K;
  }

  /**
   * Piper-McCain-Corredor correlation for pseudocritical pressure (2012).
   *
   * @param gammaG Total gas specific gravity (air = 1.0)
   * @param yH2S  Mole fraction of H2S [0, 1]
   * @param yCO2  Mole fraction of CO2 [0, 1]
   * @param yN2   Mole fraction of N2 [0, 1]
   * @return Pseudocritical pressure (bara)
   */
  public static double pseudoCriticalPressurePiper(double gammaG, double yH2S, double yCO2,
      double yN2) {
    double[] jk = piperJK(gammaG, yH2S, yCO2, yN2);
    double tpcR = jk[1] * jk[1] / jk[0];
    double ppcPsia = tpcR / jk[0];
    return ppcPsia / PSIA_PER_BARA;
  }

  // Critical properties of non-hydrocarbon components (Rankine/psia for correlation)
  /** H2S critical temperature (Rankine). */
  private static final double TC_H2S = 672.35;
  /** H2S critical pressure (psia). */
  private static final double PC_H2S = 1306.0;
  /** CO2 critical temperature (Rankine). */
  private static final double TC_CO2 = 547.58;
  /** CO2 critical pressure (psia). */
  private static final double PC_CO2 = 1071.0;
  /** N2 critical temperature (Rankine). */
  private static final double TC_N2 = 227.16;
  /** N2 critical pressure (psia). */
  private static final double PC_N2 = 493.1;

  /**
   * Compute J and K Stewart-Burkhardt-Voo parameters using Piper-McCain-Corredor regression
   * coefficients. All internal calculations in Rankine/psia.
   *
   * @param gammaG Total gas specific gravity (air = 1.0)
   * @param yH2S  Mole fraction of H2S
   * @param yCO2  Mole fraction of CO2
   * @param yN2   Mole fraction of N2
   * @return double array [J, K]
   */
  private static double[] piperJK(double gammaG, double yH2S, double yCO2, double yN2) {
    double yHC = 1.0 - yH2S - yCO2 - yN2;
    if (yHC <= 0.0) {
      yHC = 1.0;
    }
    double gammaGHC = (gammaG - 0.9672 * yN2 - 1.1765 * yCO2 - 1.5196 * yH2S) / yHC;
    if (gammaGHC < 0.55) {
      gammaGHC = 0.55;
    }

    double fH2SJ = yH2S * (TC_H2S / PC_H2S);
    double fCO2J = yCO2 * (TC_CO2 / PC_CO2);
    double fN2J = yN2 * (TC_N2 / PC_N2);
    double j = 0.11582 - 0.45820 * fH2SJ - 0.90348 * fCO2J - 0.66026 * fN2J
        + 0.70729 * gammaGHC - 0.099397 * gammaGHC * gammaGHC;

    double fH2SK = yH2S * Math.sqrt(TC_H2S / PC_H2S);
    double fCO2K = yCO2 * Math.sqrt(TC_CO2 / PC_CO2);
    double fN2K = yN2 * Math.sqrt(TC_N2 / PC_N2);
    double k = 3.8216 - 0.06534 * fH2SK - 0.42113 * fCO2K - 0.91249 * fN2K
        + 17.438 * gammaGHC - 3.2191 * gammaGHC * gammaGHC;

    return new double[] {j, k};
  }

  // ==================== WICHERT-AZIZ CORRECTION (1972) ====================

  /**
   * Wichert-Aziz correction for acid gas effects on pseudocritical properties.
   *
   * <p>
   * Corrects pseudocritical properties for the presence of H2S and CO2:
   * $$ \varepsilon = 120 (A^{0.9} - A^{1.6}) + 15 (B^{0.5} - B^4) $$
   * where $A = y_{H_2S} + y_{CO_2}$ and $B = y_{H_2S}$.
   *
   * @param tpcKelvin Pseudocritical temperature (Kelvin)
   * @param ppcBara   Pseudocritical pressure (bara)
   * @param yH2S      Mole fraction of H2S [0, 1]
   * @param yCO2      Mole fraction of CO2 [0, 1]
   * @return double array: [corrected Tpc (Kelvin), corrected Ppc (bara)]
   */
  public static double[] wichertAzizCorrection(double tpcKelvin, double ppcBara, double yH2S,
      double yCO2) {
    // Convert to Rankine/psia for internal calculation
    double tpcR = tpcKelvin * K_TO_R;
    double ppcPsia = ppcBara * PSIA_PER_BARA;

    double a = yH2S + yCO2;
    double b = yH2S;

    double epsilon = 120.0 * (Math.pow(a, 0.9) - Math.pow(a, 1.6))
        + 15.0 * (Math.pow(b, 0.5) - Math.pow(b, 4.0));

    double tpcCorrR = tpcR - epsilon;
    double ppcCorrPsia = ppcPsia * tpcCorrR / (tpcR + b * (1.0 - b) * epsilon);

    return new double[] {tpcCorrR * R_TO_K, ppcCorrPsia / PSIA_PER_BARA};
  }

  // ==================== REDUCED PROPERTY CALCULATORS ====================

  /**
   * Calculate pseudoreduced temperature.
   *
   * @param temperatureK Temperature (Kelvin)
   * @param tpcKelvin    Pseudocritical temperature (Kelvin)
   * @return Pseudoreduced temperature (dimensionless)
   */
  public static double pseudoReducedTemperature(double temperatureK, double tpcKelvin) {
    return temperatureK / tpcKelvin;
  }

  /**
   * Calculate pseudoreduced pressure.
   *
   * @param pressureBara Pressure (bara)
   * @param ppcBara      Pseudocritical pressure (bara)
   * @return Pseudoreduced pressure (dimensionless)
   */
  public static double pseudoReducedPressure(double pressureBara, double ppcBara) {
    return pressureBara / ppcBara;
  }
}
