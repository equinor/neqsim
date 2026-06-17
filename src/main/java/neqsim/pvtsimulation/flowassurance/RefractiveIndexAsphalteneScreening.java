package neqsim.pvtsimulation.flowassurance;

import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Refractive index-based approach for asphaltene stability screening.
 *
 * <p>
 * This method uses the difference between the refractive index (RI) of the crude oil and the RI at
 * the asphaltene flocculation point to assess stability. It is based on the observation that
 * asphaltenes precipitate when the oil's RI drops below a critical value during titration with a
 * poor solvent (typically n-heptane).
 * </p>
 *
 * <h2>Theoretical Background</h2>
 * <p>
 * The RI of a liquid mixture is related to its density and polarizability through the
 * Lorentz-Lorenz equation:
 * </p>
 *
 * <pre>
 * R_LL = (n^2 - 1) / (n^2 + 2) = (4/3) * pi * N_A * alpha / V_m
 * </pre>
 *
 * <p>
 * The Colloidal Instability Index based on RI (CII_RI) is defined as:
 * </p>
 *
 * <pre>
 * CII_RI = RI_oil - RI_onset
 * </pre>
 *
 * <p>
 * Where RI_onset is the RI at the flocculation point. A larger CII_RI indicates better stability.
 * </p>
 *
 * <h2>Key Parameters</h2>
 * <ul>
 * <li>RI_oil: Refractive index of live oil at conditions (measured or correlated from density)</li>
 * <li>RI_onset: RI at asphaltene precipitation onset (from n-C7 titration)</li>
 * <li>PRI: RI at which asphaltenes become insoluble (intrinsic property)</li>
 * </ul>
 *
 * <p>
 * References:
 * </p>
 * <ul>
 * <li>Buckley, J.S. et al. (1998). "Asphaltene Precipitation and Solvent Properties of Crude Oils."
 * Petroleum Science and Technology, 16(3-4), 251-285.</li>
 * <li>Fan, T., Wang, J., Buckley, J.S. (2002). "Evaluating Crude Oils by SARA Analysis." SPE
 * 75228.</li>
 * <li>Wattana, P. et al. (2003). "Characterization of Polarity-Based Asphaltene Subfractions."
 * Energy and Fuels, 17, 1532-1539.</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 */
public class RefractiveIndexAsphalteneScreening {

  /** Logger object for class. */
  private static final Logger logger =
      LogManager.getLogger(RefractiveIndexAsphalteneScreening.class);

  /** Refractive index of the crude oil at measurement conditions. */
  private double riOil = Double.NaN;

  /** Refractive index at the onset of asphaltene precipitation (from titration). */
  private double riOnset = Double.NaN;

  /** Asphaltene peptizability index (PRI = RI at which asphaltene becomes insoluble). */
  private double priAsphaltene = Double.NaN;

  /** Oil density at measurement conditions (kg/m3). */
  private double oilDensity = Double.NaN;

  /** API gravity of the crude oil. */
  private double apiGravity = Double.NaN;

  /** Weight fraction of asphaltene. */
  private double asphalteneContent = 0.0;

  /** Volume fraction of heptane at onset (from titration test). */
  private double heptaneFractionAtOnset = Double.NaN;

  /**
   * Stability classification based on RI difference.
   */
  public enum RIStability {
    /** Very stable asphaltenes. */
    VERY_STABLE("Very Stable - Large RI margin, precipitation very unlikely"),

    /** Stable asphaltenes. */
    STABLE("Stable - Adequate RI margin for normal operations"),

    /** Marginally stable. */
    MARGINAL("Marginal - Small RI margin, monitor during pressure changes"),

    /** Unstable asphaltenes. */
    UNSTABLE("Unstable - RI near or below onset, precipitation likely"),

    /** Highly unstable. */
    HIGHLY_UNSTABLE("Highly Unstable - RI well below onset, severe precipitation expected");

    private final String description;

