package neqsim.process.safety.overpressure;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * Immutable result of a {@link ReliefDisposalNetwork} aggregation.
 *
 * <p>
 * Reports the total simultaneous relief load that the shared flare or vent header must handle (the sum of all
 * concurrent contributions per API STD 521 section 5.3), the peak single non-concurrent contribution, and the governing
 * contributor.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public final class ReliefDisposalResult implements Serializable {
  private static final long serialVersionUID = 1L;

  private final String networkName;
  private final double totalSimultaneousKgPerS;
  private final double peakSingleKgPerS;
  private final String governingContributor;
  private final List<ReliefLoadContribution> contributions;
  private final List<String> warnings;

  /**
   * Creates an immutable relief disposal result.
   *
   * @param networkName the network name; not null
   * @param totalSimultaneousKgPerS the summed concurrent relief load [kg/s]
   * @param peakSingleKgPerS the largest single contribution [kg/s]
   * @param governingContributor the name of the largest single contributor; may be null when no contributions exist
   * @param contributions the contributions; not null
   * @param warnings any warnings raised during aggregation; not null
   */
  public ReliefDisposalResult(String networkName, double totalSimultaneousKgPerS, double peakSingleKgPerS,
      String governingContributor, List<ReliefLoadContribution> contributions, List<String> warnings) {
    this.networkName = networkName;
    this.totalSimultaneousKgPerS = totalSimultaneousKgPerS;
    this.peakSingleKgPerS = peakSingleKgPerS;
    this.governingContributor = governingContributor;
    this.contributions = Collections.unmodifiableList(contributions);
    this.warnings = Collections.unmodifiableList(warnings);
  }

  /**
   * Gets the network name.
   *
   * @return the network name
   */
  public String getNetworkName() {
    return networkName;
  }

  /**
   * Gets the summed concurrent relief load that the disposal header must handle.
   *
   * @return the total simultaneous relief load [kg/s]
   */
  public double getTotalSimultaneousKgPerS() {
    return totalSimultaneousKgPerS;
  }

  /**
   * Gets the summed concurrent relief load in kg/hr.
   *
   * @return the total simultaneous relief load [kg/hr]
   */
  public double getTotalSimultaneousKgPerHr() {
    return totalSimultaneousKgPerS * 3600.0;
  }

  /**
   * Gets the largest single relief contribution.
   *
   * @return the peak single contribution [kg/s]
   */
  public double getPeakSingleKgPerS() {
    return peakSingleKgPerS;
  }

  /**
   * Gets the name of the largest single contributor.
   *
   * @return the governing contributor name, or null when there are no contributions
   */
  public String getGoverningContributor() {
    return governingContributor;
  }

  /**
   * Gets the list of contributions.
   *
   * @return an unmodifiable list of contributions
   */
  public List<ReliefLoadContribution> getContributions() {
    return contributions;
  }

  /**
   * Gets the warnings raised during aggregation.
   *
   * @return an unmodifiable list of warnings
   */
  public List<String> getWarnings() {
    return warnings;
  }

  /**
   * Serializes this relief disposal roll-up to a human-readable JSON string.
   *
   * @return JSON representation of this result
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create().toJson(this);
  }
}
