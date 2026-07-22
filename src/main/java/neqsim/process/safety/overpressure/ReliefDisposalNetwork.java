package neqsim.process.safety.overpressure;

import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Aggregates the governing relief loads of several protected items that discharge into a shared disposal system (flare
 * or vent header), so that the total simultaneous load can be established for header and flare-tip sizing.
 *
 * <p>
 * Per API STD 521 section 5.3 and TR3001 section 4.9, the relief loads that can occur at the same time (for example all
 * vessels exposed to a single fire zone) are summed to give the network design load. Loads flagged as non-simultaneous
 * contribute only to the peak-single comparison. A typical workflow runs an {@link OverpressureProtectionStudy} per
 * protected item and feeds each {@link OverpressureStudyResult} into this network.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public final class ReliefDisposalNetwork {
  private static final Logger logger = LogManager.getLogger(ReliefDisposalNetwork.class);

  private final String name;
  private final List<ReliefLoadContribution> contributions = new ArrayList<ReliefLoadContribution>();
  private final List<String> warnings = new ArrayList<String>();

  /**
   * Creates a relief disposal network.
   *
   * @param name the network name; not null
   */
  public ReliefDisposalNetwork(String name) {
    this.name = name;
  }

  /**
   * Adds the governing relief load of a completed study to the network as a simultaneous contribution.
   *
   * @param result the overpressure study result; not null
   * @return this network for fluent chaining
   */
  public ReliefDisposalNetwork addRelief(OverpressureStudyResult result) {
    return addRelief(result, true);
  }

  /**
   * Adds the governing relief load of a completed study to the network.
   *
   * @param result the overpressure study result; not null
   * @param simultaneous true if the load is concurrent with the other network contributions
   * @return this network for fluent chaining
   */
  public ReliefDisposalNetwork addRelief(OverpressureStudyResult result, boolean simultaneous) {
    ReliefScenario governing = result.getGoverningScenario();
    if (governing == null) {
      warnings.add("Study for " + result.getItem().getName() + " has no governing case; contribution skipped");
      return this;
    }
    contributions.add(new ReliefLoadContribution(result.getItem().getName(), governing.getCause(), governing.getPhase(),
        governing.getReliefRateKgPerS(), simultaneous));
    return this;
  }

  /**
   * Adds an explicit relief load contribution to the network.
   *
   * @param contribution the contribution to add; not null
   * @return this network for fluent chaining
   */
  public ReliefDisposalNetwork addContribution(ReliefLoadContribution contribution) {
    contributions.add(contribution);
    return this;
  }

  /**
   * Aggregates the contributions into a {@link ReliefDisposalResult}.
   *
   * @return the immutable disposal result
   */
  public ReliefDisposalResult calculate() {
    double totalSimultaneous = 0.0;
    double peakSingle = 0.0;
    String governingContributor = null;
    for (ReliefLoadContribution contribution : contributions) {
      if (contribution.isSimultaneous()) {
        totalSimultaneous += contribution.getMassFlowKgPerS();
      }
      if (contribution.getMassFlowKgPerS() > peakSingle) {
        peakSingle = contribution.getMassFlowKgPerS();
        governingContributor = contribution.getItemName();
      }
    }
    if (contributions.isEmpty()) {
      warnings.add("No relief contributions were added to the disposal network");
    }
    logger.info("Relief disposal network '{}': total simultaneous load {} kg/s ({} kg/hr) from {} contribution(s)",
        name, String.format("%.3f", totalSimultaneous), String.format("%.0f", totalSimultaneous * 3600.0),
        contributions.size());
    return new ReliefDisposalResult(name, totalSimultaneous, peakSingle, governingContributor,
        new ArrayList<ReliefLoadContribution>(contributions), new ArrayList<String>(warnings));
  }

  /**
   * Gets the network name.
   *
   * @return the network name
   */
  public String getName() {
    return name;
  }
}
