package neqsim.process.equipment.separator;

import java.io.Serializable;
import java.util.UUID;
import neqsim.process.equipment.separator.SeparatorTransientEnergyBalanceReport.CoverageIssue;
import neqsim.process.equipment.separator.SeparatorTransientEnergyBalanceReport.TrackingStatus;

/**
 * Constant-storage accounting owned by one opt-in separator diagnostic.
 *
 * <p>
 * Pending fields are transactional. Accepted scalar fields and the immutable last report are copied by native separator
 * checkpoints. No history or external thermodynamic reference is retained.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
final class SeparatorTransientEnergyBalanceTracker implements Serializable, Cloneable {
  private static final long serialVersionUID = 1L;
  final long epoch;
  long acceptedStepCount;
  long attemptedStepCount;
  long skippedStepCount;
  double elapsedTimeSeconds;
  double attemptedElapsedTimeSeconds;
  double nativeEquipmentTimeSeconds;
  UUID lastAcceptedCalculationIdentifier;
  CoverageIssue coverageIssue = CoverageIssue.NONE;
  double acceptedTimeStepSeconds;
  double beforeEnergyJ;
  double targetEnergyJ;
  double acceptedEnergyJ;
  double acceptedVuEnergyJ;
  double publishedVolumeM3;
  double specifiedVolumeM3;
  double acceptedVolumeM3;
  double localVuDefectJ;
  double inletEnthalpyRateW;
  double gasOutletEnthalpyRateW;
  double liquidOutletEnthalpyRateW;
  double heatInputRateW;
  double inletMolarRateMolPerSecond;
  double gasOutletMolarRateMolPerSecond;
  double liquidOutletMolarRateMolPerSecond;
  boolean liquidOutletTermApplied;
  boolean componentRemovalLimited;
  double initialEnergyJ;
  double inletEnergyJ;
  double gasOutletEnergyJ;
  double liquidOutletEnergyJ;
  double heatInputEnergyJ;
  double netIntegratedEnergyJ;
  double cumulativeDebtJ;
  double maximumAbsoluteCumulativeDebtJ;
  double cumulativeSignedLocalVuDefectJ;
  double cumulativeAbsoluteLocalVuDefectJ;
  double maximumAbsoluteLocalVuDefectJ;
  private SeparatorTransientEnergyBalanceReport lastReport;
  private UUID pendingIdentifier;
  private double pendingDt;
  private double pendingBefore;
  private double pendingTarget;
  private double pendingNetRate;
  private double pendingInletRate;
  private double pendingGasRate;
  private double pendingLiquidRate;
  private double pendingHeatRate;
  private double pendingInletMolarRate;
  private double pendingGasMolarRate;
  private double pendingLiquidMolarRate;
  private boolean pendingLiquidApplied;
  private boolean pendingRemovalLimited;
  private double pendingSpecifiedVolume;
  private double pendingAcceptedEnergy;
  private double pendingAcceptedVolume;
  private boolean equationAvailable;
  private boolean acceptedVuAvailable;

  /**
   * Creates an empty diagnostic epoch.
   *
   * @param epoch separator-local epoch number
   */
  SeparatorTransientEnergyBalanceTracker(long epoch) {
    this.epoch = epoch;
  }

  /**
   * Copies scalar state while sharing only immutable values.
   *
   * @return independent mutable tracker with the same immutable last report
   */
  SeparatorTransientEnergyBalanceTracker copy() {
    try {
      return (SeparatorTransientEnergyBalanceTracker) super.clone();
    } catch (CloneNotSupportedException ex) {
      throw new AssertionError(ex);
    }
  }

  /**
   * Starts a valid-dt attempted update without publishing provisional energy.
   *
   * @param dt attempted interval in seconds
   * @param identifier native calculation identifier
   * @param equipmentTime observed native equipment clock in seconds
   */
  void beginAttempt(double dt, UUID identifier, double equipmentTime) {
    attemptedStepCount++;
    attemptedElapsedTimeSeconds += dt;
    nativeEquipmentTimeSeconds = equipmentTime;
    pendingDt = dt;
    pendingIdentifier = identifier;
    equationAvailable = false;
    acceptedVuAvailable = false;
    pendingRemovalLimited = false;
    if (!Double.isFinite(attemptedElapsedTimeSeconds) || !Double.isFinite(equipmentTime)) {
      noteIssue(CoverageIssue.NONFINITE_DIAGNOSTIC);
    }
  }

  /**
   * Retains the exact ordered integration terms before component or outlet updates.
   *
   * @param before actual pre-update inventory energy, J
   * @param target target internal energy, J
   * @param netRate already grouped native net energy rate, W
   * @param inletRate consumed inlet enthalpy, W
   * @param gasRate consumed gas outlet enthalpy, W
   * @param liquidRate applied liquid outlet enthalpy, W
   * @param heatRate applied heat, W
   * @param inletMolarRate supplied inlet molar rate, mol/s
   * @param gasMolarRate supplied gas outlet molar rate, mol/s
   * @param liquidMolarRate supplied liquid outlet molar rate, mol/s
   * @param liquidApplied whether the native phase condition included the liquid term
   */
  void recordEquation(double before, double target, double netRate, double inletRate, double gasRate, double liquidRate,
      double heatRate, double inletMolarRate, double gasMolarRate, double liquidMolarRate, boolean liquidApplied) {
    pendingBefore = before;
    pendingTarget = target;
    pendingNetRate = netRate;
    pendingInletRate = inletRate;
    pendingGasRate = gasRate;
    pendingLiquidRate = liquidRate;
    pendingHeatRate = heatRate;
    pendingInletMolarRate = inletMolarRate;
    pendingGasMolarRate = gasMolarRate;
    pendingLiquidMolarRate = liquidMolarRate;
    pendingLiquidApplied = liquidApplied;
    equationAvailable = true;
    if (!liquidApplied && liquidMolarRate != 0.0) {
      noteIssue(CoverageIssue.EXCLUDED_NONZERO_LIQUID_OUTLET);
    }
    if (inletMolarRate < 0.0 || gasMolarRate < 0.0 || liquidMolarRate < 0.0) {
      noteIssue(CoverageIssue.UNSUPPORTED_BOUNDARY_FLOW);
    }
  }

  /** Records that native component removal was limited independently of the supplied transfers. */
  void recordRemovalLimited() {
    pendingRemovalLimited = true;
    noteIssue(CoverageIssue.COMPONENT_REMOVAL_LIMITED);
  }

  /**
   * Stages the independently accepted VU result without publishing a report.
   *
   * @param specifiedVolume target volume, m3
   * @param actualEnergy actual returned internal energy, J
   * @param actualVolume actual returned volume, m3
   */
  void recordAcceptedVu(double specifiedVolume, double actualEnergy, double actualVolume) {
    pendingSpecifiedVolume = specifiedVolume;
    pendingAcceptedEnergy = actualEnergy;
    pendingAcceptedVolume = actualVolume;
    acceptedVuAvailable = true;
  }

  /**
   * Commits a successfully published physical update using only finite scalar observations.
   *
   * @param publishedEnergy actual inventory energy after all physical postprocessing, J
   * @param publishedVolume actual inventory volume after all physical postprocessing, m3
   * @param equipmentTime native equipment clock after the physical update, seconds
   */
  void accept(double publishedEnergy, double publishedVolume, double equipmentTime) {
    nativeEquipmentTimeSeconds = equipmentTime;
    double nextNet = netIntegratedEnergyJ + pendingDt * pendingNetRate;
    double nextInlet = inletEnergyJ + pendingDt * pendingInletRate;
    double nextGas = gasOutletEnergyJ + pendingDt * pendingGasRate;
    double nextLiquid = liquidOutletEnergyJ + pendingDt * pendingLiquidRate;
    double nextHeat = heatInputEnergyJ + pendingDt * pendingHeatRate;
    double defect = pendingAcceptedEnergy - pendingTarget;
    double nextSignedDefect = cumulativeSignedLocalVuDefectJ + defect;
    double nextAbsoluteDefect = cumulativeAbsoluteLocalVuDefectJ + Math.abs(defect);
    double initial = acceptedStepCount == 0 ? pendingBefore : initialEnergyJ;
    double debt = publishedEnergy - initial - nextNet;
    if (!equationAvailable || !acceptedVuAvailable || !finitePending() || !Double.isFinite(publishedEnergy)
        || !Double.isFinite(publishedVolume) || !Double.isFinite(equipmentTime) || !Double.isFinite(nextNet)
        || !Double.isFinite(nextInlet) || !Double.isFinite(nextGas) || !Double.isFinite(nextLiquid)
        || !Double.isFinite(nextHeat) || !Double.isFinite(nextSignedDefect) || !Double.isFinite(nextAbsoluteDefect)
        || !Double.isFinite(debt) || !Double.isFinite(elapsedTimeSeconds + pendingDt)) {
      skip(CoverageIssue.NONFINITE_DIAGNOSTIC, equipmentTime);
      return;
    }
    initialEnergyJ = initial;
    acceptedStepCount++;
    elapsedTimeSeconds += pendingDt;
    lastAcceptedCalculationIdentifier = pendingIdentifier;
    acceptedTimeStepSeconds = pendingDt;
    beforeEnergyJ = pendingBefore;
    targetEnergyJ = pendingTarget;
    acceptedEnergyJ = publishedEnergy;
    acceptedVuEnergyJ = pendingAcceptedEnergy;
    publishedVolumeM3 = publishedVolume;
    specifiedVolumeM3 = pendingSpecifiedVolume;
    acceptedVolumeM3 = pendingAcceptedVolume;
    localVuDefectJ = defect;
    inletEnthalpyRateW = pendingInletRate;
    gasOutletEnthalpyRateW = pendingGasRate;
    liquidOutletEnthalpyRateW = pendingLiquidRate;
    heatInputRateW = pendingHeatRate;
    inletMolarRateMolPerSecond = pendingInletMolarRate;
    gasOutletMolarRateMolPerSecond = pendingGasMolarRate;
    liquidOutletMolarRateMolPerSecond = pendingLiquidMolarRate;
    liquidOutletTermApplied = pendingLiquidApplied;
    componentRemovalLimited = pendingRemovalLimited;
    inletEnergyJ = nextInlet;
    gasOutletEnergyJ = nextGas;
    liquidOutletEnergyJ = nextLiquid;
    heatInputEnergyJ = nextHeat;
    netIntegratedEnergyJ = nextNet;
    cumulativeDebtJ = debt;
    maximumAbsoluteCumulativeDebtJ = Math.max(maximumAbsoluteCumulativeDebtJ, Math.abs(debt));
    cumulativeSignedLocalVuDefectJ = nextSignedDefect;
    cumulativeAbsoluteLocalVuDefectJ = nextAbsoluteDefect;
    maximumAbsoluteLocalVuDefectJ = Math.max(maximumAbsoluteLocalVuDefectJ, Math.abs(defect));
    publish();
  }

  /**
   * Records an unmeasured attempt, retaining the clearly last-accepted numerical fields.
   *
   * @param issue reason for missing complete coverage
   * @param equipmentTime observed native equipment clock in seconds
   */
  void skip(CoverageIssue issue, double equipmentTime) {
    skippedStepCount++;
    nativeEquipmentTimeSeconds = equipmentTime;
    noteIssue(issue);
    publish();
  }

  /**
   * Invalidates coverage after an unmeasured reevaluation while retaining prior accepted numbers.
   *
   * @param issue reevaluation reason
   * @param equipmentTime observed native equipment clock, seconds
   */
  void invalidate(CoverageIssue issue, double equipmentTime) {
    nativeEquipmentTimeSeconds = equipmentTime;
    noteIssue(issue);
    publish();
  }

  /**
   * Retains the first bounded issue without allocating or discarding prior accepted energy.
   *
   * @param issue coverage issue
   */
  void noteIssue(CoverageIssue issue) {
    if (coverageIssue == CoverageIssue.NONE) {
      coverageIssue = issue;
    }
  }

  /**
   * Reports the epoch's current coverage, even before any finite accepted energy exists.
   *
   * @return incomplete, not started, or complete
   */
  TrackingStatus status() {
    if (coverageIssue != CoverageIssue.NONE) {
      return TrackingStatus.INCOMPLETE;
    }
    return acceptedStepCount == 0 ? TrackingStatus.NOT_STARTED : TrackingStatus.COMPLETE;
  }

  /** @return immutable current-coverage report, or null before the first accepted measured update */
  SeparatorTransientEnergyBalanceReport report() {
    return lastReport;
  }

  /**
   * Checks observations without evaluating or modifying any thermodynamic object.
   *
   * @return true when every staged scalar used in the balance is finite
   */
  private boolean finitePending() {
    return Double.isFinite(pendingDt) && pendingDt > 0.0 && Double.isFinite(pendingBefore)
        && Double.isFinite(pendingTarget) && Double.isFinite(pendingNetRate) && Double.isFinite(pendingInletRate)
        && Double.isFinite(pendingGasRate) && Double.isFinite(pendingLiquidRate) && Double.isFinite(pendingHeatRate)
        && Double.isFinite(pendingInletMolarRate) && Double.isFinite(pendingGasMolarRate)
        && Double.isFinite(pendingLiquidMolarRate) && Double.isFinite(pendingSpecifiedVolume)
        && Double.isFinite(pendingAcceptedEnergy) && Double.isFinite(pendingAcceptedVolume);
  }

  /** Publishes current coverage only when an actual finite accepted energy update exists. */
  private void publish() {
    if (acceptedStepCount != 0) {
      lastReport = new SeparatorTransientEnergyBalanceReport(this);
    }
  }
}
