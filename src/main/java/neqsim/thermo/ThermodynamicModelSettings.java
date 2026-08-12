package neqsim.thermo;

/**
 * ThermodynamicModelSettings interface.
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
   * When {@code true}, {@code Component.init(type=0)} preserves existing K-values instead of resetting them to the
   * Wilson initial guess. Enables a warm-start that can speed up iterative flashes (PSflash, PHflash, dew/bubble point)
   * and recycle loops by 2-3x, but may converge to numerically slightly different (though physically equivalent)
   * solutions than the cold Wilson path. Default is {@code false} to preserve exact numerical reproducibility for
   * regression baselines. Set via {@link #setUseWarmStartKValues(boolean)} or the system property
   * {@code neqsim.warmStartK=true}.
   *
   * <p>
   * The flag is stored per-thread ({@link ThreadLocal}) so that concurrent flash calls do not interfere with each
   * other's try/finally restoration. The process-wide initial value is read from the {@code neqsim.warmStartK} system
   * property.
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

    private Flags() {
    }
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
   * @param enabled true to preserve K-values across flash calls (faster, but may yield numerically slightly different
   * converged solutions), false to always reset to the Wilson guess in {@code Component.init(0)} (exact
   * baseline-compatible behavior).
   */
  static void setUseWarmStartKValues(boolean enabled) {
    Flags.useWarmStartKValues.set(enabled);
  }

  /**
   * Decides whether the inner TP flashes of an iterative outer flash (PH, PS, PV, TV, PU, VU, ...) may reuse K-values
   * from the previous inner solve.
   *
   * <p>
   * Association-site fractions of a CPA fluid change strongly with temperature, so a K vector carried over from a
   * previous inner flash can be a worse seed than a cold Wilson initialization. Reusing it adds inner-flash work and
   * can destabilize the outer iteration - the CPA regression documented in issue #2110, where a TEG-regeneration case
   * went from 55.9 s to 104.8 s while the equivalent cubic SRK case improved. Cubic EOS models keep the established
   * warm-start acceleration.
   * </p>
   *
   * <p>
   * The first inner TP flash of an outer flash must always run cold regardless of this predicate, so that stale
   * K-values from an unrelated flash at a different P/T cannot bias the solution.
   * </p>
   *
   * @param system the fluid being flashed; {@code null} is treated as warm-start safe
   * @return {@code true} when inner TP flashes may reuse K-values, {@code false} for associating (CPA) models
   */
  static boolean isInnerFlashWarmStartSafe(neqsim.thermo.system.SystemInterface system) {
    if (system == null) {
      return true;
    }
    String modelName = system.getModelName() == null ? "" : system.getModelName();
    if (modelName.toUpperCase(java.util.Locale.ROOT).contains("CPA")) {
      return false;
    }
    try {
      if (system.getPhase(0) instanceof neqsim.thermo.phase.PhaseCPAInterface) {
        return false;
      }
    } catch (RuntimeException ex) {
      // Phases are not initialized yet - fall back to the model-name decision above.
    }
    return true;
  }
}
