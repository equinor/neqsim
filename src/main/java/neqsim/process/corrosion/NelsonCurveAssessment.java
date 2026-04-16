package neqsim.process.corrosion;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Nelson curve assessment for high-temperature hydrogen attack (HTHA) per API 941.
 *
 * <p>
 * The Nelson curves define the operating limits (temperature vs hydrogen partial pressure) below
 * which various steels can safely operate without suffering high-temperature hydrogen attack. HTHA
 * occurs when dissolved hydrogen reacts with carbon in the steel to form methane (CH4) at grain
 * boundaries, causing irreversible internal decarburisation, fissuring, and loss of mechanical
 * properties.
 * </p>
 *
 * <h2>Mechanism</h2>
 *
 * <p>
 * At elevated temperatures (&gt;200°C), molecular hydrogen dissociates on the steel surface and
 * diffuses into the metal as atomic hydrogen. It then reacts with iron carbide (Fe3C):
 * </p>
 *
 * <p>
 * Fe3C + 4H → 3Fe + CH4
 * </p>
 *
 * <p>
 * The methane molecules are too large to diffuse out and accumulate at grain boundaries, creating
 * internal pressure that leads to fissuring and blistering. This process is irreversible —
 * once HTHA occurs, the steel must be replaced.
 * </p>
 *
 * <h2>Material Categories</h2>
 *
 * <p>
 * Higher alloy content (Cr, Mo, V) stabilises carbides against hydrogen attack, shifting
 * the Nelson curve to higher temperatures and pressures:
 * </p>
 *
 * <table>
 * <caption>Material categories for Nelson curve assessment</caption>
 * <tr><th>Material</th><th>Typical Max Temp at 100 psia H2</th><th>Resistance</th></tr>
 * <tr><td>Carbon steel</td><td>~220°C</td><td>Lowest</td></tr>
 * <tr><td>C-0.5Mo</td><td>~300°C</td><td>Low-moderate</td></tr>
 * <tr><td>1Cr-0.5Mo</td><td>~400°C</td><td>Moderate</td></tr>
 * <tr><td>1.25Cr-0.5Mo</td><td>~450°C</td><td>Good</td></tr>
 * <tr><td>2.25Cr-1Mo</td><td>~500°C</td><td>Very good</td></tr>
 * <tr><td>Austenitic SS</td><td>&gt;550°C</td><td>Excellent</td></tr>
 * </table>
 *
 * <h2>Standards</h2>
 *
 * <ul>
 * <li>API 941, 8th Edition (2016) — Steels for Hydrogen Service at Elevated Temperatures
 * and Pressures in Petroleum Refineries and Petrochemical Plants</li>
 * <li>API RP 941 Annex A — Operating limit curves</li>
 * </ul>
 *
 * <h2>Usage</h2>
 *
 * <pre>{@code
 * NelsonCurveAssessment nelson = new NelsonCurveAssessment();
 * nelson.setTemperatureC(350.0);
 * nelson.setH2PartialPressureBar(20.0);
 * nelson.setMaterialType("carbon_steel");
 * nelson.evaluate();
 *
 * boolean safe = nelson.isBelowNelsonCurve();
 * double maxTemp = nelson.getMaxAllowableTemperatureC();
 * double maxPressure = nelson.getMaxAllowableH2PressureBar();
 * }</pre>
 *
 * @author ESOL
 * @version 1.0
 * @see HydrogenMaterialAssessment
 */
public class NelsonCurveAssessment implements Serializable {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1004L;

  // ─── Nelson Curve Data (API 941, 8th Edition) ───────────
  // Format: {H2 partial pressure [psia], maximum temperature [°F]}
  // Points define the safe operating boundary for each material

  /**
   * Carbon steel Nelson curve data points.
   *
   * <p>
   * Reference: API 941 Figure 1. Applicable to carbon steel (SA-106, SA-516-70, API 5L grades).
   * Note: C-0.5Mo steel was historically shown as a separate curve above carbon steel but has been
   * reclassified due to field failures. Use the carbon steel curve for C-0.5Mo unless extensive
   * qualification testing is performed.
   * </p>
   */
  private static final double[][] CARBON_STEEL_CURVE = {
      {0, 500}, {50, 480}, {100, 450}, {200, 420}, {500, 400},
      {1000, 380}, {1500, 370}, {2000, 360}, {3000, 350}, {5000, 340},
      {10000, 330}
  };

  /**
   * C-0.5Mo steel Nelson curve data points.
   *
   * <p>
   * WARNING: API 941 8th edition notes significant field failures with C-0.5Mo. Use with caution —
   * many operators now use the carbon steel curve for C-0.5Mo equipment.
   * </p>
   */
  private static final double[][] C_0_5MO_CURVE = {
      {0, 600}, {50, 580}, {100, 550}, {200, 530}, {500, 510},
      {1000, 500}, {1500, 490}, {2000, 480}, {3000, 470}, {5000, 460},
      {10000, 450}
  };

