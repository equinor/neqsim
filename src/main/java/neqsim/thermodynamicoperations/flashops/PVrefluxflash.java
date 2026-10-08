package neqsim.thermodynamicoperations.flashops;

import neqsim.thermo.system.SystemInterface;

/**
 * Constant-pressure terminal ratio flash using a bracketed vapor-fraction temperature search.
 *
 * <p>
 * Phase zero specifies liquid/vapor (condenser L/D); phase one specifies vapor/liquid (reboiler V/B).
 * </p>
 *
 * @author even solbraa
 * @version $Id: $Id
 */
public class PVrefluxflash extends Flash {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  int refluxPhase = 0;
  double refluxSpec = 0.5;

  /**
   * Constructor for PVrefluxflash.
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param refluxSpec finite, non-negative phase flow ratio
   * @param refluxPhase denominator phase: zero for gas, one for liquid
   * @throws IllegalArgumentException if the ratio or denominator phase is invalid
   */
  public PVrefluxflash(SystemInterface system, double refluxSpec, int refluxPhase) {
    if (!Double.isFinite(refluxSpec) || refluxSpec < 0.0 || (refluxPhase != 0 && refluxPhase != 1)) {
      throw new IllegalArgumentException("Reflux flash requires a finite non-negative ratio and phase 0 or 1");
    }
    this.system = system;
    this.refluxSpec = refluxSpec;
    this.refluxPhase = refluxPhase;
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    // Express the terminal ratio as a vapor-fraction target. The bracketed PVF
    // search can enter the two-phase region from either a vapor or liquid feed.
    // An unbracketed ratio secant has no slope in a single-phase region and the
    // condenser's L/D residual has the opposite temperature direction to V/B.
    double vaporFraction = refluxPhase == 0 ? 1.0 / (1.0 + refluxSpec) : 1.0 - 1.0 / (1.0 + refluxSpec);
    new PVFflash(system, vaporFraction).run();
  }

  /** {@inheritDoc} */
  @Override
  public org.jfree.chart.JFreeChart getJFreeChart(String name) {
    return null;
  }
}
