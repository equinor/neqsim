package neqsim.process.equipment.separator.entrainment;

import neqsim.process.equipment.separator.Separator;

/**
 * Entrainment model that reports no carry-over at all.
 *
 * <p>
 * Registered as {@code "zero"}. Use it when carry-over is known to be negligible, when isolating another effect in a
 * study, or as a deliberate baseline against which another model is compared. It is always applicable because it makes
 * no claim about the separator.
 * </p>
 *
 * <p>
 * This is an assumption, not a prediction: it returns zero regardless of gas load, internals or overload. A separator
 * running far beyond its capacity still reports zero carry-over under this model.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class ZeroCarryOverProvider implements EnhancedEntrainmentProvider {

  /** Stable id used to look this model up through the registry. */
  public static final String ID = "zero";

  /** Version of this model. */
  public static final String VERSION = "1.0.0";

  /** Public no-args constructor required by {@link java.util.ServiceLoader}. */
  public ZeroCarryOverProvider() {
  }

  /** {@inheritDoc} */
  @Override
  public String getId() {
    return ID;
  }

  /** {@inheritDoc} */
  @Override
  public String getVersion() {
    return VERSION;
  }

  /**
   * Always applicable; this model makes no claim that could fall out of range.
   *
   * @param separator the separator whose entrainment is requested; unused
   * @return {@link EntrainmentApplicability#ok()}
   */
  @Override
  public EntrainmentApplicability checkApplicability(Separator separator) {
    return EntrainmentApplicability.ok();
  }

  /**
   * Returns zero carry-over on every channel.
   *
   * @param separator the separator whose entrainment is requested; must not be null
   * @return a result with all carry-over values 0.0 and a zero confidence band
   * @throws IllegalArgumentException if {@code separator} is null
   */
  @Override
  public EntrainmentResult compute(Separator separator) {
    if (separator == null) {
      throw new IllegalArgumentException("separator must not be null");
    }
    return new EntrainmentResult(ID, VERSION, 0.0, 0.0, 0.0, 0.0);
  }
}