  /**
   * 1Cr-0.5Mo steel Nelson curve data points.
   *
   * <p>
   * Reference: API 941 Figure 1. Applicable to F12, P12, and equivalent grades.
   * </p>
   */
  private static final double[][] CR1_0_5MO_CURVE = {
      {0, 800}, {50, 780}, {100, 750}, {200, 720}, {500, 700},
      {1000, 680}, {1500, 670}, {2000, 660}, {3000, 650}, {5000, 640},
      {10000, 630}
  };

  /**
   * 1.25Cr-0.5Mo steel Nelson curve data points.
   *
   * <p>
   * Reference: API 941 Figure 1. Applicable to F11, P11, and equivalent grades.
   * </p>
   */
  private static final double[][] CR1_25_0_5MO_CURVE = {
      {0, 850}, {50, 830}, {100, 800}, {200, 780}, {500, 760},
      {1000, 740}, {1500, 730}, {2000, 725}, {3000, 715}, {5000, 700},
      {10000, 690}
  };

  /**
   * 2.25Cr-1Mo steel Nelson curve data points.
   *
   * <p>
   * Reference: API 941 Figure 1. Applicable to F22, P22, and equivalent grades. Most common
   * upgrade material for hydrogen service.
   * </p>
   */
  private static final double[][] CR2_25_1MO_CURVE = {
      {0, 1000}, {50, 980}, {100, 950}, {200, 920}, {500, 900},
      {1000, 880}, {1500, 870}, {2000, 860}, {3000, 850}, {5000, 840},
      {10000, 830}
  };

  /**
   * Austenitic stainless steel Nelson curve data points.
   *
   * <p>
   * Austenitic stainless steels (304, 316, 321, 347) have significantly higher HTHA resistance due
   * to low carbon activity and stable austenitic structure. The curve boundary is approximate —
   * typically not the controlling limit in practice.
   * </p>
   */
  private static final double[][] AUSTENITIC_SS_CURVE = {
      {0, 1200}, {50, 1180}, {100, 1150}, {200, 1120}, {500, 1100},
      {1000, 1080}, {1500, 1070}, {2000, 1060}, {3000, 1050}, {5000, 1040},
      {10000, 1030}
  };

  // ─── Supported material types ───────────────────────────

  /** Supported material type identifiers. */
  private static final List<String> SUPPORTED_MATERIALS = Arrays.asList(
      "carbon_steel", "c_0_5mo", "1cr_0_5mo", "1_25cr_0_5mo",
      "2_25cr_1mo", "austenitic_ss"
  );

  // ─── Input parameters ───────────────────────────────────

  /** Operating temperature [°C]. */
  private double temperatureC = 25.0;

  /** Hydrogen partial pressure [psia]. */
  private double h2PartialPressurePsia = 0.0;

  /** Material type identifier. */
  private String materialType = "carbon_steel";

  // ─── Results ────────────────────────────────────────────

  /** Whether the operating point is below (safe side of) the Nelson curve. */
  private boolean belowNelsonCurve = true;

  /** Maximum allowable temperature at the given H2 partial pressure [°C]. */
  private double maxAllowableTempC = 500.0;

  /** Maximum allowable H2 partial pressure at the given temperature [bar]. */
  private double maxAllowableH2PressureBar = 0.0;

  /** Temperature margin above the Nelson curve [°C]. Negative = safe. */
  private double temperatureMarginC = 0.0;

  /** Risk level. */
  private String riskLevel = "Low";

  /** Recommended material upgrade if current material is insufficient. */
  private String recommendedUpgrade = "";

  /** Whether evaluation has been performed. */
  private boolean evaluated = false;

  /**
   * Creates a new NelsonCurveAssessment with default parameters.
   */
  public NelsonCurveAssessment() {}

  // ─── Setters ────────────────────────────────────────────

  /**
   * Sets the operating temperature.
   *
   * @param tempC temperature in degrees Celsius
   */
  public void setTemperatureC(double tempC) {
    this.temperatureC = tempC;
    evaluated = false;
  }

  /**
   * Sets the hydrogen partial pressure in psia.
   *
   * @param psia H2 partial pressure in psia
   */
  public void setH2PartialPressurePsia(double psia) {
    this.h2PartialPressurePsia = Math.max(0.0, psia);
    evaluated = false;
  }

  /**
   * Sets the hydrogen partial pressure in bar and converts internally to psia.
   *
   * @param bar H2 partial pressure in bar
   */
  public void setH2PartialPressureBar(double bar) {
    this.h2PartialPressurePsia = bar * 14.5038;
    evaluated = false;
  }

