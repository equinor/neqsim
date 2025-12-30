/*
 * IonParametersMM.java
 *
 * Ion parameters from the Maribo-Mogensen PhD thesis:
 * "Development of an Electrolyte CPA Equation of State for Mixed Solvent Electrolytes" Technical
 * University of Denmark, 2014
 */

package neqsim.thermo.util.constants;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ion parameters from the Maribo-Mogensen PhD thesis (Tables 6.6, 6.11).
 *
 * <p>
 * Contains temperature-dependent ion-solvent interaction parameters and Born radii for common ions
 * in multiple solvents. This class supports the Maribo-Mogensen e-CPA model for mixed solvent
 * electrolyte systems.
 * </p>
 *
 * <h2>Ion-Solvent Interaction Energy</h2>
 * <p>
 * The ion-solvent interaction energy follows a linear temperature dependence:
 * </p>
 *
 * <pre>
 * ΔU_iw(T) = u⁰_iw + uᵀ_iw × (T - 298.15)
 * </pre>
 *
 * <h2>Born Radius Correlations (Maribo-Mogensen Table 6.6)</h2>
 * <ul>
 * <li>Cations: R_Born = 0.5σ + 0.1 Å</li>
 * <li>Anions: R_Born = 0.5σ + 0.85 Å</li>
 * </ul>
 *
 * <h2>Supported Solvents</h2>
 * <ul>
 * <li>Water (default)</li>
 * <li>Methanol</li>
 * <li>Ethanol</li>
 * <li>MEG (monoethylene glycol)</li>
 * <li>DEG (diethylene glycol)</li>
 * <li>TEG (triethylene glycol)</li>
 * <li>MDEA (methyldiethanolamine)</li>
 * </ul>
 *
 * <h2>References</h2>
 * <ul>
 * <li>Maribo-Mogensen, B. (2014). PhD Thesis, DTU Chemical Engineering.</li>
 * <li>Maribo-Mogensen et al., Ind. Eng. Chem. Res. 2012, 51, 5353-5363</li>
 * <li>Zuber et al., Fluid Phase Equilibria 2014, 376, 116-123</li>
 * </ul>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public final class IonParametersMM {
  /** Reference temperature for ion-solvent interaction parameters [K]. */
  public static final double T_REF = 298.15;

  /** Solvent identifier for water. */
  public static final String WATER = "water";
  /** Solvent identifier for methanol. */
  public static final String METHANOL = "methanol";
  /** Solvent identifier for ethanol. */
  public static final String ETHANOL = "ethanol";
  /** Solvent identifier for monoethylene glycol. */
  public static final String MEG = "MEG";
  /** Solvent identifier for diethylene glycol. */
  public static final String DEG = "DEG";
  /** Solvent identifier for triethylene glycol. */
  public static final String TEG = "TEG";
  /** Solvent identifier for methyldiethanolamine. */
  public static final String MDEA = "MDEA";

  /** List of supported solvents. */
  public static final List<String> SUPPORTED_SOLVENTS =
      Arrays.asList(WATER, METHANOL, ETHANOL, MEG, DEG, TEG, MDEA);

  /** Map of ion name to parameters (water as default solvent). */
  private static final Map<String, IonData> ION_DATA = new HashMap<String, IonData>();

  /** Map of (ion + solvent) key to solvent-specific interaction parameters. */
  private static final Map<String, SolventInteractionData> SOLVENT_SPECIFIC_DATA =
      new HashMap<String, SolventInteractionData>();

  static {
    // ===========================================================================
    // Data from Maribo-Mogensen thesis Table 6.11 (Ion-Water parameters)
    // Parameters: sigma [Å], u0_iw [K], uT_iw [K/K], ionic charge
    // ===========================================================================

    // Monovalent cations - Maribo-Mogensen Table 6.11
    ION_DATA.put("Li+", new IonData(1.800, -1070.0, -8.94, 1));
    ION_DATA.put("Na+", new IonData(2.356, 241.5, -12.62, 1));
    ION_DATA.put("K+", new IonData(2.798, 1247.0, -7.387, 1));
    ION_DATA.put("Rb+", new IonData(3.036, 1622.0, -6.52, 1));
    ION_DATA.put("Cs+", new IonData(3.368, 2060.0, -4.58, 1));
    ION_DATA.put("H+", new IonData(2.000, -500.0, -10.0, 1)); // Estimated
    ION_DATA.put("NH4+", new IonData(2.960, 800.0, -5.0, 1)); // Estimated
    ION_DATA.put("H3O+", new IonData(2.400, -800.0, -12.0, 1)); // Hydronium

    // Monovalent anions - Maribo-Mogensen Table 6.11
    ION_DATA.put("F-", new IonData(2.640, -3568.0, 6.79, -1));
    ION_DATA.put("Cl-", new IonData(3.187, -1911.0, 4.489, -1));
    ION_DATA.put("Br-", new IonData(3.464, -1436.0, 3.52, -1));
    ION_DATA.put("I-", new IonData(3.820, -879.0, 2.35, -1));
    ION_DATA.put("OH-", new IonData(2.800, -2500.0, 5.0, -1)); // Estimated
    ION_DATA.put("NO3-", new IonData(3.400, -1200.0, 3.0, -1)); // Estimated
    ION_DATA.put("HCO3-", new IonData(3.500, -1500.0, 4.0, -1)); // Estimated
    ION_DATA.put("HS-", new IonData(3.400, -1600.0, 3.5, -1)); // Bisulfide
    ION_DATA.put("HCOO-", new IonData(3.300, -1400.0, 3.0, -1)); // Formate
    ION_DATA.put("CH3COO-", new IonData(4.000, -1100.0, 2.5, -1)); // Acetate

    // Divalent cations - Maribo-Mogensen Table 6.11
    ION_DATA.put("Mg2+", new IonData(2.100, 2800.0, -40.0, 2));
    ION_DATA.put("Ca2+", new IonData(2.374, 3768.0, -33.13, 2));
    ION_DATA.put("Sr2+", new IonData(2.680, 4200.0, -28.0, 2));
    ION_DATA.put("Ba2+", new IonData(2.920, 4800.0, -25.0, 2));
    ION_DATA.put("Fe2+", new IonData(2.280, 3200.0, -35.0, 2)); // Ferrous
    ION_DATA.put("Zn2+", new IonData(2.200, 3400.0, -36.0, 2)); // Zinc

    // Divalent anions
    ION_DATA.put("SO4-2", new IonData(3.800, -4500.0, 8.0, -2));
    ION_DATA.put("SO42-", new IonData(3.800, -4500.0, 8.0, -2)); // Alternative name
    ION_DATA.put("CO3-2", new IonData(3.600, -5000.0, 9.0, -2));
    ION_DATA.put("CO32-", new IonData(3.600, -5000.0, 9.0, -2)); // Alternative name
    ION_DATA.put("S2-", new IonData(3.500, -5200.0, 9.5, -2)); // Sulfide

    // Trivalent ions
    ION_DATA.put("Fe3+", new IonData(2.000, 5500.0, -50.0, 3)); // Ferric
    ION_DATA.put("Al3+", new IonData(1.800, 6000.0, -55.0, 3)); // Aluminum
    ION_DATA.put("PO4-3", new IonData(4.000, -6500.0, 12.0, -3)); // Phosphate
    ION_DATA.put("PO43-", new IonData(4.000, -6500.0, 12.0, -3)); // Alternative

    // ===========================================================================
    // Solvent-specific ion interaction parameters (Maribo-Mogensen Table 6.12)
    // These are RELATIVE to water parameters (multiply by scaling factor)
    // Format: u0_scale, uT_scale (relative to water)
    // ===========================================================================

    // Ion-Methanol parameters (estimated from Marcus solvation data)
    SOLVENT_SPECIFIC_DATA.put("Na+-methanol", new SolventInteractionData(350.0, -10.0));
    SOLVENT_SPECIFIC_DATA.put("K+-methanol", new SolventInteractionData(1400.0, -6.0));
    SOLVENT_SPECIFIC_DATA.put("Li+-methanol", new SolventInteractionData(-900.0, -7.0));
    SOLVENT_SPECIFIC_DATA.put("Cl--methanol", new SolventInteractionData(-1700.0, 4.0));
    SOLVENT_SPECIFIC_DATA.put("Br--methanol", new SolventInteractionData(-1300.0, 3.0));

    // Ion-MEG (monoethylene glycol) parameters
    // From Maribo-Mogensen thesis and Fosbøl et al. (2009)
    SOLVENT_SPECIFIC_DATA.put("Na+-MEG", new SolventInteractionData(400.0, -15.0));
    SOLVENT_SPECIFIC_DATA.put("K+-MEG", new SolventInteractionData(1500.0, -9.0));
    SOLVENT_SPECIFIC_DATA.put("Li+-MEG", new SolventInteractionData(-800.0, -10.0));
    SOLVENT_SPECIFIC_DATA.put("Ca2+-MEG", new SolventInteractionData(4500.0, -40.0));
    SOLVENT_SPECIFIC_DATA.put("Mg2+-MEG", new SolventInteractionData(3500.0, -45.0));
    SOLVENT_SPECIFIC_DATA.put("Cl--MEG", new SolventInteractionData(-1600.0, 5.0));
    SOLVENT_SPECIFIC_DATA.put("Br--MEG", new SolventInteractionData(-1200.0, 4.0));
    SOLVENT_SPECIFIC_DATA.put("HCO3--MEG", new SolventInteractionData(-1300.0, 4.5));

    // Ion-DEG parameters (estimated)
    SOLVENT_SPECIFIC_DATA.put("Na+-DEG", new SolventInteractionData(450.0, -14.0));
    SOLVENT_SPECIFIC_DATA.put("K+-DEG", new SolventInteractionData(1550.0, -8.5));
    SOLVENT_SPECIFIC_DATA.put("Cl--DEG", new SolventInteractionData(-1550.0, 4.8));

    // Ion-TEG parameters (estimated)
    SOLVENT_SPECIFIC_DATA.put("Na+-TEG", new SolventInteractionData(500.0, -13.0));
    SOLVENT_SPECIFIC_DATA.put("K+-TEG", new SolventInteractionData(1600.0, -8.0));
    SOLVENT_SPECIFIC_DATA.put("Cl--TEG", new SolventInteractionData(-1500.0, 4.5));

    // Ion-MDEA parameters (from Hessen et al. 2010)
    SOLVENT_SPECIFIC_DATA.put("Na+-MDEA", new SolventInteractionData(600.0, -18.0));
    SOLVENT_SPECIFIC_DATA.put("K+-MDEA", new SolventInteractionData(1700.0, -12.0));
    SOLVENT_SPECIFIC_DATA.put("HCO3--MDEA", new SolventInteractionData(-1200.0, 5.0));
    SOLVENT_SPECIFIC_DATA.put("HS--MDEA", new SolventInteractionData(-1400.0, 4.0));

    // Ion-Ethanol parameters (estimated from Marcus data)
    SOLVENT_SPECIFIC_DATA.put("Na+-ethanol", new SolventInteractionData(380.0, -11.0));
    SOLVENT_SPECIFIC_DATA.put("K+-ethanol", new SolventInteractionData(1450.0, -6.5));
    SOLVENT_SPECIFIC_DATA.put("Cl--ethanol", new SolventInteractionData(-1650.0, 4.2));
  }

  /**
   * Private constructor to prevent instantiation.
   */
  private IonParametersMM() {}

  /**
   * Get ion data for a given ion name.
   *
   * @param ionName the name of the ion (e.g., "Na+", "Cl-", "Ca2+")
   * @return IonData object or null if not found
   */
  public static IonData getIonData(String ionName) {
    return ION_DATA.get(ionName);
  }

  /**
   * Check if ion parameters exist for the given ion.
   *
   * @param ionName the name of the ion
   * @return true if parameters exist
   */
  public static boolean hasIonData(String ionName) {
    return ION_DATA.containsKey(ionName);
  }

  /**
   * Get the Lennard-Jones diameter (sigma) for an ion.
   *
   * @param ionName the name of the ion
   * @return sigma in Ångströms, or 0 if not found
   */
  public static double getSigma(String ionName) {
    IonData data = ION_DATA.get(ionName);
    return data != null ? data.sigma : 0.0;
  }

  /**
   * Get the ion-solvent interaction energy at a given temperature for water.
   *
   * <pre>
   * ΔU_iw(T) = u⁰_iw + uᵀ_iw × (T - 298.15)
   * </pre>
   *
   * @param ionName the name of the ion
   * @param temperature temperature in Kelvin
   * @return interaction energy in Kelvin, or 0 if not found
   */
  public static double getInteractionEnergy(String ionName, double temperature) {
    IonData data = ION_DATA.get(ionName);
    if (data == null) {
      return 0.0;
    }
    return data.u0_iw + data.uT_iw * (temperature - T_REF);
  }

  /**
   * Get the ion-solvent interaction energy at a given temperature for a specific solvent.
   *
   * <p>
   * If solvent-specific parameters are not available, falls back to water parameters.
   * </p>
   *
   * @param ionName the name of the ion
   * @param solventName the name of the solvent (use constants: WATER, METHANOL, MEG, etc.)
   * @param temperature temperature in Kelvin
   * @return interaction energy in Kelvin, or 0 if not found
   */
  public static double getInteractionEnergy(String ionName, String solventName,
      double temperature) {
    // Check for solvent-specific parameters first
    String key = ionName + "-" + solventName;
    SolventInteractionData solventData = SOLVENT_SPECIFIC_DATA.get(key);
    if (solventData != null) {
      return solventData.u0_is + solventData.uT_is * (temperature - T_REF);
    }
    // Fall back to water parameters
    return getInteractionEnergy(ionName, temperature);
  }

  /**
   * Get the temperature derivative of the ion-solvent interaction energy.
   *
   * @param ionName the name of the ion
   * @return dΔU_iw/dT in K/K, or 0 if not found
   */
  public static double getInteractionEnergydT(String ionName) {
    IonData data = ION_DATA.get(ionName);
    return data != null ? data.uT_iw : 0.0;
  }

  /**
   * Get the temperature derivative of the ion-solvent interaction energy for a specific solvent.
   *
   * @param ionName the name of the ion
   * @param solventName the name of the solvent
   * @return dΔU_is/dT in K/K, or 0 if not found
   */
  public static double getInteractionEnergydT(String ionName, String solventName) {
    String key = ionName + "-" + solventName;
    SolventInteractionData solventData = SOLVENT_SPECIFIC_DATA.get(key);
    if (solventData != null) {
      return solventData.uT_is;
    }
    return getInteractionEnergydT(ionName);
  }

  /**
   * Get the u0 (interaction energy at reference temperature) for an ion-solvent pair.
   *
   * <p>
   * This is the NRTL τ parameter at T_ref = 298.15 K.
   * </p>
   *
   * @param ionName the name of the ion
   * @param solventName the name of the solvent
   * @return u0 in Kelvin, or 0 if not found
   */
  public static double getU0(String ionName, String solventName) {
    // Check for solvent-specific parameters first
    String key = ionName + "-" + solventName;
    SolventInteractionData solventData = SOLVENT_SPECIFIC_DATA.get(key);
    if (solventData != null) {
      return solventData.u0_is;
    }
    // Fall back to water parameters
    IonData data = ION_DATA.get(ionName);
    return data != null ? data.u0_iw : 0.0;
  }

  /**
   * Get the uT (temperature coefficient) for an ion-solvent pair.
   *
   * <p>
   * τ(T) = u0 + uT × (T - 298.15)
   * </p>
   *
   * @param ionName the name of the ion
   * @param solventName the name of the solvent
   * @return uT in K/K, or 0 if not found
   */
  public static double getUT(String ionName, String solventName) {
    String key = ionName + "-" + solventName;
    SolventInteractionData solventData = SOLVENT_SPECIFIC_DATA.get(key);
    if (solventData != null) {
      return solventData.uT_is;
    }
    // Fall back to water parameters
    IonData data = ION_DATA.get(ionName);
    return data != null ? data.uT_iw : 0.0;
  }

  /**
   * Check if solvent-specific parameters exist for an ion-solvent pair.
   *
   * @param ionName the name of the ion
   * @param solventName the name of the solvent
   * @return true if solvent-specific parameters exist
   */
  public static boolean hasSolventSpecificData(String ionName, String solventName) {
    String key = ionName + "-" + solventName;
    return SOLVENT_SPECIFIC_DATA.containsKey(key);
  }

  /**
   * Calculate the Born radius for an ion.
   *
   * <p>
   * Using Maribo-Mogensen empirical correlations:
   * </p>
   * <ul>
   * <li>Cations: R_Born = 0.5σ + 0.1 Å</li>
   * <li>Anions: R_Born = 0.5σ + 0.85 Å</li>
   * </ul>
   *
   * @param ionName the name of the ion
   * @return Born radius in Ångströms, or 0 if not found
   */
  public static double getBornRadius(String ionName) {
    IonData data = ION_DATA.get(ionName);
    if (data == null) {
      return 0.0;
    }
    if (data.charge > 0) {
      return 0.5 * data.sigma + 0.1;
    } else {
      return 0.5 * data.sigma + 0.85;
    }
  }

  /**
   * Get the ionic charge for an ion.
   *
   * @param ionName the name of the ion
   * @return ionic charge (positive for cations, negative for anions)
   */
  public static int getCharge(String ionName) {
    IonData data = ION_DATA.get(ionName);
    return data != null ? data.charge : 0;
  }

  /**
   * Get all available ion names.
   *
   * @return array of ion names
   */
  public static String[] getAvailableIons() {
    return ION_DATA.keySet().toArray(new String[0]);
  }

  /**
   * Check if a solvent is supported.
   *
   * @param solventName the name of the solvent
   * @return true if the solvent is supported
   */
  public static boolean isSupportedSolvent(String solventName) {
    return SUPPORTED_SOLVENTS.contains(solventName);
  }

  /**
   * Data class to hold ion parameters (for water as default solvent).
   */
  public static class IonData {
    /** Lennard-Jones diameter [Å]. */
    public final double sigma;
    /** Ion-solvent interaction at reference temperature [K]. */
    public final double u0_iw;
    /** Temperature coefficient of ion-solvent interaction [K/K]. */
    public final double uT_iw;
    /** Ionic charge. */
    public final int charge;

    /**
     * Constructor for IonData.
     *
     * @param sigma Lennard-Jones diameter in Ångströms
     * @param u0_iw ion-solvent interaction at 298.15 K in Kelvin
     * @param uT_iw temperature coefficient of interaction in K/K
     * @param charge ionic charge
     */
    public IonData(double sigma, double u0_iw, double uT_iw, int charge) {
      this.sigma = sigma;
      this.u0_iw = u0_iw;
      this.uT_iw = uT_iw;
      this.charge = charge;
    }

    /**
     * Calculate ion-solvent interaction energy at given temperature.
     *
     * @param temperature temperature in Kelvin
     * @return interaction energy in Kelvin
     */
    public double getInteractionEnergy(double temperature) {
      return u0_iw + uT_iw * (temperature - T_REF);
    }

    /**
     * Calculate Born radius using Maribo-Mogensen correlation.
     *
     * @return Born radius in Ångströms
     */
    public double getBornRadius() {
      if (charge > 0) {
        return 0.5 * sigma + 0.1;
      } else {
        return 0.5 * sigma + 0.85;
      }
    }
  }

  /**
   * Data class to hold solvent-specific ion interaction parameters.
   */
  public static class SolventInteractionData {
    /** Ion-solvent interaction at reference temperature for specific solvent [K]. */
    public final double u0_is;
    /** Temperature coefficient of ion-solvent interaction for specific solvent [K/K]. */
    public final double uT_is;

    /**
     * Constructor for SolventInteractionData.
     *
     * @param u0_is ion-solvent interaction at 298.15 K in Kelvin
     * @param uT_is temperature coefficient of interaction in K/K
     */
    public SolventInteractionData(double u0_is, double uT_is) {
      this.u0_is = u0_is;
      this.uT_is = uT_is;
    }

    /**
     * Calculate ion-solvent interaction energy at given temperature.
     *
     * @param temperature temperature in Kelvin
     * @return interaction energy in Kelvin
     */
    public double getInteractionEnergy(double temperature) {
      return u0_is + uT_is * (temperature - T_REF);
    }
  }
}