    RIStability(String description) {
      this.description = description;
    }

    /**
     * Gets the description of the stability level.
     *
     * @return stability description
     */
    public String getDescription() {
      return description;
    }
  }

  /**
   * Default constructor.
   */
  public RefractiveIndexAsphalteneScreening() {}

  /**
   * Constructor with measured RI values.
   *
   * @param riOil refractive index of the crude oil
   * @param riOnset refractive index at onset (from titration)
   */
  public RefractiveIndexAsphalteneScreening(double riOil, double riOnset) {
    this.riOil = riOil;
    this.riOnset = riOnset;
  }

  /**
   * Estimates the refractive index from oil density using the Lorentz-Lorenz derived correlation.
   *
   * <p>
   * The correlation used is: n = 1.0 + 0.5 * FRI, where FRI (function of RI) = 0.0003889 * rho -
   * 0.02755 for rho in kg/m3.
   * </p>
   *
   * <p>
   * Alternative simplified correlation (Buckley, 1998): n = 1.0 + 0.2867 * (rho/1000) ^0.5 + 0.1439
   * * (rho/1000)
   * </p>
   *
   * @param density oil density at conditions (kg/m3)
   * @return estimated refractive index
   */
  public double estimateRIFromDensity(double density) {
    // Lorentz-Lorenz based correlation (Buckley et al., 1998)
    double sg = density / 1000.0;
    double fri = 0.2898 * sg + 0.07602 * sg * sg;
    double nSquared = (1.0 + 2.0 * fri) / (1.0 - fri);
    if (nSquared < 1.0) {
      nSquared = 1.0;
    }
    return Math.sqrt(nSquared);
  }

  /**
   * Calculates the RI stability margin (difference between oil RI and onset RI).
   *
   * @return RI difference (positive = stable, negative = unstable)
   */
  public double getRIStabilityMargin() {
    if (Double.isNaN(riOil) || Double.isNaN(riOnset)) {
      logger.warn("RI values not set - cannot calculate margin");
      return Double.NaN;
    }
    return riOil - riOnset;
  }

  /**
   * Evaluates asphaltene stability based on refractive index values.
   *
   * <p>
   * Classification based on delta_RI = RI_oil - RI_onset (Buckley et al., 1998):
   * </p>
   * <ul>
   * <li>delta_RI &gt; 0.03: Very Stable</li>
   * <li>0.02 &lt; delta_RI &lt;= 0.03: Stable</li>
   * <li>0.01 &lt; delta_RI &lt;= 0.02: Marginal</li>
   * <li>0 &lt; delta_RI &lt;= 0.01: Unstable</li>
   * <li>delta_RI &lt;= 0: Highly Unstable</li>
   * </ul>
   *
   * @return stability classification
   */
  public RIStability evaluateStability() {
    double margin = getRIStabilityMargin();

    if (Double.isNaN(margin)) {
      logger.warn("Cannot evaluate stability - insufficient data");
      return RIStability.MARGINAL; // Conservative default
    }

    if (margin > 0.03) {
      return RIStability.VERY_STABLE;
    } else if (margin > 0.02) {
      return RIStability.STABLE;
    } else if (margin > 0.01) {
      return RIStability.MARGINAL;
    } else if (margin > 0) {
      return RIStability.UNSTABLE;
    } else {
      return RIStability.HIGHLY_UNSTABLE;
    }
  }

  /**
   * Calculates the critical dilution ratio (CDR) for n-heptane titration.
   *
   * <p>
   * CDR = volume of n-C7 added / volume of crude oil at onset. Higher CDR = more stable.
   * </p>
   *
   * @return critical dilution ratio
   */
  public double getCriticalDilutionRatio() {
    if (Double.isNaN(heptaneFractionAtOnset) || heptaneFractionAtOnset <= 0
        || heptaneFractionAtOnset >= 1.0) {
      return Double.NaN;
    }
    return heptaneFractionAtOnset / (1.0 - heptaneFractionAtOnset);
  }