  /**
   * Sets the material type for Nelson curve lookup.
   *
   * <p>
   * Valid types: "carbon_steel", "c_0_5mo", "1cr_0_5mo", "1_25cr_0_5mo", "2_25cr_1mo",
   * "austenitic_ss".
   * </p>
   *
   * @param type material type identifier
   */
  public void setMaterialType(String type) {
    String lower = type.toLowerCase().trim();
    if (SUPPORTED_MATERIALS.contains(lower)) {
      this.materialType = lower;
    } else {
      this.materialType = "carbon_steel";
    }
    evaluated = false;
  }

  // ─── Evaluation ─────────────────────────────────────────

  /**
   * Runs the Nelson curve assessment.
   *
   * <p>
   * Interpolates the applicable Nelson curve to determine whether the operating point is safe and
   * calculates the temperature margin and maximum allowable conditions.
   * </p>
   */
  public void evaluate() {
    double[][] curve = getCurveData();
    double tempF = temperatureC * 9.0 / 5.0 + 32.0;

    // Interpolate maximum temperature at given pressure
    double maxTempF = interpolateMaxTemp(curve, h2PartialPressurePsia);
    maxAllowableTempC = (maxTempF - 32.0) * 5.0 / 9.0;

    // Interpolate maximum pressure at given temperature
    double maxPsiA = interpolateMaxPressure(curve, tempF);
    maxAllowableH2PressureBar = maxPsiA / 14.5038;

    // Temperature margin (negative = safe)
    temperatureMarginC = temperatureC - maxAllowableTempC;
    belowNelsonCurve = temperatureMarginC <= 0.0;

    // Risk classification
    if (temperatureC < 200.0) {
      riskLevel = "Low";
      belowNelsonCurve = true;
    } else if (temperatureMarginC < -50.0) {
      riskLevel = "Low";
    } else if (temperatureMarginC < -20.0) {
      riskLevel = "Medium";
    } else if (temperatureMarginC < 0.0) {
      riskLevel = "High";
    } else {
      riskLevel = "Very High";
    }

    // Find recommended upgrade if not acceptable
    recommendedUpgrade = "";
    if (!belowNelsonCurve) {
      recommendedUpgrade = findMinimumUpgrade(tempF, h2PartialPressurePsia);
    }

    evaluated = true;
  }

  /**
   * Returns the Nelson curve data for the configured material.
   *
   * @return 2D array of {pressure_psia, max_temp_F} points
   */
  private double[][] getCurveData() {
    if ("c_0_5mo".equals(materialType)) {
      return C_0_5MO_CURVE;
    }
    if ("1cr_0_5mo".equals(materialType)) {
      return CR1_0_5MO_CURVE;
    }
    if ("1_25cr_0_5mo".equals(materialType)) {
      return CR1_25_0_5MO_CURVE;
    }
    if ("2_25cr_1mo".equals(materialType)) {
      return CR2_25_1MO_CURVE;
    }
    if ("austenitic_ss".equals(materialType)) {
      return AUSTENITIC_SS_CURVE;
    }
    return CARBON_STEEL_CURVE;
  }

  /**
   * Interpolates the maximum allowable temperature at a given H2 partial pressure.
   *
   * @param curve Nelson curve data
   * @param pressurePsia H2 partial pressure [psia]
   * @return maximum allowable temperature [°F]
   */
  private double interpolateMaxTemp(double[][] curve, double pressurePsia) {
    if (pressurePsia <= curve[0][0]) {
      return curve[0][1];
    }
    if (pressurePsia >= curve[curve.length - 1][0]) {
      return curve[curve.length - 1][1];
    }

    for (int i = 0; i < curve.length - 1; i++) {
      if (pressurePsia >= curve[i][0] && pressurePsia <= curve[i + 1][0]) {
        double fraction;
        if (curve[i + 1][0] - curve[i][0] < 1e-6) {
          fraction = 0.0;
        } else {
          // Log interpolation for pressure (typical for Nelson curves)
          double logP = Math.log(pressurePsia + 1.0);
          double logP1 = Math.log(curve[i][0] + 1.0);
          double logP2 = Math.log(curve[i + 1][0] + 1.0);
          fraction = (logP - logP1) / (logP2 - logP1);
        }
        return curve[i][1] + fraction * (curve[i + 1][1] - curve[i][1]);
      }
    }
    return curve[curve.length - 1][1];
  }

