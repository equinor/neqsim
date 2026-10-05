package neqsim.thermo;

/**
 * Physical reference constants for ordinary water from IAPWS G5-01(2026), section 4.1.
 *
 * <p>
 * Source: <a href="https://iapws.org/documents/release/fundam.download">IAPWS fundamental constants guideline</a>.
 * These values describe ordinary water, not isotopically substituted water. They are distinct from the legacy
 * parameters returned by component {@code getTC()} and {@code getPC()} methods, which also drive existing EOS and
 * empirical correlations. Substituting these reference values into those models requires separate qualification.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class WaterReferenceConstants {
  /** Ordinary-water critical temperature in kelvin. */
  public static final double CRITICAL_TEMPERATURE_K = 647.096;

  /** Ordinary-water critical absolute pressure in pascals. */
  public static final double CRITICAL_PRESSURE_PA = 22064000.0;

  /** Ordinary-water critical absolute pressure in bar. */
  public static final double CRITICAL_PRESSURE_BARA = 220.64;

  /** Prevents instantiation of this constants holder. */
  private WaterReferenceConstants() {
  }
}
