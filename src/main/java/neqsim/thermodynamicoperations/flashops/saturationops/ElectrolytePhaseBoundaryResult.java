package neqsim.thermodynamicoperations.flashops.saturationops;

import java.io.Serializable;
import neqsim.thermo.phase.PhaseType;

/**
 * Immutable diagnostics for a bracketed electrolyte phase-boundary calculation.
 *
 * <p>
 * The boundary is represented by the final bracket because a finite phase-fraction threshold is required to classify a
 * phase as material. The thermodynamic system is left at the bracket endpoint where the requested phase is present.
 * </p>
 */
public final class ElectrolytePhaseBoundaryResult implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Scanned intensive variable. */
  public enum Specification {
    /** Pressure in bara. */
    PRESSURE,
    /** Temperature in kelvin. */
    TEMPERATURE
  }

  private final Specification specification;
  private final PhaseType targetPhase;
  private final double lowerBound;
  private final double upperBound;
  private final boolean targetPresentAtLowerBound;
  private final double targetPresentValue;
  private final double targetPhaseFraction;
  private final int iterations;
  private final int flashEvaluations;
  private final String lowerTopology;
  private final String upperTopology;
  private final double maximumMaterialBalanceResidual;
  private final double maximumPhaseNormalizationResidual;
  private final double aqueousChargeMolality;
  private final double maximumIonMoleFractionOutsideAqueous;
  private final double maximumLogFugacityResidual;
  private final double maximumAbsoluteElementBalanceResidual;
  private final double maximumAbsoluteReactionLogResidual;

  /**
   * Creates an immutable phase-boundary result.
   *
   * @param specification scanned variable
   * @param targetPhase phase whose appearance or disappearance is bracketed
   * @param lowerBound final lower numeric bound
   * @param upperBound final upper numeric bound
   * @param targetPresentAtLowerBound whether the target is present at the lower bound
   * @param targetPresentValue bound value at which the system is left
   * @param targetPhaseFraction target phase fraction at the retained state
   * @param iterations bisection iterations
   * @param flashEvaluations complete TP flash evaluations
   * @param lowerTopology phase topology at the lower bound
   * @param upperTopology phase topology at the upper bound
   * @param maximumMaterialBalanceResidual maximum component mole-fraction balance residual
   * @param maximumPhaseNormalizationResidual maximum phase or beta normalization residual
   * @param aqueousChargeMolality signed aqueous charge residual in mol/kg water
   * @param maximumIonMoleFractionOutsideAqueous largest ionic mole fraction outside aqueous phases
   * @param maximumLogFugacityResidual largest neutral-component cross-phase ln-fugacity residual
   */
  public ElectrolytePhaseBoundaryResult(Specification specification, PhaseType targetPhase, double lowerBound,
      double upperBound, boolean targetPresentAtLowerBound, double targetPresentValue, double targetPhaseFraction,
      int iterations, int flashEvaluations, String lowerTopology, String upperTopology,
      double maximumMaterialBalanceResidual, double maximumPhaseNormalizationResidual, double aqueousChargeMolality,
      double maximumIonMoleFractionOutsideAqueous, double maximumLogFugacityResidual) {
    this(specification, targetPhase, lowerBound, upperBound, targetPresentAtLowerBound, targetPresentValue,
        targetPhaseFraction, iterations, flashEvaluations, lowerTopology, upperTopology, maximumMaterialBalanceResidual,
        maximumPhaseNormalizationResidual, aqueousChargeMolality, maximumIonMoleFractionOutsideAqueous,
        maximumLogFugacityResidual, 0.0, 0.0);
  }

  /**
   * Creates an immutable phase-boundary result with reactive-equilibrium diagnostics.
   *
   * @param specification scanned variable
   * @param targetPhase phase whose appearance or disappearance is bracketed
   * @param lowerBound final lower numeric bound
   * @param upperBound final upper numeric bound
   * @param targetPresentAtLowerBound whether the target is present at the lower bound
   * @param targetPresentValue bound value at which the system is left
   * @param targetPhaseFraction target phase fraction at the retained state
   * @param iterations bisection iterations
   * @param flashEvaluations complete TP flash evaluations
   * @param lowerTopology phase topology at the lower bound
   * @param upperTopology phase topology at the upper bound
   * @param maximumMaterialBalanceResidual maximum component mole-fraction balance residual
   * @param maximumPhaseNormalizationResidual maximum phase or beta normalization residual
   * @param aqueousChargeMolality signed aqueous charge residual in mol/kg water
   * @param maximumIonMoleFractionOutsideAqueous largest ionic mole fraction outside aqueous phases
   * @param maximumLogFugacityResidual largest neutral-component cross-phase ln-fugacity residual
   * @param maximumAbsoluteElementBalanceResidual largest reactive element-balance residual in moles
   * @param maximumAbsoluteReactionLogResidual largest absolute natural-log reaction residual
   */
  public ElectrolytePhaseBoundaryResult(Specification specification, PhaseType targetPhase, double lowerBound,
      double upperBound, boolean targetPresentAtLowerBound, double targetPresentValue, double targetPhaseFraction,
      int iterations, int flashEvaluations, String lowerTopology, String upperTopology,
      double maximumMaterialBalanceResidual, double maximumPhaseNormalizationResidual, double aqueousChargeMolality,
      double maximumIonMoleFractionOutsideAqueous, double maximumLogFugacityResidual,
      double maximumAbsoluteElementBalanceResidual, double maximumAbsoluteReactionLogResidual) {
    this.specification = specification;
    this.targetPhase = targetPhase;
    this.lowerBound = lowerBound;
    this.upperBound = upperBound;
    this.targetPresentAtLowerBound = targetPresentAtLowerBound;
    this.targetPresentValue = targetPresentValue;
    this.targetPhaseFraction = targetPhaseFraction;
    this.iterations = iterations;
    this.flashEvaluations = flashEvaluations;
    this.lowerTopology = lowerTopology;
    this.upperTopology = upperTopology;
    this.maximumMaterialBalanceResidual = maximumMaterialBalanceResidual;
    this.maximumPhaseNormalizationResidual = maximumPhaseNormalizationResidual;
    this.aqueousChargeMolality = aqueousChargeMolality;
    this.maximumIonMoleFractionOutsideAqueous = maximumIonMoleFractionOutsideAqueous;
    this.maximumLogFugacityResidual = maximumLogFugacityResidual;
    this.maximumAbsoluteElementBalanceResidual = maximumAbsoluteElementBalanceResidual;
    this.maximumAbsoluteReactionLogResidual = maximumAbsoluteReactionLogResidual;
  }

  /** @return scanned variable */
  public Specification getSpecification() {
    return specification;
  }

  /** @return phase whose appearance or disappearance was bracketed */
  public PhaseType getTargetPhase() {
    return targetPhase;
  }

  /** @return final lower bound in bara or kelvin */
  public double getLowerBound() {
    return lowerBound;
  }

  /** @return final upper bound in bara or kelvin */
  public double getUpperBound() {
    return upperBound;
  }

  /** @return midpoint estimate of the phase boundary */
  public double getBoundaryValue() {
    return 0.5 * (lowerBound + upperBound);
  }

  /** @return final bracket width in bara or kelvin */
  public double getBracketWidth() {
    return upperBound - lowerBound;
  }

  /** @return true when the target phase is present at the lower bound */
  public boolean isTargetPresentAtLowerBound() {
    return targetPresentAtLowerBound;
  }

  /** @return value at which the retained system has the target phase */
  public double getTargetPresentValue() {
    return targetPresentValue;
  }

  /** @return target phase fraction at the retained state */
  public double getTargetPhaseFraction() {
    return targetPhaseFraction;
  }

  /** @return bisection iterations */
  public int getIterations() {
    return iterations;
  }

  /** @return number of complete TP flash evaluations */
  public int getFlashEvaluations() {
    return flashEvaluations;
  }

  /** @return topology at the lower bound */
  public String getLowerTopology() {
    return lowerTopology;
  }

  /** @return topology at the upper bound */
  public String getUpperTopology() {
    return upperTopology;
  }

  /** @return maximum component material-balance residual */
  public double getMaximumMaterialBalanceResidual() {
    return maximumMaterialBalanceResidual;
  }

  /** @return maximum phase-composition or beta normalization residual */
  public double getMaximumPhaseNormalizationResidual() {
    return maximumPhaseNormalizationResidual;
  }

  /** @return signed aqueous electroneutrality residual in mol/kg water */
  public double getAqueousChargeMolality() {
    return aqueousChargeMolality;
  }

  /** @return largest ionic mole fraction outside an aqueous phase */
  public double getMaximumIonMoleFractionOutsideAqueous() {
    return maximumIonMoleFractionOutsideAqueous;
  }

  /** @return largest neutral-component cross-phase ln-fugacity residual */
  public double getMaximumLogFugacityResidual() {
    return maximumLogFugacityResidual;
  }

  /** @return largest absolute reactive element-balance residual in moles, or zero for non-reactive systems */
  public double getMaximumAbsoluteElementBalanceResidual() {
    return maximumAbsoluteElementBalanceResidual;
  }

  /** @return largest absolute natural-log reaction residual, or zero for non-reactive systems */
  public double getMaximumAbsoluteReactionLogResidual() {
    return maximumAbsoluteReactionLogResidual;
  }
}