  /**
   * Interpolates the maximum allowable H2 partial pressure at a given temperature.
   *
   * @param curve Nelson curve data
   * @param tempF temperature [°F]
   * @return maximum allowable H2 partial pressure [psia]
   */
  private double interpolateMaxPressure(double[][] curve, double tempF) {
    // If temperature is above the highest curve limit, no H2 is safe
    if (tempF >= curve[0][1]) {
      return 0.0;
    }
    // If temperature is below the lowest limit on the curve, very high pressure is safe
    if (tempF <= curve[curve.length - 1][1]) {
      return curve[curve.length - 1][0];
    }

    // Nelson curve: pressure increases as max temp decreases
    for (int i = 0; i < curve.length - 1; i++) {
      // Curve has decreasing temperature with increasing pressure
      if (tempF <= curve[i][1] && tempF >= curve[i + 1][1]) {
        double fraction;
        if (Math.abs(curve[i][1] - curve[i + 1][1]) < 1e-6) {
          fraction = 0.0;
        } else {
          fraction = (curve[i][1] - tempF) / (curve[i][1] - curve[i + 1][1]);
        }
        return curve[i][0] + fraction * (curve[i + 1][0] - curve[i][0]);
      }
    }
    return 0.0;
  }

  /**
   * Finds the minimum material upgrade that would be acceptable at these conditions.
   *
   * @param tempF temperature [°F]
   * @param pressurePsia H2 partial pressure [psia]
   * @return recommended material name, or empty if none found
   */
  private String findMinimumUpgrade(double tempF, double pressurePsia) {
    double[][][] curves = {C_0_5MO_CURVE, CR1_0_5MO_CURVE, CR1_25_0_5MO_CURVE,
        CR2_25_1MO_CURVE, AUSTENITIC_SS_CURVE};
    String[] names = {"C-0.5Mo (use with caution per API 941)",
        "1Cr-0.5Mo", "1.25Cr-0.5Mo", "2.25Cr-1Mo",
        "Austenitic stainless steel (304/316)"};

    for (int i = 0; i < curves.length; i++) {
      double maxT = interpolateMaxTemp(curves[i], pressurePsia);
      if (tempF <= maxT) {
        return names[i];
      }
    }
    return "No standard material sufficient — consider special alloys or reduce conditions";
  }

  // ─── Getters ────────────────────────────────────────────

  /**
   * Checks whether the operating point is below the Nelson curve (safe).
   *
   * @return true if below the Nelson curve
   */
  public boolean isBelowNelsonCurve() {
    return belowNelsonCurve;
  }

  /**
   * Gets the maximum allowable temperature at the given H2 partial pressure.
   *
   * @return maximum allowable temperature [°C]
   */
  public double getMaxAllowableTemperatureC() {
    return maxAllowableTempC;
  }

  /**
   * Gets the maximum allowable H2 partial pressure at the given temperature.
   *
   * @return maximum allowable H2 partial pressure [bar]
   */
  public double getMaxAllowableH2PressureBar() {
    return maxAllowableH2PressureBar;
  }

  /**
   * Gets the temperature margin relative to the Nelson curve.
   *
   * <p>
   * Negative values mean the operating point is below (safe side of) the curve. Positive values
   * mean the operating point exceeds the curve — material is at risk of HTHA.
   * </p>
   *
   * @return temperature margin [°C]
   */
  public double getTemperatureMarginC() {
    return temperatureMarginC;
  }

  /**
   * Gets the risk level.
   *
   * @return risk level: "Low", "Medium", "High", or "Very High"
   */
  public String getRiskLevel() {
    return riskLevel;
  }

  /**
   * Gets the recommended material upgrade.
   *
   * @return recommended upgrade material, or empty if current material is acceptable
   */
  public String getRecommendedUpgrade() {
    return recommendedUpgrade;
  }

  /**
   * Gets the list of supported material types.
   *
   * @return list of material type identifiers
   */
  public static List<String> getSupportedMaterialTypes() {
    return SUPPORTED_MATERIALS;
  }

  /**
   * Returns the assessment results as a Map.
   *
   * @return results as linked hash map
   */
  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("temperatureC", temperatureC);
    result.put("h2PartialPressure_psia", h2PartialPressurePsia);
    result.put("h2PartialPressure_bar", h2PartialPressurePsia / 14.5038);
    result.put("materialType", materialType);
    result.put("belowNelsonCurve", belowNelsonCurve);
    result.put("maxAllowableTemperature_C", maxAllowableTempC);
    result.put("maxAllowableH2Pressure_bar", maxAllowableH2PressureBar);
    result.put("temperatureMargin_C", temperatureMarginC);
    result.put("riskLevel", riskLevel);
    result.put("recommendedUpgrade", recommendedUpgrade);
    result.put("standard", "API 941 8th Edition (2016)");
    return result;
  }

  /**
   * Returns the assessment results as a JSON string.
   *
   * @return JSON representation of the assessment
   */
  public String toJson() {
    Gson gson = new GsonBuilder().setPrettyPrinting()
        .serializeSpecialFloatingPointValues().create();
    return gson.toJson(toMap());
  }
}
