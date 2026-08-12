package neqsim.process.corrosion;

/**
 * Alkanolamine buffers supported by {@link AmineBufferedPH}, with their protonation equilibrium correlations.
 *
 * <p>
 * The dissociation of the protonated amine, {@code AmineH+ + H2O <-> Amine + H3O+}, follows
 * </p>
 *
 * <p>
 * {@code ln K = K1 + K2/T + K3*ln(T) + K4*T}
 * </p>
 *
 * <p>
 * with the coefficients taken from the same NeqSim reaction database (<i>REACTIONDATA.csv</i>) that the rigorous
 * electrolyte model uses, so this screening calculation and a full electrolyte flash rest on the same underlying data.
 * </p>
 *
 * <p>
 * The database rows are not all expressed on the same concentration basis. The MDEA row is already a molality-basis
 * constant, whereas the DEA row is mole-fraction based and requires the constant offset
 * {@code log10(1000 / M_water) = 1.7444} to convert it. The {@code basisOffset} field carries that conversion, and each
 * entry is verified against its literature pKa at 25 &deg;C in the unit tests.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public enum BufferAmine {
  /** Diethanolamine; pKa 8.92 at 25 &deg;C. Coefficients from Austgen (1989), mole-fraction basis. */
  DEA("diethanolamine", -13.34, -4218.7, 0.0, 0.009872, 1.7444),
  /** Methyldiethanolamine; pKa 8.52 at 25 &deg;C. Coefficients from Austgen (1989), molality basis. */
  MDEA("methyldiethanolamine", -50.77, -4044.8, 7.848, 0.0, 0.0);

  /** Natural logarithm of 10, for converting between ln K and pK. */
  private static final double LN10 = Math.log(10.0);

  private final String fullName;
  private final double k1;
  private final double k2;
  private final double k3;
  private final double k4;
  private final double basisOffset;

  /**
   * Construct an amine entry.
   *
   * @param fullName the full chemical name
   * @param k1 constant term of the ln K correlation
   * @param k2 reciprocal-temperature coefficient [K]
   * @param k3 logarithmic-temperature coefficient
   * @param k4 linear-temperature coefficient [1/K]
   * @param basisOffset offset in pK units converting the correlation to a molality basis
   */
  BufferAmine(String fullName, double k1, double k2, double k3, double k4, double basisOffset) {
    this.fullName = fullName;
    this.k1 = k1;
    this.k2 = k2;
    this.k3 = k3;
    this.k4 = k4;
    this.basisOffset = basisOffset;
  }

  /**
   * Gets the full chemical name of the amine.
   *
   * @return the full name
   */
  public String getFullName() {
    return fullName;
  }

  /**
   * Compute the molality-basis pKa of the protonated amine at a given temperature.
   *
   * @param temperatureK absolute temperature [K]; must be positive
   * @return the pKa, dimensionless
   * @throws IllegalArgumentException if the temperature is not positive
   */
  public double getPKa(double temperatureK) {
    if (!(temperatureK > 0.0)) {
      throw new IllegalArgumentException("Temperature must be positive");
    }
    double lnK = k1 + k2 / temperatureK + k3 * Math.log(temperatureK) + k4 * temperatureK;
    return -lnK / LN10 - basisOffset;
  }
}
