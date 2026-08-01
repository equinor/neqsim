package neqsim.process.equipment.pipeline;

import java.io.Serializable;

/**
 * Discrete phase and total-mass balance for one accepted {@link TwoFluidPipe} transient call.
 *
 * <p>
 * Boundary and source terms are integrated with the same Runge-Kutta stage weights as the conservative state. The
 * signed residual is {@code final - initial - (inlet - outlet + source)}. A positive source adds mass to the reported
 * phase. Oil and water source terms include gas-liquid phase transfer splits; their sum is the liquid source.
 * </p>
 */
public final class TwoFluidMassBalanceReport implements Serializable {
  private static final long serialVersionUID = 1L;
  private static final int GAS_INDEX = 0;
  private static final int OIL_INDEX = 1;
  private static final int WATER_INDEX = 2;
  private static final int PHASE_COUNT = 3;
  private static final double RELATIVE_SCALE_FLOOR_KG = 1.0e-12;

  /** Mass aggregation to query. */
  public enum Phase {
    /** Gas phase. */
    GAS,
    /** Hydrocarbon-liquid phase. */
    OIL,
    /** Aqueous phase. */
    WATER,
    /** Oil plus water. */
    LIQUID,
    /** Gas plus oil plus water. */
    TOTAL
  }

  private final double elapsedTimeSeconds;
  private final int acceptedSubsteps;
  private final double[] initialMassKg;
  private final double[] finalMassKg;
  private final double[] inletMassKg;
  private final double[] outletMassKg;
  private final double[] sourceMassKg;

  /**
   * Create a report from phase-resolved integrals.
   *
   * @param elapsedTimeSeconds accepted elapsed time in seconds
   * @param acceptedSubsteps number of accepted internal time steps
   * @param initialMassKg initial gas, oil, and water inventory in kg
   * @param finalMassKg final gas, oil, and water inventory in kg
   * @param inletMassKg integrated gas, oil, and water inlet flux in kg
   * @param outletMassKg integrated gas, oil, and water outlet flux in kg
   * @param sourceMassKg integrated gas, oil, and water source terms in kg
   */
  TwoFluidMassBalanceReport(double elapsedTimeSeconds, int acceptedSubsteps, double[] initialMassKg,
      double[] finalMassKg, double[] inletMassKg, double[] outletMassKg, double[] sourceMassKg) {
    this.elapsedTimeSeconds = elapsedTimeSeconds;
    this.acceptedSubsteps = acceptedSubsteps;
    this.initialMassKg = requirePhaseValues(initialMassKg, "initialMassKg");
    this.finalMassKg = requirePhaseValues(finalMassKg, "finalMassKg");
    this.inletMassKg = requirePhaseValues(inletMassKg, "inletMassKg");
    this.outletMassKg = requirePhaseValues(outletMassKg, "outletMassKg");
    this.sourceMassKg = requirePhaseValues(sourceMassKg, "sourceMassKg");
  }

  private static double[] requirePhaseValues(double[] values, String name) {
    if (values == null || values.length != PHASE_COUNT) {
      throw new IllegalArgumentException(name + " must contain gas, oil, and water values");
    }
    double[] copy = values.clone();
    for (double value : copy) {
      if (!Double.isFinite(value)) {
        throw new IllegalArgumentException(name + " must contain only finite values");
      }
    }
    return copy;
  }

  private static double aggregate(double[] values, Phase phase) {
    switch (phase) {
    case GAS:
      return values[GAS_INDEX];
    case OIL:
      return values[OIL_INDEX];
    case WATER:
      return values[WATER_INDEX];
    case LIQUID:
      return values[OIL_INDEX] + values[WATER_INDEX];
    case TOTAL:
      return values[GAS_INDEX] + values[OIL_INDEX] + values[WATER_INDEX];
    default:
      throw new IllegalArgumentException("Unsupported phase aggregation: " + phase);
    }
  }

  /**
   * Get accepted elapsed time.
   *
   * @return elapsed time in seconds
   */
  public double getElapsedTimeSeconds() {
    return elapsedTimeSeconds;
  }

  /**
   * Get number of accepted internal time steps.
   *
   * @return accepted substep count
   */
  public int getAcceptedSubsteps() {
    return acceptedSubsteps;
  }

  /**
   * Get initial inventory.
   *
   * @param phase phase or aggregate
   * @return mass in kg
   */
  public double getInitialMassKg(Phase phase) {
    return aggregate(initialMassKg, phase);
  }

  /**
   * Get final inventory.
   *
   * @param phase phase or aggregate
   * @return mass in kg
   */
  public double getFinalMassKg(Phase phase) {
    return aggregate(finalMassKg, phase);
  }

  /**
   * Get inventory change.
   *
   * @param phase phase or aggregate
   * @return final minus initial mass in kg
   */
  public double getMassChangeKg(Phase phase) {
    return getFinalMassKg(phase) - getInitialMassKg(phase);
  }

  /**
   * Get time-integrated inlet flux.
   *
   * @param phase phase or aggregate
   * @return mass entering the domain in kg
   */
  public double getInletMassKg(Phase phase) {
    return aggregate(inletMassKg, phase);
  }

  /**
   * Get time-integrated outlet flux.
   *
   * @param phase phase or aggregate
   * @return mass leaving the domain in kg
   */
  public double getOutletMassKg(Phase phase) {
    return aggregate(outletMassKg, phase);
  }

  /**
   * Get time-integrated source contribution.
   *
   * @param phase phase or aggregate
   * @return signed source mass in kg
   */
  public double getSourceMassKg(Phase phase) {
    return aggregate(sourceMassKg, phase);
  }

  /**
   * Get signed discrete balance residual.
   *
   * @param phase phase or aggregate
   * @return residual in kg
   */
  public double getResidualKg(Phase phase) {
    double expectedChange = getInletMassKg(phase) - getOutletMassKg(phase) + getSourceMassKg(phase);
    return getMassChangeKg(phase) - expectedChange;
  }

  /**
   * Get absolute residual relative to the largest relevant inventory or integrated transfer.
   *
   * <p>
   * The denominator is the maximum of initial inventory, final inventory, and the sum of absolute inlet, outlet, and
   * source contributions. This definition remains finite for phase appearance from zero initial inventory.
   * </p>
   *
   * @param phase phase or aggregate
   * @return non-negative relative residual
   */
  public double getRelativeResidual(Phase phase) {
    double transferScale = Math.abs(getInletMassKg(phase)) + Math.abs(getOutletMassKg(phase))
        + Math.abs(getSourceMassKg(phase));
    double scale = Math.max(Math.abs(getInitialMassKg(phase)), Math.abs(getFinalMassKg(phase)));
    scale = Math.max(scale, transferScale);
    scale = Math.max(scale, RELATIVE_SCALE_FLOOR_KG);
    return Math.abs(getResidualKg(phase)) / scale;
  }

  /**
   * Check an absolute-or-relative balance tolerance.
   *
   * @param phase phase or aggregate
   * @param absoluteToleranceKg absolute tolerance in kg
   * @param relativeTolerance relative tolerance
   * @return true when either tolerance is met
   */
  public boolean isWithinTolerance(Phase phase, double absoluteToleranceKg, double relativeTolerance) {
    if (absoluteToleranceKg < 0.0 || relativeTolerance < 0.0) {
      throw new IllegalArgumentException("Mass-balance tolerances must be non-negative");
    }
    return Math.abs(getResidualKg(phase)) <= absoluteToleranceKg || getRelativeResidual(phase) <= relativeTolerance;
  }
}
