package neqsim.thermo.characterization;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Characterization of a biological feedstock used by biochemical conversion models.
 *
 * <p>
 * The class separates feedstock evidence from reactor configuration. It records the as-received solids content,
 * biodegradable fractions, elemental analysis, methane-yield basis, hydrolysis kinetics, and handling properties in a
 * form that can be reused by preparation, digestion, sustainability, and economic calculations.
 * </p>
 *
 * <p>
 * Library values are representative screening defaults. They are not design guarantees and should be replaced by
 * project-specific biochemical methane potential, composition, and handling measurements before detailed engineering.
 * </p>
 *
 * @author NeqSim team
 * @version 1.0
 */
public class BioFeedstock implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Feedstock name. */
  private String name;
  /** Total solids as fraction of as-received mass. */
  private double totalSolidsFraction;
  /** Volatile solids as fraction of total solids. */
  private double volatileSolidsFraction;
  /** Maximum degradable fraction of volatile solids. */
  private double maximumVsDestruction;
  /** Specific methane yield per kg destroyed volatile solids. */
  private double methaneYieldNm3PerKgDestroyedVs;
  /** First-order hydrolysis rate constant in inverse days. */
  private double hydrolysisRatePerDay;
  /** Methane fraction of dry product gas. */
  private double methaneFraction;

  /** Cellulose as fraction of total solids. */
  private double celluloseFraction;
  /** Hemicellulose as fraction of total solids. */
  private double hemicelluloseFraction;
  /** Lignin as fraction of total solids. */
  private double ligninFraction;
  /** Ash as fraction of total solids. */
  private double ashFraction;

  /** Carbon as fraction of dry solids. */
  private double carbonFraction;
  /** Hydrogen as fraction of dry solids. */
  private double hydrogenFraction;
  /** Oxygen as fraction of dry solids. */
  private double oxygenFraction;
  /** Nitrogen as fraction of dry solids. */
  private double nitrogenFraction;
  /** Sulfur as fraction of dry solids. */
  private double sulfurFraction;
  /** Phosphorus as fraction of dry solids. */
  private double phosphorusFraction;

  /** Loose bulk density in kg/m3. */
  private double bulkDensityKgPerM3;
  /** Prepared or compacted density in kg/m3. */
  private double preparedDensityKgPerM3;
  /** Evidence note or data-set identifier. */
  private String evidenceReference = "screening default";

  /**
   * Creates a feedstock characterization.
   *
   * @param name feedstock name
   */
  public BioFeedstock(String name) {
    if (name == null || name.trim().isEmpty()) {
      throw new IllegalArgumentException("Feedstock name must be provided");
    }
    this.name = name;
  }

  /**
   * Returns a screening characterization for a supported feedstock family.
   *
   * @param feedstockName crop_residue, food_residue, manure, or sewage_sludge
   * @return configured feedstock
   */
  public static BioFeedstock library(String feedstockName) {
    if (feedstockName == null || feedstockName.trim().isEmpty()) {
      throw new IllegalArgumentException("Feedstock family must be provided");
    }
    String key = feedstockName.trim().toLowerCase().replace(' ', '_').replace('-', '_');
    BioFeedstock feedstock = new BioFeedstock(feedstockName);
    if ("crop_residue".equals(key) || "straw".equals(key)) {
      feedstock.setSolidsAnalysis(0.85, 0.85, 0.62, 0.06);
      feedstock.setLignocellulosicAnalysis(0.38, 0.27, 0.19);
      feedstock.setElementalAnalysis(0.47, 0.058, 0.41, 0.008, 0.0015, 0.0010);
      feedstock.setDigestionProperties(0.38, 0.08, 0.60);
      feedstock.setHandlingProperties(80.0, 650.0);
    } else if ("food_residue".equals(key) || "food_waste".equals(key)) {
      feedstock.setSolidsAnalysis(0.25, 0.92, 0.80, 0.08);
      feedstock.setLignocellulosicAnalysis(0.08, 0.05, 0.03);
      feedstock.setElementalAnalysis(0.48, 0.068, 0.36, 0.028, 0.004, 0.006);
      feedstock.setDigestionProperties(0.45, 0.25, 0.62);
      feedstock.setHandlingProperties(650.0, 750.0);
    } else if ("manure".equals(key)) {
      feedstock.setSolidsAnalysis(0.08, 0.80, 0.45, 0.20);
      feedstock.setLignocellulosicAnalysis(0.22, 0.17, 0.16);
      feedstock.setElementalAnalysis(0.43, 0.061, 0.36, 0.052, 0.008, 0.012);
      feedstock.setDigestionProperties(0.30, 0.10, 0.58);
      feedstock.setHandlingProperties(950.0, 1000.0);
    } else if ("sewage_sludge".equals(key)) {
      feedstock.setSolidsAnalysis(0.05, 0.70, 0.55, 0.30);
      feedstock.setLignocellulosicAnalysis(0.05, 0.03, 0.04);
      feedstock.setElementalAnalysis(0.515, 0.073, 0.305, 0.075, 0.015, 0.015);
      feedstock.setDigestionProperties(0.38, 0.16, 0.62);
      feedstock.setHandlingProperties(980.0, 1000.0);
    } else {
      throw new IllegalArgumentException("Unknown biological feedstock: '" + feedstockName
          + "'. Supported: crop_residue, food_residue, manure, sewage_sludge");
    }
    feedstock.validate();
    return feedstock;
  }

  /**
   * Creates an independent copy of this characterization.
   *
   * @return copied feedstock characterization
   */
  public BioFeedstock copy() {
    BioFeedstock copied = new BioFeedstock(name);
    copied.setSolidsAnalysis(totalSolidsFraction, volatileSolidsFraction, maximumVsDestruction, ashFraction);
    copied.setLignocellulosicAnalysis(celluloseFraction, hemicelluloseFraction, ligninFraction);
    copied.setElementalAnalysis(carbonFraction, hydrogenFraction, oxygenFraction, nitrogenFraction, sulfurFraction,
        phosphorusFraction);
    copied.setDigestionProperties(methaneYieldNm3PerKgDestroyedVs, hydrolysisRatePerDay, methaneFraction);
    copied.setHandlingProperties(bulkDensityKgPerM3, preparedDensityKgPerM3);
    copied.setEvidenceReference(evidenceReference);
    return copied;
  }

  /**
   * Sets the main solids analysis.
   *
   * @param totalSolids total solids fraction of as-received feed
   * @param volatileSolids volatile solids fraction of total solids
   * @param maximumDestruction maximum degradable volatile-solids fraction
   * @param ash ash fraction of total solids
   */
  public void setSolidsAnalysis(double totalSolids, double volatileSolids, double maximumDestruction, double ash) {
    totalSolidsFraction = totalSolids;
    volatileSolidsFraction = volatileSolids;
    maximumVsDestruction = maximumDestruction;
    ashFraction = ash;
  }

  /**
   * Sets lignocellulosic fractions on a total-solids basis.
   *
   * @param cellulose cellulose fraction
   * @param hemicellulose hemicellulose fraction
   * @param lignin lignin fraction
   */
  public void setLignocellulosicAnalysis(double cellulose, double hemicellulose, double lignin) {
    celluloseFraction = cellulose;
    hemicelluloseFraction = hemicellulose;
    ligninFraction = lignin;
  }

  /**
   * Sets elemental fractions on a dry-solids basis.
   *
   * @param carbon carbon mass fraction
   * @param hydrogen hydrogen mass fraction
   * @param oxygen oxygen mass fraction
   * @param nitrogen nitrogen mass fraction
   * @param sulfur sulfur mass fraction
   * @param phosphorus phosphorus mass fraction
   */
  public void setElementalAnalysis(double carbon, double hydrogen, double oxygen, double nitrogen, double sulfur,
      double phosphorus) {
    carbonFraction = carbon;
    hydrogenFraction = hydrogen;
    oxygenFraction = oxygen;
    nitrogenFraction = nitrogen;
    sulfurFraction = sulfur;
    phosphorusFraction = phosphorus;
  }

  /**
   * Sets biochemical conversion properties.
   *
   * @param methaneYield specific methane yield in Nm3/kg destroyed VS
   * @param hydrolysisRate first-order hydrolysis rate in 1/day
   * @param dryGasMethaneFraction methane fraction of dry product gas
   */
  public void setDigestionProperties(double methaneYield, double hydrolysisRate, double dryGasMethaneFraction) {
    methaneYieldNm3PerKgDestroyedVs = methaneYield;
    hydrolysisRatePerDay = hydrolysisRate;
    methaneFraction = dryGasMethaneFraction;
  }

  /**
   * Sets handling densities.
   *
   * @param bulkDensity loose bulk density in kg/m3
   * @param preparedDensity prepared density in kg/m3
   */
  public void setHandlingProperties(double bulkDensity, double preparedDensity) {
    bulkDensityKgPerM3 = bulkDensity;
    preparedDensityKgPerM3 = preparedDensity;
  }

  /**
   * Validates fraction bounds and required physical properties.
   *
   * @return immutable list of validation warnings
   * @throws IllegalArgumentException when a required value is outside physical bounds
   */
  public List<String> validate() {
    requireFraction("total solids", totalSolidsFraction);
    requireFraction("volatile solids", volatileSolidsFraction);
    requireFraction("maximum VS destruction", maximumVsDestruction);
    requireFraction("ash", ashFraction);
    requireFraction("methane fraction", methaneFraction);
    requireFraction("cellulose", celluloseFraction);
    requireFraction("hemicellulose", hemicelluloseFraction);
    requireFraction("lignin", ligninFraction);
    requireFraction("carbon", carbonFraction);
    requireFraction("hydrogen", hydrogenFraction);
    requireFraction("oxygen", oxygenFraction);
    requireFraction("nitrogen", nitrogenFraction);
    requireFraction("sulfur", sulfurFraction);
    requireFraction("phosphorus", phosphorusFraction);
    if (methaneYieldNm3PerKgDestroyedVs <= 0.0 || hydrolysisRatePerDay <= 0.0) {
      throw new IllegalArgumentException("Methane yield and hydrolysis rate must be positive");
    }
    if (bulkDensityKgPerM3 <= 0.0 || preparedDensityKgPerM3 <= 0.0) {
      throw new IllegalArgumentException("Handling densities must be positive");
    }

    List<String> warnings = new ArrayList<String>();
    double biochemicalSum = celluloseFraction + hemicelluloseFraction + ligninFraction + ashFraction;
    if (biochemicalSum > 1.0 + 1.0e-9) {
      warnings.add("Cellulose, hemicellulose, lignin, and ash exceed total solids");
    }
    double elementalSum = carbonFraction + hydrogenFraction + oxygenFraction + nitrogenFraction + sulfurFraction
        + phosphorusFraction;
    if (Math.abs(elementalSum - 1.0) > 0.08) {
      warnings.add("Elemental analysis differs from unity by more than eight mass percent");
    }
    return Collections.unmodifiableList(warnings);
  }

  /**
   * Checks that a value is a fraction.
   *
   * @param label property label
   * @param value value to check
   */
  private static void requireFraction(String label, double value) {
    if (Double.isNaN(value) || value < 0.0 || value > 1.0) {
      throw new IllegalArgumentException(label + " must be between zero and one");
    }
  }

  /**
   * Returns a result map suitable for reports and JSON serialization.
   *
   * @return property map
   */
  public Map<String, Object> toMap() {
    Map<String, Object> values = new LinkedHashMap<String, Object>();
    values.put("name", name);
    values.put("totalSolidsFraction", totalSolidsFraction);
    values.put("volatileSolidsFraction", volatileSolidsFraction);
    values.put("maximumVsDestruction", maximumVsDestruction);
    values.put("methaneYield_Nm3PerKgDestroyedVS", methaneYieldNm3PerKgDestroyedVs);
    values.put("hydrolysisRate_perDay", hydrolysisRatePerDay);
    values.put("methaneFraction", methaneFraction);
    values.put("celluloseFraction", celluloseFraction);
    values.put("hemicelluloseFraction", hemicelluloseFraction);
    values.put("ligninFraction", ligninFraction);
    values.put("ashFraction", ashFraction);
    values.put("carbonFraction", carbonFraction);
    values.put("hydrogenFraction", hydrogenFraction);
    values.put("oxygenFraction", oxygenFraction);
    values.put("nitrogenFraction", nitrogenFraction);
    values.put("sulfurFraction", sulfurFraction);
    values.put("phosphorusFraction", phosphorusFraction);
    values.put("bulkDensity_kgPerM3", bulkDensityKgPerM3);
    values.put("preparedDensity_kgPerM3", preparedDensityKgPerM3);
    values.put("evidenceReference", evidenceReference);
    values.put("validationWarnings", validate());
    return values;
  }

  /** @return feedstock name */
  public String getName() {
    return name;
  }

  /** @return total-solids fraction */
  public double getTotalSolidsFraction() {
    return totalSolidsFraction;
  }

  /** @return volatile-solids fraction of total solids */
  public double getVolatileSolidsFraction() {
    return volatileSolidsFraction;
  }

  /** @return maximum volatile-solids destruction */
  public double getMaximumVsDestruction() {
    return maximumVsDestruction;
  }

  /** @return methane yield in Nm3/kg destroyed VS */
  public double getMethaneYieldNm3PerKgDestroyedVs() {
    return methaneYieldNm3PerKgDestroyedVs;
  }

  /** @return hydrolysis rate in 1/day */
  public double getHydrolysisRatePerDay() {
    return hydrolysisRatePerDay;
  }

  /** @return dry-gas methane fraction */
  public double getMethaneFraction() {
    return methaneFraction;
  }

  /** @return cellulose fraction of total solids */
  public double getCelluloseFraction() {
    return celluloseFraction;
  }

  /** @return hemicellulose fraction of total solids */
  public double getHemicelluloseFraction() {
    return hemicelluloseFraction;
  }

  /** @return lignin fraction of total solids */
  public double getLigninFraction() {
    return ligninFraction;
  }

  /** @return ash fraction of total solids */
  public double getAshFraction() {
    return ashFraction;
  }

  /** @return carbon fraction of dry solids */
  public double getCarbonFraction() {
    return carbonFraction;
  }

  /** @return hydrogen fraction of dry solids */
  public double getHydrogenFraction() {
    return hydrogenFraction;
  }

  /** @return oxygen fraction of dry solids */
  public double getOxygenFraction() {
    return oxygenFraction;
  }

  /** @return nitrogen fraction of dry solids */
  public double getNitrogenFraction() {
    return nitrogenFraction;
  }

  /** @return sulfur fraction of dry solids */
  public double getSulfurFraction() {
    return sulfurFraction;
  }

  /** @return phosphorus fraction of dry solids */
  public double getPhosphorusFraction() {
    return phosphorusFraction;
  }

  /** @return loose bulk density in kg/m3 */
  public double getBulkDensityKgPerM3() {
    return bulkDensityKgPerM3;
  }

  /** @return prepared density in kg/m3 */
  public double getPreparedDensityKgPerM3() {
    return preparedDensityKgPerM3;
  }

  /** @return evidence reference */
  public String getEvidenceReference() {
    return evidenceReference;
  }

  /**
   * Sets a traceable evidence note or data-set identifier.
   *
   * @param reference evidence reference
   */
  public void setEvidenceReference(String reference) {
    evidenceReference = reference == null ? "" : reference;
  }
}
