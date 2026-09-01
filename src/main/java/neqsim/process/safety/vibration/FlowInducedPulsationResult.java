package neqsim.process.safety.vibration;

import com.google.gson.GsonBuilder;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Result of a flow-induced pulsation (FIP) screening for a single side branch (dead leg) on a gas main line.
 *
 * <p>
 * Carries the vortex-shedding frequency at the screened main-line velocity, the acoustic eigenfrequencies of the branch
 * with their &plusmn;20 % lock-in envelopes, the main-line velocity that would drive each mode into resonance, and the
 * resulting likelihood band.
 * </p>
 *
 * @author ESOL
 * @version 2.0
 */
public class FlowInducedPulsationResult implements Serializable {

  private static final long serialVersionUID = 1L;

  /**
   * One acoustic mode of the side branch, with its lock-in envelope.
   */
  public static class BranchMode implements Serializable {

    private static final long serialVersionUID = 1L;

    private final int modeIndex;
    private final double frequencyHz;
    private final double envelopeLowHz;
    private final double envelopeHighHz;
    private final boolean lockedIn;
    private final double resonanceVelocityMPerS;
    private final double resonanceVelocityLowMPerS;
    private final double resonanceVelocityHighMPerS;

    /**
     * Creates an acoustic mode record.
     *
     * @param modeIndex mode index n, 0 for the fundamental
     * @param frequencyHz acoustic eigenfrequency of the mode, Hz
     * @param envelopeLowHz lower bound of the lock-in envelope, Hz
     * @param envelopeHighHz upper bound of the lock-in envelope, Hz
     * @param lockedIn true when the shedding frequency falls inside the envelope
     * @param resonanceVelocityMPerS main-line velocity that places the shedding frequency exactly on this mode, m/s
     * @param resonanceVelocityLowMPerS lower main-line velocity that still excites this mode, m/s
     * @param resonanceVelocityHighMPerS upper main-line velocity that still excites this mode, m/s
     */
    public BranchMode(int modeIndex, double frequencyHz, double envelopeLowHz, double envelopeHighHz, boolean lockedIn,
        double resonanceVelocityMPerS, double resonanceVelocityLowMPerS, double resonanceVelocityHighMPerS) {
      this.modeIndex = modeIndex;
      this.frequencyHz = frequencyHz;
      this.envelopeLowHz = envelopeLowHz;
      this.envelopeHighHz = envelopeHighHz;
      this.lockedIn = lockedIn;
      this.resonanceVelocityMPerS = resonanceVelocityMPerS;
      this.resonanceVelocityLowMPerS = resonanceVelocityLowMPerS;
      this.resonanceVelocityHighMPerS = resonanceVelocityHighMPerS;
    }

    /**
     * Gets the mode index.
     *
     * @return mode index, 0 for the fundamental
     */
    public int getModeIndex() {
      return modeIndex;
    }

    /**
     * Gets the acoustic eigenfrequency.
     *
     * @return frequency, Hz
     */
    public double getFrequencyHz() {
      return frequencyHz;
    }

    /**
     * Gets the lower bound of the lock-in envelope.
     *
     * @return frequency, Hz
     */
    public double getEnvelopeLowHz() {
      return envelopeLowHz;
    }

    /**
     * Gets the upper bound of the lock-in envelope.
     *
     * @return frequency, Hz
     */
    public double getEnvelopeHighHz() {
      return envelopeHighHz;
    }

    /**
     * Reports whether this mode is in resonance at the screened velocity.
     *
     * @return true when the shedding frequency falls inside the envelope
     */
    public boolean isLockedIn() {
      return lockedIn;
    }

    /**
     * Gets the main-line velocity that places the shedding frequency exactly on this mode.
     *
     * @return velocity, m/s
     */
    public double getResonanceVelocityMPerS() {
      return resonanceVelocityMPerS;
    }

    /**
     * Gets the lower main-line velocity that still excites this mode.
     *
     * @return velocity, m/s
     */
    public double getResonanceVelocityLowMPerS() {
      return resonanceVelocityLowMPerS;
    }

    /**
     * Gets the upper main-line velocity that still excites this mode.
     *
     * @return velocity, m/s
     */
    public double getResonanceVelocityHighMPerS() {
      return resonanceVelocityHighMPerS;
    }
  }

  private final String branchName;
  private final PipingFivLikelihood likelihood;
  private final boolean anyModeLockedIn;
  private final double sheddingFrequencyHz;
  private final double lowestResonanceVelocityMPerS;
  private final List<BranchMode> modes;
  private final Map<String, Double> contributingFactors;
  private final String recommendation;

  /**
   * Creates a flow-induced pulsation screening result.
   *
   * @param branchName branch identifier
   * @param likelihood derived likelihood band
   * @param anyModeLockedIn true when a resonance condition is possible at the screened velocity
   * @param sheddingFrequencyHz vortex-shedding frequency at the screened velocity, Hz
   * @param lowestResonanceVelocityMPerS lowest main-line velocity at which any evaluated mode resonates, m/s
   * @param modes acoustic modes of the branch
   * @param contributingFactors factor name to factor value
   * @param recommendation recommended action
   */
  public FlowInducedPulsationResult(String branchName, PipingFivLikelihood likelihood, boolean anyModeLockedIn,
      double sheddingFrequencyHz, double lowestResonanceVelocityMPerS, List<BranchMode> modes,
      Map<String, Double> contributingFactors, String recommendation) {
    this.branchName = branchName;
    this.likelihood = likelihood;
    this.anyModeLockedIn = anyModeLockedIn;
    this.sheddingFrequencyHz = sheddingFrequencyHz;
    this.lowestResonanceVelocityMPerS = lowestResonanceVelocityMPerS;
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
   * Reports whether a resonance condition is possible at the screened velocity.
   *
   * @return true when at least one mode is in lock-in
   */
  public boolean isAnyModeLockedIn() {
    return anyModeLockedIn;
  }

  /**
   * Gets the vortex-shedding frequency at the screened velocity.
   *
   * @return frequency, Hz
   */
  public double getSheddingFrequencyHz() {
    return sheddingFrequencyHz;
  }

  /**
   * Gets the lowest main-line velocity at which any evaluated mode resonates.
   *
   * @return velocity, m/s
   */
  public double getLowestResonanceVelocityMPerS() {
    return lowestResonanceVelocityMPerS;
  }

  /**
   * Gets the acoustic modes of the branch.
   *
   * @return modes (unmodifiable copy)
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
