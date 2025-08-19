package neqsim.physicalproperties.methods.liquidphysicalproperties.viscosity;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.physicalproperties.system.PhysicalProperties;

/**
 * Salt-water viscosity using Laliberté (2007) with erratum coefficients.
 * <p>
 * Mixture rule (weight fractions): {@code η_m = η_w^{w_w} Π η_i^{w_i}}, where {@code η_w} is the
 * pure-water viscosity (from NeqSim's water correlation) and {@code η_i} are "solute viscosities"
 * from Laliberté:
 * 
 * <pre>
 * (η_i / mPa·s) = exp( [ν1 (1 - w_w)^{ν2} + ν3] / [ν4 (t/°C) + 1] )
 *                 / ( ν5 (1 - w_w)^{ν6} + 1 )
 * </pre>
 * 
 * with {@code w_w} = mass fraction of water in the liquid phase.
 * <p>
 * Supported salts (coefficients ν1–ν6): NaCl, KCl, KCOOH (potassium formate), NaBr, CaCl2, KBr. If
 * a salt is not recognized, NaCl coefficients are used, as suggested in the supplementary info to
 * the paper.
 *
 * References: - G. Laliberté, Ind. Eng. Chem. Res., 2007, 46, 8865–8872 (+ erratum).
 *
 * @author Even Solbraa
 */
public class Water extends Viscosity {
  private static final long serialVersionUID = 1000;
  static Logger logger = LogManager.getLogger(Water.class);

  // ν1..ν6 for each supported salt (erratum values), order: {v1,v2,v3,v4,v5,v6}
  private static final Map<String, double[]> LALIBERTE_COEFFS = new HashMap<>();
  static {
    LALIBERTE_COEFFS.put("NACL", new double[] {16.2218, 1.3229, 1.4849, 0.0075, 30.7802, 2.0583});
    LALIBERTE_COEFFS.put("KCL", new double[] {6.4883, 1.3175, -0.7778, 0.0927, -1.3000, 2.0811});
    LALIBERTE_COEFFS.put("KCOOH", new double[] {15.0442, 4.5087, 1.5924, 0.0113, 81.0129, 11.8962}); // potassium
                                                                                                     // formate
    LALIBERTE_COEFFS.put("NABR", new double[] {13.0291, 1.7478, 0.6041, 0.0108, 17.6807, 2.3831});
    LALIBERTE_COEFFS.put("CACL2",
        new double[] {32.0276, 0.7879, -1.1495, 0.0027, 780860.75, 5.8442});
    LALIBERTE_COEFFS.put("KBR",
        new double[] {348.320, -0.0003, -349.1532, -0.0043, -1.1044, 0.7632});

    // Useful synonyms → canonical keys
    LALIBERTE_COEFFS.put("SODIUMCHLORIDE", LALIBERTE_COEFFS.get("NACL"));
    LALIBERTE_COEFFS.put("POTASSIUMCHLORIDE", LALIBERTE_COEFFS.get("KCL"));
    LALIBERTE_COEFFS.put("POTASSIUMFORMATE", LALIBERTE_COEFFS.get("KCOOH"));
    LALIBERTE_COEFFS.put("HCOOK", LALIBERTE_COEFFS.get("KCOOH"));
    LALIBERTE_COEFFS.put("SODIUMBROMIDE", LALIBERTE_COEFFS.get("NABR"));
    LALIBERTE_COEFFS.put("POTASSIUMBROMIDE", LALIBERTE_COEFFS.get("KBR"));
    LALIBERTE_COEFFS.put("CALCIUMCHLORIDE", LALIBERTE_COEFFS.get("CACL2"));
  }

  public Water(PhysicalProperties liquidPhase) {
    super(liquidPhase);
  }

  @Override
  public Water clone() {
    Water properties = null;
    try {
      properties = (Water) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }
    return properties;
  }

  /**
   * Returns {@code true} if this looks like an aqueous salt solution that we can handle with
   * Laliberté.
   */
  private boolean isAqueousSaltSolution() {
    int n = liquidPhase.getPhase().getNumberOfComponents();
    int waterIndex = indexOfWater();
    if (waterIndex < 0)
      return false;

    // require at least some non-water mass fraction
    double ww = liquidPhase.getPhase().getWtFrac(waterIndex);
    double ws = 1.0 - ww;
    if (ws <= 1e-12)
      return false;

    // Check that (almost) all non-water mass is from recognized salts
    double covered = 0.0;
    for (int i = 0; i < n; i++) {
      if (i == waterIndex)
        continue;
      String key = canonicalSaltKey(liquidPhase.getPhase().getComponent(i).getComponentName());
      if (key != null)
        covered += liquidPhase.getPhase().getWtFrac(i);
    }
    // tolerate tiny amounts of other species (e.g., dissolved gases)
    return covered >= 0.98 * ws;
  }

