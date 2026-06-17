package neqsim.thermo.util.amines;

import java.util.HashMap;
import java.util.Map;

/**
 * Calculates the heat of absorption for CO2 and H2S in aqueous amine solutions.
 *
 * <p>
 * Provides correlations for the exothermic heat released when acid gases (CO2, H2S) are absorbed
 * into amine solutions. The heat of absorption depends on the amine type, temperature, CO2 loading,
 * and amine concentration.
 * </p>
 *
 * <p>
 * References:
 * </p>
 * <ul>
 * <li>Kim, I., Svendsen, H.F. (2007). Heat of Absorption of Carbon Dioxide (CO2) in
 * Monoethanolamine (MEA) and 2-(Aminoethyl)ethanolamine (AEEA) Solutions. Ind. Eng. Chem. Res., 46,
 * 5803-5809.</li>
 * <li>Carson, J.K., Marsh, K.N., Mather, A.E. (2000). Enthalpy of solution of carbon dioxide in
 * (water + MEA or DEA or MDEA) and (water + MEA + MDEA) at T = 298.15 K. J. Chem. Thermodyn., 32,
 * 1285-1296.</li>
 * <li>Arcis, H., Rodier, L., Coxam, J.Y. (2007). Enthalpy of solution of CO2 in aqueous solutions
 * of MDEA at T = 322.5 K and pressures up to 5 MPa. J. Chem. Thermodyn., 39, 878-887.</li>
 * </ul>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class AmineHeatOfAbsorption implements java.io.Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * Supported amine types for heat of absorption calculation.
   */
  public enum AmineType {
    /** Monoethanolamine (primary amine). */
    MEA("MEA", 61.08, 0.5),
    /** Diethanolamine (secondary amine). */
    DEA("DEA", 105.14, 0.5),
    /** Methyldiethanolamine (tertiary amine). */
    MDEA("MDEA", 119.16, 1.0),
    /** Activated MDEA (MDEA + Piperazine blend). */
    AMDEA("aMDEA", 119.16, 0.8);

    private final String name;
    private final double molarMass;
    private final double maxLoading;

    /**
     * Constructor for AmineType enum.
     *
     * @param name display name
     * @param molarMass molar mass in g/mol
     * @param maxLoading maximum practical CO2 loading (mol CO2/mol amine)
     */
    AmineType(String name, double molarMass, double maxLoading) {
      this.name = name;
      this.molarMass = molarMass;
      this.maxLoading = maxLoading;
    }

    /**
     * Gets the display name of this amine type.
     *
     * @return display name
     */
    public String getName() {
      return name;
    }

    /**
     * Gets the molar mass in g/mol.
     *
     * @return molar mass in g/mol
     */
    public double getMolarMass() {
      return molarMass;
    }

    /**
     * Gets the maximum practical CO2 loading.
     *
     * @return maximum loading in mol CO2/mol amine
     */
    public double getMaxLoading() {
      return maxLoading;
    }
  }

  private AmineType amineType = AmineType.MEA;
  private double amineConcentration = 0.30; // mass fraction
  private double co2Loading = 0.0; // mol CO2 / mol amine
  private double temperature = 313.15; // Kelvin

  /**
   * Constructor for AmineHeatOfAbsorption.
   */
  public AmineHeatOfAbsorption() {}

  /**
   * Constructor for AmineHeatOfAbsorption with parameters.
   *
   * @param amineType the type of amine
   * @param amineConcentration mass fraction of amine in solution (0 to 1)
   * @param co2Loading CO2 loading in mol CO2 / mol amine
   * @param temperatureK temperature in Kelvin
   */
  public AmineHeatOfAbsorption(AmineType amineType, double amineConcentration, double co2Loading,
      double temperatureK) {
    this.amineType = amineType;
    this.amineConcentration = amineConcentration;
    this.co2Loading = co2Loading;
    this.temperature = temperatureK;
  }

  /**
   * Calculates the differential heat of absorption of CO2 in kJ/mol CO2.
   *
   * <p>
   * The differential heat of absorption is the enthalpy change per mole of CO2 absorbed at a given
   * loading. This is negative (exothermic) for all amine types.
   * </p>
   *
   * @return heat of absorption in kJ/mol CO2 (negative for exothermic)
   */
  public double calcHeatOfAbsorptionCO2() {
    switch (amineType) {
      case MEA:
        return calcMEAHeatOfAbsorption();
      case DEA:
        return calcDEAHeatOfAbsorption();
      case MDEA:
        return calcMDEAHeatOfAbsorption();
      case AMDEA:
        return calcAMDEAHeatOfAbsorption();
      default:
        return calcMEAHeatOfAbsorption();
    }
  }

  /**
   * Calculates the integral (total) heat released when absorbing CO2 to the current loading.
   *
   * @return total heat released in kJ per mol amine (positive = heat released)
   */
  public double calcTotalHeatReleased() {
    int nSteps = 50;
    double dAlpha = co2Loading / nSteps;
    double totalHeat = 0.0;
    double savedLoading = this.co2Loading;

    for (int i = 0; i < nSteps; i++) {
      this.co2Loading = (i + 0.5) * dAlpha;
      totalHeat += Math.abs(calcHeatOfAbsorptionCO2()) * dAlpha;
    }

    this.co2Loading = savedLoading;
    return totalHeat;
  }

  /**
   * Calculates the heat of absorption of H2S in kJ/mol H2S.
   *
   * <p>
   * H2S absorption is less exothermic than CO2 for all amine types. The heat is primarily from the
   * H2S ionization reaction.
   * </p>
   *
   * @return heat of absorption in kJ/mol H2S (negative for exothermic)
   */
  public double calcHeatOfAbsorptionH2S() {
    // H2S reaction heat is relatively constant across amine types
    // H2S + amine -> amineH+ + HS-
    // Typical value from Kohl and Nielsen (1997)
    double baseHeat = -29.0; // kJ/mol H2S at 298 K
    double T = this.temperature;
    // Temperature correction: Kirchhoff equation approximation
    double deltaCp = -40.0; // J/(mol.K) typical for H2S absorption
    double heatT = baseHeat + deltaCp * (T - 298.15) / 1000.0;
    return heatT;
  }

  /**
   * Calculates the heat of absorption of CO2 in MEA solution.
   *
   * <p>
   * Uses the Kim and Svendsen (2007) correlation for 30 wt% MEA, with corrections for concentration
   * and loading effects. MEA absorbs CO2 through both carbamate formation (fast, exothermic) at low
   * loadings and bicarbonate formation at higher loadings.
   * </p>
   *
   * @return heat of absorption in kJ/mol CO2 (negative for exothermic)
   */
  private double calcMEAHeatOfAbsorption() {
    double T = this.temperature;
    double alpha = this.co2Loading;

    // Kim & Svendsen (2007): Heat of absorption for 30 wt% MEA
    // -Habs = 85 kJ/mol at low loading, decreasing to ~55 kJ/mol at alpha=0.5
    // Base heat at 313.15 K (40 C), 30 wt%
    double baseHeat;
    if (alpha < 0.4) {
      // Carbamate formation dominant: MEA + CO2 -> MEACOO- + H+
      baseHeat = -84.0 + 20.0 * alpha;
    } else {
      // Bicarbonate formation becomes important
      baseHeat = -76.0 + 42.0 * (alpha - 0.4);
    }

    // Temperature correction: d(Habs)/dT ~ 0.06 kJ/(mol.K)
    double tempCorr = 0.06 * (T - 313.15);

    // Concentration correction (relative to 30 wt%)
    double concCorr = 1.0 + 0.3 * (amineConcentration - 0.30);

    return baseHeat * concCorr + tempCorr;
  }

  /**
   * Calculates the heat of absorption of CO2 in DEA solution.
   *
   * <p>
   * DEA forms both carbamate and protonated species. Heat is intermediate between MEA and MDEA.
   * Based on Carson et al. (2000) measurements.
   * </p>
   *
   * @return heat of absorption in kJ/mol CO2 (negative for exothermic)
   */
  private double calcDEAHeatOfAbsorption() {
    double T = this.temperature;
    double alpha = this.co2Loading;

    // Carson et al. (2000): Heat of absorption for DEA solutions
    double baseHeat;
    if (alpha < 0.35) {
      baseHeat = -72.0 + 15.0 * alpha;
    } else {
      baseHeat = -66.8 + 35.0 * (alpha - 0.35);
    }

    double tempCorr = 0.05 * (T - 298.15);
    double concCorr = 1.0 + 0.25 * (amineConcentration - 0.30);

    return baseHeat * concCorr + tempCorr;
  }

  /**
   * Calculates the heat of absorption of CO2 in MDEA solution.
   *
   * <p>
   * MDEA is a tertiary amine: no carbamate formation. CO2 reacts only via the base-catalyzed
   * hydration mechanism, resulting in lower heat of absorption compared to primary/secondary
   * amines. Based on Arcis et al. (2007) and Carson et al. (2000).
   * </p>
   *
   * @return heat of absorption in kJ/mol CO2 (negative for exothermic)
   */
  private double calcMDEAHeatOfAbsorption() {
    double T = this.temperature;
    double alpha = this.co2Loading;

    // MDEA: base-catalyzed hydration only
    // MDEA + CO2 + H2O -> MDEAH+ + HCO3-
    double baseHeat = -55.0 + 20.0 * alpha;

    double tempCorr = 0.04 * (T - 298.15);
    double concCorr = 1.0 + 0.2 * (amineConcentration - 0.50);

    return baseHeat * concCorr + tempCorr;
  }

  /**
   * Calculates the heat of absorption of CO2 in activated MDEA (aMDEA) solution.
   *
   * <p>
   * Activated MDEA blends MDEA with piperazine (PZ). The piperazine provides fast kinetics through
   * carbamate formation while MDEA provides high capacity. The heat of absorption is intermediate
   * between MEA and MDEA, depending on the PZ/MDEA ratio.
   * </p>
   *
   * @return heat of absorption in kJ/mol CO2 (negative for exothermic)
   */
  private double calcAMDEAHeatOfAbsorption() {
    // Weighted contribution: assume ~5 wt% PZ in 45 wt% MDEA
    double pzFraction = 0.1; // approximate PZ/(PZ+MDEA) weight ratio
    double heatMDEA = calcMDEAHeatOfAbsorption();

    // Piperazine carbamate formation: ~70 kJ/mol CO2
    double heatPZ = -70.0 + 15.0 * co2Loading;
    double tempCorr = 0.05 * (temperature - 298.15);
    heatPZ += tempCorr;

    return (1.0 - pzFraction) * heatMDEA + pzFraction * heatPZ;
  }

  /**
   * Generates a summary map of absorption properties for the current conditions.
   *
   * @return map containing key-value pairs of absorption properties
   */
  public Map<String, Double> getAbsorptionProperties() {
    Map<String, Double> props = new HashMap<String, Double>();
    props.put("amineType", (double) amineType.ordinal());
    props.put("temperature_K", temperature);
    props.put("temperature_C", temperature - 273.15);
    props.put("amineConcentration_wt", amineConcentration);
    props.put("CO2Loading_molmol", co2Loading);
    props.put("heatOfAbsorptionCO2_kJmol", calcHeatOfAbsorptionCO2());
    props.put("heatOfAbsorptionH2S_kJmol", calcHeatOfAbsorptionH2S());
    props.put("totalHeatReleased_kJmolAmine", calcTotalHeatReleased());
    props.put("maxLoading_molmol", amineType.getMaxLoading());
    return props;
  }

  /**
   * Sets the amine type.
   *
   * @param amineType the amine type to set
   */
  public void setAmineType(AmineType amineType) {
    this.amineType = amineType;
  }

  /**
   * Gets the amine type.
   *
   * @return the current amine type
   */
  public AmineType getAmineType() {
    return amineType;
  }

  /**
   * Sets the amine concentration as mass fraction.
   *
   * @param amineConcentration mass fraction (0 to 1, e.g., 0.30 for 30 wt%)
   */
  public void setAmineConcentration(double amineConcentration) {
    this.amineConcentration = amineConcentration;
  }

  /**
   * Gets the amine concentration as mass fraction.
   *
   * @return mass fraction of amine
   */
  public double getAmineConcentration() {
    return amineConcentration;
  }

  /**
   * Sets the CO2 loading in mol CO2 per mol amine.
   *
   * @param co2Loading the CO2 loading (dimensionless)
   */
  public void setCO2Loading(double co2Loading) {
    this.co2Loading = co2Loading;
  }

  /**
   * Gets the CO2 loading.
   *
   * @return CO2 loading in mol CO2 / mol amine
   */
  public double getCO2Loading() {
    return co2Loading;
  }

  /**
   * Sets the temperature in Kelvin.
   *
   * @param temperatureK temperature in Kelvin
   */
  public void setTemperature(double temperatureK) {
    this.temperature = temperatureK;
  }

  /**
   * Gets the temperature in Kelvin.
   *
   * @return temperature in Kelvin
   */
  public double getTemperature() {
    return temperature;
  }
}
