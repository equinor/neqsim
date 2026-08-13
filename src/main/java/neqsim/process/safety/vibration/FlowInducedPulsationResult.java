package neqsim.process.safety.vibration;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.GsonBuilder;

/**
 * Result of a flow-induced pulsation (FIP) screening for a single closed side branch (dead leg) on a gas main line.
 *
 * <p>
 * Carries the acoustic mode frequencies of the branch, the Strouhal number of each mode at the screened main-line
 * velocity, the main-line velocity band that would drive each mode into shear-layer lock-in, and the resulting
 * likelihood band.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class FlowInducedPulsationResult implements Serializable {

  private static final long serialVersionUID = 1L;

  /**
   * One acoustic quarter-wave mode of the closed branch.
   */
  public static class BranchMode implements Serializable {

    private static final long serialVersionUID = 1L;

    private final int modeNumber;
    private final double frequencyHz;
    private final double strouhalNumber;
    private final boolean lockedIn;
    private final double lockInVelocityLowMPerS;
    private final double lockInVelocityHighMPerS;

    /**
     * Creates an acoustic mode record.
     *
     * @param modeNumber mode index, 1 for the fundamental quarter-wave mode
     * @param frequencyHz acoustic natural frequency of the mode, Hz
     * @param strouhalNumber Strouhal number of the mode at the screened main-line velocity
     * @param lockedIn true when the Strouhal number falls inside the shear-layer lock-in band
     * @param lockInVelocityLowMPerS lowest main-line velocity that drives this mode into lock-in, m/s
     * @param lockInVelocityHighMPerS highest main-line velocity that drives this mode into lock-in, m/s
     */
    public BranchMode(int modeNumber, double frequencyHz, double strouhalNumber, boolean lockedIn,
        double lockInVelocityLowMPerS, double lockInVelocityHighMPerS) {
      this.modeNumber = modeNumber;
      this.frequencyHz = frequencyHz;
      this.strouhalNumber = strouhalNumber;
      this.lockedIn = lockedIn;
      this.lockInVelocityLowMPerS = lockInVelocityLowMPerS;
      this.lockInVelocityHighMPerS = lockInVelocityHighMPerS;
    }

    /**
     * Gets the mode index.
     *
     * @return mode number, 1 for the fundamental
     */
    public int getModeNumber() {
      return modeNumber;
    }

    /**
     * Gets the acoustic natural frequency.
     *
     * @return frequency, Hz
     */
    public double getFrequencyHz() {
      return frequencyHz;
    }

    /**
     * Gets the Strouhal number at the screened main-line velocity.
     *
     * @return Strouhal number, dimensionless
     */
    public double getStrouhalNumber() {
      return strouhalNumber;
    }

    /**
     * Reports whether this mode is in lock-in at the screened velocity.
     *
     * @return true when the mode is excited
     */
    public boolean isLockedIn() {
      return lockedIn;
    }

    /**
     * Gets the lower end of the main-line velocity band that excites this mode.
     *
     * @return velocity, m/s
     */
    public double getLockInVelocityLowMPerS() {
      return lockInVelocityLowMPerS;
    }

    /**
     * Gets the upper end of the main-line velocity band that excites this mode.
     *
     * @return velocity, m/s
     */
    public double getLockInVelocityHighMPerS() {
      return lockInVelocityHighMPerS;
    }
  }

  private final String branchName;
  private final PipingFivLikelihood likelihood;
  private final boolean anyModeLockedIn;
  private final double lowestLockInVelocityMPerS;
  private final List<BranchMode> modes;
  private final Map<String, Double> contributingFactors;
  private final String recommendation;

  /**
   * Creates a flow-induced pulsation screening result.
   *
   * @param branchName branch identifier
   * @param likelihood derived likelihood band
   * @param anyModeLockedIn true when at least one acoustic mode is in lock-in at the screened velocity
   * @param lowestLockInVelocityMPerS lowest main-line velocity at which any mode enters lock-in, m/s, or
   * {@link Double#NaN} when no mode can be excited within the screened velocity range
   * @param modes acoustic modes of the branch
   * @param contributingFactors factor name to factor value
   * @param recommendation recommended action
   */
  public FlowInducedPulsationResult(String branchName, PipingFivLikelihood likelihood, boolean anyModeLockedIn,
      double lowestLockInVelocityMPerS, List<BranchMode> modes, Map<String, Double> contributingFactors,
      String recommendation) {
    this.branchName = branchName;
    this.likelihood = likelihood;
    this.anyModeLockedIn = anyModeLockedIn;
    this.lowestLockInVelocityMPerS = lowestLockInVelocityMPerS;
    this.modes = new ArrayList<BranchMode>(modes);
    this.contributingFactors = new LinkedHashMap<String, Double>(contributingFactors);
    this.recommendation = recommendation;
  }

  /**
   * Gets the branch name.
   *
   * @return branch identifier
   */
  public String getBranchName() {
    return branchName;
  }

  /**
   * Gets the likelihood band.
   *
   * @return likelihood band
   */
  public PipingFivLikelihood getLikelihood() {
    return likelihood;
  }

  /**
   * Reports whether any acoustic mode is excited at the screened velocity.
   *
   * @return true when at least one mode is in lock-in
   */
  public boolean isAnyModeLockedIn() {
    return anyModeLockedIn;
  }

  /**
   * Gets the lowest main-line velocity that drives any mode into lock-in.
   *
   * @return velocity, m/s, or {@link Double#NaN} when no mode is reachable
   */
  public double getLowestLockInVelocityMPerS() {
    return lowestLockInVelocityMPerS;
  }

  /**
   * Gets the acoustic modes of the branch.
   *
   * @return modes (defensive copy)
   */
  public List<BranchMode> getModes() {
    return Collections.unmodifiableList(new ArrayList<BranchMode>(modes));
  }

  /**
   * Gets the contributing factors.
   *
   * @return contributing factors (defensive copy)
   */
  public Map<String, Double> getContributingFactors() {
    return new LinkedHashMap<String, Double>(contributingFactors);
  }

  /**
   * Gets the recommended action.
   *
   * @return recommended action
   */
  public String getRecommendation() {
    return recommendation;
  }

  /**
   * Serializes the result to pretty-printed JSON.
   *
   * @return result as pretty JSON
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().create().toJson(this);
  }
}
