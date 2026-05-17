package neqsim.physicalproperties.methods.gasphysicalproperties.diffusivity;

import java.util.HashMap;
import java.util.Map;
import neqsim.physicalproperties.system.PhysicalProperties;

/**
 * Fuller-Schettler-Giddings method for gas-phase binary diffusion coefficients.
 *
 * <p>
 * This is considered the most accurate and widely recommended general correlation for gas-phase
 * diffusion coefficients. It uses atomic diffusion volumes instead of Lennard-Jones parameters,
 * making it applicable to a wider range of compounds including polar molecules and complex
 * hydrocarbons where L-J parameters may be unavailable or unreliable.
 * </p>
 *
 * <p>
 * The correlation is:
 * </p>
 *
 * <pre>
 * D_AB = 1.013e-2 * T ^ 1.75 * sqrt(1 / M_A + 1 / M_B) / (P * (V_A ^ (1 / 3) + V_B ^ (1 / 3)) ^ 2)
 * </pre>
 *
 * <p>
 * where D_AB is in cm^2/s, T in K, P in atm, M in g/mol, and V is the diffusion volume in cm^3/mol.
 * </p>
 *
 * <p>
 * References:
 * </p>
 * <ul>
 * <li>Fuller, E.N., Schettler, P.D. and Giddings, J.C. (1966). "A new method for prediction of
 * binary gas-phase diffusion coefficients." Ind. Eng. Chem., 58(5), 18-27.</li>
 * <li>Fuller, E.N., Ensley, K. and Giddings, J.C. (1969). "Diffusion of halogenated hydrocarbons in
 * helium." J. Phys. Chem., 73, 3679-3685.</li>
 * <li>Poling, B.E., Prausnitz, J.M. and O'Connell, J.P. (2001). "The Properties of Gases and
 * Liquids." 5th ed., McGraw-Hill, Table 11-1.</li>
 * </ul>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class FullerSchettlerGiddingsDiffusivity extends Diffusivity {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * Atomic and structural diffusion volume increments (cm^3/mol) from Fuller, Schettler and
   * Giddings (1966, 1969) and updated by Poling, Prausnitz and O'Connell (2001), Table 11-1.
   */
  private static final Map<String, Double> ATOMIC_DIFFUSION_VOLUMES = createAtomicVolumes();

  /**
   * Special diffusion volumes for simple molecules (cm^3/mol). These are determined directly from
   * experimental data and should be used in preference to summing atomic increments.
   */
  private static final Map<String, Double> MOLECULAR_DIFFUSION_VOLUMES = createMolecularVolumes();

  /**
   * Create the table of atomic diffusion volume increments.
   *
   * @return map of element symbol to diffusion volume increment
   */
  private static Map<String, Double> createAtomicVolumes() {
    Map<String, Double> v = new HashMap<String, Double>();
    v.put("C", 15.9); // Carbon
    v.put("H", 2.31); // Hydrogen
    v.put("O", 6.11); // Oxygen
    v.put("N", 4.54); // Nitrogen
    v.put("F", 14.7); // Fluorine
    v.put("Cl", 21.0); // Chlorine
    v.put("Br", 21.9); // Bromine
    v.put("I", 29.8); // Iodine
    v.put("S", 22.9); // Sulfur
    // Structural increments
    v.put("RING", -18.3); // Aromatic or heterocyclic ring correction
    return v;
  }

  /**
   * Create the table of special molecular diffusion volumes. These are experimentally derived
   * values for simple molecules that should be used instead of summing atomic increments.
   *
   * @return map of component name to molecular diffusion volume
   */
  private static Map<String, Double> createMolecularVolumes() {
    Map<String, Double> v = new HashMap<String, Double>();
    // Noble gases and diatomics
    v.put("helium", 2.67);
    v.put("He", 2.67);
    v.put("neon", 5.98);
    v.put("Ne", 5.98);
    v.put("argon", 16.2);
    v.put("Ar", 16.2);
    v.put("krypton", 24.5);
    v.put("Kr", 24.5);
    v.put("xenon", 32.7);
    v.put("Xe", 32.7);
    v.put("hydrogen", 6.12);
    v.put("H2", 6.12);
    v.put("nitrogen", 18.5);
    v.put("N2", 18.5);
    v.put("oxygen", 16.3);
    v.put("O2", 16.3);

    // Common molecules
    v.put("water", 13.1);
    v.put("H2O", 13.1);
    v.put("CO2", 26.9);
    v.put("CO", 18.0);
    v.put("N2O", 35.9);
    v.put("NH3", 20.7);
    v.put("SO2", 41.8);
    v.put("H2S", 27.0);
    v.put("COS", 41.8);
    v.put("Cl2", 38.4);
    v.put("Br2", 69.0);
    v.put("SF6", 71.3);
    v.put("CCl2F2", 114.8);

    // Light hydrocarbons
    v.put("methane", 25.14); // C + 4H = 15.9 + 4*2.31 = 25.14
    v.put("ethane", 43.35); // 2C + 6H = 2*15.9 + 6*2.31
    v.put("propane", 61.56); // 3C + 8H
    v.put("i-butane", 79.77); // 4C + 10H
    v.put("n-butane", 79.77);
    v.put("i-pentane", 97.98); // 5C + 12H
    v.put("n-pentane", 97.98);
    v.put("n-hexane", 116.19); // 6C + 14H
    v.put("n-heptane", 134.40); // 7C + 16H
    v.put("n-octane", 152.61); // 8C + 18H
    v.put("n-nonane", 170.82); // 9C + 20H
    v.put("n-decane", 189.03); // 10C + 22H

    // Cyclic/aromatic hydrocarbons (with ring correction)
    v.put("cyclohexane", 98.67); // 6C + 12H - 18.3(ring) = 6*15.9+12*2.31-18.3
    v.put("benzene", 77.46); // 6C + 6H - 18.3(ring) = 6*15.9+6*2.31-18.3
    v.put("toluene", 95.67); // 7C + 8H - 18.3(ring)

    // Oxygenated compounds
    v.put("methanol", 30.42); // C + 4H + O = 15.9 + 4*2.31 + 6.11
    v.put("ethanol", 48.63); // 2C + 6H + O
    v.put("acetone", 67.68); // 3C + 6H + O = 3*15.9+6*2.31+6.11

    // MEG (ethylene glycol): C2H6O2
    v.put("MEG", 54.74); // 2C + 6H + 2O = 2*15.9+6*2.31+2*6.11
    v.put("DEG", 91.96); // 4C + 10H + 3O
    v.put("TEG", 129.18); // 6C + 14H + 4O

    return v;
  }

  /**
   * Constructor for FullerSchettlerGiddingsDiffusivity.
   *
   * @param gasPhase a {@link neqsim.physicalproperties.system.PhysicalProperties} object
   */
  public FullerSchettlerGiddingsDiffusivity(PhysicalProperties gasPhase) {
    super(gasPhase);
  }

  /**
   * Get the diffusion volume for a component.
   *
   * <p>
   * First checks for a special molecular value, then attempts estimation from molecular formula /
   * critical properties.
   * </p>
   *
   * @param componentIndex index of the component
   * @return diffusion volume in cm^3/mol
   */
  private double getDiffusionVolume(int componentIndex) {
    String name = gasPhase.getPhase().getComponent(componentIndex).getComponentName();

    // Check special molecular volumes first (case-insensitive lookup)
    for (Map.Entry<String, Double> entry : MOLECULAR_DIFFUSION_VOLUMES.entrySet()) {
      if (entry.getKey().equalsIgnoreCase(name)) {
        return entry.getValue();
      }
    }

    // Estimate from critical volume: V_d ≈ 0.285 * Vc (cm^3/mol)
    // getCriticalVolume() returns cm^3/mol
    double Vc = gasPhase.getPhase().getComponent(componentIndex).getCriticalVolume();
    if (Vc > 0) {
      return 0.285 * Vc;
    }

    // Fallback: estimate from molar mass using a simple group contribution
    // For general hydrocarbons CnH(2n+2): V = n*15.9 + (2n+2)*2.31
    double molarMass = gasPhase.getPhase().getComponent(componentIndex).getMolarMass() * 1000.0;
    // Very rough: V ≈ 0.95 * M for hydrocarbons
    return Math.max(10.0, 0.95 * molarMass);
  }

  /** {@inheritDoc} */
  @Override
  public double calcBinaryDiffusionCoefficient(int i, int j, int method) {
    double T = gasPhase.getPhase().getTemperature();
    double P = gasPhase.getPhase().getPressure(); // in bara

    // Temperature range validation
    if (enableTemperatureWarnings && (T < T_MIN || T > T_MAX)) {
      logger.warn(
          "Temperature {} K is outside validated range [{}-{}] for Fuller-Schettler-Giddings", T,
          T_MIN, T_MAX);
    }

    // Molar masses in g/mol
    double MA = gasPhase.getPhase().getComponent(i).getMolarMass() * 1000.0;
    double MB = gasPhase.getPhase().getComponent(j).getMolarMass() * 1000.0;

    // Diffusion volumes in cm^3/mol
    double VA = getDiffusionVolume(i);
    double VB = getDiffusionVolume(j);

    // Fuller-Schettler-Giddings correlation
    // D_AB [cm^2/s] = 1.013e-2 * T^1.75 * sqrt(1/M_A + 1/M_B)
    // / (P_atm * (V_A^(1/3) + V_B^(1/3))^2)
    double sigmaV = Math.pow(VA, 1.0 / 3.0) + Math.pow(VB, 1.0 / 3.0);
    double mFactor = Math.sqrt(1.0 / MA + 1.0 / MB);

    // D in cm^2/s (P must be in atm; bara ≈ atm for this correlation)
    // Constant 1.013e-3 from Fuller, Ensley & Giddings (1969), J. Phys. Chem. 73, 3679
    binaryDiffusionCoefficients[i][j] =
        1.013e-3 * Math.pow(T, 1.75) * mFactor / (P * sigmaV * sigmaV);

    // Convert from cm^2/s to m^2/s
    binaryDiffusionCoefficients[i][j] *= 1e-4;

    return binaryDiffusionCoefficients[i][j];
  }
}
