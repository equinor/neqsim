package neqsim.process.fielddevelopment.tieback;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Report containing tieback analysis results for a discovery.
 *
 * <p>
 * The TiebackReport aggregates all evaluated tieback options and provides methods for:
 * </p>
 * <ul>
 * <li>Accessing ranked options (best NPV first)</li>
 * <li>Filtering by feasibility</li>
 * <li>Comparing options</li>
 * <li>Generating summary text</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * 
 * <pre>{@code
 * TiebackAnalyzer analyzer = new TiebackAnalyzer();
 * TiebackReport report = analyzer.analyze(discovery, hosts, 61.5, 2.3);
 * 
 * // Get best option
 * TiebackOption best = report.getBestOption();
 * 
 * // Get all feasible options
 * List<TiebackOption> feasible = report.getFeasibleOptions();
 * 
 * // Print summary
 * System.out.println(report.getSummary());
 * }</pre>
 *
 * @author ESOL
 * @version 1.0
 * @see TiebackAnalyzer
 * @see TiebackOption
 */
public class TiebackReport implements Serializable {
  private static final long serialVersionUID = 1000L;

  // ============================================================================
  // FIELDS
  // ============================================================================

  /** Discovery name. */
  private String discoveryName;

  /** All evaluated options (sorted by NPV, best first). */
  private List<TiebackOption> options;

  /** Discovery latitude. */
  private double discoveryLatitude;

  /** Discovery longitude. */
  private double discoveryLongitude;

  // ============================================================================
  // CONSTRUCTORS
  // ============================================================================

  /**
   * Creates a new tieback report.
   *
   * @param discoveryName name of the discovery
   * @param options list of evaluated options (will be sorted by NPV)
   * @param discoveryLatitude discovery latitude
   * @param discoveryLongitude discovery longitude
   */
  public TiebackReport(String discoveryName, List<TiebackOption> options, double discoveryLatitude,
      double discoveryLongitude) {
    this.discoveryName = discoveryName;
    this.options = new ArrayList<TiebackOption>(options);
    Collections.sort(this.options);
    this.discoveryLatitude = discoveryLatitude;
    this.discoveryLongitude = discoveryLongitude;
  }

  // ============================================================================
  // QUERY METHODS
  // ============================================================================

  /**
   * Gets the best option by NPV.
   *
   * @return the option with highest NPV, or null if no options
   */
  public TiebackOption getBestOption() {
    if (options.isEmpty()) {
      return null;
    }
    return options.get(0);
  }

  /**
   * Gets the best feasible option.
   *
   * @return the feasible option with highest NPV, or null if none feasible
   */
  public TiebackOption getBestFeasibleOption() {
    for (TiebackOption opt : options) {
      if (opt.isFeasible()) {
        return opt;
      }
    }
    return null;
  }

  /**
   * Gets all feasible options.
   *
   * @return list of feasible options, sorted by NPV
   */
  public List<TiebackOption> getFeasibleOptions() {
    List<TiebackOption> feasible = new ArrayList<TiebackOption>();
    for (TiebackOption opt : options) {
      if (opt.isFeasible()) {
        feasible.add(opt);
      }
    }
    return feasible;
  }

  /**
   * Gets options with positive NPV.
   *
   * @return list of options with NPV &gt; 0
   */
  public List<TiebackOption> getProfitableOptions() {
    List<TiebackOption> profitable = new ArrayList<TiebackOption>();
    for (TiebackOption opt : options) {
      if (opt.isFeasible() && opt.getNpvMusd() > 0) {
        profitable.add(opt);
      }
    }
    return profitable;
  }

  /**
   * Gets the number of options.
   *
   * @return number of options evaluated
   */
  public int getOptionCount() {
    return options.size();
  }

  /**
   * Gets the number of feasible options.
   *
   * @return number of feasible options
   */
  public int getFeasibleOptionCount() {
    int count = 0;
    for (TiebackOption opt : options) {
      if (opt.isFeasible()) {
        count++;
      }
    }
    return count;
  }

