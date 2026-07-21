package neqsim.process.equipment.reactor.sulfurrecovery;

import java.io.Serializable;
import neqsim.thermo.system.SystemInterface;

/**
 * Feed-forward and tail-gas feedback calculation for Claus combustion-air demand.
 *
 * <p>
 * The feed-forward term burns one third of the H2S and completely oxidizes configured ammonia and hydrocarbon
 * contaminants. The feedback term trims that demand from the measured tail-gas H2S/SO2 ratio. A flowsheet may use
 * {@link #calculateOxygenDemand(SystemInterface)} alone or use
 * {@link #calculateTrimmedOxygenDemand(SystemInterface, double)} in an outer convergence loop.
 * </p>
 */
public class AirDemandController implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  private double targetH2SToSO2Ratio = 2.0;
  private double feedbackGain = 0.08;
  private double minimumTrimFactor = 0.70;
  private double maximumTrimFactor = 1.30;

  /** Calculate the stoichiometric oxygen demand on the stream molar-flow basis. */
  public double calculateOxygenDemand(SystemInterface acidGas) {
    SystemInterface system = SulfurProcessUtil.prepareSystem(acidGas);
    return 0.5 * SulfurProcessUtil.moles(system, "H2S") + 0.75 * SulfurProcessUtil.moles(system, "ammonia")
        + 2.0 * SulfurProcessUtil.moles(system, "methane") + 0.5 * SulfurProcessUtil.moles(system, "hydrogen")
        + 0.5 * SulfurProcessUtil.moles(system, "CO");
  }

  /**
   * Calculate oxygen demand including a bounded feedback correction.
   *
   * @param acidGas acid-gas feed
   * @param measuredRatio H2S/SO2 ratio measured after the final Claus condenser
   * @return oxygen demand on the stream molar-flow basis
   */
  public double calculateTrimmedOxygenDemand(SystemInterface acidGas, double measuredRatio) {
    double normalizedError;
    if (!Double.isFinite(measuredRatio)) {
      normalizedError = 1.0;
    } else {
      normalizedError = (measuredRatio - targetH2SToSO2Ratio) / Math.max(targetH2SToSO2Ratio, 1.0e-12);
    }
    double trimFactor = SulfurProcessUtil.clamp(1.0 + feedbackGain * normalizedError, minimumTrimFactor,
        maximumTrimFactor);
    return calculateOxygenDemand(acidGas) * trimFactor;
  }

  /** Set the target tail-gas H2S/SO2 molar ratio. */
  public void setTargetH2SToSO2Ratio(double targetRatio) {
    targetH2SToSO2Ratio = Math.max(targetRatio, 1.0e-12);
  }

  /** Return the target tail-gas H2S/SO2 molar ratio. */
  public double getTargetH2SToSO2Ratio() {
    return targetH2SToSO2Ratio;
  }

  /** Set proportional feedback gain. */
  public void setFeedbackGain(double feedbackGain) {
    this.feedbackGain = Math.max(feedbackGain, 0.0);
  }

  /** Set lower and upper oxygen trim factors. */
  public void setTrimLimits(double minimum, double maximum) {
    if (minimum <= 0.0 || maximum < minimum) {
      throw new IllegalArgumentException("Oxygen trim limits must satisfy 0 < minimum <= maximum");
    }
    minimumTrimFactor = minimum;
    maximumTrimFactor = maximum;
  }
}
