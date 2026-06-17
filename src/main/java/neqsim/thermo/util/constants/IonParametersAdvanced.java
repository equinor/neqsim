/*
 * IonParametersAdvanced.java
 *
 * Ion-specific parameters for the e-CPA-Advanced electrolyte equation of state. Contains fitted
 * parameters for ion-solvent short-range interactions, temperature-dependent Born radii, and
 * ion-pair formation constants.
 *
 * References: - Robinson & Stokes (1959/2002): Electrolyte Solutions, activity coefficient data -
 * Marcus (1988): Chem. Rev. 88, 1475-1498 (ionic radii and hydration data) - Maribo-Mogensen
 * (2014): PhD Thesis, DTU (e-CPA framework and DH parameters) - Hamer & Wu (1972): J. Phys. Chem.
 * Ref. Data 1, 1047-1100
 */

package neqsim.thermo.util.constants;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Ion-specific parameters for the e-CPA-Advanced electrolyte equation of state.
 *
 * <p>
 * This class provides ion-specific interaction parameters fitted to experimental mean ionic
 * activity coefficient and osmotic coefficient data. Unlike the original e-CPA model which uses
 * universal linear correlations for all ions, this model has individual parameters per ion-solvent
 * pair, enabling higher accuracy especially for multivalent ions and sulfate systems.
 * </p>
 *
 * <h2>Parameter Structure</h2>
 * <p>
 * Each ion has the following parameters:
 * </p>
 * <ul>
 * <li>sigma: Hard-sphere diameter [Angstrom]</li>
 * <li>W0: Ion-water interaction energy at 298.15 K [J*m3/mol2]</li>
 * <li>WT: Linear temperature coefficient of W [J*m3/(mol2*K)]</li>
 * <li>WTT: Quadratic temperature coefficient [J*m3/(mol2*K2)]</li>
 * <li>RBorn0: Born radius at 298.15 K [Angstrom]</li>
 * <li>RBornT: Temperature coefficient of Born radius [Angstrom/K]</li>
 * </ul>
 *
 * <h2>Data Sources</h2>
 * <ul>
 * <li>Robinson and Stokes (1959, 2002) — activity coefficient data at 25 C</li>
 * <li>Hamer and Wu (1972) — critical evaluation of activity coefficients</li>
 * <li>Marcus (1988) — ionic radii and hydration free energies</li>
 * <li>Archer (1992) — NaCl properties 0-300 C</li>
 * </ul>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class IonParametersAdvanced implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Reference temperature for parameter fitting [K]. */
  public static final double T_REF = 298.15;

  /** Map of ion name to advanced parameters. */
  private static final Map<String, AdvancedIonData> ION_DATA =
      new HashMap<String, AdvancedIonData>();

  /** Map of ion-pair keys to ion-pair formation constants. */
  private static final Map<String, IonPairData> ION_PAIR_DATA = new HashMap<String, IonPairData>();

  static {
    // ===========================================================================
    // Ion-specific parameters fitted to Robinson & Stokes (1959) activity
    // coefficient data and Hamer & Wu (1972) osmotic coefficient data.
    //
    // Parameters: sigma [A], W0 [J*m3/mol2], WT [J*m3/(mol2*K)],
    // WTT [J*m3/(mol2*K2)], RBorn0 [A], RBornT [A/K]
    //
    // Fitting methodology:
    // 1. sigma: Initial from Marcus ionic radii, refined vs gamma_pm at 25C
    // 2. W0: Fitted to gamma_pm at 25C (0.1-6 mol/kg)
    // 3. WT: Fitted to T-dependent data (Silvester & Pitzer 1977)
    // 4. WTT: Only for systems with data over wide T range
    // 5. RBorn0: Constrained by Marcus hydration free energy
    // 6. RBornT: Fitted to T-dependent density and activity data
    // ===========================================================================

    // --- Monovalent cations ---
    // Li+ : Small, strong kosmotrope. Very negative hydration energy (-515 kJ/mol)
    // W0 fitted to R&S (1959) LiCl gamma_pm data using corrected reference state
    ION_DATA.put("Li+", new AdvancedIonData(1.82, // sigma [A] - Marcus effective radius
        3.780e-03, // W0 - fitted to LiCl activity coefficient data (corrected ref state)
        -1.12e-07, // WT
        0.0, // WTT
        1.64, // RBorn0 [A] - constrained by dGhydration
        4.5e-04, // RBornT [A/K]
        1, // ionic charge
        -515.0 // hydration Gibbs energy [kJ/mol]
    ));

    // Na+ : Reference cation, moderate kosmotrope. dGhyd = -405 kJ/mol
    // W0 fitted to R&S (1959) NaCl gamma_pm data at 25C using corrected reference state
    ION_DATA.put("Na+", new AdvancedIonData(2.36, // sigma [A]
        3.761e-03, // W0 - fitted to NaCl activity coefficient data (corrected ref state)
        -8.50e-08, // WT
        2.0e-10, // WTT
        1.86, // RBorn0 [A]
        3.8e-04, // RBornT
        1, // charge
        -405.0 // dGhyd
    ));

    // K+ : Weak chaotrope. dGhyd = -321 kJ/mol
    // W0 fitted to R&S (1959) KCl gamma_pm data using corrected reference state
    ION_DATA.put("K+", new AdvancedIonData(2.80, // sigma [A]
        3.907e-03, // W0 - fitted to KCl activity coefficient data (corrected ref state)
        -5.20e-08, // WT
        0.0, // WTT
        2.18, // RBorn0
        3.2e-04, // RBornT
        1, // charge
        -321.0 // dGhyd
    ));

    // Rb+ : Chaotrope. dGhyd = -296 kJ/mol
    ION_DATA.put("Rb+", new AdvancedIonData(3.04, // sigma
        -2.80e-06, // W0
        -4.50e-08, // WT
        0.0, // WTT
        2.32, // RBorn0
        3.0e-04, // RBornT
        1, -296.0));

    // Cs+ : Strong chaotrope. dGhyd = -276 kJ/mol
    ION_DATA.put("Cs+", new AdvancedIonData(3.37, // sigma
        -1.50e-06, // W0
        -3.80e-08, // WT
        0.0, // WTT
        2.50, // RBorn0
        2.8e-04, // RBornT
        1, -276.0));

    // H+ (hydronium): Very strong kosmotrope. dGhyd = -1090 kJ/mol
    ION_DATA.put("H+",
        new AdvancedIonData(2.00, -5.20e-05, -1.50e-07, 0.0, 1.30, 5.0e-04, 1, -1090.0));

    // NH4+ : Similar to K+. dGhyd = -307 kJ/mol
    ION_DATA.put("NH4+",
        new AdvancedIonData(2.96, -5.00e-06, -4.80e-08, 0.0, 2.20, 3.3e-04, 1, -307.0));

    // --- Monovalent anions ---
    // F- : Strong kosmotrope. dGhyd = -465 kJ/mol
    ION_DATA.put("F-", new AdvancedIonData(2.64, // sigma
        -4.20e-05, // W0 - strong interaction
        6.80e-08, // WT - note: positive for anions (opposite T-trend)
        0.0, // WTT
        1.68, // RBorn0 - small Born radius (high hydration energy)
        3.5e-04, // RBornT
        -1, -465.0));

    // Cl- : Reference anion, mild chaotrope. dGhyd = -363 kJ/mol
    // W0 fitted to R&S (1959) NaCl gamma_pm data at 25C using corrected reference state
    ION_DATA.put("Cl-", new AdvancedIonData(3.19, // sigma
        -3.469e-03, // W0 - fitted to NaCl activity coefficient data (corrected ref state)
        4.50e-08, // WT
        -1.5e-10, // WTT
        2.26, // RBorn0
        2.8e-04, // RBornT
        -1, -363.0));

    // Br- : Chaotrope. dGhyd = -337 kJ/mol
    ION_DATA.put("Br-", new AdvancedIonData(3.46, // sigma
        -1.68e-05, // W0
        3.50e-08, // WT
        0.0, // WTT
        2.44, // RBorn0
        2.5e-04, // RBornT
        -1, -337.0));

    // I- : Strong chaotrope. dGhyd = -283 kJ/mol
    ION_DATA.put("I-", new AdvancedIonData(3.82, // sigma
        -1.05e-05, // W0
        2.40e-08, // WT
        0.0, // WTT
        2.70, // RBorn0
        2.2e-04, // RBornT
        -1, -283.0));

    // OH- : Strong kosmotrope. dGhyd = -430 kJ/mol
    ION_DATA.put("OH-",
        new AdvancedIonData(2.80, -3.50e-05, 5.50e-08, 0.0, 1.80, 3.2e-04, -1, -430.0));

    // NO3- : Mild chaotrope. dGhyd = -306 kJ/mol
    ION_DATA.put("NO3-",
        new AdvancedIonData(3.40, -1.40e-05, 3.00e-08, 0.0, 2.40, 2.7e-04, -1, -306.0));

    // HCO3- : Moderate. dGhyd = -373 kJ/mol
    ION_DATA.put("HCO3-",
        new AdvancedIonData(3.50, -1.90e-05, 4.00e-08, 0.0, 2.35, 2.9e-04, -1, -373.0));

    // HS- : Bisulfide. dGhyd = -330 kJ/mol
    ION_DATA.put("HS-",
        new AdvancedIonData(3.40, -1.80e-05, 3.50e-08, 0.0, 2.40, 2.6e-04, -1, -330.0));

    // --- Divalent cations ---
    // Mg2+ : Very strong kosmotrope. dGhyd = -1922 kJ/mol
    ION_DATA.put("Mg++", new AdvancedIonData(2.10, // sigma - small due to high charge density
        -8.50e-05, // W0 - very strong interaction
        -2.80e-07, // WT
        5.0e-10, // WTT
        1.42, // RBorn0 - small (high solvation energy)
        6.0e-04, // RBornT
        2, -1922.0));

    // Ca2+ : Strong kosmotrope. dGhyd = -1592 kJ/mol
    // W0 fitted to R&S (1959) CaCl2 gamma_pm data using corrected reference state
    ION_DATA.put("Ca++", new AdvancedIonData(2.38, // sigma
        -8.165e-03, // W0 - fitted to CaCl2 activity coefficient data (corrected ref state)
        -2.30e-07, // WT
        4.0e-10, // WTT
        1.58, // RBorn0
        5.5e-04, // RBornT
        2, -1592.0));

    // Sr2+ : Moderate kosmotrope. dGhyd = -1445 kJ/mol
    ION_DATA.put("Sr++", new AdvancedIonData(2.68, // sigma
        -5.00e-05, // W0
        -1.95e-07, // WT
        0.0, // WTT
        1.74, // RBorn0
        5.0e-04, // RBornT
        2, -1445.0));

    // Ba2+ : Weaker kosmotrope. dGhyd = -1317 kJ/mol
    ION_DATA.put("Ba++", new AdvancedIonData(2.92, // sigma
        -3.80e-05, // W0
        -1.70e-07, // WT
        0.0, // WTT
        1.88, // RBorn0
        4.5e-04, // RBornT
        2, -1317.0));

    // Fe2+ : Strong kosmotrope. dGhyd = -1946 kJ/mol
    ION_DATA.put("Fe++",
        new AdvancedIonData(2.28, -7.80e-05, -2.50e-07, 0.0, 1.45, 5.8e-04, 2, -1946.0));

    // Zn2+ : Strong kosmotrope. dGhyd = -2046 kJ/mol
    ION_DATA.put("Zn++",
        new AdvancedIonData(2.20, -8.80e-05, -2.90e-07, 0.0, 1.40, 6.2e-04, 2, -2046.0));

    // --- Divalent anions ---
    // SO4 2- : Strong kosmotrope. dGhyd = -1090 kJ/mol
    // W0 fitted to R&S (1959) Na2SO4 gamma_pm data using corrected reference state
    ION_DATA.put("SO4--", new AdvancedIonData(3.80, // sigma - large polyatomic
        -6.909e-03, // W0 - fitted to Na2SO4 activity coefficient data (corrected ref state)
        8.00e-08, // WT
        -3.0e-10, // WTT
        2.58, // RBorn0
        4.0e-04, // RBornT
        -2, -1090.0));

    // CO3 2- : Strong kosmotrope. dGhyd = -1315 kJ/mol
    ION_DATA.put("CO3--",
        new AdvancedIonData(3.60, -6.00e-05, 9.00e-08, 0.0, 2.45, 3.8e-04, -2, -1315.0));

    // S 2- : dGhyd = -1380 kJ/mol
    ION_DATA.put("S--",
        new AdvancedIonData(3.50, -6.50e-05, 9.50e-08, 0.0, 2.40, 3.7e-04, -2, -1380.0));

    // --- Trivalent ions ---
    ION_DATA.put("Fe+++",
        new AdvancedIonData(2.00, -1.20e-04, -3.50e-07, 0.0, 1.25, 7.0e-04, 3, -4430.0));
    ION_DATA.put("Al+++",
        new AdvancedIonData(1.80, -1.40e-04, -3.80e-07, 0.0, 1.18, 7.5e-04, 3, -4665.0));

    // ===========================================================================
    // Ion-pair formation data for 2:2 and select 2:1 systems
    // K_IP(T) = K0_IP * exp(-dH_IP/(R*T) + dH_IP/(R*T_ref))
    //
    // Data sources:
    // - Bjerrum (1926): Ion association theory
    // - Reardon (1990): CaSO4 association constants
    // - Atkinson & Kor (1976): MgSO4 ion pairing
    // ===========================================================================

    // MgSO4: Strong ion pairing (K_IP ~ 160 at 25C)
    ION_PAIR_DATA.put("Mg++-SO4--", new IonPairData(160.0, // K0_IP [L/mol] at 298.15 K
        12500.0, // dH_IP [J/mol] - enthalpy of ion pair formation
        2.90 // d_IP [A] - contact distance
    ));

    // CaSO4: Very strong ion pairing (K_IP ~ 200 at 25C)
    ION_PAIR_DATA.put("Ca++-SO4--", new IonPairData(200.0, 14000.0, 3.09));

    // ZnSO4: Strong ion pairing
    ION_PAIR_DATA.put("Zn++-SO4--", new IonPairData(150.0, 11500.0, 3.00));

    // BaSO4: Strong ion pairing
    ION_PAIR_DATA.put("Ba++-SO4--", new IonPairData(180.0, 13000.0, 3.36));

    // SrSO4: Strong ion pairing
    ION_PAIR_DATA.put("Sr++-SO4--", new IonPairData(170.0, 12800.0, 3.24));

    // FeSO4: Moderate ion pairing
    ION_PAIR_DATA.put("Fe++-SO4--", new IonPairData(140.0, 11000.0, 3.04));
  }

  /**
   * Private constructor to prevent instantiation.
   */
  private IonParametersAdvanced() {}

  /**
   * Set the W0 (base ion-water interaction energy) for a given ion. Used during parameter fitting
   * to update W0 without recompiling. Creates a new AdvancedIonData with the updated W0 while
   * keeping all other parameters.
   *
   * @param ionName the ion name (e.g., "Na+", "Cl-")
   * @param newW0 new W0 value in J*m3/mol2
   */
  public static void setW0(String ionName, double newW0) {
    AdvancedIonData old = ION_DATA.get(ionName);
    if (old != null) {
      ION_DATA.put(ionName, new AdvancedIonData(old.sigma, newW0, old.wT, old.wTT, old.rBorn0,
          old.rBornT, old.charge, old.dGhydration));
    }
  }

  /**
   * Set the W parameters (W0, WT, WTT) for a given ion. Used during parameter fitting.
   *
   * @param ionName the ion name (e.g., "Na+", "Cl-")
   * @param newW0 base interaction energy at T_ref in J*m3/mol2
   * @param newWT linear T-coefficient in J*m3/(mol2*K)
   * @param newWTT quadratic T-coefficient in J*m3/(mol2*K2)
   */
  public static void setWParameters(String ionName, double newW0, double newWT, double newWTT) {
    AdvancedIonData old = ION_DATA.get(ionName);
    if (old != null) {
      ION_DATA.put(ionName, new AdvancedIonData(old.sigma, newW0, newWT, newWTT, old.rBorn0,
          old.rBornT, old.charge, old.dGhydration));
    }
  }

  /**
   * Get advanced ion data for a given ion name.
   *
   * @param ionName the name of the ion (e.g., "Na+", "Cl-", "Ca++", "SO4--")
   * @return AdvancedIonData object, or null if ion not found
   */
  public static AdvancedIonData getIonData(String ionName) {
    return ION_DATA.get(ionName);
  }

  /**
   * Check if ion-specific advanced parameters exist for a given ion.
   *
   * @param ionName the name of the ion
   * @return true if parameters exist
   */
  public static boolean hasIonData(String ionName) {
    return ION_DATA.containsKey(ionName);
  }

  /**
   * Get ion-pair formation data for a cation-anion pair.
   *
   * @param cationName the cation name (e.g., "Ca++")
   * @param anionName the anion name (e.g., "SO4--")
   * @return IonPairData object, or null if no ion pairing data available
   */
  public static IonPairData getIonPairData(String cationName, String anionName) {
    String key = cationName + "-" + anionName;
    IonPairData data = ION_PAIR_DATA.get(key);
    if (data == null) {
      // Try reverse order
      data = ION_PAIR_DATA.get(anionName + "-" + cationName);
    }
    return data;
  }

  /**
   * Check if ion pairing is expected for a given cation-anion combination.
   *
   * @param cationName the cation name
   * @param anionName the anion name
   * @return true if ion-pair formation data exists
   */
  public static boolean hasIonPairData(String cationName, String anionName) {
    return getIonPairData(cationName, anionName) != null;
  }

  /**
   * Calculate the ion-water interaction energy W at a given temperature.
   *
   * @param ionName the ion name
   * @param temperature the temperature in Kelvin
   * @return the interaction energy W(T) in J*m3/mol2
   */
  public static double calcW(String ionName, double temperature) {
    AdvancedIonData data = ION_DATA.get(ionName);
    if (data == null) {
      return 0.0;
    }
    double dT = temperature - T_REF;
    return data.w0 + data.wT * dT + data.wTT * dT * dT;
  }

  /**
   * Calculate the temperature derivative of W.
   *
   * @param ionName the ion name
   * @param temperature the temperature in Kelvin
   * @return dW/dT in J*m3/(mol2*K)
   */
  public static double calcWdT(String ionName, double temperature) {
    AdvancedIonData data = ION_DATA.get(ionName);
    if (data == null) {
      return 0.0;
    }
    double dT = temperature - T_REF;
    return data.wT + 2.0 * data.wTT * dT;
  }

  /**
   * Calculate the second temperature derivative of W.
   *
   * @param ionName the ion name
   * @return d2W/dT2 in J*m3/(mol2*K2)
   */
  public static double calcWdTdT(String ionName) {
    AdvancedIonData data = ION_DATA.get(ionName);
    if (data == null) {
      return 0.0;
    }
    return 2.0 * data.wTT;
  }

  /**
   * Calculate the Born radius at a given temperature.
   *
   * @param ionName the ion name
   * @param temperature the temperature in Kelvin
   * @return Born radius in Angstrom
   */
  public static double calcBornRadius(String ionName, double temperature) {
    AdvancedIonData data = ION_DATA.get(ionName);
    if (data == null) {
      return 1.0; // default fallback
    }
    return data.rBorn0 + data.rBornT * (temperature - T_REF);
  }

  /**
   * Calculate ion-pair formation constant at a given temperature.
   *
   * @param cationName the cation name
   * @param anionName the anion name
   * @param temperature the temperature in Kelvin
   * @return K_IP in L/mol
   */
  public static double calcIonPairConstant(String cationName, String anionName,
      double temperature) {
    IonPairData data = getIonPairData(cationName, anionName);
    if (data == null) {
      return 0.0;
    }
    // K_IP(T) = K0 * exp(-dH/(R*T) + dH/(R*T_ref))
    double R = 8.314; // J/(mol*K)
    return data.k0 * Math.exp(-data.dH / (R * temperature) + data.dH / (R * T_REF));
  }

  /**
   * Data class holding ion-specific advanced parameters.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  public static class AdvancedIonData implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000L;

    /** Hard-sphere diameter [Angstrom]. */
    public final double sigma;
    /** Ion-water interaction energy at T_ref [J*m3/mol2]. */
    public final double w0;
    /** Linear T-coefficient of W [J*m3/(mol2*K)]. */
    public final double wT;
    /** Quadratic T-coefficient of W [J*m3/(mol2*K2)]. */
    public final double wTT;
    /** Born radius at T_ref [Angstrom]. */
    public final double rBorn0;
    /** T-coefficient of Born radius [Angstrom/K]. */
    public final double rBornT;
    /** Ionic charge (signed: +1, -1, +2, -2, etc.). */
    public final int charge;
    /** Hydration Gibbs energy [kJ/mol] (Marcus 1988). */
    public final double dGhydration;

    /**
     * Constructor for AdvancedIonData.
     *
     * @param sigma hard-sphere diameter in Angstrom
     * @param w0 ion-water interaction at T_ref in J*m3/mol2
     * @param wT linear T-coefficient in J*m3/(mol2*K)
     * @param wTT quadratic T-coefficient in J*m3/(mol2*K2)
     * @param rBorn0 Born radius at T_ref in Angstrom
     * @param rBornT T-coefficient of Born radius in Angstrom/K
     * @param charge ionic charge (signed integer)
     * @param dGhydration hydration Gibbs energy in kJ/mol
     */
    public AdvancedIonData(double sigma, double w0, double wT, double wTT, double rBorn0,
        double rBornT, int charge, double dGhydration) {
      this.sigma = sigma;
      this.w0 = w0;
      this.wT = wT;
      this.wTT = wTT;
      this.rBorn0 = rBorn0;
      this.rBornT = rBornT;
      this.charge = charge;
      this.dGhydration = dGhydration;
    }
  }

  /**
   * Data class for ion-pair formation parameters.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  public static class IonPairData implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000L;

    /** Ion-pair formation constant at T_ref [L/mol]. */
    public final double k0;
    /** Enthalpy of ion-pair formation [J/mol]. */
    public final double dH;
    /** Contact distance for ion pairing [Angstrom]. */
    public final double dIP;

    /**
     * Constructor for IonPairData.
     *
     * @param k0 ion-pair formation constant at T_ref in L/mol
     * @param dH enthalpy of ion-pair formation in J/mol
     * @param dIP contact distance in Angstrom
     */
    public IonPairData(double k0, double dH, double dIP) {
      this.k0 = k0;
      this.dH = dH;
      this.dIP = dIP;
    }
  }
}
