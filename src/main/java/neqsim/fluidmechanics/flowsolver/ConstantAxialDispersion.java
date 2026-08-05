package neqsim.fluidmechanics.flowsolver;

/** User-specified constant physical axial-dispersion coefficient. */
public final class ConstantAxialDispersion implements AxialDispersionModel {
  private static final long serialVersionUID = 1000L;
  private final double coefficientM2PerSecond;

  /**
   * Create a constant physical-dispersion model.
   *
   * @param coefficientM2PerSecond finite non-negative coefficient in m2/s
   * @throws IllegalArgumentException if the coefficient is negative or non-finite
   */
  public ConstantAxialDispersion(double coefficientM2PerSecond) {
    if (!Double.isFinite(coefficientM2PerSecond) || coefficientM2PerSecond < 0.0) {
      throw new IllegalArgumentException("Physical axial dispersion must be finite and non-negative in m2/s.");
    }
    this.coefficientM2PerSecond = coefficientM2PerSecond;
  }

  /** {@inheritDoc} */
  @Override
  public double getCoefficientM2PerSecond(int cellIndex, double cellLengthM, double cellMassKg,
      double massFlowKgPerSecond) {
    return coefficientM2PerSecond;
  }

  /** @return configured constant physical coefficient in m2/s */
  public double getConstantCoefficientM2PerSecond() {
    return coefficientM2PerSecond;
  }

  /** {@inheritDoc} */
  @Override
  public String getName() {
    return "constant";
  }

  /** {@inheritDoc} */
  @Override
  public boolean isEnabled() {
    return coefficientM2PerSecond > 0.0;
  }
}