  /**
   * Gets the number of profitable options.
   *
   * @return number of options with NPV &gt; 0
   */
  public int getProfitableOptionCount() {
    int count = 0;
    for (TiebackOption opt : options) {
      if (opt.isFeasible() && opt.getNpvMusd() > 0) {
        count++;
      }
    }
    return count;
  }

  /**
   * Checks if any feasible option exists.
   *
   * @return true if at least one option is feasible
   */
  public boolean hasFeasibleOption() {
    return getFeasibleOptionCount() > 0;
  }

  /**
   * Checks if any profitable option exists.
   *
   * @return true if at least one option has NPV &gt; 0
   */
  public boolean hasProfitableOption() {
    return getProfitableOptionCount() > 0;
  }

  /**
   * Gets an option by host name.
   *
   * @param hostName name of the host facility
   * @return the option, or null if not found
   */
  public TiebackOption getOptionByHost(String hostName) {
    for (TiebackOption opt : options) {
      if (opt.getHostName().equals(hostName)) {
        return opt;
      }
    }
    return null;
  }

  // ============================================================================
  // COMPARISON METHODS
  // ============================================================================

  /**
   * Gets the NPV range across all feasible options.
   *
   * @return array with [min NPV, max NPV] in MUSD
   */
  public double[] getNpvRange() {
    double min = Double.MAX_VALUE;
    double max = Double.MIN_VALUE;

    for (TiebackOption opt : options) {
      if (opt.isFeasible()) {
        if (opt.getNpvMusd() < min) {
          min = opt.getNpvMusd();
        }
        if (opt.getNpvMusd() > max) {
          max = opt.getNpvMusd();
        }
      }
    }

    if (min == Double.MAX_VALUE) {
      return new double[] {0, 0};
    }

    return new double[] {min, max};
  }

  /**
   * Gets the CAPEX range across all feasible options.
   *
   * @return array with [min CAPEX, max CAPEX] in MUSD
   */
  public double[] getCapexRange() {
    double min = Double.MAX_VALUE;
    double max = Double.MIN_VALUE;

    for (TiebackOption opt : options) {
      if (opt.isFeasible()) {
        double capex = opt.getTotalCapexMusd();
        if (capex < min) {
          min = capex;
        }
        if (capex > max) {
          max = capex;
        }
      }
    }

    if (min == Double.MAX_VALUE) {
      return new double[] {0, 0};
    }

    return new double[] {min, max};
  }

  /**
   * Compares two options.
   *
   * @param host1 first host name
   * @param host2 second host name
   * @return comparison text
   */
  public String compareOptions(String host1, String host2) {
    TiebackOption opt1 = getOptionByHost(host1);
    TiebackOption opt2 = getOptionByHost(host2);

    if (opt1 == null || opt2 == null) {
      return "Cannot compare: option not found";
    }

    StringBuilder sb = new StringBuilder();
    sb.append("Comparison: ").append(host1).append(" vs ").append(host2).append("\n");
    sb.append(String.format("  Distance:      %.1f km vs %.1f km (Δ %.1f km)%n",
        opt1.getDistanceKm(), opt2.getDistanceKm(), opt1.getDistanceKm() - opt2.getDistanceKm()));
    sb.append(String.format("  CAPEX:         %.1f MUSD vs %.1f MUSD (Δ %.1f MUSD)%n",
        opt1.getTotalCapexMusd(), opt2.getTotalCapexMusd(),
        opt1.getTotalCapexMusd() - opt2.getTotalCapexMusd()));
    sb.append(String.format("  NPV:           %.1f MUSD vs %.1f MUSD (Δ %.1f MUSD)%n",
        opt1.getNpvMusd(), opt2.getNpvMusd(), opt1.getNpvMusd() - opt2.getNpvMusd()));
    sb.append(String.format("  IRR:           %.1f%% vs %.1f%%", opt1.getIrr() * 100,
        opt2.getIrr() * 100));

    return sb.toString();
  }

