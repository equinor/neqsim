package neqsim.physicalproperties.methods.gasphysicalproperties.diffusivity;

import java.util.HashMap;
import java.util.Map;
import neqsim.physicalproperties.system.PhysicalProperties;

/**
 * Fuller-Schettler-Giddings method for gas-phase binary diffusion coefficients.
 *
 * <p>
 * Low-pressure gas correlation using regressed simple-molecule volumes or sums of atomic and structural increments.
 * Known organic sums are assembled once, not inferred from a general molecular-formula parser. Unknown components
 * retain approximate critical-volume or molar-mass fallbacks; these do not establish parameter validity.
 * </p>
 *
 * <pre>
 * D_AB = 1.013e-7 * T ^ 1.75 * sqrt(1 / M_A + 1 / M_B) / (P * (V_A ^ (1 / 3) + V_B ^ (1 / 3)) ^ 2)
 * </pre>
 *
 * <p>
 * D_AB is in m2/s, T in K, P in absolute bar and M in g/mol. V denotes empirical Fuller diffusion-volume numbers, not
 * thermodynamic molar volumes. The legacy bar prefactor is rounded; invalid/nonfinite states or masses and
 * nonpositive/nonfinite results return NaN. The warning interval is not a universal accuracy guarantee.
 * </p>
 *
 * <p>
 * References:
 * </p>
 * <ul>
 * <li>Fuller, E.N., Schettler, P.D. and Giddings, J.C. (1966). "A new method for prediction of binary gas-phase
 * diffusion coefficients." Ind. Eng. Chem., 58(5), 18-27.</li>
 * <li>Fuller, E.N., Ensley, K. and Giddings, J.C. (1969). "Diffusion of halogenated hydrocarbons in helium." J. Phys.
 * Chem., 73, 3679-3685.</li>
 * <li>Poling, B.E., Prausnitz, J.M. and O'Connell, J.P. (2001). "The Properties of Gases and Liquids." 5th ed.,
 * McGraw-Hill, Table 11-1.</li>
 * <li>Gu, W., Cheng, P. and Tang, M. (2018). R. Soc. Open Sci. 5, 171936, equation 2.3 and Table 1,
 * doi:10.1098/rsos.171936 (CC BY 4.0). Accessible parameter and additive-rule evidence, not sixth-edition
 * conformance.</li>
 * </ul>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class FullerSchettlerGiddingsDiffusivity extends Diffusivity {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * Atomic and structural empirical diffusion-volume increments from Fuller, Schettler and Giddings (1966, 1969) and
   * updated by Poling, Prausnitz and O'Connell (2001), Table 11-1.
   */
  private static final Map<String, Double> ATOMIC_DIFFUSION_VOLUMES = createAtomicVolumes();

  /**
   * Regressed simple-molecule diffusion volumes and explicitly assembled known organic sums. Regressed values take
   * precedence over an atomic sum for the corresponding simple molecule.
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
   * Create regressed simple-molecule values and known organic group sums. Only aromatic and heterocyclic rings receive
   * the structural subtraction; saturated carbocyclic rings do not.
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

    // Known organic structures: C/H/O atom counts and aromatic/heterocyclic ring count.
    v.put("methane", organicVolume(1, 4, 0, 0));
    v.put("ethane", organicVolume(2, 6, 0, 0));
    v.put("propane", organicVolume(3, 8, 0, 0));
    v.put("i-butane", organicVolume(4, 10, 0, 0));
    v.put("n-butane", organicVolume(4, 10, 0, 0));
    v.put("i-pentane", organicVolume(5, 12, 0, 0));
    v.put("n-pentane", organicVolume(5, 12, 0, 0));
    v.put("n-hexane", organicVolume(6, 14, 0, 0));
    v.put("n-heptane", organicVolume(7, 16, 0, 0));
    v.put("n-octane", organicVolume(8, 18, 0, 0));
    v.put("n-nonane", organicVolume(9, 20, 0, 0));
    v.put("n-decane", organicVolume(10, 22, 0, 0));
    v.put("nC10", organicVolume(10, 22, 0, 0)); // Canonical database name.
    v.put("cyclohexane", organicVolume(6, 12, 0, 0));
    v.put("benzene", organicVolume(6, 6, 0, 1));
    v.put("toluene", organicVolume(7, 8, 0, 1));
    v.put("methanol", organicVolume(1, 4, 1, 0));
    v.put("ethanol", organicVolume(2, 6, 1, 0));
    v.put("acetone", organicVolume(3, 6, 1, 0));
    v.put("MEG", organicVolume(2, 6, 2, 0));
    v.put("DEG", organicVolume(4, 10, 3, 0));
    v.put("TEG", organicVolume(6, 14, 4, 0));

    return v;
  }

  /**
   * Assemble a known structure at class initialization, retaining the published increments.
   *
   * @param carbon number of carbon atoms
   * @param hydrogen number of hydrogen atoms
   * @param oxygen number of oxygen atoms
   * @param rings number of aromatic or heterocyclic rings
   * @return empirical diffusion-volume sum
   */
  private static double organicVolume(int carbon, int hydrogen, int oxygen, int rings) {
    return carbon * ATOMIC_DIFFUSION_VOLUMES.get("C") + hydrogen * ATOMIC_DIFFUSION_VOLUMES.get("H")
        + oxygen * ATOMIC_DIFFUSION_VOLUMES.get("O") + rings * ATOMIC_DIFFUSION_VOLUMES.get("RING");
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
   * First checks for a special molecular value, then uses approximate critical-volume or molar-mass fallback data.
   * </p>
   *
   * @param componentIndex index of the component
   * @return empirical Fuller diffusion-volume number
   */
  private double getDiffusionVolume(int componentIndex) {
    String name = gasPhase.getPhase().getComponent(componentIndex).getComponentName();

    // Fast exact lookup preserves live component identity without a state-dependent cache.
    Double known = MOLECULAR_DIFFUSION_VOLUMES.get(name);
    if (known != null) {
      return known;
    }
    // Retain compatibility with case-insensitive identities.
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

    if (!(T > 0) || !Double.isFinite(T) || !(P > 0) || !Double.isFinite(P)) {
      binaryDiffusionCoefficients[i][j] = Double.NaN;
      return Double.NaN;
    }

    // This warning interval is not a physical qualification envelope.
    if (enableTemperatureWarnings && (T < T_MIN || T > T_MAX)) {
      logger.warn("Temperature {} K is outside validated range [{}-{}] for Fuller-Schettler-Giddings", T, T_MIN, T_MAX);
    }

    // Molar masses in g/mol
    double MA = gasPhase.getPhase().getComponent(i).getMolarMass() * 1000.0;
    double MB = gasPhase.getPhase().getComponent(j).getMolarMass() * 1000.0;

    if (!(MA > 0) || !Double.isFinite(MA) || !(MB > 0) || !Double.isFinite(MB)) {
      binaryDiffusionCoefficients[i][j] = Double.NaN;
      return Double.NaN;
    }

    // Empirical Fuller diffusion-volume numbers.
    double VA = getDiffusionVolume(i);
    double VB = getDiffusionVolume(j);

    // Legacy rounded bar prefactor; 1e-4 converts cm2/s to m2/s.
    double sigmaV = Math.pow(VA, 1.0 / 3.0) + Math.pow(VB, 1.0 / 3.0);
    double mFactor = Math.sqrt(1.0 / MA + 1.0 / MB);
    double coefficient = 1.013e-3 * Math.pow(T, 1.75) * mFactor / (P * sigmaV * sigmaV) * 1e-4;
    binaryDiffusionCoefficients[i][j] = coefficient > 0 && Double.isFinite(coefficient) ? coefficient : Double.NaN;
    return binaryDiffusionCoefficients[i][j];
  }
}