  /**
   * Estimates onset RI from SARA fractions using Buckley correlation.
   *
   * <p>
   * Correlation from Buckley et al. (1998): RI_onset correlates with asphaltene solubility class
   * and R/A ratio.
   * </p>
   *
   * @param saturates weight fraction of saturates
   * @param aromatics weight fraction of aromatics
   * @param resins weight fraction of resins
   * @param asphaltenes weight fraction of asphaltenes
   * @return estimated onset RI
   */
  public double estimateOnsetRIFromSARA(double saturates, double aromatics, double resins,
      double asphaltenes) {
    // Improved correlation based on Buckley et al. (2007), Fan et al. (2002),
    // and Wattana et al. (2003). PRI is an intrinsic property of the asphaltene
    // that depends on asphaltene polarity and solvent environment.
    //
    // Key observations from literature:
    // - PRI typically ranges from 1.42-1.55 for most crude oils
    // - Higher asphaltene content correlates with higher PRI (more polar asphaltenes)
    // - Higher saturate/aromatic ratio (less aromatic solvent) raises onset RI
    // - Resin/asphaltene ratio reflects peptization effectiveness

    double satFrac = saturates + 1e-10;
    double aroFrac = aromatics + 1e-10;
    double resFrac = resins + 1e-10;
    double asphFrac = asphaltenes + 1e-10;

    // Saturate-to-aromatic ratio (solvent quality indicator)
    double saRatio = satFrac / (satFrac + aroFrac);

    // Resin-to-asphaltene ratio (peptization indicator)
    double raRatio = resFrac / asphFrac;

    // Base PRI from Buckley (2007) typical value for medium crudes
    double baseOnset = 1.442;

    // Asphaltene content effect: higher asphaltene content slightly increases PRI
    // Based on Fan et al. (2002). The effect is secondary to solvent quality.
    double asphEffect = 0.10 * asphFrac;

    // Saturate/aromatic ratio effect: more paraffinic solvent environment
    // raises onset RI significantly. Buckley (2007) showed oils with higher
    // saturate content are less effective solvents. This is the dominant factor.
    double saEffect = 0.12 * (saRatio - 0.45);

    // Resin/asphaltene ratio effect: higher R/A means better peptization,
    // which lowers onset RI. Capped effect per Hammami et al. (2000).
    double raEffect = -0.008 * Math.min(Math.max(raRatio - 1.5, 0.0), 6.0);

    riOnset = baseOnset + asphEffect + saEffect + raEffect;

    // Clamp to physically reasonable range for crude oils
    riOnset = Math.max(1.42, Math.min(1.55, riOnset));
    return riOnset;
  }

  /**
   * Generates a screening report.
   *
   * @return formatted report string
   */
  public String generateReport() {
    StringBuilder report = new StringBuilder();
    report.append("=== REFRACTIVE INDEX ASPHALTENE SCREENING ===\n\n");

    if (!Double.isNaN(riOil)) {
      report.append(String.format("Oil RI:           %.4f%n", riOil));
    }
    if (!Double.isNaN(riOnset)) {
      report.append(String.format("Onset RI:         %.4f%n", riOnset));
    }
    if (!Double.isNaN(riOil) && !Double.isNaN(riOnset)) {
      double margin = getRIStabilityMargin();
      report.append(String.format("RI Margin:        %.4f%n", margin));
      RIStability stability = evaluateStability();
      report.append(String.format("Stability:        %s%n", stability.name()));
      report.append(String.format("Assessment:       %s%n", stability.getDescription()));
    }
    if (!Double.isNaN(heptaneFractionAtOnset)) {
      report.append(String.format("CDR (n-C7):       %.2f%n", getCriticalDilutionRatio()));
    }
    if (!Double.isNaN(oilDensity)) {
      report.append(String.format("Oil Density:      %.1f kg/m3%n", oilDensity));
      double estimatedRI = estimateRIFromDensity(oilDensity);
      report.append(String.format("Estimated RI:     %.4f (from density)%n", estimatedRI));
    }

    return report.toString();
  }

