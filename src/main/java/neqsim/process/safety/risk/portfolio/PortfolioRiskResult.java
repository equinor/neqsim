package neqsim.process.safety.risk.portfolio;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import com.google.gson.GsonBuilder;

/**
 * Results from Portfolio Risk Analysis.
 *
 * <p>
 * Contains aggregated results from multi-asset Monte Carlo simulation, including portfolio-level
 * statistics, asset contributions, common cause impacts, and diversification benefits.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class PortfolioRiskResult implements Serializable {

  private static final long serialVersionUID = 1000L;

  /** Analysis name. */
  private String analysisName;

  /** Number of simulations. */
  private int numberOfSimulations;

  /** Simulation period in years. */
  private double simulationPeriodYears;

  // Portfolio-level production
  private double totalMaxProduction;
  private double totalExpectedProduction;
  private double portfolioAvailability;

  // Portfolio-level loss statistics (boe)
  private double expectedPortfolioLoss;
  private double portfolioLossStdDev;
  private double p10PortfolioLoss;
  private double p50PortfolioLoss;
  private double p90PortfolioLoss;
  private double p99PortfolioLoss;
  private double maxPortfolioLoss;

  // Common cause contribution
  private double expectedCommonCauseLoss;
  private double commonCauseFraction;

  // Diversification
  private double diversificationBenefit;

  // Asset-level results
  private List<AssetResult> assetResults;

  /**
   * Asset-level result.
   */
  public static class AssetResult implements Serializable {
    private static final long serialVersionUID = 1L;

    private String assetId;
    private String assetName;
    private double maxProduction;
    private double expectedProduction;
    private double availability;
    private double expectedLoss;
    private double p90Loss;
    private double contributionToPortfolioRisk;

    public String getAssetId() {
      return assetId;
    }

    public void setAssetId(String id) {
      this.assetId = id;
    }

    public String getAssetName() {
      return assetName;
    }

    public void setAssetName(String name) {
      this.assetName = name;
    }

    public double getMaxProduction() {
      return maxProduction;
    }

    public void setMaxProduction(double prod) {
      this.maxProduction = prod;
    }

    public double getExpectedProduction() {
      return expectedProduction;
    }

    public void setExpectedProduction(double prod) {
      this.expectedProduction = prod;
    }

    public double getAvailability() {
      return availability;
    }

    public void setAvailability(double avail) {
      this.availability = avail;
    }

    public double getExpectedLoss() {
      return expectedLoss;
    }

    public void setExpectedLoss(double loss) {
      this.expectedLoss = loss;
    }

    public double getP90Loss() {
      return p90Loss;
    }

    public void setP90Loss(double loss) {
      this.p90Loss = loss;
    }

    public double getContributionToPortfolioRisk() {
      return contributionToPortfolioRisk;
    }

    public void setContributionToPortfolioRisk(double contrib) {
      this.contributionToPortfolioRisk = contrib;
    }

    public Map<String, Object> toMap() {
      Map<String, Object> map = new HashMap<>();
      map.put("assetId", assetId);
      map.put("assetName", assetName);
      map.put("maxProduction", maxProduction);
      map.put("expectedProduction", expectedProduction);
      map.put("availability", availability);
      map.put("expectedLoss", expectedLoss);
      map.put("p90Loss", p90Loss);
      map.put("contributionToPortfolioRisk", contributionToPortfolioRisk);
      return map;
    }
  }

  /**
   * Creates a portfolio risk result.
   *
   * @param analysisName name of analysis
   */
  public PortfolioRiskResult(String analysisName) {
    this.analysisName = analysisName;
    this.assetResults = new ArrayList<>();
  }

  // Setters

  public void setNumberOfSimulations(int n) {
    this.numberOfSimulations = n;
  }

  public void setSimulationPeriodYears(double years) {
    this.simulationPeriodYears = years;
  }

  public void setTotalMaxProduction(double prod) {
    this.totalMaxProduction = prod;
  }

  public void setTotalExpectedProduction(double prod) {
    this.totalExpectedProduction = prod;
  }

  public void setPortfolioAvailability(double avail) {
    this.portfolioAvailability = avail;
  }

  public void setExpectedPortfolioLoss(double loss) {
    this.expectedPortfolioLoss = loss;
  }

  public void setPortfolioLossStdDev(double stdDev) {
    this.portfolioLossStdDev = stdDev;
  }

  public void setP10PortfolioLoss(double loss) {
    this.p10PortfolioLoss = loss;
  }

  public void setP50PortfolioLoss(double loss) {
    this.p50PortfolioLoss = loss;
  }

  public void setP90PortfolioLoss(double loss) {
    this.p90PortfolioLoss = loss;
  }

  public void setP99PortfolioLoss(double loss) {
    this.p99PortfolioLoss = loss;
  }

  public void setMaxPortfolioLoss(double loss) {
    this.maxPortfolioLoss = loss;
  }

  public void setExpectedCommonCauseLoss(double loss) {
    this.expectedCommonCauseLoss = loss;
  }

  public void setCommonCauseFraction(double fraction) {
    this.commonCauseFraction = fraction;
  }

  public void setDiversificationBenefit(double benefit) {
    this.diversificationBenefit = benefit;
  }

  public void addAssetResult(AssetResult result) {
    assetResults.add(result);
  }

  // Getters

  public String getAnalysisName() {
    return analysisName;
  }

  public int getNumberOfSimulations() {
    return numberOfSimulations;
  }

  public double getSimulationPeriodYears() {
    return simulationPeriodYears;
  }

  public double getTotalMaxProduction() {
    return totalMaxProduction;
  }

  public double getTotalExpectedProduction() {
    return totalExpectedProduction;
  }

  public double getPortfolioAvailability() {
    return portfolioAvailability;
  }

  public double getExpectedPortfolioLoss() {
    return expectedPortfolioLoss;
  }

  public double getPortfolioLossStdDev() {
    return portfolioLossStdDev;
  }

  public double getP10PortfolioLoss() {
    return p10PortfolioLoss;
  }

  public double getP50PortfolioLoss() {
    return p50PortfolioLoss;
  }

  public double getP90PortfolioLoss() {
    return p90PortfolioLoss;
  }

  public double getP99PortfolioLoss() {
    return p99PortfolioLoss;
  }

  public double getMaxPortfolioLoss() {
    return maxPortfolioLoss;
  }

  public double getExpectedCommonCauseLoss() {
    return expectedCommonCauseLoss;
  }

  public double getCommonCauseFraction() {
    return commonCauseFraction;
  }

  public double getDiversificationBenefit() {
    return diversificationBenefit;
  }

  public List<AssetResult> getAssetResults() {
    return new ArrayList<>(assetResults);
  }

  /**
   * Gets Value at Risk at specified confidence level.
   *
   * @param confidencePercent confidence level (e.g., 95, 99)
   * @return VaR value
   */
  public double getValueAtRisk(int confidencePercent) {
    switch (confidencePercent) {
      case 90:
        return p90PortfolioLoss;
      case 99:
        return p99PortfolioLoss;
      default:
        // Approximate using normal distribution
        double z = getNormalZ(confidencePercent / 100.0);
        return expectedPortfolioLoss + z * portfolioLossStdDev;
    }
  }

  private double getNormalZ(double confidence) {
    // Approximate inverse normal CDF
    if (confidence >= 0.99) {
      return 2.326;
    }
    if (confidence >= 0.95) {
      return 1.645;
    }
    if (confidence >= 0.90) {
      return 1.282;
    }
    return 1.0;
  }

  /**
   * Converts to map for JSON serialization.
   *
   * @return map representation
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("analysisName", analysisName);
    map.put("numberOfSimulations", numberOfSimulations);
    map.put("simulationPeriodYears", simulationPeriodYears);

    // Portfolio production
    Map<String, Object> production = new HashMap<>();
    production.put("totalMaxProduction", totalMaxProduction);
    production.put("totalExpectedProduction", totalExpectedProduction);
    production.put("portfolioAvailability", portfolioAvailability);
    map.put("production", production);

    // Loss statistics
    Map<String, Object> losses = new HashMap<>();
    losses.put("expected", expectedPortfolioLoss);
    losses.put("stdDev", portfolioLossStdDev);
    losses.put("p10", p10PortfolioLoss);
    losses.put("p50", p50PortfolioLoss);
    losses.put("p90", p90PortfolioLoss);
    losses.put("p99", p99PortfolioLoss);
    losses.put("max", maxPortfolioLoss);
    map.put("portfolioLoss", losses);

    // Common cause
    Map<String, Object> commonCause = new HashMap<>();
    commonCause.put("expectedLoss", expectedCommonCauseLoss);
    commonCause.put("fractionOfTotal", commonCauseFraction);
    map.put("commonCause", commonCause);

    // Diversification
    map.put("diversificationBenefit", diversificationBenefit);

    // Value at Risk
    Map<String, Object> var = new HashMap<>();
    var.put("var90", p90PortfolioLoss);
    var.put("var99", p99PortfolioLoss);
    map.put("valueAtRisk", var);

    // Asset results
    List<Map<String, Object>> assetList = new ArrayList<>();
    for (AssetResult ar : assetResults) {
      assetList.add(ar.toMap());
    }
    map.put("assetResults", assetList);

    return map;
  }

  /**
   * Converts to JSON string.
   *
   * @return JSON representation
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(toMap());
  }

  /**
   * Generates summary report.
   *
   * @return report string
   */
  public String toReport() {
    StringBuilder sb = new StringBuilder();
    sb.append("PORTFOLIO RISK ANALYSIS REPORT\n");
    sb.append(StringUtils.repeat("═", 70)).append("\n\n");

    sb.append("Analysis: ").append(analysisName).append("\n");
    sb.append(String.format("Simulations: %,d over %.1f years%n", numberOfSimulations,
        simulationPeriodYears));
    sb.append("\n");

    sb.append("PORTFOLIO PRODUCTION\n");
    sb.append(StringUtils.repeat("─", 40)).append("\n");
    sb.append(String.format("  Total Capacity:    %,.0f boe/day%n", totalMaxProduction));
    sb.append(String.format("  Expected Output:   %,.0f boe/day%n", totalExpectedProduction));
    sb.append(String.format("  Availability:      %.1f%%%n", portfolioAvailability * 100));
    sb.append("\n");

    sb.append("PRODUCTION LOSS (boe/period)\n");
    sb.append(StringUtils.repeat("─", 40)).append("\n");
    sb.append(String.format("  Expected:          %,.0f%n", expectedPortfolioLoss));
    sb.append(String.format("  Std Dev:           %,.0f%n", portfolioLossStdDev));
    sb.append(String.format("  P10 (optimistic):  %,.0f%n", p10PortfolioLoss));
    sb.append(String.format("  P50 (median):      %,.0f%n", p50PortfolioLoss));
    sb.append(String.format("  P90 (pessimistic): %,.0f%n", p90PortfolioLoss));
    sb.append(String.format("  P99 (extreme):     %,.0f%n", p99PortfolioLoss));
    sb.append("\n");

    sb.append("COMMON CAUSE ANALYSIS\n");
    sb.append(StringUtils.repeat("─", 40)).append("\n");
    sb.append(String.format("  Common Cause Loss: %,.0f boe (%.1f%% of total)%n",
        expectedCommonCauseLoss, commonCauseFraction * 100));
    sb.append(String.format("  Diversification:   %.1f%% benefit%n", diversificationBenefit * 100));
    sb.append("\n");

    sb.append("ASSET CONTRIBUTIONS\n");
    sb.append(StringUtils.repeat("─", 60)).append("\n");
    sb.append(String.format("%-20s %12s %12s %12s%n", "Asset", "Production", "Loss", "% Risk"));
    sb.append(StringUtils.repeat("─", 60)).append("\n");
    for (AssetResult ar : assetResults) {
      sb.append(String.format("%-20s %,12.0f %,12.0f %11.1f%%%n", ar.getAssetName(),
          ar.getExpectedProduction(), ar.getExpectedLoss(),
          ar.getContributionToPortfolioRisk() * 100));
    }

    return sb.toString();
  }

  @Override
  public String toString() {
    return String.format("PortfolioRiskResult[%s: %.0f boe expected loss, %.1f%% availability]",
        analysisName, expectedPortfolioLoss, portfolioAvailability * 100);
  }
}
