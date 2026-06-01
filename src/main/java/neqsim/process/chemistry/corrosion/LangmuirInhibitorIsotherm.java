package neqsim.process.chemistry.corrosion;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Langmuir adsorption isotherm for film-forming corrosion inhibitors.
 *
 * <p>
 * The fractional surface coverage by inhibitor molecules is
 *
 * <pre>
 * theta = (K_ads * C) / (1 + K_ads * C)
 * </pre>
 *
 * where {@code C} is the inhibitor concentration in the bulk aqueous phase (mol/L) and
 * {@code K_ads} is the adsorption equilibrium constant. The temperature dependence of {@code K_ads}
 * follows the van 't Hoff equation
 *
 * <pre>
 * K_ads(T) = K_ads_ref * exp(-dHads / R * (1 / T - 1 / T_ref))
 * </pre>
 *
 * with adsorption enthalpy {@code dHads} typically in the range -20 to -60 kJ/mol for physisorption
 * of imidazoline / quaternary-ammonium inhibitors on carbon steel (Bentiss et al., 2002; Khaled,
 * 2008).
 *
 * <p>
 * The inhibition efficiency is taken proportional to coverage: {@code eta = theta_max * theta}
 * where {@code theta_max} caps the maximum achievable efficiency (typical 0.90 - 0.99 for
 * high-performing imidazoline blends).
 *
 * @author ESOL
 * @version 1.0
 */
public class LangmuirInhibitorIsotherm implements Serializable {

  private static final long serialVersionUID = 1000L;

  private double kAdsRef = 5000.0; // L/mol at 25 C, typical imidazoline
  private double tRefK = 298.15;
  private double dHadsKjMol = -35.0; // kJ/mol, physisorption
  private double thetaMax = 0.95;
  private double inhibitorMolarMass = 350.0; // g/mol, typical imidazoline
  private static final double R_KJ_MOL_K = 8.314e-3;

  /**
   * Default constructor with imidazoline-typical parameters.
   */
  public LangmuirInhibitorIsotherm() {}

  /**
   * Constructs an isotherm with custom adsorption parameters.
   *
   * @param kAdsRef reference adsorption constant at 298.15 K [L/mol]
   * @param dHadsKjMol adsorption enthalpy [kJ/mol] (negative for exothermic)
   * @param thetaMax maximum coverage cap (0..1)
   * @param molarMass inhibitor molar mass [g/mol]
   */
  public LangmuirInhibitorIsotherm(double kAdsRef, double dHadsKjMol, double thetaMax,
      double molarMass) {
    this.kAdsRef = kAdsRef;
    this.dHadsKjMol = dHadsKjMol;
    this.thetaMax = Math.max(0.0, Math.min(1.0, thetaMax));
    this.inhibitorMolarMass = molarMass;
  }

  /**
   * Returns the temperature-corrected adsorption constant K_ads.
   *
   * @param temperatureC operating temperature [C]
   * @return K_ads [L/mol]
   */
  public double getKAds(double temperatureC) {
    double tK = temperatureC + 273.15;
    return kAdsRef * Math.exp(-dHadsKjMol / R_KJ_MOL_K * (1.0 / tK - 1.0 / tRefK));
  }

  /**
   * Computes fractional surface coverage at the given inhibitor dose.
   *
   * @param doseMgL inhibitor concentration [mg/L]
   * @param temperatureC operating temperature [C]
   * @return coverage theta in [0,1]
   */
  public double getCoverage(double doseMgL, double temperatureC) {
    double cMolL = doseMgL / 1000.0 / inhibitorMolarMass;
    double k = getKAds(temperatureC);
    double kc = k * cMolL;
    return kc / (1.0 + kc);
  }

  /**
   * Computes inhibition efficiency (fraction of base corrosion rate suppressed).
   *
   * @param doseMgL inhibitor concentration [mg/L]
   * @param temperatureC operating temperature [C]
   * @return efficiency in [0, thetaMax]
   */
  public double getEfficiency(double doseMgL, double temperatureC) {
    return thetaMax * getCoverage(doseMgL, temperatureC);
  }

  /**
   * Solves for the dose that gives a target efficiency.
   *
   * @param targetEfficiency target efficiency (0..thetaMax)
   * @param temperatureC operating temperature [C]
   * @return required dose [mg/L]; returns positive infinity if target exceeds thetaMax
   */
  public double getDoseForEfficiency(double targetEfficiency, double temperatureC) {
    if (targetEfficiency >= thetaMax) {
      return Double.POSITIVE_INFINITY;
    }
    double theta = targetEfficiency / thetaMax;
    double k = getKAds(temperatureC);
    double cMolL = theta / (k * (1.0 - theta));
    return cMolL * 1000.0 * inhibitorMolarMass;
  }

  /**
   * Returns a structured map representation suitable for JSON serialisation.
   *
   * @return ordered map
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("kAdsRef", kAdsRef);
    map.put("dHadsKjMol", dHadsKjMol);
    map.put("thetaMax", thetaMax);
    map.put("inhibitorMolarMass", inhibitorMolarMass);
    return map;
  }

  /**
   * Returns a JSON representation.
   *
   * @return JSON string
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(toMap());
  }
}
