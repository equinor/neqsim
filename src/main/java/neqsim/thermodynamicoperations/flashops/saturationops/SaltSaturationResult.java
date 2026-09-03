package neqsim.thermodynamicoperations.flashops.saturationops;

import java.io.Serializable;

/**
 * Immutable diagnostics from adding a dissolved salt to activity saturation.
 *
 * <p>
 * The added amount is expressed as salt formula units. The result describes the existing
 * {@link CalcSaltSatauration} bracket and bisection calculation; it does not represent a solid phase or qualify the
 * underlying COMPSALT or activity-model parameters.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class SaltSaturationResult implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  private final String saltName;
  private final double initialSaturationRatio;
  private final double finalSaturationRatio;
  private final double addedSaltMoles;
  private final int bracketIterations;
  private final int solveIterations;
  private final int thermodynamicInitializationCount;
  private final boolean alreadySaturated;
  private final boolean converged;
  private final boolean iterationLimitReached;

  SaltSaturationResult(String saltName, double initialSaturationRatio, double finalSaturationRatio,
      double addedSaltMoles, int bracketIterations, int solveIterations, int thermodynamicInitializationCount,
      boolean alreadySaturated, boolean converged, boolean iterationLimitReached) {
    this.saltName = saltName;
    this.initialSaturationRatio = initialSaturationRatio;
    this.finalSaturationRatio = finalSaturationRatio;
    this.addedSaltMoles = addedSaltMoles;
    this.bracketIterations = bracketIterations;
    this.solveIterations = solveIterations;
    this.thermodynamicInitializationCount = thermodynamicInitializationCount;
    this.alreadySaturated = alreadySaturated;
    this.converged = converged;
    this.iterationLimitReached = iterationLimitReached;
  }

  /** @return COMPSALT salt name */
  public String getSaltName() {
    return saltName;
  }

  /** @return ion activity product divided by Ksp before salt addition */
  public double getInitialSaturationRatio() {
    return initialSaturationRatio;
  }

  /** @return ion activity product divided by Ksp after the accepted addition */
  public double getFinalSaturationRatio() {
    return finalSaturationRatio;
  }

  /** @return added dissolved salt formula units in mol */
  public double getAddedSaltMoles() {
    return addedSaltMoles;
  }

  /** @return number of upper-bound expansion evaluations */
  public int getBracketIterations() {
    return bracketIterations;
  }

  /** @return number of bisection evaluations */
  public int getSolveIterations() {
    return solveIterations;
  }

  /** @return number of complete thermodynamic initializations performed by the calculation */
  public int getThermodynamicInitializationCount() {
    return thermodynamicInitializationCount;
  }

  /** @return true when the input state was already at or above unit saturation */
  public boolean isAlreadySaturated() {
    return alreadySaturated;
  }

  /** @return true when the final saturation-ratio residual meets the solver tolerance */
  public boolean isConverged() {
    return converged;
  }

  /** @return true when the bisection stopped at its iteration limit without convergence */
  public boolean isIterationLimitReached() {
    return iterationLimitReached;
  }

  /** @return absolute residual from unit saturation */
  public double getAbsoluteSaturationRatioResidual() {
    return Math.abs(finalSaturationRatio - 1.0);
  }
}
