package neqsim.process.equipment.separator;

import java.io.Serializable;
import java.util.UUID;

/**
 * Immutable, bounded accounting of the energy equation actually consumed by a transient separator.
 *
 * <p>
 * Positive inlet enthalpy and external heat add energy; outlet enthalpies remove it. Enthalpies retain the equation of
 * state's caloric reference and can be negative. This report distinguishes the accepted VU solve's local defect from
 * the accumulated difference between inventory energy and integrated boundary transfers. Numerical values describe the
 * last accepted update, while coverage and attempt counters include later skipped updates. It is not a physical-model,
 * process-response, or safety qualification.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class SeparatorTransientEnergyBalanceReport implements Serializable {
  private static final long serialVersionUID = 1L;

  /** Availability and coverage of the current tracking epoch. */
  public enum TrackingStatus {
    /** Tracking is disabled. */
    DISABLED,
    /** Tracking is enabled but no update has been accepted. */
    NOT_STARTED,
    /** All attempted updates in the current epoch have complete diagnostic coverage. */
    COMPLETE,
    /** At least one attempted update lacks complete coverage. */
    INCOMPLETE,
    /** The equipment's dynamic algorithm is not instrumented by this diagnostic. */
    UNSUPPORTED
  }

  /** First bounded reason why an epoch cannot establish complete energy coverage. */
  public enum CoverageIssue {
    /** No gap has been observed. */
    NONE,
    /** Empty inventory bypassed the update. */
    SKIPPED_EMPTY_INVENTORY,
    /** Vessel property initialization bypassed the update. */
    SKIPPED_PROPERTY_INITIALIZATION,
    /** Component removal depleted inventory before an accepted VU solve. */
    SKIPPED_DEPLETED_INVENTORY,
    /** Explicit transient initialization caught an exception. */
    INITIALIZATION_FAILED,
    /** A steady-state reevaluation occurred without a measured dynamic energy update. */
    STEADY_STATE_REEVALUATION,
    /** An existing liquid withdrawal was excluded because the vessel had no liquid phase. */
    EXCLUDED_NONZERO_LIQUID_OUTLET,
    /** A diagnostic quantity was not finite. */
    NONFINITE_DIAGNOSTIC,
    /** A supplied material rate was negative. */
    UNSUPPORTED_BOUNDARY_FLOW,
    /** Component removal was limited independently of the supplied boundary transfers. */
    COMPONENT_REMOVAL_LIMITED
  }

  /** Result of an explicit caller-supplied cumulative-energy policy. */
  public enum EnergyVerdict {
    /** Tracking is disabled. */
    DISABLED,
    /** No accepted update exists. */
    NOT_STARTED,
    /** Coverage is incomplete. */
    INCOMPLETE,
    /** The equipment algorithm is unsupported. */
    UNSUPPORTED,
    /** Complete coverage satisfies the caller's maximum relative residual. */
    PASS,
    /** Complete coverage exceeds the caller's maximum relative residual. */
    FAIL
  }

  private final long epoch;
  private final long acceptedStepCount;
  private final long attemptedStepCount;
  private final long skippedStepCount;
  private final double elapsedTimeSeconds;
  private final double attemptedElapsedTimeSeconds;
  private final double nativeEquipmentTimeSeconds;
  private final UUID lastAcceptedCalculationIdentifier;
  private final CoverageIssue coverageIssue;
  private final double timeStepSeconds;
  private final double beforeEnergyJ;
  private final double targetEnergyJ;
  private final double acceptedEnergyJ;
  private final double acceptedVuEnergyJ;
  private final double publishedVolumeM3;
  private final double specifiedVolumeM3;
  private final double acceptedVolumeM3;
  private final double localVuDefectJ;
  private final double inletEnthalpyRateW;
  private final double gasOutletEnthalpyRateW;
  private final double liquidOutletEnthalpyRateW;
  private final double heatInputRateW;
  private final double inletMolarRateMolPerSecond;
  private final double gasOutletMolarRateMolPerSecond;
  private final double liquidOutletMolarRateMolPerSecond;
  private final boolean liquidOutletTermApplied;
  private final boolean componentRemovalLimited;
  private final double initialEnergyJ;
  private final double inletEnergyJ;
  private final double gasOutletEnergyJ;
  private final double liquidOutletEnergyJ;
  private final double heatInputEnergyJ;
  private final double netIntegratedEnergyJ;
  private final double cumulativeDebtJ;
  private final double maximumAbsoluteCumulativeDebtJ;
  private final double cumulativeSignedLocalVuDefectJ;
  private final double cumulativeAbsoluteLocalVuDefectJ;
  private final double maximumAbsoluteLocalVuDefectJ;

  /**
   * Copies scalar accepted-state and current-coverage values from the bounded tracker.
   *
   * @param source tracker with at least one accepted update
   */
  SeparatorTransientEnergyBalanceReport(SeparatorTransientEnergyBalanceTracker source) {
    epoch = source.epoch;
    acceptedStepCount = source.acceptedStepCount;
    attemptedStepCount = source.attemptedStepCount;
    skippedStepCount = source.skippedStepCount;
    elapsedTimeSeconds = source.elapsedTimeSeconds;
    attemptedElapsedTimeSeconds = source.attemptedElapsedTimeSeconds;
    nativeEquipmentTimeSeconds = source.nativeEquipmentTimeSeconds;
    lastAcceptedCalculationIdentifier = source.lastAcceptedCalculationIdentifier;
    coverageIssue = source.coverageIssue;
    timeStepSeconds = source.acceptedTimeStepSeconds;
    beforeEnergyJ = source.beforeEnergyJ;
    targetEnergyJ = source.targetEnergyJ;
    acceptedEnergyJ = source.acceptedEnergyJ;
    acceptedVuEnergyJ = source.acceptedVuEnergyJ;
    publishedVolumeM3 = source.publishedVolumeM3;
    specifiedVolumeM3 = source.specifiedVolumeM3;
    acceptedVolumeM3 = source.acceptedVolumeM3;
    localVuDefectJ = source.localVuDefectJ;
    inletEnthalpyRateW = source.inletEnthalpyRateW;
    gasOutletEnthalpyRateW = source.gasOutletEnthalpyRateW;
    liquidOutletEnthalpyRateW = source.liquidOutletEnthalpyRateW;
    heatInputRateW = source.heatInputRateW;
    inletMolarRateMolPerSecond = source.inletMolarRateMolPerSecond;
    gasOutletMolarRateMolPerSecond = source.gasOutletMolarRateMolPerSecond;
    liquidOutletMolarRateMolPerSecond = source.liquidOutletMolarRateMolPerSecond;
    liquidOutletTermApplied = source.liquidOutletTermApplied;
    componentRemovalLimited = source.componentRemovalLimited;
    initialEnergyJ = source.initialEnergyJ;
    inletEnergyJ = source.inletEnergyJ;
    gasOutletEnergyJ = source.gasOutletEnergyJ;
    liquidOutletEnergyJ = source.liquidOutletEnergyJ;
    heatInputEnergyJ = source.heatInputEnergyJ;
    netIntegratedEnergyJ = source.netIntegratedEnergyJ;
    cumulativeDebtJ = source.cumulativeDebtJ;
    maximumAbsoluteCumulativeDebtJ = source.maximumAbsoluteCumulativeDebtJ;
    cumulativeSignedLocalVuDefectJ = source.cumulativeSignedLocalVuDefectJ;
    cumulativeAbsoluteLocalVuDefectJ = source.cumulativeAbsoluteLocalVuDefectJ;
    maximumAbsoluteLocalVuDefectJ = source.maximumAbsoluteLocalVuDefectJ;
  }

  /** @return epoch number, local to this separator's diagnostic lifecycle */
  public long getEpoch() {
    return epoch;
  }

  /** @return number of completely accepted physical updates in this epoch */
  public long getAcceptedStepCount() {
    return acceptedStepCount;
  }

  /** @return number of valid-dt attempts, including skipped updates */
  public long getAttemptedStepCount() {
    return attemptedStepCount;
  }

  /** @return number of attempts which skipped an accepted VU update */
  public long getSkippedStepCount() {
    return skippedStepCount;
  }

  /** @return sum of accepted timesteps in seconds; independent of the equipment clock */
  public double getElapsedTimeSeconds() {
    return elapsedTimeSeconds;
  }

  /** @return sum of attempted valid timesteps in seconds */
  public double getAttemptedElapsedTimeSeconds() {
    return attemptedElapsedTimeSeconds;
  }

  /** @return observed native equipment clock, which need not equal accepted elapsed time */
  public double getNativeEquipmentTimeSeconds() {
    return nativeEquipmentTimeSeconds;
  }

  /** @return identifier of the last accepted update */
  public UUID getLastAcceptedCalculationIdentifier() {
    return lastAcceptedCalculationIdentifier;
  }

  /** @return first coverage issue retained for this epoch */
  public CoverageIssue getCoverageIssue() {
    return coverageIssue;
  }

  /** @return true only if the whole attempted epoch has complete coverage */
  public boolean isComplete() {
    return coverageIssue == CoverageIssue.NONE;
  }

  /** @return last accepted timestep in seconds */
  public double getTimeStepSeconds() {
    return timeStepSeconds;
  }

  /** @return actual inventory energy immediately before the last accepted update, J */
  public double getBeforeEnergyJ() {
    return beforeEnergyJ;
  }

  /** @return target internal energy supplied to the last accepted VU solve, J */
  public double getTargetEnergyJ() {
    return targetEnergyJ;
  }

  /** @return actual inventory energy after all successful physical postprocessing, J */
  public double getPublishedEnergyJ() {
    return acceptedEnergyJ;
  }

  /** @return actual internal energy returned by the last accepted VU solve, J */
  public double getAcceptedVuEnergyJ() {
    return acceptedVuEnergyJ;
  }

  /** @return actual inventory volume after all successful physical postprocessing, m3 */
  public double getPublishedVolumeM3() {
    return publishedVolumeM3;
  }

  /** @return specified vessel volume for the last accepted solve, m3 */
  public double getSpecifiedVolumeM3() {
    return specifiedVolumeM3;
  }

  /** @return actual volume returned by the last accepted solve, m3 */
  public double getAcceptedVolumeM3() {
    return acceptedVolumeM3;
  }

  /** @return signed actual minus target internal energy for the last accepted VU solve, J */
  public double getLocalVuDefectJ() {
    return localVuDefectJ;
  }

  /** @return absolute local energy defect divided by max(abs(target energy), 1 J) */
  public double getRelativeLocalVuDefect() {
    return Math.abs(localVuDefectJ) / Math.max(Math.abs(targetEnergyJ), 1.0);
  }

  /** @return signed actual minus specified volume, m3 */
  public double getVolumeDefectM3() {
    return acceptedVolumeM3 - specifiedVolumeM3;
  }

  /** @return native acceptance volume residual, abs(actual-target)/max(abs(target), 1 m3) */
  public double getRelativeVolumeResidual() {
    return Math.abs(getVolumeDefectM3()) / Math.max(Math.abs(specifiedVolumeM3), 1.0);
  }

  /** @return consumed inlet enthalpy flux, W */
  public double getInletEnthalpyRateW() {
    return inletEnthalpyRateW;
  }

  /** @return consumed gas outlet enthalpy flux, W */
  public double getGasOutletEnthalpyRateW() {
    return gasOutletEnthalpyRateW;
  }

  /** @return applied liquid outlet enthalpy flux, W; zero if the native phase condition excluded it */
  public double getLiquidOutletEnthalpyRateW() {
    return liquidOutletEnthalpyRateW;
  }

  /** @return consumed external heat input, W; positive adds energy */
  public double getHeatInputRateW() {
    return heatInputRateW;
  }

  /** @return supplied inlet molar rate at the integration point, mol/s */
  public double getInletMolarRateMolPerSecond() {
    return inletMolarRateMolPerSecond;
  }

  /** @return supplied gas outlet molar rate at the integration point, mol/s */
  public double getGasOutletMolarRateMolPerSecond() {
    return gasOutletMolarRateMolPerSecond;
  }

  /** @return supplied liquid outlet molar rate at the integration point, mol/s */
  public double getLiquidOutletMolarRateMolPerSecond() {
    return liquidOutletMolarRateMolPerSecond;
  }

  /** @return whether native phase-conditional integration included the liquid outlet */
  public boolean isLiquidOutletTermApplied() {
    return liquidOutletTermApplied;
  }

  /** @return whether any component removal was limited in the last accepted update */
  public boolean isComponentRemovalLimited() {
    return componentRemovalLimited;
  }

  /** @return actual initialized inventory energy before the first accepted tracked update, J */
  public double getInitialEnergyJ() {
    return initialEnergyJ;
  }

  /** @return cumulative consumed inlet enthalpy transfer, J */
  public double getInletEnergyJ() {
    return inletEnergyJ;
  }

  /** @return cumulative consumed gas outlet enthalpy transfer, J */
  public double getGasOutletEnergyJ() {
    return gasOutletEnergyJ;
  }

  /** @return cumulative applied liquid outlet enthalpy transfer, J */
  public double getLiquidOutletEnergyJ() {
    return liquidOutletEnergyJ;
  }

  /** @return cumulative external heat input, J */
  public double getHeatInputEnergyJ() {
    return heatInputEnergyJ;
  }

  /** @return sum of the exact ordered dt*net-rate values used in accepted updates, J */
  public double getNetIntegratedEnergyJ() {
    return netIntegratedEnergyJ;
  }

  /** @return initial energy plus the cumulative accepted net transfer, J */
  public double getExpectedEnergyJ() {
    return initialEnergyJ + netIntegratedEnergyJ;
  }

  /** @return latest signed inventory minus initial-and-integrated energy, J */
  public double getCumulativeDebtJ() {
    return cumulativeDebtJ;
  }

  /** @return maximum absolute cumulative debt over accepted updates, J */
  public double getMaximumAbsoluteCumulativeDebtJ() {
    return maximumAbsoluteCumulativeDebtJ;
  }

  /** @return signed sum of local accepted VU energy defects, J */
  public double getCumulativeSignedLocalVuDefectJ() {
    return cumulativeSignedLocalVuDefectJ;
  }

  /** @return sum of absolute local accepted VU energy defects, J */
  public double getCumulativeAbsoluteLocalVuDefectJ() {
    return cumulativeAbsoluteLocalVuDefectJ;
  }

  /** @return maximum absolute local accepted VU energy defect, J */
  public double getMaximumAbsoluteLocalVuDefectJ() {
    return maximumAbsoluteLocalVuDefectJ;
  }

  /** @return cumulative debt outside the signed local VU defects, including postprocessing/interstep changes, J */
  public double getNonVuEnergyDifferenceJ() {
    return cumulativeDebtJ - cumulativeSignedLocalVuDefectJ;
  }

  /** @return absolute initial inventory energy, J, without a normalization floor */
  public double getAbsoluteInitialEnergyJ() {
    return Math.abs(initialEnergyJ);
  }

  /** @return explicit normalization denominator max(abs(initial energy), 1 J) */
  public double getNormalizationScaleJ() {
    return Math.max(Math.abs(initialEnergyJ), 1.0);
  }

  /** @return absolute latest cumulative debt divided by the reported normalization scale */
  public double getRelativeCumulativeResidual() {
    return Math.abs(cumulativeDebtJ) / getNormalizationScaleJ();
  }

  /** @return maximum absolute cumulative debt divided by the reported normalization scale */
  public double getMaximumRelativeCumulativeResidual() {
    return maximumAbsoluteCumulativeDebtJ / getNormalizationScaleJ();
  }

  /**
   * Applies an explicit numerical policy to the complete attempted epoch.
   *
   * @param maximumRelativeResidual finite nonnegative caller limit
   * @return incomplete, pass, or fail; no process-response or safety qualification
   */
  public EnergyVerdict assess(double maximumRelativeResidual) {
    requireValidPolicy(maximumRelativeResidual);
    if (!isComplete()) {
      return EnergyVerdict.INCOMPLETE;
    }
    return getMaximumRelativeCumulativeResidual() <= maximumRelativeResidual ? EnergyVerdict.PASS : EnergyVerdict.FAIL;
  }

  /**
   * Validates a caller policy without changing diagnostic or physical state.
   *
   * @param limit finite nonnegative relative residual limit
   */
  static void requireValidPolicy(double limit) {
    if (!Double.isFinite(limit) || limit < 0.0) {
      throw new IllegalArgumentException("Cumulative energy residual limit must be finite and nonnegative");
    }
  }
}
