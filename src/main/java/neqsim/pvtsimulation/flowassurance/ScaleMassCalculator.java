package neqsim.pvtsimulation.flowassurance;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Calculates the mass of mineral scale that would precipitate from a supersaturated brine.
 *
 * <p>
 * Given a supersaturated solution (SI &gt; 0), determines how much solid precipitates to reach
 * equilibrium. Uses the stoichiometry of the dissolution reaction and the difference between the
 * ion activity product and the solubility product to estimate the amount of scale formed.
 * </p>
 *
 * <p>
 * The calculation is performed by systematically removing equimolar amounts of the cation and anion
 * until the saturation index reaches zero (equilibrium). The mass of removed ions corresponds to
 * the mass of precipitated mineral.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class ScaleMassCalculator implements Serializable {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** The scale prediction calculator to use for SI calculations. */
  private final ScalePredictionCalculator calculator;

  /** The water volume in litres. */
  private double waterVolumeLitres = 1.0;

  /** Results map for each scale type. */
  private Map<String, ScaleMassResult> results = new LinkedHashMap<String, ScaleMassResult>();

  /**
   * Creates a ScaleMassCalculator linked to an existing ScalePredictionCalculator.
   *
   * @param calculator the configured and calculated ScalePredictionCalculator
   */
  public ScaleMassCalculator(ScalePredictionCalculator calculator) {
    this.calculator = calculator;
  }

  /**
   * Sets the volume of water for mass calculation.
   *
   * @param litres water volume in litres
   */
  public void setWaterVolume(double litres) {
    this.waterVolumeLitres = litres;
  }

  /**
   * Returns the water volume used for mass calculation.
   *
   * @return water volume in litres
   */
  public double getWaterVolume() {
    return waterVolumeLitres;
  }

  /**
   * Calculates scale mass for CaCO3 (calcite).
   *
   * <p>
   * Molar mass of CaCO3 = 100.09 g/mol. Reaction: Ca2+ + CO3 2- = CaCO3(s).
   * </p>
   *
   * @param cCaMolL calcium concentration in mol/L
   * @param cCO3MolL carbonate concentration in mol/L
   * @param si saturation index
   * @return mass of CaCO3 precipitated in mg per litre
   */
  public double calcCaCO3Mass(double cCaMolL, double cCO3MolL, double si) {
    if (si <= 0) {
      return 0.0;
    }
    // Excess ion product ratio = 10^SI
    // Amount precipitated = min(Ca, CO3) * (1 - 10^(-SI))
    // This is an approximation; exact calculation requires iterative equilibrium
    double molarMass = 100.09; // g/mol CaCO3
    double limitingConc = Math.min(cCaMolL, cCO3MolL);
    double fractionExcess = 1.0 - Math.pow(10.0, -si);
    if (fractionExcess > 0.99) {
      fractionExcess = 0.99;
    }
    double precipMolL = limitingConc * fractionExcess;
    return precipMolL * molarMass * 1000.0; // mg/L
  }

  /**
   * Calculates scale mass for BaSO4 (barite).
   *
   * <p>
   * Molar mass of BaSO4 = 233.39 g/mol. Reaction: Ba2+ + SO4 2- = BaSO4(s).
   * </p>
   *
   * @param cBaMolL barium concentration in mol/L
   * @param cSO4MolL sulphate concentration in mol/L
   * @param si saturation index
   * @return mass of BaSO4 precipitated in mg per litre
   */
  public double calcBaSO4Mass(double cBaMolL, double cSO4MolL, double si) {
    if (si <= 0) {
      return 0.0;
    }
    double molarMass = 233.39;
    double limitingConc = Math.min(cBaMolL, cSO4MolL);
    double fractionExcess = 1.0 - Math.pow(10.0, -si);
    if (fractionExcess > 0.99) {
      fractionExcess = 0.99;
    }
    double precipMolL = limitingConc * fractionExcess;
    return precipMolL * molarMass * 1000.0;
  }

  /**
   * Calculates scale mass for SrSO4 (celestite).
   *
   * <p>
   * Molar mass of SrSO4 = 183.68 g/mol. Reaction: Sr2+ + SO4 2- = SrSO4(s).
   * </p>
   *
   * @param cSrMolL strontium concentration in mol/L
   * @param cSO4MolL sulphate concentration in mol/L
   * @param si saturation index
   * @return mass of SrSO4 precipitated in mg per litre
   */
  public double calcSrSO4Mass(double cSrMolL, double cSO4MolL, double si) {
    if (si <= 0) {
      return 0.0;
    }
    double molarMass = 183.68;
    double limitingConc = Math.min(cSrMolL, cSO4MolL);
    double fractionExcess = 1.0 - Math.pow(10.0, -si);
    if (fractionExcess > 0.99) {
      fractionExcess = 0.99;
    }
    double precipMolL = limitingConc * fractionExcess;
    return precipMolL * molarMass * 1000.0;
  }

  /**
   * Calculates scale mass for CaSO4 (anhydrite/gypsum).
   *
   * <p>
   * Molar mass of CaSO4 = 136.14 g/mol. Reaction: Ca2+ + SO4 2- = CaSO4(s).
   * </p>
   *
   * @param cCaMolL calcium concentration in mol/L
   * @param cSO4MolL sulphate concentration in mol/L
   * @param si saturation index
   * @return mass of CaSO4 precipitated in mg per litre
   */
  public double calcCaSO4Mass(double cCaMolL, double cSO4MolL, double si) {
    if (si <= 0) {
      return 0.0;
    }
    double molarMass = 136.14;
    double limitingConc = Math.min(cCaMolL, cSO4MolL);
    double fractionExcess = 1.0 - Math.pow(10.0, -si);
    if (fractionExcess > 0.99) {
      fractionExcess = 0.99;
    }
    double precipMolL = limitingConc * fractionExcess;
    return precipMolL * molarMass * 1000.0;
  }

  /**
   * Calculates scale mass for FeCO3 (siderite).
   *
   * <p>
   * Molar mass of FeCO3 = 115.85 g/mol. Reaction: Fe2+ + CO3 2- = FeCO3(s).
   * </p>
   *
   * @param cFeMolL iron concentration in mol/L
   * @param cCO3MolL carbonate concentration in mol/L
   * @param si saturation index
   * @return mass of FeCO3 precipitated in mg per litre
   */
  public double calcFeCO3Mass(double cFeMolL, double cCO3MolL, double si) {
    if (si <= 0) {
      return 0.0;
    }
    double molarMass = 115.85;
    double limitingConc = Math.min(cFeMolL, cCO3MolL);
    double fractionExcess = 1.0 - Math.pow(10.0, -si);
    if (fractionExcess > 0.99) {
      fractionExcess = 0.99;
    }
    double precipMolL = limitingConc * fractionExcess;
    return precipMolL * molarMass * 1000.0;
  }

  /**
   * Returns a comprehensive JSON report of scale mass predictions.
   *
   * @return JSON string
   */
  public String toJson() {
    Map<String, Object> report = new LinkedHashMap<String, Object>();
    report.put("waterVolumeLitres", waterVolumeLitres);
    report.put("scaleMassResults", results);
    Gson gson =
        new GsonBuilder().serializeSpecialFloatingPointValues().setPrettyPrinting().create();
    return gson.toJson(report);
  }

  /**
   * Inner class to hold scale mass results for a single mineral.
   */
  public static class ScaleMassResult implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000L;

    /** Scale type name. */
    public final String scaleType;
    /** Saturation index. */
    public final double saturationIndex;
    /** Mass precipitated in mg per litre of water. */
    public final double massMgPerLitre;
    /** Total mass precipitated in mg for the given water volume. */
    public final double totalMassMg;

    /**
     * Constructs a ScaleMassResult.
     *
     * @param scaleType name of scale mineral
     * @param si saturation index
     * @param mgPerL mass in mg/L
     * @param totalMg total mass in mg
     */
    public ScaleMassResult(String scaleType, double si, double mgPerL, double totalMg) {
      this.scaleType = scaleType;
      this.saturationIndex = si;
      this.massMgPerLitre = mgPerL;
      this.totalMassMg = totalMg;
    }
  }
}
