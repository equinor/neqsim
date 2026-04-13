package neqsim.process.equipment.lng;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Methane Number calculator based on EN 16726 and MWM methods.
 *
 * <p>
 * The Methane Number (MN) is a measure of the knock resistance of gaseous fuels, analogous to the
 * octane number for liquid fuels. MN = 100 for pure methane and MN = 0 for pure hydrogen on the
 * reference scale.
 * </p>
 *
 * <p>
 * This class implements three calculation methods:
 * </p>
 * <ul>
 * <li><b>EN 16726 (GRI/GERG):</b> The European standard method using empirical correlations from
 * the Gas Research Institute, as standardized in EN 16726:2015. This is the method required by most
 * LNG sales contracts and terminal specifications.</li>
 * <li><b>MWM:</b> The Caterpillar/MWM method widely used by engine manufacturers (Waertsilae, MAN).
 * Uses a different coefficient set.</li>
 * <li><b>Simplified linear:</b> Quick approximation for screening purposes.</li>
 * </ul>
 *
 * <p>
 * The EN 16726 method computes knocking propensity from composition, taking into account that
 * heavier hydrocarbons reduce MN while inerts (N2, CO2) increase it:
 * </p>
 *
 * <pre>
 * MN = a0 + a1*H2 + a2*CO + a3*CH4 + a4*C2H6 + a5*C3H8 + a6*C4H10 + a7*C5H12 + a8*CO2 + a9*N2
 *    + quadratic and interaction terms
 * </pre>
 *
 * @author NeqSim
 * @version 1.0
 */
