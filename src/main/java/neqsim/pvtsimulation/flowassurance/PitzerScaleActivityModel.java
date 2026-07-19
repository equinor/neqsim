package neqsim.pvtsimulation.flowassurance;

import java.io.Serializable;
import java.util.EnumMap;
import java.util.Map;

/**
 * Binary Pitzer activity-coefficient model for NaCl-dominated oilfield brines.
 *
 * <p>
 * The implementation follows Pitzer's virial formulation and evaluates the temperature-dependent binary parameters
 * distributed with the USGS PHREEQC {@code pitzer.dat} database. It reproduces critically evaluated NaCl mean activity
 * coefficients from dilute solution to halite saturation and maps trace-ion coefficients by the MacInnes convention at
 * equivalent ionic strength. This makes it a materially better high-salinity screening model than Davies or B-dot.
 * </p>
 *
 * <p>
 * This class is intentionally called a <em>binary</em> model. It does not include the complete multicomponent Pitzer
 * theta and psi interaction matrix. For brines with large non-NaCl fractions, validate against a full Pitzer engine
 * (for example PHREEQC) or a rigorously speciated NeqSim electrolyte equation of state.
 * </p>
 *
 * <p>
 * References: Pitzer, J. Phys. Chem. 77 (1973) 268-277, DOI 10.1021/j100621a026; Hamer and Wu, J. Phys. Chem. Ref. Data
 * 1 (1972) 1047-1100, DOI 10.1063/1.3253108; PHREEQC version 3 {@code pitzer.dat}.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public final class PitzerScaleActivityModel implements Serializable {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Scale-forming ions supported by the binary parameter set. */
  public enum Ion {
    /** Sodium ion. */
    SODIUM,
    /** Chloride ion. */
    CHLORIDE,
    /** Calcium ion. */
    CALCIUM,
    /** Barium ion. */
    BARIUM,
    /** Strontium ion. */
    STRONTIUM,
    /** Ferrous ion. */
    FERROUS,
    /** Sulphate ion. */
    SULPHATE,
    /** Carbonate ion. */
    CARBONATE,
    /** Bicarbonate ion. */
    BICARBONATE
  }

  /** Binary salts represented by PHREEQC Pitzer parameters. */
  public enum Salt {
    /** Sodium chloride. */
    NACL(1, 1, 1, 1, 2.0, 0.0,
        p(0.07534, 9598.4, 35.48, -0.058731, 1.798e-5, -5.0e5),
        p(0.2769, 1.377e4, 46.8, -0.069512, 2.0e-5, -7.4823e5), p(0.0),
        p(0.00148, -120.5, -0.2081, 0.0, 1.166e-7, 11121.0)),
    /** Calcium chloride. */
    CACL2(1, 2, 2, 1, 2.0, 12.0, p(0.3159, 0.0, 0.0, -3.27e-4, 1.4e-7, 0.0),
        p(1.614, 0.0, 0.0, 7.63e-3, -8.19e-7, 0.0), p(-1.13, 0.0, 0.0, -0.0476, 0.0, 0.0),
        p(1.4e-4, -57.0, -0.098, -7.83e-4, 7.18e-7, 0.0)),
    /** Barium chloride. */
    BACL2(1, 2, 2, 1, 2.0, 0.0, p(0.5268, 0.0, 0.0, 0.0, 0.0, 4.75e4),
        p(0.687, 0.0, 0.0, 0.01417, 0.0, 0.0), p(0.0), p(-0.143, -114.5, 0.0, 0.0, 0.0, 0.0)),
    /** Strontium chloride. */
    SRCL2(1, 2, 2, 1, 2.0, 0.0, p(0.2858, 0.0, 0.0, 7.17e-4, 0.0, 0.0),
        p(1.667, 0.0, 0.0, 2.8425e-3, 0.0, 0.0), p(0.0), p(-0.0013, 0.0, 0.0, 0.0, 0.0, 0.0)),
    /** Ferrous chloride. */
    FECL2(1, 2, 2, 1, 2.0, 0.0, p(0.335925), p(1.53225), p(0.0), p(-0.00860725)),
    /** Sodium sulphate. */
    NA2SO4(2, 1, 1, 2, 2.0, 0.0, p(0.0273, 0.0, -5.8, 0.00989, 0.0, -1.563e5),
        p(0.956, 2663.0, 0.0, 0.01158, 0.0, -3.194e5), p(0.0),
        p(0.003418, -384.0, 0.0, -8.451e-4, 0.0, 5.177e4)),
    /** Sodium carbonate. */
    NA2CO3(2, 1, 1, 2, 2.0, 0.0, p(0.0399, 0.0, 0.0, 0.00179, 0.0, 0.0),
        p(1.389, 0.0, 0.0, 0.00205, 0.0, 0.0), p(0.0), p(0.0044)),
    /** Sodium bicarbonate. */
    NAHCO3(1, 1, 1, 1, 2.0, 12.0, p(-0.018, 0.0, 0.0, -2.3e-4, 0.0, 0.0),
        p(-0.0101, 0.0, 0.0, 0.00172, 0.0, 0.0), p(6.84, 0.0, 0.0, -0.00711, 0.0, 0.0), p(0.0));

    private final int cationStoichiometry;
    private final int anionStoichiometry;
    private final int cationCharge;
    private final int anionCharge;
    private final double alpha1;
    private final double alpha2;
    private final double[] beta0;
    private final double[] beta1;
    private final double[] beta2;
    private final double[] cPhi;

    Salt(int cationStoichiometry, int anionStoichiometry, int cationCharge, int anionCharge, double alpha1,
        double alpha2, double[] beta0, double[] beta1, double[] beta2, double[] cPhi) {
      this.cationStoichiometry = cationStoichiometry;
      this.anionStoichiometry = anionStoichiometry;
      this.cationCharge = cationCharge;
      this.anionCharge = anionCharge;
      this.alpha1 = alpha1;
      this.alpha2 = alpha2;
      this.beta0 = beta0;
      this.beta1 = beta1;
      this.beta2 = beta2;
      this.cPhi = cPhi;
    }
  }

  private static final double REFERENCE_TEMPERATURE_K = 298.15;
  private static final double PITZER_B = 1.2;
  private static final double MIN_IONIC_STRENGTH = 1.0e-12;

  private final double temperatureK;
  private final double ionicStrengthMolal;
  private final double debyeHuckelA;
  private final Map<Ion, Double> ionActivityCoefficients = new EnumMap<Ion, Double>(Ion.class);

  /**
   * Creates a Pitzer binary model at the requested conditions.
   *
   * @param temperatureK temperature in kelvin
   * @param ionicStrengthMolal ionic strength in mol/kg water
   * @param debyeHuckelA Debye-Huckel A coefficient in log10 units at the same temperature
   */
  public PitzerScaleActivityModel(double temperatureK, double ionicStrengthMolal, double debyeHuckelA) {
    if (!(temperatureK > 0.0)) {
      throw new IllegalArgumentException("temperatureK must be positive");
    }
    if (ionicStrengthMolal < 0.0 || Double.isNaN(ionicStrengthMolal)) {
      throw new IllegalArgumentException("ionicStrengthMolal must be non-negative");
    }
    this.temperatureK = temperatureK;
    this.ionicStrengthMolal = ionicStrengthMolal;
    this.debyeHuckelA = debyeHuckelA;
    calculateIonActivityCoefficients();
  }

  /**
   * Returns a binary mean molal activity coefficient.
   *
   * @param salt binary salt
   * @param saltMolality stoichiometric salt molality in mol/kg water
   * @return mean molal activity coefficient
   */
  public double getMeanActivityCoefficient(Salt salt, double saltMolality) {
    if (saltMolality < 0.0 || Double.isNaN(saltMolality)) {
      throw new IllegalArgumentException("saltMolality must be non-negative");
    }
    if (saltMolality == 0.0) {
      return 1.0;
    }

    double ionicStrength = 0.5 * saltMolality
        * (salt.cationStoichiometry * salt.cationCharge * salt.cationCharge
            + salt.anionStoichiometry * salt.anionCharge * salt.anionCharge);
    double sqrtI = Math.sqrt(Math.max(ionicStrength, MIN_IONIC_STRENGTH));
    double aPhi = debyeHuckelA * Math.log(10.0) / 3.0;
    double fGamma = -aPhi
        * (sqrtI / (1.0 + PITZER_B * sqrtI) + 2.0 / PITZER_B * Math.log(1.0 + PITZER_B * sqrtI));

    double beta0 = temperatureParameter(salt.beta0, temperatureK);
    double beta1 = temperatureParameter(salt.beta1, temperatureK);
    double beta2 = temperatureParameter(salt.beta2, temperatureK);
    double bGamma = 2.0 * beta0 + betaGammaTerm(beta1, salt.alpha1, ionicStrength);
    if (salt.alpha2 > 0.0 && Math.abs(beta2) > 0.0) {
      bGamma += betaGammaTerm(beta2, salt.alpha2, ionicStrength);
    }

    int nu = salt.cationStoichiometry + salt.anionStoichiometry;
    double bFactor = 2.0 * salt.cationStoichiometry * salt.anionStoichiometry / nu;
    double cFactor = 3.0 * Math.pow(salt.cationStoichiometry * salt.anionStoichiometry, 1.5) / nu;
    double cPhi = temperatureParameter(salt.cPhi, temperatureK);
    double lnGamma = Math.abs(salt.cationCharge * salt.anionCharge) * fGamma
        + saltMolality * bFactor * bGamma + saltMolality * saltMolality * cFactor * cPhi;
    return Math.exp(lnGamma);
  }

  /**
   * Returns a trace-ion activity coefficient mapped at the configured equivalent ionic strength.
   *
   * @param ion requested ion
   * @return molal activity coefficient
   */
  public double getIonActivityCoefficient(Ion ion) {
    Double value = ionActivityCoefficients.get(ion);
    return value == null ? Double.NaN : value.doubleValue();
  }

  /**
   * Returns the activity-coefficient product for a scale mineral.
   *
   * @param cation scale-forming cation
   * @param anion scale-forming anion
   * @return product gamma(cation) times gamma(anion)
   */
  public double getActivityCoefficientProduct(Ion cation, Ion anion) {
    return getIonActivityCoefficient(cation) * getIonActivityCoefficient(anion);
  }

  /**
   * Returns the model ionic strength.
   *
   * @return ionic strength in mol/kg water
   */
  public double getIonicStrengthMolal() {
    return ionicStrengthMolal;
  }

  private void calculateIonActivityCoefficients() {
    double i = Math.max(ionicStrengthMolal, MIN_IONIC_STRENGTH);
    double gammaNaCl = meanAtEquivalentIonicStrength(Salt.NACL, i);
    ionActivityCoefficients.put(Ion.SODIUM, gammaNaCl);
    ionActivityCoefficients.put(Ion.CHLORIDE, gammaNaCl);

    putDivalentCation(Ion.CALCIUM, Salt.CACL2, i, gammaNaCl);
    putDivalentCation(Ion.BARIUM, Salt.BACL2, i, gammaNaCl);
    putDivalentCation(Ion.STRONTIUM, Salt.SRCL2, i, gammaNaCl);
    putDivalentCation(Ion.FERROUS, Salt.FECL2, i, gammaNaCl);
    putDivalentAnion(Ion.SULPHATE, Salt.NA2SO4, i, gammaNaCl);
    putDivalentAnion(Ion.CARBONATE, Salt.NA2CO3, i, gammaNaCl);

    double gammaNaHco3 = meanAtEquivalentIonicStrength(Salt.NAHCO3, i);
    ionActivityCoefficients.put(Ion.BICARBONATE, gammaNaHco3 * gammaNaHco3 / gammaNaCl);
  }

  private void putDivalentCation(Ion ion, Salt salt, double ionicStrength, double gammaChloride) {
    double mean = meanAtEquivalentIonicStrength(salt, ionicStrength);
    ionActivityCoefficients.put(ion, Math.pow(mean, 3.0) / (gammaChloride * gammaChloride));
  }

  private void putDivalentAnion(Ion ion, Salt salt, double ionicStrength, double gammaSodium) {
    double mean = meanAtEquivalentIonicStrength(salt, ionicStrength);
    ionActivityCoefficients.put(ion, Math.pow(mean, 3.0) / (gammaSodium * gammaSodium));
  }

  private double meanAtEquivalentIonicStrength(Salt salt, double ionicStrength) {
    double ionicStrengthPerMolality = 0.5
        * (salt.cationStoichiometry * salt.cationCharge * salt.cationCharge
            + salt.anionStoichiometry * salt.anionCharge * salt.anionCharge);
    return getMeanActivityCoefficient(salt, ionicStrength / ionicStrengthPerMolality);
  }

  private static double betaGammaTerm(double beta, double alpha, double ionicStrength) {
    if (alpha <= 0.0 || Math.abs(beta) == 0.0) {
      return 0.0;
    }
    double x = alpha * Math.sqrt(Math.max(ionicStrength, MIN_IONIC_STRENGTH));
    double numerator = 1.0 - (1.0 + x - 0.5 * x * x) * Math.exp(-x);
    return 2.0 * beta * numerator / (alpha * alpha * Math.max(ionicStrength, MIN_IONIC_STRENGTH));
  }

  /**
   * Evaluates the PHREEQC six-term temperature expression around 298.15 K.
   *
   * @param coefficients A0 through A5
   * @param temperatureK temperature in kelvin
   * @return parameter value at temperature
   */
  static double temperatureParameter(double[] coefficients, double temperatureK) {
    double tr = REFERENCE_TEMPERATURE_K;
    return coefficients[0] + coefficients[1] * (1.0 / temperatureK - 1.0 / tr)
        + coefficients[2] * Math.log(temperatureK / tr) + coefficients[3] * (temperatureK - tr)
        + coefficients[4] * (temperatureK * temperatureK - tr * tr)
        + coefficients[5] * (1.0 / (temperatureK * temperatureK) - 1.0 / (tr * tr));
  }

  private static double[] p(double... values) {
    double[] coefficients = new double[6];
    System.arraycopy(values, 0, coefficients, 0, Math.min(values.length, coefficients.length));
    return coefficients;
  }
}
