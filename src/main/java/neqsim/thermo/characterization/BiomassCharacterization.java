package neqsim.thermo.characterization;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Characterizes biomass feedstocks for use in NeqSim thermochemical process simulations.
 *
 * <p>
 * Converts proximate and ultimate analysis data into derived properties needed by gasifiers,
 * pyrolysis reactors, and combustion models. Provides higher/lower heating values, stoichiometric
 * air requirements, and a chemical formula representation.
 * </p>
 *
 * <p>
 * Supports two input modes:
 * </p>
 * <ul>
 * <li><b>Proximate analysis</b> (dry basis): moisture, volatile matter, fixed carbon, ash — all in
 * mass-percent.</li>
 * <li><b>Ultimate analysis</b> (dry-ash-free basis): C, H, O, N, S, Cl — all in mass-percent.</li>
 * </ul>
 *
 * <p>
 * Heating values are estimated with the Channiwala-Parikh correlation when not supplied directly.
 * </p>
 *
 * <p>
 * Usage example:
 * </p>
 *
 * <pre>
 * BiomassCharacterization wood = BiomassCharacterization.library("wood_chips");
 * double hhv = wood.getHHV(); // MJ/kg daf
 *
 * BiomassCharacterization custom = new BiomassCharacterization("MyBiomass");
 * custom.setProximateAnalysis(8.0, 78.0, 14.0, 0.5);
 * custom.setUltimateAnalysis(50.0, 6.1, 43.0, 0.3, 0.05, 0.01);
 * custom.calculate();
 * </pre>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class BiomassCharacterization implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Name or label for this biomass feedstock. */
  private String name;

  // ── Proximate analysis (dry basis, wt%) ──
  /** Moisture content in weight percent (as-received basis). */
  private double moisture = 0.0;
  /** Volatile matter in weight percent (dry basis). */
  private double volatileMatter = 0.0;
  /** Fixed carbon in weight percent (dry basis). */
  private double fixedCarbon = 0.0;
  /** Ash content in weight percent (dry basis). */
  private double ash = 0.0;

  // ── Ultimate analysis (dry-ash-free basis, wt%) ──
  /** Carbon content in weight percent (daf). */
  private double carbonWt = 0.0;
  /** Hydrogen content in weight percent (daf). */
  private double hydrogenWt = 0.0;
  /** Oxygen content in weight percent (daf). */
  private double oxygenWt = 0.0;
  /** Nitrogen content in weight percent (daf). */
  private double nitrogenWt = 0.0;
  /** Sulfur content in weight percent (daf). */
  private double sulfurWt = 0.0;
  /** Chlorine content in weight percent (daf). */
  private double chlorineWt = 0.0;

  // ── Derived properties (populated by calculate()) ──
  /** Higher heating value in MJ/kg (dry-ash-free). */
  private double hhv = Double.NaN;
  /** Whether HHV has been explicitly set by the user. */
  private boolean hhvUserOverride = false;
  /** Lower heating value in MJ/kg (dry-ash-free). */
  private double lhv = Double.NaN;
  /** Stoichiometric air requirement in kg-air / kg-fuel (dry basis). */
  private double stoichiometricAir = Double.NaN;
  /** Empirical chemical formula string, e.g. "CH1.46O0.64N0.005S0.001". */
  private String chemicalFormula = "";
  /** Molar H/C ratio. */
  private double hcRatio = Double.NaN;
  /** Molar O/C ratio. */
  private double ocRatio = Double.NaN;

  /** Whether calculate() has been called. */
  private boolean calculated = false;

  // ── Atomic masses ──
  private static final double MW_C = 12.011;
  private static final double MW_H = 1.008;
  private static final double MW_O = 15.999;
  private static final double MW_N = 14.007;
  private static final double MW_S = 32.065;
  private static final double MW_CL = 35.453;

  /**
   * Constructs a new BiomassCharacterization with the given name.
   *
   * @param name feedstock name
   */
  public BiomassCharacterization(String name) {
    this.name = name;
  }

  /**
   * Sets the proximate analysis on a dry basis.
   *
   * @param moisturePercent moisture content (wt%, as-received)
   * @param volatileMatterPercent volatile matter (wt%, dry basis)
   * @param fixedCarbonPercent fixed carbon (wt%, dry basis)
   * @param ashPercent ash content (wt%, dry basis)
   */
  public void setProximateAnalysis(double moisturePercent, double volatileMatterPercent,
      double fixedCarbonPercent, double ashPercent) {
    this.moisture = moisturePercent;
    this.volatileMatter = volatileMatterPercent;
    this.fixedCarbon = fixedCarbonPercent;
    this.ash = ashPercent;
    this.calculated = false;
  }

  /**
   * Sets the ultimate analysis on a dry-ash-free basis.
   *
   * @param carbonPercent carbon (wt%, daf)
   * @param hydrogenPercent hydrogen (wt%, daf)
   * @param oxygenPercent oxygen (wt%, daf)
   * @param nitrogenPercent nitrogen (wt%, daf)
   * @param sulfurPercent sulfur (wt%, daf)
   * @param chlorinePercent chlorine (wt%, daf)
   */
  public void setUltimateAnalysis(double carbonPercent, double hydrogenPercent,
      double oxygenPercent, double nitrogenPercent, double sulfurPercent, double chlorinePercent) {
    this.carbonWt = carbonPercent;
    this.hydrogenWt = hydrogenPercent;
    this.oxygenWt = oxygenPercent;
    this.nitrogenWt = nitrogenPercent;
    this.sulfurWt = sulfurPercent;
    this.chlorineWt = chlorinePercent;
    this.calculated = false;
  }

  /**
   * Calculates all derived properties from the proximate and ultimate analysis.
   */
  public void calculate() {
    if (!hhvUserOverride) {
      calculateHHV();
    }
    calculateLHV();
    calculateChemicalFormula();
    calculateStoichiometricAir();
    this.calculated = true;
  }

  /**
   * Estimates higher heating value using the Channiwala-Parikh correlation.
   *
   * <p>
   * HHV (MJ/kg, daf) = 0.3491 C + 1.1783 H + 0.1005 S - 0.1034 O - 0.0151 N - 0.0211 Ash_daf
   * </p>
   */
  private void calculateHHV() {
    double ashDaf = ash / (100.0 - moisture) * 100.0;
    hhv = 0.3491 * carbonWt + 1.1783 * hydrogenWt + 0.1005 * sulfurWt - 0.1034 * oxygenWt
        - 0.0151 * nitrogenWt - 0.0211 * ashDaf;
  }

  /**
   * Calculates lower heating value from HHV.
   *
   * <p>
   * LHV = HHV - 0.2183 * H (accounts for latent heat of water formed from hydrogen combustion)
   * </p>
   */
  private void calculateLHV() {
    if (Double.isNaN(hhv)) {
      calculateHHV();
    }
    lhv = hhv - 0.2183 * hydrogenWt;
  }

  /**
   * Builds the empirical chemical formula (normalised to 1 mole of carbon).
   */
  private void calculateChemicalFormula() {
    if (carbonWt <= 0.0) {
      chemicalFormula = "undefined";
      hcRatio = Double.NaN;
      ocRatio = Double.NaN;
      return;
    }
    double nC = carbonWt / MW_C;
    double nH = hydrogenWt / MW_H;
    double nO = oxygenWt / MW_O;
    double nN = nitrogenWt / MW_N;
    double nS = sulfurWt / MW_S;

    hcRatio = nH / nC;
    ocRatio = nO / nC;
    double nsRatio = nS / nC;
    double nnRatio = nN / nC;

    StringBuilder sb = new StringBuilder("CH");
    sb.append(String.format("%.3f", hcRatio));
    sb.append("O").append(String.format("%.3f", ocRatio));
    if (nnRatio > 1e-6) {
      sb.append("N").append(String.format("%.4f", nnRatio));
    }
    if (nsRatio > 1e-6) {
      sb.append("S").append(String.format("%.5f", nsRatio));
    }
    chemicalFormula = sb.toString();
  }

  /**
   * Calculates the stoichiometric air requirement (kg air per kg fuel, dry basis).
   *
   * <p>
   * Based on the stoichiometry: C + O2 -&gt; CO2, H2 + 0.5 O2 -&gt; H2O, S + O2 -&gt; SO2, less
   * oxygen already present in biomass, divided by oxygen mass fraction in air (0.233).
   * </p>
   */
  private void calculateStoichiometricAir() {
    double factor = (100.0 - moisture) * (100.0 - ash) / 10000.0;
    double o2Required = ((carbonWt / MW_C) * MW_O * 2.0 + (hydrogenWt / MW_H / 2.0) * MW_O * 2.0
        + (sulfurWt / MW_S) * MW_O * 2.0 - oxygenWt) / 100.0;
    o2Required = o2Required * factor;
    stoichiometricAir = Math.max(0.0, o2Required / 0.233);
  }

  // ── Getters ──

  /**
   * Returns the feedstock name.
   *
   * @return feedstock name
   */
  public String getName() {
    return name;
  }

  /**
   * Returns the moisture content (wt%, as-received).
   *
   * @return moisture content in percent
   */
  public double getMoisture() {
    return moisture;
  }

  /**
   * Returns the volatile matter (wt%, dry basis).
   *
   * @return volatile matter in percent
   */
  public double getVolatileMatter() {
    return volatileMatter;
  }

  /**
   * Returns the fixed carbon (wt%, dry basis).
   *
   * @return fixed carbon in percent
   */
  public double getFixedCarbon() {
    return fixedCarbon;
  }

  /**
   * Returns the ash content (wt%, dry basis).
   *
   * @return ash content in percent
   */
  public double getAsh() {
    return ash;
  }

  /**
   * Returns the carbon content (wt%, daf).
   *
   * @return carbon in percent
   */
  public double getCarbonWt() {
    return carbonWt;
  }

  /**
   * Returns the hydrogen content (wt%, daf).
   *
   * @return hydrogen in percent
   */
  public double getHydrogenWt() {
    return hydrogenWt;
  }

  /**
   * Returns the oxygen content (wt%, daf).
   *
   * @return oxygen in percent
   */
  public double getOxygenWt() {
    return oxygenWt;
  }

  /**
   * Returns the nitrogen content (wt%, daf).
   *
   * @return nitrogen in percent
   */
  public double getNitrogenWt() {
    return nitrogenWt;
  }

  /**
   * Returns the sulfur content (wt%, daf).
   *
   * @return sulfur in percent
   */
  public double getSulfurWt() {
    return sulfurWt;
  }

  /**
   * Returns the chlorine content (wt%, daf).
   *
   * @return chlorine in percent
   */
  public double getChlorineWt() {
    return chlorineWt;
  }

  /**
   * Returns the higher heating value (MJ/kg, daf).
   *
   * @return HHV in MJ/kg
   */
  public double getHHV() {
    ensureCalculated();
    return hhv;
  }

  /**
   * Returns the lower heating value (MJ/kg, daf).
   *
   * @return LHV in MJ/kg
   */
  public double getLHV() {
    ensureCalculated();
    return lhv;
  }

  /**
   * Returns the stoichiometric air requirement (kg air / kg dry fuel).
   *
   * @return stoichiometric air in kg/kg
   */
  public double getStoichiometricAir() {
    ensureCalculated();
    return stoichiometricAir;
  }

  /**
   * Returns the empirical chemical formula string (normalised to 1 C atom).
   *
   * @return chemical formula string
   */
  public String getChemicalFormula() {
    ensureCalculated();
    return chemicalFormula;
  }

  /**
   * Returns the molar H/C ratio.
   *
   * @return H/C ratio
   */
  public double getHCRatio() {
    ensureCalculated();
    return hcRatio;
  }

  /**
   * Returns the molar O/C ratio.
   *
   * @return O/C ratio
   */
  public double getOCRatio() {
    ensureCalculated();
    return ocRatio;
  }

  /**
   * Returns true if {@link #calculate()} has been called.
   *
   * @return true if calculated
   */
  public boolean isCalculated() {
    return calculated;
  }

  /**
   * Sets the HHV directly (overrides the Channiwala-Parikh estimate).
   *
   * @param hhvMjPerKg higher heating value in MJ/kg (daf)
   */
  public void setHHV(double hhvMjPerKg) {
    this.hhv = hhvMjPerKg;
    this.hhvUserOverride = true;
  }

  /**
   * Sets the LHV directly (overrides the calculated value).
   *
   * @param lhvMjPerKg lower heating value in MJ/kg (daf)
   */
  public void setLHV(double lhvMjPerKg) {
    this.lhv = lhvMjPerKg;
  }

  /**
   * Ensures calculate() has been called; calls it automatically if not.
   */
  private void ensureCalculated() {
    if (!calculated) {
      calculate();
    }
  }

  /**
   * Returns a map summarising the characterization results.
   *
   * @return map of property name to value
   */
  public Map<String, Double> toMap() {
    ensureCalculated();
    Map<String, Double> map = new LinkedHashMap<String, Double>();
    map.put("moisture_wt%", moisture);
    map.put("volatileMatter_wt%_db", volatileMatter);
    map.put("fixedCarbon_wt%_db", fixedCarbon);
    map.put("ash_wt%_db", ash);
    map.put("C_wt%_daf", carbonWt);
    map.put("H_wt%_daf", hydrogenWt);
    map.put("O_wt%_daf", oxygenWt);
    map.put("N_wt%_daf", nitrogenWt);
    map.put("S_wt%_daf", sulfurWt);
    map.put("Cl_wt%_daf", chlorineWt);
    map.put("HHV_MJ/kg_daf", hhv);
    map.put("LHV_MJ/kg_daf", lhv);
    map.put("stoichiometricAir_kg/kg", stoichiometricAir);
    map.put("H/C_molar", hcRatio);
    map.put("O/C_molar", ocRatio);
    return map;
  }

  /** {@inheritDoc} */
  @Override
  public String toString() {
    ensureCalculated();
    StringBuilder sb = new StringBuilder();
    sb.append("BiomassCharacterization: ").append(name).append("\n");
    sb.append("  Formula: ").append(chemicalFormula).append("\n");
    sb.append(String.format("  HHV = %.2f MJ/kg (daf)%n", hhv));
    sb.append(String.format("  LHV = %.2f MJ/kg (daf)%n", lhv));
    sb.append(String.format("  Stoich. air = %.2f kg/kg%n", stoichiometricAir));
    return sb.toString();
  }

  // ── Library of common biomass feedstocks ──

  /**
   * Returns a pre-configured BiomassCharacterization for a common feedstock.
   *
   * <p>
   * Supported names (case-insensitive):
   * </p>
   * <ul>
   * <li>wood_chips — softwood chips (pine/spruce)</li>
   * <li>straw — wheat/barley straw</li>
   * <li>corn_stover — dried maize residue</li>
   * <li>bagasse — sugarcane bagasse</li>
   * <li>sewage_sludge — dried municipal sewage sludge</li>
   * <li>msw — municipal solid waste (generic)</li>
   * <li>microalgae — generic Chlorella-type microalgae</li>
   * <li>rice_husk — rice husk</li>
   * </ul>
   *
   * @param feedstockName name of feedstock (case-insensitive)
   * @return configured BiomassCharacterization
   * @throws IllegalArgumentException if feedstock name is unknown
   */
  public static BiomassCharacterization library(String feedstockName) {
    String key = feedstockName.trim().toLowerCase().replace(' ', '_').replace('-', '_');
    BiomassCharacterization bc = new BiomassCharacterization(feedstockName);
    switch (key) {
      case "wood_chips":
        bc.setProximateAnalysis(25.0, 82.0, 17.0, 1.0);
        bc.setUltimateAnalysis(51.0, 6.1, 42.0, 0.3, 0.05, 0.01);
        break;
      case "straw":
        bc.setProximateAnalysis(10.0, 78.0, 16.0, 6.0);
        bc.setUltimateAnalysis(47.0, 5.8, 41.0, 0.8, 0.15, 0.3);
        break;
      case "corn_stover":
        bc.setProximateAnalysis(7.0, 80.0, 15.0, 5.0);
        bc.setUltimateAnalysis(47.5, 5.9, 40.5, 0.7, 0.1, 0.1);
        break;
      case "bagasse":
        bc.setProximateAnalysis(50.0, 84.0, 14.0, 2.0);
        bc.setUltimateAnalysis(48.6, 5.9, 44.5, 0.3, 0.05, 0.02);
        break;
      case "sewage_sludge":
        bc.setProximateAnalysis(5.0, 52.0, 8.0, 40.0);
        bc.setUltimateAnalysis(51.5, 7.3, 30.5, 7.5, 1.5, 0.1);
        break;
      case "msw":
        bc.setProximateAnalysis(20.0, 70.0, 10.0, 20.0);
        bc.setUltimateAnalysis(48.0, 6.0, 38.0, 1.2, 0.3, 0.5);
        break;
      case "microalgae":
        bc.setProximateAnalysis(5.0, 75.0, 15.0, 10.0);
        bc.setUltimateAnalysis(50.0, 7.0, 30.0, 8.0, 0.8, 0.0);
        break;
      case "rice_husk":
        bc.setProximateAnalysis(8.0, 65.0, 16.0, 19.0);
        bc.setUltimateAnalysis(49.0, 6.1, 43.5, 0.5, 0.05, 0.05);
        break;
      default:
        throw new IllegalArgumentException("Unknown biomass feedstock: '" + feedstockName
            + "'. Supported: wood_chips, straw, corn_stover, bagasse, sewage_sludge, msw, "
            + "microalgae, rice_husk");
    }
    bc.calculate();
    return bc;
  }

  /**
   * Returns an unmodifiable list of supported library feedstock names.
   *
   * @return list of feedstock names
   */
  public static java.util.List<String> getLibraryFeedstocks() {
    return Collections.unmodifiableList(Arrays.asList("wood_chips", "straw", "corn_stover",
        "bagasse", "sewage_sludge", "msw", "microalgae", "rice_husk"));
  }
}