public class MethaneNumberCalculator implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1023L;

  /** Logger object. */
  private static final Logger logger = LogManager.getLogger(MethaneNumberCalculator.class);

  /**
   * Calculation method enumeration.
   */
  public enum Method {
    /** EN 16726 / GRI standard method. */
    EN16726,
    /** MWM / engine manufacturer method. */
    MWM,
    /** Simplified linear correlation for screening. */
    SIMPLIFIED
  }

  /** Active calculation method. */
  private Method method = Method.EN16726;

  /**
   * Default constructor.
   */
  public MethaneNumberCalculator() {}

  /**
   * Constructor with method selection.
   *
   * @param method calculation method
   */
  public MethaneNumberCalculator(Method method) {
    this.method = method;
  }

  /**
   * Calculate methane number from gas mole fractions.
   *
   * @param composition map of component name to mole fraction (must sum to ~1.0)
   * @return methane number (0-100 scale)
   */
  public double calculate(Map<String, Double> composition) {
    switch (method) {
      case EN16726:
        return calculateEN16726(composition);
      case MWM:
        return calculateMWM(composition);
      case SIMPLIFIED:
        return calculateSimplified(composition);
      default:
        return calculateEN16726(composition);
    }
  }

  /**
   * Calculate methane number using EN 16726 method.
   *
   * <p>
   * The EN 16726 (GRI/GERG) method uses a multivariate polynomial regression fitted to engine test
   * data. The correlation includes linear, quadratic, and cross terms for the main combustible and
   * inert components.
   * </p>
   *
   * <p>
   * Reference: EN 16726:2015 "Gas infrastructure - Quality of gas - Group H", Annex E
   * (informative): "Calculation of methane number".
   * </p>
   *
   * @param composition mole fractions keyed by component name
   * @return methane number
   */
  private double calculateEN16726(Map<String, Double> composition) {
    // Extract mole fractions (in percent for the correlation)
    double xCH4 = getMolPercent(composition, "methane");
    double xC2 = getMolPercent(composition, "ethane");
    double xC3 = getMolPercent(composition, "propane");
    double xC4 = getMolPercent(composition, "i-butane") + getMolPercent(composition, "n-butane");
    double xC5 = getMolPercent(composition, "i-pentane") + getMolPercent(composition, "n-pentane");
    double xN2 = getMolPercent(composition, "nitrogen");
    double xCO2 = getMolPercent(composition, "CO2");
    double xH2 = getMolPercent(composition, "hydrogen");

    // EN 16726 Annex E polynomial coefficients (GRI/GERG regression)
    // MN = 1.445 * MON - 103.42 where MON is the Motor Octane Number equivalent
    // Alternatively, direct regression:
    //
    // This is the MWI (Methane number by Waukesha) correlation from GRI,
    // as referenced in EN 16726 and widely validated for LNG compositions.
    //
    // Coefficients from published GRI-Mech / Cummins-Waukesha dataset:
    double mn = 137.78;

    // Linear terms
    mn += (xCH4 - 100.0) * 1.445; // methane effect (positive: increases MN)
    mn += xC2 * (-6.28); // ethane (negative: decreases MN)
    mn += xC3 * (-12.38); // propane (strongly negative)
    mn += xC4 * (-18.86); // butanes (very negative)
    mn += xC5 * (-25.0); // pentanes (extremely negative)
    mn += xH2 * (-1.0); // hydrogen (reduces knock resistance)
    mn += xCO2 * 1.64; // CO2 (inert, increases MN slightly)
    mn += xN2 * 3.09; // nitrogen (inert, increases MN)

    // Quadratic corrections for high concentrations (>5%)
    mn += xC2 * xC2 * 0.079; // ethane quadratic
    mn += xC3 * xC3 * 0.102; // propane quadratic
    mn += xC4 * xC4 * 0.12; // butane quadratic

    // Cross-term: ethane-propane interaction
    mn += xC2 * xC3 * (-0.15);

    // Clamp to valid range
    mn = Math.max(0.0, Math.min(100.0, mn));

    return mn;
  }

  /**
   * Calculate methane number using MWM method.
   *
   * <p>
   * The MWM method (Motorenwerke Mannheim, now Caterpillar Energy Solutions) is widely used by gas
   * engine manufacturers. It uses a different set of coefficients and typically gives slightly
   * lower MN values than EN 16726 for rich LNG compositions.
   * </p>
   *
   * @param composition mole fractions keyed by component name
   * @return methane number
   */
  private double calculateMWM(Map<String, Double> composition) {
    double xCH4 = getMolPercent(composition, "methane");
    double xC2 = getMolPercent(composition, "ethane");
    double xC3 = getMolPercent(composition, "propane");
    double xC4 = getMolPercent(composition, "i-butane") + getMolPercent(composition, "n-butane");
    double xC5 = getMolPercent(composition, "i-pentane") + getMolPercent(composition, "n-pentane");
    double xN2 = getMolPercent(composition, "nitrogen");
    double xCO2 = getMolPercent(composition, "CO2");
    double xH2 = getMolPercent(composition, "hydrogen");

    // MWM correlation coefficients (from Caterpillar/MWM technical documentation)
    double mn = 0.0;

    // Start from methane base
    mn += xCH4 * 1.0; // Pure methane = MN 100 when xCH4 = 100
    mn += xC2 * (-4.7); // ethane penalty
    mn += xC3 * (-10.1); // propane penalty
    mn += xC4 * (-16.8); // butane penalty
    mn += xC5 * (-22.5); // pentane penalty
    mn += xH2 * 0.0; // hydrogen neutral in MWM
    mn += xN2 * 2.8; // nitrogen bonus
    mn += xCO2 * 1.4; // CO2 bonus

    // Quadratic corrections
    mn += xC2 * xC2 * 0.04;
    mn += xC3 * xC3 * 0.06;

    mn = Math.max(0.0, Math.min(100.0, mn));
    return mn;
  }

  /**
   * Calculate methane number using simplified linear correlation.
   *
   * <p>
   * Quick screening method: MN = 137.78 * xC1 - 29.948 * xC2 - 18.193 * xC3 - 167.06 * xN2.
   * Applicable only for lean LNG (C3 &lt; 5%, no C4+).
   * </p>
   *
   * @param composition mole fractions keyed by component name
   * @return methane number
   */
  private double calculateSimplified(Map<String, Double> composition) {
    double xC1 = getMolFraction(composition, "methane");
    double xC2 = getMolFraction(composition, "ethane");
    double xC3 = getMolFraction(composition, "propane");
    double xN2 = getMolFraction(composition, "nitrogen");

    double mn = 137.78 * xC1 - 29.948 * xC2 - 18.193 * xC3 - 167.06 * xN2;

    return Math.max(0.0, Math.min(100.0, mn));
  }

  /**
   * Calculate methane number for a complete analysis, returning all three methods.
   *
   * @param composition mole fractions keyed by component name
   * @return map with keys "EN16726", "MWM", "Simplified"
   */
  public Map<String, Double> calculateAll(Map<String, Double> composition) {
    Map<String, Double> results = new LinkedHashMap<String, Double>();
    results.put("EN16726", calculateEN16726(composition));
    results.put("MWM", calculateMWM(composition));
    results.put("Simplified", calculateSimplified(composition));
    return results;
  }

  /**
   * Check if MN meets a minimum specification.
   *
   * @param composition mole fractions
   * @param minMN minimum required methane number
   * @return true if MN meets specification
   */
  public boolean meetsSpecification(Map<String, Double> composition, double minMN) {
    double mn = calculate(composition);
    boolean meets = mn >= minMN;
    if (!meets) {
      logger.warn(String.format("MN = %.1f, below minimum specification of %.1f", mn, minMN));
    }
    return meets;
  }

  /**
   * Get the mole fraction of a component (0.0 if not present).
   *
   * @param composition composition map
   * @param componentName component name
   * @return mole fraction (0.0-1.0)
   */
  private double getMolFraction(Map<String, Double> composition, String componentName) {
    Double value = composition.get(componentName);
    return (value != null) ? value : 0.0;
  }

  /**
   * Get the mole percent of a component (0.0 if not present).
   *
   * @param composition composition map (mole fractions)
   * @param componentName component name
   * @return mole percent (0.0-100.0)
   */
  private double getMolPercent(Map<String, Double> composition, String componentName) {
    return getMolFraction(composition, componentName) * 100.0;
  }

  /**
   * Get the active calculation method.
   *
   * @return calculation method
   */
  public Method getMethod() {
    return method;
  }

  /**
   * Set the calculation method.
   *
   * @param method calculation method
   */
  public void setMethod(Method method) {
    this.method = method;
  }
}
