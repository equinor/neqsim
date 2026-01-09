package neqsim.process.fielddevelopment.network;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Result container for network solver.
 *
 * <p>
 * Contains all outputs from a network solution including well rates, pressures, and convergence
 * information.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 * @see NetworkSolver
 */
public class NetworkResult implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Network name. */
  public String networkName;

  /** Solution mode used. */
  public NetworkSolver.SolutionMode solutionMode;

  /** Manifold pressure (bara). */
  public double manifoldPressure;

  /** Total production rate (Sm3/day). */
  public double totalRate;

  /** Individual well rates (Sm3/day). */
  public Map<String, Double> wellRates = new HashMap<>();

  /** Wellhead pressures (bara). */
  public Map<String, Double> wellheadPressures = new HashMap<>();

  /** Flowline pressure drops (bar). */
  public Map<String, Double> flowlinePressureDrops = new HashMap<>();

  /** Well enabled status. */
  public Map<String, Boolean> wellEnabled = new HashMap<>();

  /** Whether the solver converged. */
  public boolean converged;

  /** Number of iterations. */
  public int iterations;

  /** Final residual. */
  public double residual;

  /**
   * Creates a new network result.
   *
   * @param networkName network name
   */
  public NetworkResult(String networkName) {
    this.networkName = networkName;
  }

  /**
   * Gets the total production rate in specified unit.
   *
   * @param unit rate unit (Sm3/day, MSm3/day, bbl/day)
   * @return total rate
   */
  public double getTotalRate(String unit) {
    if (unit.equalsIgnoreCase("MSm3/day") || unit.equalsIgnoreCase("MSm3/d")) {
      return totalRate / 1e6;
    } else if (unit.equalsIgnoreCase("bbl/day") || unit.equalsIgnoreCase("bpd")) {
      return totalRate / 0.159;
    }
    return totalRate;
  }

  /**
   * Gets a well's production rate.
   *
   * @param wellName well name
   * @param unit rate unit
   * @return well rate
   */
  public double getWellRate(String wellName, String unit) {
    double rate = wellRates.getOrDefault(wellName, 0.0);
    if (unit.equalsIgnoreCase("MSm3/day") || unit.equalsIgnoreCase("MSm3/d")) {
      return rate / 1e6;
    } else if (unit.equalsIgnoreCase("bbl/day") || unit.equalsIgnoreCase("bpd")) {
      return rate / 0.159;
    }
    return rate;
  }

  /**
   * Gets a well's wellhead pressure.
   *
   * @param wellName well name
   * @return wellhead pressure in bara
   */
  public double getWellheadPressure(String wellName) {
    return wellheadPressures.getOrDefault(wellName, 0.0);
  }

  /**
   * Gets a well's flowline pressure drop.
   *
   * @param wellName well name
   * @return pressure drop in bar
   */
  public double getFlowlinePressureDrop(String wellName) {
    return flowlinePressureDrops.getOrDefault(wellName, 0.0);
  }

  /**
   * Checks if a well is enabled.
   *
   * @param wellName well name
   * @return true if enabled
   */
  public boolean isWellEnabled(String wellName) {
    return wellEnabled.getOrDefault(wellName, false);
  }

  /**
   * Gets the number of producing wells.
   *
   * @return producing well count
   */
  public int getProducingWellCount() {
    return (int) wellEnabled.values().stream().filter(e -> e).count();
  }

  /**
   * Gets a markdown summary table.
   *
   * @return markdown table
   */
  public String getSummaryTable() {
    StringBuilder sb = new StringBuilder();
    sb.append("# Network: ").append(networkName).append("\n\n");
    sb.append("**Manifold Pressure:** ").append(String.format("%.1f bara", manifoldPressure))
        .append("\n");
    sb.append("**Total Rate:** ").append(String.format("%.2f MSm3/day", totalRate / 1e6))
        .append("\n");
    sb.append("**Converged:** ").append(converged ? "Yes" : "No");
    sb.append(" (").append(iterations).append(" iterations)\n\n");

    sb.append("| Well | Status | Rate (MSm3/d) | WHP (bara) | ΔP Flowline (bar) |\n");
    sb.append("|------|--------|---------------|------------|-------------------|\n");

    for (String wellName : wellRates.keySet()) {
      boolean enabled = wellEnabled.getOrDefault(wellName, false);
      double rate = wellRates.getOrDefault(wellName, 0.0);
      double whp = wellheadPressures.getOrDefault(wellName, 0.0);
      double dp = flowlinePressureDrops.getOrDefault(wellName, 0.0);

      sb.append(String.format("| %s | %s | %.2f | %.1f | %.1f |\n", wellName,
          enabled ? "ON" : "OFF", rate / 1e6, whp, dp));
    }

    return sb.toString();
  }

  @Override
  public String toString() {
    return String.format("NetworkResult[%s, rate=%.2f MSm3/d, pManifold=%.1f bar, converged=%s]",
        networkName, totalRate / 1e6, manifoldPressure, converged);
  }
}