  // ============================================================================
  // SUMMARY METHODS
  // ============================================================================

  /**
   * Gets a summary of the tieback analysis.
   *
   * @return multi-line summary text
   */
  public String getSummary() {
    StringBuilder sb = new StringBuilder();
    sb.append(repeatChar('=', 70)).append("\n");
    sb.append("TIEBACK ANALYSIS REPORT: ").append(discoveryName).append("\n");
    sb.append(repeatChar('=', 70)).append("\n");
    sb.append(String.format("Discovery location: %.4f°N, %.4f°E%n", discoveryLatitude,
        discoveryLongitude));
    sb.append(String.format("Options evaluated: %d%n", options.size()));
    sb.append(String.format("Feasible options: %d%n", getFeasibleOptionCount()));
    sb.append(String.format("Profitable options: %d%n", getProfitableOptionCount()));
    sb.append("\n");

    if (!options.isEmpty()) {
      sb.append(repeatChar('-', 70)).append("\n");
      sb.append(String.format("%-20s %8s %10s %10s %8s %10s%n", "Host", "Distance", "CAPEX", "NPV",
          "IRR", "Status"));
      sb.append(String.format("%-20s %8s %10s %10s %8s %10s%n", "", "(km)", "(MUSD)", "(MUSD)",
          "(%)", ""));
      sb.append(repeatChar('-', 70)).append("\n");

      for (TiebackOption opt : options) {
        String status = opt.isFeasible() ? (opt.getNpvMusd() > 0 ? "VIABLE" : "NEGATIVE") : "FAIL";
        sb.append(String.format("%-20s %8.1f %10.1f %10.1f %8.1f %10s%n",
            truncate(opt.getHostName(), 20), opt.getDistanceKm(), opt.getTotalCapexMusd(),
            opt.getNpvMusd(), opt.getIrr() * 100, status));
      }

      sb.append(repeatChar('-', 70)).append("\n");
    }

    // Best option summary
    TiebackOption best = getBestFeasibleOption();
    if (best != null) {
      sb.append("\nBEST OPTION: ").append(best.getHostName()).append("\n");
      sb.append(String.format("  Distance: %.1f km%n", best.getDistanceKm()));
      sb.append(String.format("  CAPEX: %.1f MUSD%n", best.getTotalCapexMusd()));
      sb.append(String.format("  NPV: %.1f MUSD%n", best.getNpvMusd()));
      sb.append(String.format("  IRR: %.1f%%%n", best.getIrr() * 100));
      sb.append(String.format("  Payback: %.1f years%n", best.getPaybackYears()));
      sb.append(String.format("  Flow Assurance: %s%n", best.getOverallFlowAssuranceResult()));
    } else {
      sb.append("\nNO FEASIBLE OPTIONS FOUND\n");
    }

    return sb.toString();
  }

  /**
   * Gets a short summary suitable for logging.
   *
   * @return single-line summary
   */
  public String getShortSummary() {
    TiebackOption best = getBestFeasibleOption();
    if (best == null) {
      return discoveryName + ": No feasible tieback option";
    }
    return String.format("%s: Best = %s (NPV %.1f MUSD, IRR %.1f%%)", discoveryName,
        best.getHostName(), best.getNpvMusd(), best.getIrr() * 100);
  }

  /**
   * Gets a recommendation based on the analysis.
   *
   * @return recommendation text
   */
  public String getRecommendation() {
    TiebackOption best = getBestFeasibleOption();

    if (best == null) {
      return "No feasible tieback option identified. Consider standalone development or new host.";
    }

    if (best.getNpvMusd() <= 0) {
      return "All options have negative NPV. Development is not economic at current assumptions.";
    }

    if (best.getIrr() < 0.10) {
      return String.format(
          "Best option (%s) has low return (IRR %.1f%%). Proceed with caution or optimize scope.",
          best.getHostName(), best.getIrr() * 100);
    }

    if (best.hasFlowAssuranceIssues()) {
      return String.format(
          "Best option (%s) has good economics but flow assurance issues. Detailed FA study "
              + "required before sanction.",
          best.getHostName());
    }

    return String.format(
        "Recommend tieback to %s. NPV %.1f MUSD, IRR %.1f%%, payback %.1f years. "
            + "Proceed to detailed engineering.",
        best.getHostName(), best.getNpvMusd(), best.getIrr() * 100, best.getPaybackYears());
  }

