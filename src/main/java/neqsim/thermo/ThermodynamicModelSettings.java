package neqsim.thermo;

/**
 * <p>
 * ThermodynamicModelSettings interface.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public interface ThermodynamicModelSettings extends java.io.Serializable {
  /** Constant <code>phaseFractionMinimumLimit=1e-12</code>. */
  static double phaseFractionMinimumLimit = 1e-12;
  /** Constant <code>MAX_NUMBER_OF_COMPONENTS=200</code>. */
  int MAX_NUMBER_OF_COMPONENTS = 200;

  /**
   * When {@code true}, {@code Component.init(type=0)} preserves existing K-values instead of
   * resetting them to the Wilson initial guess. Enables a warm-start that can speed up iterative
   * flashes (PSflash, PHflash, dew/bubble point) and recycle loops by 2-3x, but may converge to
   * numerically slightly different (though physically equivalent) solutions than the cold Wilson
   * path. Default is {@code false} to preserve exact numerical reproducibility for regression
   * baselines. Set via {@link #setUseWarmStartKValues(boolean)} or the system property
   * {@code neqsim.warmStartK=true}.
   *
   * <p>
   * The flag is stored per-thread ({@link ThreadLocal}) so that concurrent flash calls do not
   * interfere with each other's try/finally restoration. The process-wide initial value is read
   * from the {@code neqsim.warmStartK} system property.
   */
  boolean DEFAULT_USE_WARM_START_K = Boolean.getBoolean("neqsim.warmStartK");

  /** Mutable per-thread flag holder. */
  final class Flags {
    private static final ThreadLocal<Boolean> useWarmStartKValues = new ThreadLocal<Boolean>() {
      @Override
      protected Boolean initialValue() {
        return DEFAULT_USE_WARM_START_K;
      }
    };

    private Flags() {}
  }

  /**
   * Returns true if warm-start K-value preservation is enabled for the current thread.
   *
   * @return warm-start flag
   */
  static boolean isUseWarmStartKValues() {
    return Flags.useWarmStartKValues.get();
  }

  /**
   * Enable or disable warm-start K-value preservation for the current thread.
   *
   * @param enabled true to preserve K-values across flash calls (faster, but may yield numerically
   *        slightly different converged solutions), false to always reset to the Wilson guess in
   *        {@code Component.init(0)} (exact baseline-compatible behavior).
   */
  static void setUseWarmStartKValues(boolean enabled) {
    Flags.useWarmStartKValues.set(enabled);
  }
}