  /**
   * Gets all results as a map for JSON serialization.
   *
   * @return map of screening results
   */
  public Map<String, Object> getResultsMap() {
    Map<String, Object> results = new LinkedHashMap<String, Object>();
    results.put("model", "Refractive Index Screening");
    results.put("ri_oil", riOil);
    results.put("ri_onset", riOnset);

    if (!Double.isNaN(riOil) && !Double.isNaN(riOnset)) {
      results.put("ri_margin", getRIStabilityMargin());
      results.put("stability", evaluateStability().name());
    }
    if (!Double.isNaN(heptaneFractionAtOnset)) {
      results.put("critical_dilution_ratio", getCriticalDilutionRatio());
    }

    return results;
  }

  // ───────────────── Getters and Setters ─────────────────

  /**
   * Gets the refractive index of the crude oil.
   *
   * @return refractive index
   */
  public double getRiOil() {
    return riOil;
  }

  /**
   * Sets the refractive index of the crude oil.
   *
   * @param riOil refractive index
   */
  public void setRiOil(double riOil) {
    this.riOil = riOil;
  }

  /**
   * Gets the refractive index at onset.
   *
   * @return onset RI
   */
  public double getRiOnset() {
    return riOnset;
  }

  /**
   * Sets the refractive index at onset.
   *
   * @param riOnset onset RI
   */
  public void setRiOnset(double riOnset) {
    this.riOnset = riOnset;
  }

  /**
   * Gets the oil density.
   *
   * @return density (kg/m3)
   */
  public double getOilDensity() {
    return oilDensity;
  }

  /**
   * Sets the oil density. Also calculates estimated RI if not set.
   *
   * @param oilDensity density (kg/m3)
   */
  public void setOilDensity(double oilDensity) {
    this.oilDensity = oilDensity;
    if (Double.isNaN(riOil)) {
      riOil = estimateRIFromDensity(oilDensity);
    }
  }

  /**
   * Gets the API gravity.
   *
   * @return API gravity
   */
  public double getApiGravity() {
    return apiGravity;
  }

  /**
   * Sets the API gravity. Converts to density if density not set.
   *
   * @param apiGravity API gravity
   */
  public void setApiGravity(double apiGravity) {
    this.apiGravity = apiGravity;
    if (Double.isNaN(oilDensity)) {
      double sg = 141.5 / (apiGravity + 131.5);
      this.oilDensity = sg * 1000.0;
    }
  }

  /**
   * Gets the asphaltene content.
   *
   * @return weight fraction
   */
  public double getAsphalteneContent() {
    return asphalteneContent;
  }

  /**
   * Sets the asphaltene content.
   *
   * @param asphalteneContent weight fraction (0-1)
   */
  public void setAsphalteneContent(double asphalteneContent) {
    this.asphalteneContent = asphalteneContent;
  }

  /**
   * Gets the heptane volume fraction at onset.
   *
   * @return volume fraction (0-1)
   */
  public double getHeptaneFractionAtOnset() {
    return heptaneFractionAtOnset;
  }

  /**
   * Sets the heptane volume fraction at onset from titration test.
   *
   * @param heptaneFractionAtOnset volume fraction (0-1)
   */
  public void setHeptaneFractionAtOnset(double heptaneFractionAtOnset) {
    this.heptaneFractionAtOnset = heptaneFractionAtOnset;
  }

  /**
   * Gets the PRI value (asphaltene peptizability refractive index).
   *
   * @return PRI value
   */
  public double getPriAsphaltene() {
    return priAsphaltene;
  }

  /**
   * Sets the PRI value.
   *
   * @param priAsphaltene PRI value
   */
  public void setPriAsphaltene(double priAsphaltene) {
    this.priAsphaltene = priAsphaltene;
  }
}
