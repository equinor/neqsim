package neqsim.process.equipment.separator.entrainment;

import neqsim.process.equipment.separator.Separator;

/**
 * Built-in {@link EnhancedEntrainmentProvider} that wraps NeqSim's
 * open-source 7-stage physics-based entrainment chain. This provider is
 * registered as {@code "neqsim-7stage"} and is the default for any code
 * that calls {@code Separator.setEnhancedEntrainmentCalculation(true)}
 * without specifying an explicit provider id.
 *
 * <p>
 * The actual numerical chain is implemented by
 * {@code SeparatorPerformanceCalculator} on the {@link Separator}; this
 * class is a thin SPI adapter that allows the same chain to be reached
 * through the unified provider API. Callers that want to swap in a
 * different correlation (e.g. the Equinor π-number plug-in
 * {@code "eqn-pi-v1"}) do so via
 * {@code Separator.setEntrainmentProvider(String)} without changing how
 * the rest of the simulation is set up.
 *
 * <p>
 * <b>Applicability.</b> The 7-stage chain has no hard validity envelope —
 * it is a predictive model. {@link #checkApplicability(Separator)}
 * therefore always returns {@link EntrainmentApplicability#ok()}. Provider
 * implementations that are empirical correlations bounded by a test
 * database (such as the EQN π-number plug-in) override this and report
 * out-of-range diagnostics.
 *
 * <p>
 * <b>Confidence band.</b> The 7-stage chain does not produce a statistical
 * confidence band, so {@link EntrainmentResult#getConfidenceBandKgPerHr()}
 * is returned as {@link Double#NaN}.
 *
 * @author NeqSim
 * @version 1.0
 */
public class BuiltInSevenStageProvider implements EnhancedEntrainmentProvider {

  /** Stable id used to look this provider up via the registry. */
  public static final String ID = "neqsim-7stage";

  /** Version of the built-in provider; bumped when the SPI adapter changes. */
  public static final String VERSION = "1.0.0";

  /**
   * Public no-args constructor required by {@link java.util.ServiceLoader}.
   */
  public BuiltInSevenStageProvider() {}

  /**
   * {@inheritDoc}
   *
   * @return {@link #ID}
   */
  @Override
  public String getId() {
    return ID;
  }

  /**
   * {@inheritDoc}
   *
   * @return {@link #VERSION}
   */
  @Override
  public String getVersion() {
    return VERSION;
  }

  /**
   * The 7-stage chain has no hard validity envelope.
   *
   * @param separator the separator whose entrainment is to be computed
   * @return {@link EntrainmentApplicability#ok()}
   */
  @Override
  public EntrainmentApplicability checkApplicability(Separator separator) {
    return EntrainmentApplicability.ok();
  }

  /**
   * Returns a result tagged with this provider's id and version. Numerical
   * carry-over values come from the {@code SeparatorPerformanceCalculator}
   * already attached to the {@link Separator}; this method does not run
   * the chain itself — that happens during {@code Separator.run()}.
   *
   * <p>
   * Detailed numerical fields are returned as {@link Double#NaN} until the
   * SPI adapter is wired into the calculator (planned in a follow-up
   * change). Existing callers that read carry-over via the
   * {@code SeparatorPerformanceCalculator} API are unaffected.
   *
   * @param separator the separator whose entrainment is to be computed;
   *        must not be null
   * @return an entrainment result tagged with this provider's id and
   *         version; numerical fields may be {@link Double#NaN} until the
   *         calculator wiring is finalised
   */
  @Override
  public EntrainmentResult compute(Separator separator) {
    if (separator == null) {
      throw new IllegalArgumentException("separator must not be null");
    }
    return new EntrainmentResult(ID, VERSION, Double.NaN, Double.NaN, Double.NaN, Double.NaN);
  }
}