  // ============================================================================
  // CSV/TABLE EXPORT
  // ============================================================================

  /**
   * Gets a CSV representation of all options.
   *
   * @return CSV text with header row
   */
  public String toCsv() {
    StringBuilder sb = new StringBuilder();
    sb.append("Host,Distance_km,WaterDepth_m,Wells,CAPEX_MUSD,NPV_MUSD,IRR_pct,Payback_yr,"
        + "Feasible,FlowAssurance\n");

    for (TiebackOption opt : options) {
      sb.append(String.format("%s,%.1f,%.0f,%d,%.1f,%.1f,%.1f,%.1f,%s,%s%n", opt.getHostName(),
          opt.getDistanceKm(), opt.getMaxWaterDepthM(), opt.getWellCount(), opt.getTotalCapexMusd(),
          opt.getNpvMusd(), opt.getIrr() * 100, opt.getPaybackYears(),
          opt.isFeasible() ? "Yes" : "No", opt.getOverallFlowAssuranceResult()));
    }

    return sb.toString();
  }

  /**
   * Gets a markdown table representation.
   *
   * @return markdown table text
   */
  public String toMarkdownTable() {
    StringBuilder sb = new StringBuilder();
    sb.append("| Host | Distance (km) | CAPEX (MUSD) | NPV (MUSD) | IRR (%) | Status |\n");
    sb.append("|------|--------------|--------------|------------|---------|--------|\n");

    for (TiebackOption opt : options) {
      String status =
          opt.isFeasible() ? (opt.getNpvMusd() > 0 ? "✓ Viable" : "⚠ Negative") : "✗ Fail";
      sb.append(String.format("| %s | %.1f | %.1f | %.1f | %.1f | %s |%n", opt.getHostName(),
          opt.getDistanceKm(), opt.getTotalCapexMusd(), opt.getNpvMusd(), opt.getIrr() * 100,
          status));
    }

    return sb.toString();
  }

  // ============================================================================
  // GETTERS
  // ============================================================================

  /**
   * Gets the discovery name.
   *
   * @return discovery name
   */
  public String getDiscoveryName() {
    return discoveryName;
  }

  /**
   * Gets all options.
   *
   * @return list of all options (sorted by NPV)
   */
  public List<TiebackOption> getOptions() {
    return Collections.unmodifiableList(options);
  }

  /**
   * Gets the discovery latitude.
   *
   * @return latitude in degrees
   */
  public double getDiscoveryLatitude() {
    return discoveryLatitude;
  }

  /**
   * Gets the discovery longitude.
   *
   * @return longitude in degrees
   */
  public double getDiscoveryLongitude() {
    return discoveryLongitude;
  }

  // ============================================================================
  // UTILITY
  // ============================================================================

  /**
   * Truncates a string to the specified length.
   *
   * @param s string to truncate
   * @param maxLen maximum length
   * @return truncated string
   */
  private static String truncate(String s, int maxLen) {
    if (s == null) {
      return "";
    }
    if (s.length() <= maxLen) {
      return s;
    }
    return s.substring(0, maxLen - 2) + "..";
  }

  /**
   * Repeats a character n times (Java 8 compatible).
   *
   * @param c character to repeat
   * @param n number of times
   * @return string with character repeated n times
   */
  private static String repeatChar(char c, int n) {
    StringBuilder sb = new StringBuilder(n);
    for (int i = 0; i < n; i++) {
      sb.append(c);
    }
    return sb.toString();
  }

  @Override
  public String toString() {
    return getShortSummary();
  }
}
