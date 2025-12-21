/*
 * IonParametersMM.java
 *
 * Ion parameters from the Maribo-Mogensen PhD thesis:
 * "Development of an Electrolyte CPA Equation of State for Mixed Solvent Electrolytes" Technical
 * University of Denmark, 2014
 */

package neqsim.thermo.util.constants;

import java.util.HashMap;
import java.util.Map;

/**
 * Ion parameters from the Maribo-Mogensen PhD thesis (Table 6.11).
 *
 * <p>
 * Contains temperature-dependent ion-solvent interaction parameters and Born radii for common ions.
 * </p>
 *
 * <p>
 * The ion-solvent interaction energy is given by:
 * </p>
 *
 * <pre>
 * ΔU_iw(T) = u⁰_iw + uᵀ_iw × (T - 298.15)
 * </pre>
 *
 * <p>
 * Born radius correlations:
 * </p>
 * <ul>
 * <li>Cations: R_Born = 0.5σ + 0.1 Å</li>
 * <li>Anions: R_Born = 0.5σ + 0.85 Å</li>
 * </ul>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public final class IonParametersMM {
  /** Reference temperature for ion-solvent interaction parameters [K]. */
  public static final double T_REF = 298.15;

  /** Map of ion name to parameters. */
  private static final Map<String, IonData> ION_DATA = new HashMap<>();

  static {
    // Data from Maribo-Mogensen thesis Table 6.11
    // Parameters: sigma [Å], u0_iw [K], uT_iw [K/K], ionic charge

    // Monovalent cations
    ION_DATA.put("Li+", new IonData(1.800, -1070.0, -8.94, 1));
    ION_DATA.put("Na+", new IonData(2.356, 241.5, -12.62, 1));
    ION_DATA.put("K+", new IonData(2.798, 1247.0, -7.387, 1));
    ION_DATA.put("Rb+", new IonData(3.036, 1622.0, -6.52, 1));
    ION_DATA.put("Cs+", new IonData(3.368, 2060.0, -4.58, 1));
    ION_DATA.put("H+", new IonData(2.000, -500.0, -10.0, 1)); // Estimated
    ION_DATA.put("NH4+", new IonData(2.960, 800.0, -5.0, 1)); // Estimated

    // Monovalent anions
    ION_DATA.put("F-", new IonData(2.640, -3568.0, 6.79, -1));
    ION_DATA.put("Cl-", new IonData(3.187, -1911.0, 4.489, -1));
    ION_DATA.put("Br-", new IonData(3.464, -1436.0, 3.52, -1));
    ION_DATA.put("I-", new IonData(3.820, -879.0, 2.35, -1));
    ION_DATA.put("OH-", new IonData(2.800, -2500.0, 5.0, -1)); // Estimated
    ION_DATA.put("NO3-", new IonData(3.400, -1200.0, 3.0, -1)); // Estimated
    ION_DATA.put("HCO3-", new IonData(3.500, -1500.0, 4.0, -1)); // Estimated

    // Divalent cations
    ION_DATA.put("Mg2+", new IonData(2.100, 2800.0, -40.0, 2)); // Estimated
    ION_DATA.put("Ca2+", new IonData(2.374, 3768.0, -33.13, 2));
    ION_DATA.put("Sr2+", new IonData(2.680, 4200.0, -28.0, 2)); // Estimated
    ION_DATA.put("Ba2+", new IonData(2.920, 4800.0, -25.0, 2)); // Estimated

    // Divalent anions
    ION_DATA.put("SO4-2", new IonData(3.800, -4500.0, 8.0, -2)); // Estimated
    ION_DATA.put("SO42-", new IonData(3.800, -4500.0, 8.0, -2)); // Alternative name
    ION_DATA.put("CO3-2", new IonData(3.600, -5000.0, 9.0, -2)); // Estimated
    ION_DATA.put("CO32-", new IonData(3.600, -5000.0, 9.0, -2)); // Alternative name
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
   * Get the ion-solvent interaction energy at a given temperature.
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
   * Data class to hold ion parameters.
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
}