  private int indexOfWater() {
    int n = liquidPhase.getPhase().getNumberOfComponents();
    for (int i = 0; i < n; i++) {
      String nm = liquidPhase.getPhase().getComponent(i).getComponentName();
      if (nm != null && nm.trim().equalsIgnoreCase("water"))
        return i;
      if (nm != null && nm.trim().equalsIgnoreCase("H2O"))
        return i;
    }
    return -1;
  }

  /** Normalize component name to a Laliberté coefficient key, or null if unknown. */
  private String canonicalSaltKey(String name) {
    if (name == null)
      return null;
    // remove whitespace, hyphens, parentheses, dots; uppercase
    String key = name.replaceAll("[\\s\\-()\\.]", "").toUpperCase(Locale.ROOT);
    if (LALIBERTE_COEFFS.containsKey(key))
      return key;
    // simple chemical-formula normalizations
    if ("CAC12".equals(key))
      key = "CACL2"; // guard against common OCR/typo
    if (LALIBERTE_COEFFS.containsKey(key))
      return key;
    // Not found → return null
    return null;
  }

  /** Get ν1..ν6 for a salt; default to NaCl set if salt is unknown. */
  private double[] coeffsForSaltOrDefault(String key) {
    if (key == null)
      return LALIBERTE_COEFFS.get("NACL");
    double[] c = LALIBERTE_COEFFS.get(key);
    return (c != null) ? c : LALIBERTE_COEFFS.get("NACL");
  }

  /** Solute viscosity (mPa·s) by Laliberté for given ν1..ν6, temperature (°C) and w_w. */
  private double soluteViscosity_mPaS(double[] v, double tempC, double w_w) {
    double oneMinusWw = Math.max(0.0, 1.0 - w_w); // guard
    double num = v[0] * Math.pow(oneMinusWw, v[1]) + v[2];
    double denTop = v[3] * tempC + 1.0;
    double expo = num / denTop;
    double den = v[4] * Math.pow(oneMinusWw, v[5]) + 1.0;
    return Math.exp(expo) / den; // mPa·s
  }

  @Override
  public double calcViscosity() {
    // Only use the special correlation for "salt water". Otherwise, use the default.
    if (!isAqueousSaltSolution()) {
      return super.calcViscosity();
    }

    // 1) Get weight fractions and temperature
    int n = liquidPhase.getPhase().getNumberOfComponents();
    int iw = indexOfWater();
    double ww = liquidPhase.getPhase().getWtFrac(iw);
    double tempC = liquidPhase.getPhase().getTemperature() - 273.15;

    // 2) Pure water viscosity in mPa·s from NeqSim's component correlation
    // (respects pressure correction already handled in calcPureComponentViscosity()).
    calcPureComponentViscosity();
    double etaW_mPaS = pureComponentViscosity[iw];

    // 3) Mixture rule (weight fractions as exponents)
    double etaMix_mPaS = Math.pow(etaW_mPaS, ww);

    for (int i = 0; i < n; i++) {
      if (i == iw)
        continue;
      double wi = liquidPhase.getPhase().getWtFrac(i);
      if (wi <= 0.0)
        continue;

      String key = canonicalSaltKey(liquidPhase.getPhase().getComponent(i).getComponentName());
      double[] v = coeffsForSaltOrDefault(key);

      if (key == null) {
        // If truly unknown and not negligible, log once and still fall back to NaCl set.
        logger.debug(
            "Viscosity (Water): component '{}' not recognized as salt – using NaCl coefficients.",
            liquidPhase.getPhase().getComponent(i).getComponentName());
      }

      double etaI_mPaS = soluteViscosity_mPaS(v, tempC, ww);
      etaMix_mPaS *= Math.pow(etaI_mPaS, wi);
    }

    // 4) Return in Pa·s
    return etaMix_mPaS / 1.0e3;
  }
}
