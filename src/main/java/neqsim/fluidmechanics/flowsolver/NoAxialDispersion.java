package neqsim.fluidmechanics.flowsolver;

/**
 * Default physical-dispersion model: pure advection with {@code D_ax = 0}.
 *
 * <p>
 * Deserialization returns {@link #INSTANCE}, preserving the canonical immutable default across serialized process
 * models. The public constructor remains available for straightforward Java and JPype configuration.
 * </p>
 */
public final class NoAxialDispersion implements AxialDispersionModel {
  private static final long serialVersionUID = 1000L;

  /** Shared immutable default instance. */
  public static final NoAxialDispersion INSTANCE = new NoAxialDispersion();

  /** Public constructor for straightforward Java and JPype configuration. */
  public NoAxialDispersion() {
  }

  /**
   * Preserve the canonical default instance after Java deserialization.
   *
   * @return shared immutable default instance
   */
  private Object readResolve() {
    return INSTANCE;
  }

  /** {@inheritDoc} */
  @Override
  public double getCoefficientM2PerSecond(int cellIndex, double cellLengthM, double cellMassKg,
      double massFlowKgPerSecond) {
    return 0.0;
  }

  /** {@inheritDoc} */
  @Override
  public String getName() {
    return "none";
  }

  /** {@inheritDoc} */
  @Override
  public boolean isEnabled() {
    return false;
  }
}
