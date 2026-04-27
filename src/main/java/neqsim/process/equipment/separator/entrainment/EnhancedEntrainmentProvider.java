package neqsim.process.equipment.separator.entrainment;

import neqsim.process.equipment.separator.Separator;

/**
 * Service Provider Interface (SPI) for pluggable entrainment / carry-over
 * models in NeqSim. Implementations are discovered at runtime through
 * {@link java.util.ServiceLoader} and selected on a {@code Separator} via
 * {@code Separator.setEntrainmentProvider(String)}.
 *
 * <p>
 * <b>Why this interface exists.</b> NeqSim ships with a single open-source
 * "built-in" entrainment model — the 7-stage physics-based chain documented in
 * {@code separator-entrainment-modeling.md}. Some operators have additional,
 * proprietary correlations (e.g. Equinor's π-number regression fitted to the
 * EQN scrubber test database) that cannot be open-sourced because they
 * embed vendor or test-rig data. This SPI allows such providers to be
 * delivered as separate, signed JARs that depend on public NeqSim only at
 * the SPI level — no fork, no patching, no public disclosure of the
 * proprietary content. If a private provider JAR is on the classpath
 * {@link java.util.ServiceLoader} discovers it automatically; if not, code
 * that requested it gets a clear error.
 *
 * <p>
 * <b>Public / private split policy.</b> The public NeqSim repo holds the
 * maximum amount of structure that does not leak proprietary content:
 * SPI interface, method signatures, result / applicability data classes,
 * registry plumbing, and provider <i>ids</i> (the string {@code "eqn-pi-v1"}
 * is public; the correlation behind it is not). Only the numerical
 * implementation, regression coefficients, validity envelopes, vendor-tagged
 * data and correctness tests live in the private repo. Public and private
 * are kept architecturally aligned — same package layout, same class names
 * where possible — so a maintainer reading the public source can see
 * <i>what</i> approach (4) requires and returns without being able to
 * reproduce <i>how</i> it computes.
 *
 * <p>
 * <b>Stability contract.</b> This interface is API-stable. Existing methods
 * will not change signature; new capabilities are added only as
 * {@code default} methods so existing implementations (including private
 * plug-ins) keep compiling against new public NeqSim releases without
 * recompilation.
 *
 * <p>
 * <b>Implementer responsibilities.</b> An implementation must:
 * <ul>
 *   <li>return a globally unique, stable {@link #getId()} (no spaces, no
 *       version number — version goes in {@link #getVersion()})</li>
 *   <li>be deterministic given the same {@code Separator} state — providers
 *       are called from the steady-state and transient solvers and must not
 *       mutate global state</li>
 *   <li>fail fast through {@link #checkApplicability(Separator)} when inputs
 *       are outside the validity envelope, rather than silently extrapolate</li>
 *   <li>register through a
 *       {@code META-INF/services/neqsim.process.equipment.separator.entrainment.EnhancedEntrainmentProvider}
 *       file in its JAR so {@code ServiceLoader} picks it up</li>
 * </ul>
 *
 * @author NeqSim
 * @version 1.0
 */
public interface EnhancedEntrainmentProvider {

  /**
   * SPI revision implemented by this provider. The default is
   * {@code 1} — the revision that ships with the first public release of
   * this interface. When the SPI gains semantic capabilities that an old
   * provider cannot express, public NeqSim bumps {@code CURRENT_API_VERSION}
   * and providers built against the new revision override this method to
   * return the higher value. The {@code EntrainmentProviderRegistry}
   * refuses providers that declare an api version higher than the core
   * supports, so version mismatches fail loudly at registration rather
   * than silently at runtime.
   *
   * <p>
   * Adding new {@code default} methods to this interface alone does
   * <i>not</i> require an api-version bump — only changes that affect what
   * a provider must compute or return.
   *
   * @return the SPI revision this provider was built against; the
   *         current public revision is
   *         {@link EntrainmentProviderRegistry#CURRENT_API_VERSION}
   */
  default int getApiVersion() {
    return 1;
  }

  /**
   * Returns the globally unique, stable identifier of this provider.
   *
   * <p>
   * Examples: {@code "neqsim-7stage"} (built-in), {@code "eqn-pi-v1"}
   * (Equinor private plug-in v1), {@code "eqn-pi-v2"} (v2). The id is the
   * argument to {@code Separator.setEntrainmentProvider(String)}.
   *
   * @return the provider id, must be non-null and constant for the lifetime
   *         of the JVM
   */
  String getId();

  /**
   * Returns the version string of this provider.
   *
   * <p>
   * Recommended format: semantic versioning, e.g. {@code "1.0.0"}. The
   * version is recorded on the {@link EntrainmentResult} so plant historians
   * and reports can tag which method produced a number.
   *
   * @return the provider version, must be non-null
   */
  String getVersion();

  /**
   * Checks whether this provider's correlation is applicable to the supplied
   * separator configuration.
   *
   * <p>
   * Implementations must inspect the geometry, internals selection and
   * operating conditions of {@code separator} and report any input that
   * falls outside their regression / validity envelope. The aggregate
   * applicability flag is true only if every input is in range.
   *
   * @param separator the separator whose entrainment is to be computed; must
   *        not be null
   * @return an applicability report describing in/out of envelope status
   *         and per-input diagnostics; never null
   */
  EntrainmentApplicability checkApplicability(Separator separator);

  /**
   * Computes the entrainment / carry-over result for the supplied separator.
   *
   * <p>
   * Implementations should call {@link #checkApplicability(Separator)}
   * internally and decide whether to throw, extrapolate with a widened
   * confidence band, or return {@link Double#NaN} carry-over values when
   * inputs are out of range. Whichever policy is chosen must be documented
   * on the implementing class.
   *
   * @param separator the separator whose entrainment is to be computed; must
   *        not be null
   * @return the computed entrainment result tagged with this provider's id
   *         and version; never null
   * @throws RuntimeException if the provider cannot produce a result (e.g.
   *         missing required input, hard out-of-range failure, missing data
   *         file at runtime)
   */
  EntrainmentResult compute(Separator separator);
}
