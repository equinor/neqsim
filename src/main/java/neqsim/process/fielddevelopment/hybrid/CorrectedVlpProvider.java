package neqsim.process.fielddevelopment.hybrid;

import neqsim.process.fielddevelopment.reservoir.VlpProvider;

/**
 * Fast lift curve (surrogate) that is periodically corrected against a high-fidelity reference model.
 *
 * <p>
 * This is the link between the fast loop and the slow loop of the hybrid production model. The surrogate, typically a
 * {@link VfpTableLookup}, is evaluated at every iteration of every time step. The reference, typically a live
 * multiphase pipe or tubing model, is evaluated only when {@link #calibrate(double, double, double, double, double)} is
 * called at the current operating point; the difference is stored as an additive pressure offset and applied to the
 * surrogate until the next calibration. With {@link #setUseReference(boolean)} the reference is used directly for every
 * evaluation (full network mode).
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class CorrectedVlpProvider implements VlpProvider {
  private final VlpProvider surrogate;
  private VlpProvider reference;
  private double offsetBar = 0.0;
  private boolean useReference = false;
  private long surrogateEvaluations = 0;
  private long referenceEvaluations = 0;
  private int calibrations = 0;

  /**
   * Creates a corrected provider without a reference model; it then behaves as the surrogate.
   *
   * @param surrogate fast lift model
   */
  public CorrectedVlpProvider(VlpProvider surrogate) {
    this(surrogate, null);
  }

  /**
   * Creates a corrected provider.
   *
   * @param surrogate fast lift model
   * @param reference high-fidelity lift model on the same rate basis, or null
   */
  public CorrectedVlpProvider(VlpProvider surrogate, VlpProvider reference) {
    if (surrogate == null) {
      throw new IllegalArgumentException("Surrogate lift model must not be null");
    }
    this.surrogate = surrogate;
    this.reference = reference;
  }

  /**
   * Sets the high-fidelity reference model.
   *
   * @param reference lift model on the same rate basis as the surrogate, or null to remove it
   */
  public void setReference(VlpProvider reference) {
    this.reference = reference;
  }

  /**
   * Tells whether a reference model is available.
   *
   * @return true if a reference model has been set
   */
  public boolean hasReference() {
    return reference != null;
  }

  /**
   * Selects whether every evaluation uses the reference model instead of the corrected surrogate.
   *
   * @param useReference true for full-fidelity evaluation; ignored when there is no reference model
   */
  public void setUseReference(boolean useReference) {
    this.useReference = useReference;
  }

  /**
   * Gets the pressure offset currently added to the surrogate.
   *
   * @return offset in bar (reference minus surrogate at the last calibration point)
   */
  public double getOffsetBar() {
    return offsetBar;
  }

  /**
   * Sets the pressure offset added to the surrogate.
   *
   * @param offsetBar offset in bar
   */
  public void setOffsetBar(double offsetBar) {
    this.offsetBar = offsetBar;
  }

  /**
   * Number of surrogate evaluations so far.
   *
   * @return surrogate evaluation count
   */
  public long getSurrogateEvaluations() {
    return surrogateEvaluations;
  }

  /**
   * Number of reference evaluations so far.
   *
   * @return reference evaluation count
   */
  public long getReferenceEvaluations() {
    return referenceEvaluations;
  }

  /**
   * Number of successful calibrations so far.
   *
   * @return calibration count
   */
  public int getCalibrations() {
    return calibrations;
  }

  /**
   * Evaluates the reference model.
   *
   * @param rate surface rate in Sm3/day
   * @param outletPressure outlet pressure in bara
   * @param waterRatio water ratio
   * @param gasRatio gas ratio
   * @param alq artificial lift quantity
   * @return inlet pressure of the reference model in bara, NaN without a reference or for an infeasible point
   */
  public double referencePressure(double rate, double outletPressure, double waterRatio, double gasRatio, double alq) {
    if (reference == null) {
      return Double.NaN;
    }
    referenceEvaluations++;
    return reference.bottomHolePressure(rate, outletPressure, waterRatio, gasRatio, alq);
  }

  /**
   * Evaluates the uncorrected surrogate.
   *
   * @param rate surface rate in Sm3/day
   * @param outletPressure outlet pressure in bara
   * @param waterRatio water ratio
   * @param gasRatio gas ratio
   * @param alq artificial lift quantity
   * @return inlet pressure of the surrogate in bara
   */
  public double surrogatePressure(double rate, double outletPressure, double waterRatio, double gasRatio, double alq) {
    surrogateEvaluations++;
    return surrogate.bottomHolePressure(rate, outletPressure, waterRatio, gasRatio, alq);
  }

  /**
   * Calibrates the offset at an operating point: offset = reference - surrogate.
   *
   * @param rate surface rate in Sm3/day
   * @param outletPressure outlet pressure in bara
   * @param waterRatio water ratio
   * @param gasRatio gas ratio
   * @param alq artificial lift quantity
   * @return absolute change of the offset in bar; 0 when there is no reference or the reference point is infeasible
   */
  public double calibrate(double rate, double outletPressure, double waterRatio, double gasRatio, double alq) {
    if (reference == null) {
      return 0.0;
    }
    double full = referencePressure(rate, outletPressure, waterRatio, gasRatio, alq);
    double fast = surrogatePressure(rate, outletPressure, waterRatio, gasRatio, alq);
    if (Double.isNaN(full) || Double.isInfinite(full) || Double.isNaN(fast) || Double.isInfinite(fast)) {
      return 0.0;
    }
    double previous = offsetBar;
    offsetBar = full - fast;
    calibrations++;
    return Math.abs(offsetBar - previous);
  }

  /** {@inheritDoc} */
  @Override
  public double bottomHolePressure(double rate, double outletPressure, double waterRatio, double gasRatio, double alq) {
    if (useReference && reference != null) {
      return referencePressure(rate, outletPressure, waterRatio, gasRatio, alq);
    }
    return surrogatePressure(rate, outletPressure, waterRatio, gasRatio, alq) + offsetBar;
  }
}
